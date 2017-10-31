// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow.OnDismissListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.TabLoadStatus;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager.FullscreenListener;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.ActionModeController.ActionBarDelegate;
import org.chromium.chrome.browser.toolbar.BottomToolbarPhone;
import org.chromium.chrome.browser.toolbar.ViewShiftingActionBarDelegate;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.FadingBackgroundView;
import org.chromium.chrome.browser.widget.ViewHighlighter;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController.ContentType;
import org.chromium.chrome.browser.widget.textbubble.ViewAnchoredTextBubble;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.BrowserControlsState;
import org.chromium.ui.UiUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * This class defines the bottom sheet that has multiple states and a persistently showing toolbar.
 * Namely, the states are:
 * - PEEK: Only the toolbar is visible at the bottom of the screen.
 * - HALF: The sheet is expanded to consume around half of the screen.
 * - FULL: The sheet is expanded to its full height.
 *
 * All the computation in this file is based off of the bottom of the screen instead of the top
 * for simplicity. This means that the bottom of the screen is 0 on the Y axis.
 */
public class BottomSheet
        extends FrameLayout implements FadingBackgroundView.FadingViewObserver, NativePageHost {
    /** The different states that the bottom sheet can have. */
    @IntDef({SHEET_STATE_NONE, SHEET_STATE_PEEK, SHEET_STATE_HALF, SHEET_STATE_FULL,
            SHEET_STATE_SCROLLING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SheetState {}
    /**
     * SHEET_STATE_NONE is for internal use only and indicates the sheet is not currently
     * transitioning between states.
     */
    private static final int SHEET_STATE_NONE = -1;
    public static final int SHEET_STATE_PEEK = 0;
    public static final int SHEET_STATE_HALF = 1;
    public static final int SHEET_STATE_FULL = 2;
    public static final int SHEET_STATE_SCROLLING = 3;

    /** The different reasons that the sheet's state can change. */
    @IntDef({StateChangeReason.NONE, StateChangeReason.OMNIBOX_FOCUS, StateChangeReason.SWIPE,
            StateChangeReason.NEW_TAB, StateChangeReason.EXPAND_BUTTON, StateChangeReason.STARTUP,
            StateChangeReason.BACK_PRESS, StateChangeReason.TAP_SCRIM,
            StateChangeReason.NAVIGATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StateChangeReason {
        int NONE = 0;
        int OMNIBOX_FOCUS = 1;
        int SWIPE = 2;
        int NEW_TAB = 3;
        int EXPAND_BUTTON = 4;
        int STARTUP = 5;
        int BACK_PRESS = 6;
        int TAP_SCRIM = 7;
        int NAVIGATION = 8;
    }

    /**
     * The base duration of the settling animation of the sheet. 218 ms is a spec for material
     * design (this is the minimum time a user is guaranteed to pay attention to something).
     */
    public static final long BASE_ANIMATION_DURATION_MS = 218;

    /** The amount of time it takes to transition sheet content in or out. */
    private static final long TRANSITION_DURATION_MS = 150;

    /**
     * The fraction of the way to the next state the sheet must be swiped to animate there when
     * released. This is the value used when there are 3 active states. A smaller value here means
     * a smaller swipe is needed to move the sheet around.
     */
    private static final float THRESHOLD_TO_NEXT_STATE_3 = 0.5f;

    /** This is similar to {@link #THRESHOLD_TO_NEXT_STATE_3} but for 2 states instead of 3. */
    private static final float THRESHOLD_TO_NEXT_STATE_2 = 0.3f;

    /** The minimum y/x ratio that a scroll must have to be considered vertical. */
    private static final float MIN_VERTICAL_SCROLL_SLOPE = 2.0f;

    /** The height ratio for the sheet in the SHEET_STATE_HALF state. */
    private static final float HALF_HEIGHT_RATIO = 0.55f;

    /** The fraction of the width of the screen that, when swiped, will cause the sheet to move. */
    private static final float SWIPE_ALLOWED_FRACTION = 0.2f;

    /** The threshold of application screen height for showing a tall bottom navigation bar. */
    private static final float TALL_BOTTOM_NAV_THRESHOLD_DP = 683.0f;

    /**
     * Information about the different scroll states of the sheet. Order is important for these,
     * they go from smallest to largest.
     */
    private static final int[] sStates =
            new int[] {SHEET_STATE_PEEK, SHEET_STATE_HALF, SHEET_STATE_FULL};
    private final float[] mStateRatios = new float[3];

    /** The interpolator that the height animator uses. */
    private final Interpolator mInterpolator = new DecelerateInterpolator(1.0f);

    /** The list of observers of this sheet. */
    private final ObserverList<BottomSheetObserver> mObservers = new ObserverList<>();

    /** This is a cached array for getting the window location of different views. */
    private final int[] mLocationArray = new int[2];

    /** The visible rect for the screen taking the keyboard into account. */
    private final Rect mVisibleViewportRect = new Rect();

    /** The minimum distance between half and full states to allow the half state. */
    private final float mMinHalfFullDistance;

    /** The height of the shadow that sits above the toolbar. */
    private final int mToolbarShadowHeight;

    /** The height of the bottom navigation bar that appears when the bottom sheet is expanded. */
    private final int mBottomNavHeight;

    /** Whether a tall bottom navigation bar should be used */
    private final boolean mUseTallBottomNav;

    /** The {@link BottomSheetMetrics} used to record user actions and histograms. */
    private final BottomSheetMetrics mMetrics;

    /** The {@link BottomSheetNewTabController} used to present the new tab UI. */
    private BottomSheetNewTabController mNtpController;

    /** For detecting scroll and fling events on the bottom sheet. */
    private GestureDetector mGestureDetector;

    /** Whether or not the user is scrolling the bottom sheet. */
    private boolean mIsScrolling;

    /** Track the velocity of the user's scrolls to determine up or down direction. */
    private VelocityTracker mVelocityTracker;

    /** The animator used to move the sheet to a fixed state when released by the user. */
    private ValueAnimator mSettleAnimator;

    /** The animator set responsible for swapping the bottom sheet content. */
    private AnimatorSet mContentSwapAnimatorSet;

    /** The height of the toolbar. */
    private float mToolbarHeight;

    /** The width of the view that contains the bottom sheet. */
    private float mContainerWidth;

    /** The height of the view that contains the bottom sheet. */
    private float mContainerHeight;

    /** The current state that the sheet is in. */
    @SheetState
    private int mCurrentState = SHEET_STATE_PEEK;

    /** The target sheet state. This is the state that the sheet is currently moving to. */
    @SheetState
    private int mTargetState = SHEET_STATE_NONE;

    /** Used for getting the current tab. */
    private TabModelSelector mTabModelSelector;

    /** The fullscreen manager for information about toolbar offsets. */
    private ChromeFullscreenManager mFullscreenManager;

    /** A handle to the content being shown by the sheet. */
    @Nullable
    private BottomSheetContent mSheetContent;

    /** A handle to the toolbar control container. */
    private View mControlContainer;

    /** A handle to the find-in-page toolbar. */
    private View mFindInPageView;

    /** A handle to the FrameLayout that holds the content of the bottom sheet. */
    private FrameLayout mBottomSheetContentContainer;

    /**
     * The last ratio sent to observers of onTransitionPeekToHalf(). This is used to ensure the
     * final value sent to these observers is 1.0f.
     */
    private float mLastPeekToHalfRatioSent;

    /** The FrameLayout used to hold the bottom sheet toolbar. */
    private FrameLayout mToolbarHolder;

    /**
     * The default toolbar view. This is shown when the current bottom sheet content doesn't have
     * its own toolbar and when the bottom sheet is closed.
     */
    private BottomToolbarPhone mDefaultToolbarView;

    /** Whether the {@link BottomSheet} and its children should react to touch events. */
    private boolean mIsTouchEnabled;

    /** Whether the sheet is currently open. */
    private boolean mIsSheetOpen;

    /** The activity displaying the bottom sheet. */
    private ChromeActivity mActivity;

    /** A delegate for when the action bar starts showing. */
    private ViewShiftingActionBarDelegate mActionBarDelegate;

    /** The {@link LayoutManagerChrome} used to show and hide overview mode. **/
    private LayoutManagerChrome mLayoutManager;

    /** Whether or not the back button was used to enter the tab switcher. */
    private boolean mBackButtonDismissesChrome;

    /** Whether {@link #destroy()} has been called. **/
    private boolean mIsDestroyed;

    /** The token used to enable browser controls persistence. */
    private int mPersistentControlsToken;

    /**
     * An interface defining content that can be displayed inside of the bottom sheet for Chrome
     * Home.
     */
    public interface BottomSheetContent {
        /**
         * Gets the {@link View} that holds the content to be displayed in the Chrome Home bottom
         * sheet.
         * @return The content view.
         */
        View getContentView();

        /**
         * Gets the {@link View}s that need additional padding applied to them to accommodate other
         * UI elements, such as the transparent bottom navigation menu.
         * @return The {@link View}s that need additional padding applied to them.
         */
        List<View> getViewsForPadding();

        /**
         * Get the {@link View} that contains the toolbar specific to the content being
         * displayed. If null is returned, the omnibox is used.
         *
         * @return The toolbar view.
         */
        @Nullable
        View getToolbarView();

        /**
         * @return Whether or not the toolbar is currently using a lightly colored background.
         */
        boolean isUsingLightToolbarTheme();

        /**
         * @return Whether or not the content is themed for incognito (i.e. dark colors).
         */
        boolean isIncognitoThemedContent();

        /**
         * @return The vertical scroll offset of the content view.
         */
        int getVerticalScrollOffset();

        /**
         * Called to destroy the {@link BottomSheetContent} when it is no longer in use.
         */
        void destroy();

        /**
         * @return The {@link BottomSheetContentController.ContentType} for this content.
         */
        @ContentType
        int getType();

        /**
         * @return Whether the default top padding should be applied to the content view.
         */
        boolean applyDefaultTopPadding();

        /**
         * Called to scroll to the top of {@link BottomSheetContent}.
         */
        void scrollToTop();
    }

    /**
     * This class is responsible for detecting swipe and scroll events on the bottom sheet or
     * ignoring them when appropriate.
     */
    private class BottomSheetSwipeDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return isTouchInSwipableXRange(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!canMoveSheet()) {
                // Currently it's possible to enter the tab switcher after an onScroll() event has
                // began. If that happens, reset the sheet offset and return false to end the scroll
                // event.
                // TODO(twellington): Remove this after it is no longer possible to close the NTP
                // while moving the BottomSheet.
                setSheetState(SHEET_STATE_PEEK, false);
                return false;
            }

            if (!isTouchInSwipableXRange(e2)) return false;

            // Only start scrolling if the scroll is up or down. If the user is already scrolling,
            // continue moving the sheet.
            float slope = Math.abs(distanceX) > 0f ? Math.abs(distanceY) / Math.abs(distanceX) : 0f;
            if (!mIsScrolling && slope < MIN_VERTICAL_SCROLL_SLOPE) {
                mVelocityTracker.clear();
                return false;
            }

            // Cancel the settling animation if it is running so it doesn't conflict with where the
            // user wants to move the sheet.
            cancelAnimation();

            mVelocityTracker.addMovement(e2);

            float currentShownRatio =
                    mContainerHeight > 0 ? getSheetOffsetFromBottom() / mContainerHeight : 0;
            boolean isSheetInMaxPosition =
                    MathUtils.areFloatsEqual(currentShownRatio, getFullRatio());

            // Allow the bottom sheet's content to be scrolled up without dragging the sheet down.
            if (!isTouchEventInToolbar(e2) && isSheetInMaxPosition && mSheetContent != null
                    && mSheetContent.getVerticalScrollOffset() > 0) {
                return false;
            }

            // If the sheet is in the max position, don't move the sheet if the scroll is upward.
            // Instead, allow the sheet's content to handle it if it needs to.
            if (isSheetInMaxPosition && distanceY > 0) return false;

            // Similarly, if the sheet is in the min position, don't move if the scroll is downward.
            if (currentShownRatio <= getPeekRatio() && distanceY < 0) return false;

            float newOffset = getSheetOffsetFromBottom() + distanceY;
            setSheetOffsetFromBottom(MathUtils.clamp(newOffset, getMinOffset(), getMaxOffset()));

            setInternalCurrentState(SHEET_STATE_SCROLLING, StateChangeReason.SWIPE);

            mIsScrolling = true;
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!isTouchInSwipableXRange(e2) || !mIsScrolling) return false;

            cancelAnimation();

            // Figure out the projected state of the sheet and animate there. Note that a swipe up
            // will have a negative velocity, swipe down will have a positive velocity. Negate this
            // values so that the logic is more intuitive.
            @SheetState
            int targetState = getTargetSheetState(
                    getSheetOffsetFromBottom() + getFlingDistance(-velocityY), -velocityY);
            setSheetState(targetState, true, StateChangeReason.SWIPE);
            mIsScrolling = false;

            return true;
        }
    }

    /**
     * Returns whether the provided bottom sheet state is in one of the stable open or closed
     * states: {@link #SHEET_STATE_FULL}, {@link #SHEET_STATE_PEEK} or {@link #SHEET_STATE_HALF}
     * @param sheetState A {@link SheetState} to test.
     */
    public static boolean isStateStable(@SheetState int sheetState) {
        switch (sheetState) {
            case SHEET_STATE_PEEK:
            case SHEET_STATE_HALF:
            case SHEET_STATE_FULL:
                return true;
            case SHEET_STATE_SCROLLING:
                return false;
            case SHEET_STATE_NONE: // Should never be tested, internal only value.
            default:
                assert false;
                return false;
        }
    }

    /**
     * Check if a touch event is in the swipable x-axis range of the toolbar when in peeking mode.
     * If the "chrome-home-swipe-logic" flag is not set to "restrict-area", "button-only" or the
     * sheet is open, this function returns true.
     * @param e The touch event.
     * @return True if the touch is inside the swipable area of the toolbar.
     */
    private boolean isTouchInSwipableXRange(MotionEvent e) {
        // If the sheet is already open, the experiment is not enabled, or accessibility is enabled
        // there is no need to restrict the swipe area.
        if (mActivity == null || isSheetOpen() || AccessibilityUtil.isAccessibilityEnabled()) {
            return true;
        }

        String logicType = FeatureUtilities.getChromeHomeSwipeLogicType();

        // By default, the entire toolbar is swipable.
        float startX = mVisibleViewportRect.left;
        float endX = mDefaultToolbarView.getWidth() + mVisibleViewportRect.left;

        if (ChromeSwitches.CHROME_HOME_SWIPE_LOGIC_RESTRICT_AREA.equals(logicType)) {
            // Determine an area in the middle of the toolbar that is swipable. This will only
            // trigger if the expand button is disabled.
            float allowedSwipeWidth = mContainerWidth * SWIPE_ALLOWED_FRACTION;
            startX = mVisibleViewportRect.left + (mContainerWidth - allowedSwipeWidth) / 2;
            endX = startX + allowedSwipeWidth;
        }

        return e.getRawX() > startX && e.getRawX() < endX || getSheetState() != SHEET_STATE_PEEK;
    }

    /**
     * Constructor for inflation from XML.
     * @param context An Android context.
     * @param atts The XML attributes.
     */
    public BottomSheet(Context context, AttributeSet atts) {
        super(context, atts);

        mMinHalfFullDistance =
                getResources().getDimensionPixelSize(R.dimen.chrome_home_min_full_half_distance);
        mToolbarShadowHeight =
                getResources().getDimensionPixelOffset(R.dimen.toolbar_shadow_height);

        DisplayMetrics metrics =
                ContextUtils.getApplicationContext().getResources().getDisplayMetrics();
        mUseTallBottomNav =
                Float.compare(Math.max(metrics.heightPixels, metrics.widthPixels) / metrics.density,
                        TALL_BOTTOM_NAV_THRESHOLD_DP)
                >= 0;
        mBottomNavHeight = getResources().getDimensionPixelSize(
                mUseTallBottomNav ? R.dimen.bottom_nav_height_tall : R.dimen.bottom_nav_height);

        mVelocityTracker = VelocityTracker.obtain();

        mGestureDetector = new GestureDetector(context, new BottomSheetSwipeDetector());
        mGestureDetector.setIsLongpressEnabled(false);

        mMetrics = new BottomSheetMetrics();
        addObserver(mMetrics);

        BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                .addStartupCompletedObserver(new BrowserStartupController.StartupCallback() {
                    @Override
                    public void onSuccess(boolean alreadyStarted) {
                        mIsTouchEnabled = true;
                    }

                    @Override
                    public void onFailure() {}
                });
    }

    /**
     * Called when the activity containing the {@link BottomSheet} is destroyed.
     */
    public void destroy() {
        mIsDestroyed = true;
        mIsTouchEnabled = false;
        mObservers.clear();
        endAnimations();
    }

    /**
     * Handle a back press event.
     *     - If the navigation stack is empty, the sheet will be opened to the half state.
     *         - If the tab switcher is visible, {@link ChromeActivity} will handle the event.
     *     - If the sheet is open it will be closed unless it was opened by a back press.
     * @return True if the sheet handled the back press.
     */
    public boolean handleBackPress() {
        Tab tab = getActiveTab();
        boolean consumeEvent = false;

        if (!isSheetOpen() && tab != null && !tab.canGoBack() && !isInOverviewMode()
                && tab.getLaunchType() == TabLaunchType.FROM_CHROME_UI) {
            mBackButtonDismissesChrome = true;

            setSheetState(SHEET_STATE_HALF, true);
            return true;
        } else if (isSheetOpen() && !mBackButtonDismissesChrome) {
            consumeEvent = true;
        }

        if (getSheetState() != SHEET_STATE_PEEK) {
            setSheetState(SHEET_STATE_PEEK, true, StateChangeReason.BACK_PRESS);
        }

        return consumeEvent;
    }

    /**
     * Sets whether the {@link BottomSheet} and its children should react to touch events.
     */
    public void setTouchEnabled(boolean enabled) {
        mIsTouchEnabled = enabled;
    }

    /**
     * A notification that the "expand" button for the bottom sheet has been pressed.
     */
    public void onExpandButtonPressed() {
        setSheetState(BottomSheet.SHEET_STATE_HALF, true, StateChangeReason.EXPAND_BUTTON);
    }

    /** Immediately end all animations and null the animators. */
    public void endAnimations() {
        if (mSettleAnimator != null) mSettleAnimator.end();
        mSettleAnimator = null;
        endTransitionAnimations();
    }

    /**
     * Immediately end the bottom sheet content transition animations and null the animator.
     */
    public void endTransitionAnimations() {
        if (mContentSwapAnimatorSet == null || !mContentSwapAnimatorSet.isRunning()) return;
        mContentSwapAnimatorSet.end();
        mContentSwapAnimatorSet = null;
    }

    /**
     * @return An action bar delegate that appropriately moves the sheet when the action bar is
     *         shown.
     */
    public ActionBarDelegate getActionBarDelegate() {
        return mActionBarDelegate;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        // If touch is disabled, act like a black hole and consume touch events without doing
        // anything with them.
        if (!mIsTouchEnabled) return true;

        if (!canMoveSheet()) return false;

        // The incoming motion event may have been adjusted by the view sending it down. Create a
        // motion event with the raw (x, y) coordinates of the original so the gesture detector
        // functions properly.
        mGestureDetector.onTouchEvent(createRawMotionEvent(e));

        return mIsScrolling;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // If touch is disabled, act like a black hole and consume touch events without doing
        // anything with them.
        if (!mIsTouchEnabled) return true;

        if (isToolbarAndroidViewHidden()) return false;

        // The down event is interpreted above in onInterceptTouchEvent, it does not need to be
        // interpreted a second time.
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) {
            mGestureDetector.onTouchEvent(createRawMotionEvent(e));
        }

        // If the user is scrolling and the event is a cancel or up action, update scroll state
        // and return.
        if (mIsScrolling
                && (e.getActionMasked() == MotionEvent.ACTION_UP
                           || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            mIsScrolling = false;

            mVelocityTracker.computeCurrentVelocity(1000);

            for (BottomSheetObserver o : mObservers) o.onSheetReleased();

            // If an animation was not created to settle the sheet at some state, do it now.
            if (mSettleAnimator == null) {
                // Negate velocity so a positive number indicates a swipe up.
                float currentVelocity = -mVelocityTracker.getYVelocity();
                @SheetState
                int targetState = getTargetSheetState(getSheetOffsetFromBottom(), currentVelocity);
                setSheetState(targetState, true, StateChangeReason.SWIPE);
            }
        }

        return true;
    }

    @Override
    public boolean gatherTransparentRegion(Region region) {
        // TODO(mdjones): Figure out what this should actually be set to since the view animates
        // without necessarily calling this method again.
        region.setEmpty();
        return true;
    }

    /**
     * Defocus the omnibox.
     */
    public void defocusOmnibox() {
        if (mDefaultToolbarView == null) return;
        mDefaultToolbarView.getLocationBar().setUrlBarFocus(false);
    }

    /**
     * @param tabModelSelector A TabModelSelector for getting the current tab and activity.
     */
    public void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
        mNtpController.setTabModelSelector(tabModelSelector);
    }

    /**
     * @param layoutManager The {@link LayoutManagerChrome} used to show and hide overview mode.
     */
    public void setLayoutManagerChrome(LayoutManagerChrome layoutManager) {
        mLayoutManager = layoutManager;
        mNtpController.setLayoutManagerChrome(layoutManager);
    }

    /**
     * @param fullscreenManager Chrome's fullscreen manager for information about toolbar offsets.
     */
    public void setFullscreenManager(ChromeFullscreenManager fullscreenManager) {
        mFullscreenManager = fullscreenManager;
    }

    /**
     * @return Whether or not the toolbar Android View is hidden due to being scrolled off-screen.
     */
    @VisibleForTesting
    public boolean isToolbarAndroidViewHidden() {
        return mFullscreenManager == null || mFullscreenManager.getBottomControlOffset() > 0
                || mControlContainer.getVisibility() != VISIBLE;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        assert heightSize != 0;
        int height = heightSize + mToolbarShadowHeight;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * Adds layout change listeners to the views that the bottom sheet depends on. Namely the
     * heights of the root view and control container are important as they are used in many of the
     * calculations in this class.
     * @param root The container of the bottom sheet.
     * @param controlContainer The container for the toolbar.
     * @param activity The activity displaying the bottom sheet.
     */
    public void init(View root, View controlContainer, ChromeActivity activity) {
        mControlContainer = controlContainer;
        mToolbarHeight = mControlContainer.getHeight();
        mActivity = activity;
        mActionBarDelegate = new ViewShiftingActionBarDelegate(mActivity, this);

        getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;

        mBottomSheetContentContainer = (FrameLayout) findViewById(R.id.bottom_sheet_content);
        mBottomSheetContentContainer.setBackgroundColor(
                ApiCompatibilityUtils.getColor(getResources(), R.color.modern_primary_color));

        // Listen to height changes on the root.
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private int mPreviousKeyboardHeight;

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Compute the new height taking the keyboard into account.
                // TODO(mdjones): Share this logic with LocationBarLayout: crbug.com/725725.
                float previousWidth = mContainerWidth;
                float previousHeight = mContainerHeight;
                mContainerWidth = right - left;
                mContainerHeight = bottom - top;

                if (previousWidth != mContainerWidth || previousHeight != mContainerHeight) {
                    updateSheetStateRatios();
                }

                int heightMinusKeyboard = (int) mContainerHeight;
                int keyboardHeight = 0;

                // Reset mVisibleViewportRect regardless of sheet open state as it is used outside
                // of calculating the keyboard height.
                mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(
                        mVisibleViewportRect);
                if (isSheetOpen()) {
                    int decorHeight = mActivity.getWindow().getDecorView().getHeight();
                    heightMinusKeyboard = Math.min(decorHeight, mVisibleViewportRect.height());
                    keyboardHeight = (int) (mContainerHeight - heightMinusKeyboard);
                }

                if (previousHeight != mContainerHeight
                        || mPreviousKeyboardHeight != keyboardHeight) {
                    for (BottomSheetObserver o : mObservers) {
                        o.onSheetLayout((int) mContainerHeight, heightMinusKeyboard);
                    }
                }

                if (keyboardHeight != mPreviousKeyboardHeight) {
                    // If the keyboard height changed, recompute the padding for the content area.
                    // This shrinks the content size while retaining the default background color
                    // where the keyboard is appearing. If the sheet is not showing, resize the
                    // sheet to its default state.
                    // Setting the padding is posted in a runnable for the sake of Android J.
                    // See crbug.com/751013.
                    final int finalPadding = keyboardHeight;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            mBottomSheetContentContainer.setPadding(0, 0, 0, finalPadding);

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                                // A layout on the toolbar holder is requested so that the toolbar
                                // doesn't disappear under certain scenarios on Android J.
                                // See crbug.com/751013.
                                mToolbarHolder.requestLayout();
                            }
                        }
                    });
                }

                if (previousHeight != mContainerHeight
                        || mPreviousKeyboardHeight != keyboardHeight) {
                    // If we are in the middle of a touch event stream (i.e. scrolling while
                    // keyboard is up) don't set the sheet state. Instead allow the gesture detector
                    // to position the sheet and make sure the keyboard hides.
                    if (mIsScrolling) {
                        UiUtils.hideKeyboard(BottomSheet.this);
                    } else {
                        cancelAnimation();
                        setSheetState(mCurrentState, false);
                    }
                }

                mPreviousKeyboardHeight = keyboardHeight;
            }
        });

        // Listen to height changes on the toolbar.
        controlContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Make sure the size of the layout actually changed.
                if (bottom - top == oldBottom - oldTop && right - left == oldRight - oldLeft) {
                    return;
                }

                mToolbarHeight = bottom - top;
                updateSheetStateRatios();

                if (!mIsScrolling) {
                    cancelAnimation();

                    // This onLayoutChange() will be called after the user enters fullscreen video
                    // mode. Ensure the sheet state is reset to peek so that the sheet does not
                    // open over the fullscreen video. See crbug.com/740499.
                    if (mFullscreenManager != null
                            && mFullscreenManager.getPersistentFullscreenMode()) {
                        setSheetState(SHEET_STATE_PEEK, false);
                    } else {
                        setSheetState(mCurrentState, false);
                    }
                }
            }
        });

        mToolbarHolder = (FrameLayout) mControlContainer.findViewById(R.id.toolbar_holder);
        mDefaultToolbarView = (BottomToolbarPhone) mControlContainer.findViewById(R.id.toolbar);
        mDefaultToolbarView.setActivity(mActivity);

        mNtpController = new BottomSheetNewTabController(this, mDefaultToolbarView, mActivity);

        mActivity.getFullscreenManager().addListener(new FullscreenListener() {
            @Override
            public void onToggleOverlayVideoMode(boolean enabled) {
                if (isSheetOpen()) setSheetState(SHEET_STATE_PEEK, false);
            }

            @Override
            public void onControlsOffsetChanged(
                    float topOffset, float bottomOffset, boolean needsAnimate) {}

            @Override
            public void onContentOffsetChanged(float offset) {}

            @Override
            public void onBottomControlsHeightChanged(int bottomControlsHeight) {}
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        // Trigger a relayout on window focus to correct any positioning issues when leaving Chrome
        // previously.  This is required as a layout is not triggered when coming back to Chrome
        // with the keyboard previously shown.
        if (hasWindowFocus) requestLayout();
    }

    /**
     * Set the color of the pull handle used by the toolbar.
     */
    public void updateHandleTint() {
        boolean isLightToolbarTheme = mDefaultToolbarView.isLightTheme();

        // If the current sheet content's toolbar is using a special theme, use that.
        if (mSheetContent != null && mSheetContent.getToolbarView() != null) {
            isLightToolbarTheme = mSheetContent.isUsingLightToolbarTheme();
        }

        // A light toolbar theme means the handle should be dark.
        mDefaultToolbarView.updateHandleTint(!isLightToolbarTheme);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // A layout is requested so that the toolbar contents don't disappear under certain
            // scenarios on Android J. See crbug.com/769611.
            mControlContainer.requestLayout();
        }
    }

    @Override
    public int loadUrl(LoadUrlParams params, boolean incognito) {
        if (NewTabPage.isNTPUrl(params.getUrl())) {
            displayNewTabUi(incognito);
            return TabLoadStatus.PAGE_LOAD_FAILED;
        }

        // Load chrome://bookmarks, downloads, and history in the bottom sheet.
        if (handleNativePageUrl(params.getUrl())) return TabLoadStatus.PAGE_LOAD_FAILED;

        boolean isShowingNtp = isShowingNewTab();
        for (BottomSheetObserver o : mObservers) o.onLoadUrl(params.getUrl());

        // Native page URLs in this context do not need to communicate with the tab.
        if (NativePageFactory.isNativePageUrl(params.getUrl(), incognito) && !isShowingNtp) {
            return TabLoadStatus.PAGE_LOAD_FAILED;
        }

        assert mTabModelSelector != null;

        int tabLoadStatus = TabLoadStatus.DEFAULT_PAGE_LOAD;

        // First try to get the tab behind the sheet.
        if (!isShowingNtp && getActiveTab() != null && getActiveTab().isIncognito() == incognito) {
            tabLoadStatus = getActiveTab().loadUrl(params);
        } else {
            // If no compatible tab is active behind the sheet, open a new one.
            mTabModelSelector.openNewTab(
                    params, TabModel.TabLaunchType.FROM_CHROME_UI, getActiveTab(), incognito);
        }

        // In all non-native cases, minimize the sheet.
        setSheetState(SHEET_STATE_PEEK, true, StateChangeReason.NAVIGATION);

        return tabLoadStatus;
    }

    /**
     * If the URL scheme is "chrome", we try to load bookmarks, downloads, and history in the
     * bottom sheet.
     *
     * @param url The URL to be loaded.
     * @return Whether or not the URL was loaded in the sheet.
     */
    private boolean handleNativePageUrl(String url) {
        if (url == null) return false;

        Uri uri = Uri.parse(url);
        if (!UrlConstants.CHROME_SCHEME.equals(uri.getScheme())
                && !UrlConstants.CHROME_NATIVE_SCHEME.equals(uri.getScheme())) {
            return false;
        }

        if (UrlConstants.BOOKMARKS_HOST.equals(uri.getHost())) {
            mActivity.getBottomSheetContentController().showContentAndOpenSheet(
                    R.id.action_bookmarks);
        } else if (UrlConstants.DOWNLOADS_HOST.equals(uri.getHost())) {
            mActivity.getBottomSheetContentController().showContentAndOpenSheet(
                    R.id.action_downloads);
        } else if (UrlConstants.HISTORY_HOST.equals(uri.getHost())) {
            mActivity.getBottomSheetContentController().showContentAndOpenSheet(
                    R.id.action_history);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Load a non-native URL in a new tab. This should be used when the new tab UI controlled by
     * {@link BottomSheetContentController} is showing.
     * @param params The params describing the URL to be loaded.
     */
    public void loadUrlInNewTab(LoadUrlParams params) {
        assert isShowingNewTab();

        loadUrl(params, mTabModelSelector.isIncognitoSelected());
    }

    @Override
    public boolean isIncognito() {
        if (getActiveTab() == null) return false;
        return getActiveTab().isIncognito();
    }

    @Override
    public int getParentId() {
        return Tab.INVALID_TAB_ID;
    }

    @Override
    public Tab getActiveTab() {
        if (mTabModelSelector == null) return null;
        if (mNtpController.isShowingNewTabUi() && isVisible()
                && getTargetSheetState() != SHEET_STATE_PEEK) {
            return null;
        }
        return mTabModelSelector.getCurrentTab();
    }

    @Override
    public boolean isVisible() {
        return mCurrentState != SHEET_STATE_PEEK;
    }

    /**
     * Gets the minimum offset of the bottom sheet.
     * @return The min offset.
     */
    public float getMinOffset() {
        return getPeekRatio() * mContainerHeight;
    }

    /**
     * Gets the sheet's offset from the bottom of the screen.
     * @return The sheet's distance from the bottom of the screen.
     */
    public float getSheetOffsetFromBottom() {
        return mContainerHeight - getTranslationY();
    }

    /**
     * @return The {@link BottomSheetNewTabController} used to present the new tab UI.
     */
    public BottomSheetNewTabController getNewTabController() {
        return mNtpController;
    }

    /**
     * Show content in the bottom sheet's content area.
     * @param content The {@link BottomSheetContent} to show, or null if no content should be shown.
     */
    public void showContent(@Nullable final BottomSheetContent content) {
        // If an animation is already running, end it.
        if (mContentSwapAnimatorSet != null) mContentSwapAnimatorSet.end();

        // If the desired content is already showing, do nothing.
        if (mSheetContent == content) return;

        List<Animator> animators = new ArrayList<>();
        mContentSwapAnimatorSet = new AnimatorSet();
        mContentSwapAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onContentSwapAnimationEnd(content);
            }
        });

        // Add an animator for the toolbar transition if needed.
        View newToolbar = content != null && content.getToolbarView() != null
                ? content.getToolbarView()
                : mDefaultToolbarView;
        View oldToolbar = mSheetContent != null && mSheetContent.getToolbarView() != null
                ? mSheetContent.getToolbarView()
                : mDefaultToolbarView;
        if (newToolbar != oldToolbar) {
            // For the toolbar transition, make sure we don't detach the default toolbar view.
            animators.add(getViewTransitionAnimator(
                    newToolbar, oldToolbar, mToolbarHolder, mDefaultToolbarView != oldToolbar));
        }

        // Add an animator for the content transition if needed.
        View oldContent = mSheetContent != null ? mSheetContent.getContentView() : null;
        if (content == null) {
            if (oldContent != null) mBottomSheetContentContainer.removeView(oldContent);
        } else {
            View contentView = content.getContentView();
            animators.add(getViewTransitionAnimator(
                    contentView, oldContent, mBottomSheetContentContainer, true));
        }

        // Temporarily make the background of the toolbar holder a solid color so the transition
        // doesn't appear to show a hole in the toolbar.
        int colorId = content == null || !content.isIncognitoThemedContent()
                ? R.color.modern_primary_color
                : R.color.incognito_primary_color;
        if (!mIsSheetOpen || (content != null && content.isIncognitoThemedContent())
                || (mSheetContent != null && mSheetContent.isIncognitoThemedContent())) {
            // If the sheet is closed, the bottom sheet content container is invisible, so
            // background color is needed on the toolbar holder to prevent a blank rectangle from
            // appearing during the content transition.
            mToolbarHolder.setBackgroundColor(
                    ApiCompatibilityUtils.getColor(getResources(), colorId));
        }
        mBottomSheetContentContainer.setBackgroundColor(
                ApiCompatibilityUtils.getColor(getResources(), colorId));

        // Set color on the content view to compensate for a JellyBean bug (crbug.com/766237).
        if (content != null) {
            content.getContentView().setBackgroundColor(
                    ApiCompatibilityUtils.getColor(getResources(), colorId));
        }

        // Return early if there are no animators to run.
        if (animators.isEmpty()) {
            onContentSwapAnimationEnd(content);
            return;
        }

        mContentSwapAnimatorSet.playTogether(animators);
        mContentSwapAnimatorSet.start();

        // If the existing content is null or the tab switcher assets are showing, end the animation
        // immediately.
        if (mSheetContent == null || mDefaultToolbarView.isInTabSwitcherMode()
                || SysUtils.isLowEndDevice()) {
            mContentSwapAnimatorSet.end();
        }
    }

    /**
     * Called when the animation to swap BottomSheetContent ends.
     * @param content The BottomSheetContent showing at the end of the animation.
     */
    private void onContentSwapAnimationEnd(BottomSheetContent content) {
        if (mIsDestroyed) return;

        onSheetContentChanged(content);
        mContentSwapAnimatorSet = null;
    }

    /**
     * Creates a transition animation between two views. The old view is faded out completely
     * before the new view is faded in. There is an option to detach the old view or not.
     * @param newView The new view to transition to.
     * @param oldView The old view to transition from.
     * @param detachOldView Whether or not to detach the old view once faded out.
     * @return An animator that runs the specified animation.
     */
    private Animator getViewTransitionAnimator(final View newView, final View oldView,
            final ViewGroup parent, final boolean detachOldView) {
        if (newView == oldView) return null;

        AnimatorSet animatorSet = new AnimatorSet();
        List<Animator> animators = new ArrayList<>();

        newView.setVisibility(View.VISIBLE);

        // Fade out the old view.
        if (oldView != null) {
            ValueAnimator fadeOutAnimator = ObjectAnimator.ofFloat(oldView, View.ALPHA, 0);
            fadeOutAnimator.setDuration(TRANSITION_DURATION_MS);
            fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (detachOldView && oldView.getParent() != null) {
                        parent.removeView(oldView);
                    } else {
                        oldView.setVisibility(View.INVISIBLE);
                    }
                    if (parent != newView.getParent()) parent.addView(newView);
                }
            });
            animators.add(fadeOutAnimator);
        } else {
            // Normally the new view is added at the end of the fade-out animation of the old view,
            // if there is no old view, attach the new one immediately.
            if (parent != newView.getParent()) parent.addView(newView);
        }

        // Fade in the new view.
        newView.setAlpha(0);
        ValueAnimator fadeInAnimator = ObjectAnimator.ofFloat(newView, View.ALPHA, 1);
        fadeInAnimator.setDuration(TRANSITION_DURATION_MS);
        animators.add(fadeInAnimator);

        animatorSet.playSequentially(animators);

        return animatorSet;
    }

    /**
     * Determines if a touch event is inside the toolbar. This assumes the toolbar is the full
     * width of the screen and that the toolbar is at the top of the bottom sheet.
     * @param e The motion event to test.
     * @return True if the event occurred in the toolbar region.
     */
    private boolean isTouchEventInToolbar(MotionEvent e) {
        if (mControlContainer == null) return false;

        mControlContainer.getLocationInWindow(mLocationArray);

        return e.getRawY() < mLocationArray[1] + mToolbarHeight;
    }

    /**
     * A notification that the sheet is exiting the peek state into one that shows content.
     * @param reason The reason the sheet was opened, if any.
     */
    private void onSheetOpened(@StateChangeReason int reason) {
        if (mIsSheetOpen) return;

        mIsSheetOpen = true;

        // Make sure the toolbar is visible before expanding the sheet.
        Tab tab = getActiveTab();
        if (isToolbarAndroidViewHidden() && tab != null) {
            tab.updateBrowserControlsState(BrowserControlsState.SHOWN, false);
        }

        mBottomSheetContentContainer.setVisibility(View.VISIBLE);

        mIsSheetOpen = true;

        // Browser controls should stay visible until the sheet is closed.
        mPersistentControlsToken =
                mFullscreenManager.getBrowserVisibilityDelegate().showControlsPersistent();

        dismissSelectedText();
        for (BottomSheetObserver o : mObservers) o.onSheetOpened(reason);
        mActivity.addViewObscuringAllTabs(this);

        Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());
        tracker.notifyEvent(EventConstants.BOTTOM_SHEET_EXPANDED);
    }

    /**
     * A notification that the sheet has returned to the peeking state.
     * @param reason The {@link StateChangeReason} that the sheet was closed, if any.
     */
    private void onSheetClosed(@StateChangeReason int reason) {
        if (!mIsSheetOpen) return;
        mBottomSheetContentContainer.setVisibility(View.INVISIBLE);
        mBackButtonDismissesChrome = false;
        mIsSheetOpen = false;

        // Update the browser controls since they are permanently shown while the sheet is open.
        mFullscreenManager.getBrowserVisibilityDelegate().hideControlsPersistent(
                mPersistentControlsToken);

        for (BottomSheetObserver o : mObservers) o.onSheetClosed(reason);
        announceForAccessibility(getResources().getString(R.string.bottom_sheet_closed));
        clearFocus();
        mActivity.removeViewObscuringAllTabs(this);

        setFocusable(false);
        setFocusableInTouchMode(false);
        setContentDescription(null);

        showHelpBubbleIfNecessary();
    }

    /**
     * Creates an unadjusted version of a MotionEvent.
     * @param e The original event.
     * @return The unadjusted version of the event.
     */
    private MotionEvent createRawMotionEvent(MotionEvent e) {
        MotionEvent rawEvent = MotionEvent.obtain(e);
        rawEvent.setLocation(e.getRawX(), e.getRawY());
        return rawEvent;
    }

    /**
     * Updates the bottom sheet's state ratios and adjusts the sheet's state if necessary.
     */
    private void updateSheetStateRatios() {
        if (mContainerHeight <= 0) return;

        // Though mStateRatios is a static constant, the peeking ratio is computed here because
        // the correct toolbar height and container height are not know until those views are
        // inflated. The other views are a specific DP distance from the top and bottom and are
        // also updated.
        mStateRatios[0] = mToolbarHeight / mContainerHeight;
        mStateRatios[1] = HALF_HEIGHT_RATIO;
        // The max height ratio will be greater than 1 to account for the toolbar shadow.
        mStateRatios[2] = (mContainerHeight + mToolbarShadowHeight) / mContainerHeight;

        if (mCurrentState == SHEET_STATE_HALF && isSmallScreen()) {
            setSheetState(SHEET_STATE_FULL, false);
        }
    }

    /**
     * Cancels and nulls the height animation if it exists.
     */
    private void cancelAnimation() {
        if (mSettleAnimator == null) return;
        mSettleAnimator.cancel();
        mSettleAnimator = null;
    }

    /**
     * Creates the sheet's animation to a target state.
     * @param targetState The target state.
     * @param reason The reason the sheet started animation.
     */
    private void createSettleAnimation(
            @SheetState final int targetState, @StateChangeReason final int reason) {
        mTargetState = targetState;
        mSettleAnimator = ValueAnimator.ofFloat(
                getSheetOffsetFromBottom(), getSheetHeightForState(targetState));
        mSettleAnimator.setDuration(BASE_ANIMATION_DURATION_MS);
        mSettleAnimator.setInterpolator(mInterpolator);

        // When the animation is canceled or ends, reset the handle to null.
        mSettleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (mIsDestroyed) return;

                mSettleAnimator = null;
                setInternalCurrentState(targetState, reason);
                mTargetState = SHEET_STATE_NONE;
            }
        });

        mSettleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                setSheetOffsetFromBottom((Float) animator.getAnimatedValue());
            }
        });

        setInternalCurrentState(SHEET_STATE_SCROLLING, reason);
        mSettleAnimator.start();
    }

    /**
     * Gets the distance of a fling based on the velocity and the base animation time. This formula
     * assumes the deceleration curve is quadratic (t^2), hence the displacement formula should be:
     * displacement = initialVelocity * duration / 2.
     * @param velocity The velocity of the fling.
     * @return The distance the fling would cover.
     */
    private float getFlingDistance(float velocity) {
        // This includes conversion from seconds to ms.
        return velocity * BASE_ANIMATION_DURATION_MS / 2000f;
    }

    /**
     * Gets the maximum offset of the bottom sheet.
     * @return The max offset.
     */
    private float getMaxOffset() {
        return getFullRatio() * mContainerHeight;
    }

    /**
     * Sets the sheet's offset relative to the bottom of the screen.
     * @param offset The offset that the sheet should be.
     */
    private void setSheetOffsetFromBottom(float offset) {
        if (MathUtils.areFloatsEqual(offset, getSheetOffsetFromBottom())) return;

        setTranslationY(mContainerHeight - offset);
        sendOffsetChangeEvents();
    }

    /**
     * Deselects any text in the active tab's web contents and dismisses the text controls.
     */
    private void dismissSelectedText() {
        Tab activeTab = getActiveTab();
        if (activeTab == null) return;

        ContentViewCore contentViewCore = activeTab.getContentViewCore();
        if (contentViewCore == null) return;
        contentViewCore.clearSelection();
    }

    /**
     * This is the same as {@link #setSheetOffsetFromBottom(float)} but exclusively for testing.
     * @param offset The offset to set the sheet to.
     */
    @VisibleForTesting
    public void setSheetOffsetFromBottomForTesting(float offset) {
        setSheetOffsetFromBottom(offset);
    }

    /**
     * @return The ratio of the height of the screen that the peeking state is.
     */
    @VisibleForTesting
    public float getPeekRatio() {
        return mStateRatios[0];
    }

    /**
     * @return The ratio of the height of the screen that the half expanded state is.
     */
    @VisibleForTesting
    public float getHalfRatio() {
        return mStateRatios[1];
    }

    /**
     * @return The ratio of the height of the screen that the fully expanded state is.
     */
    @VisibleForTesting
    public float getFullRatio() {
        return mStateRatios[2];
    }

    /**
     * @return The height of the container that the bottom sheet exists in.
     */
    @VisibleForTesting
    public float getSheetContainerHeight() {
        return mContainerHeight;
    }

    /**
     * Sends notifications if the sheet is transitioning from the peeking to half expanded state and
     * from the peeking to fully expanded state. The peek to half events are only sent when the
     * sheet is between the peeking and half states.
     */
    private void sendOffsetChangeEvents() {
        float screenRatio =
                mContainerHeight > 0 ? getSheetOffsetFromBottom() / mContainerHeight : 0;

        // This ratio is relative to the peek and full positions of the sheet.
        float peekFullRatio = MathUtils.clamp(
                (screenRatio - getPeekRatio()) / (getFullRatio() - getPeekRatio()), 0, 1);

        for (BottomSheetObserver o : mObservers) {
            o.onSheetOffsetChanged(MathUtils.areFloatsEqual(peekFullRatio, 0) ? 0 : peekFullRatio);
        }

        // This ratio is relative to the peek and half positions of the sheet.
        float peekHalfRatio = MathUtils.clamp(
                (screenRatio - getPeekRatio()) / (getHalfRatio() - getPeekRatio()), 0, 1);

        // If the ratio is close enough to zero, just set it to zero.
        if (MathUtils.areFloatsEqual(peekHalfRatio, 0f)) peekHalfRatio = 0f;

        if (mLastPeekToHalfRatioSent < 1f || peekHalfRatio < 1f) {
            mLastPeekToHalfRatioSent = peekHalfRatio;
            for (BottomSheetObserver o : mObservers) {
                o.onTransitionPeekToHalf(peekHalfRatio);
            }
        }
    }

    /**
     * @see #setSheetState(int, boolean, int)
     */
    public void setSheetState(@SheetState int state, boolean animate) {
        setSheetState(state, animate, StateChangeReason.NONE);
    }

    /**
     * Moves the sheet to the provided state.
     * @param state The state to move the panel to. This cannot be SHEET_STATE_SCROLLING or
     *              SHEET_STATE_NONE.
     * @param animate If true, the sheet will animate to the provided state, otherwise it will
     *                move there instantly.
     * @param reason The reason the sheet state is changing. This can be specified to indicate to
     *               observers that a more specific event has occured, otherwise
     *               STATE_CHANGE_REASON_NONE can be used.
     */
    public void setSheetState(
            @SheetState int state, boolean animate, @StateChangeReason int reason) {
        assert state != SHEET_STATE_SCROLLING && state != SHEET_STATE_NONE;

        // Half state is not valid on small screens.
        if (state == SHEET_STATE_HALF && isSmallScreen()) state = SHEET_STATE_FULL;

        mTargetState = state;

        cancelAnimation();

        if (animate && state != mCurrentState) {
            createSettleAnimation(state, reason);
        } else {
            setSheetOffsetFromBottom(getSheetHeightForState(state));
            setInternalCurrentState(mTargetState, reason);
            mTargetState = SHEET_STATE_NONE;
        }
    }

    /**
     * @return The target state that the sheet is moving to during animation. If the sheet is
     *         stationary or a target state has not been determined, SHEET_STATE_NONE will be
     *         returned. A target state will be set when the user releases the sheet from drag
     *         ({@link BottomSheetObserver#onSheetReleased()}) and has begun animation to the next
     *         state.
     */
    public int getTargetSheetState() {
        return mTargetState;
    }

    /**
     * @return The current state of the bottom sheet. If the sheet is animating, this will be the
     *         state the sheet is animating to.
     */
    @SheetState
    public int getSheetState() {
        return mCurrentState;
    }

    /** @return Whether the sheet is currently open. */
    public boolean isSheetOpen() {
        return mIsSheetOpen;
    }

    /**
     * Set the current state of the bottom sheet. This is for internal use to notify observers of
     * state change events.
     * @param state The current state of the sheet.
     * @param reason The reason the state is changing if any.
     */
    private void setInternalCurrentState(@SheetState int state, @StateChangeReason int reason) {
        if (state == mCurrentState) return;

        // TODO(mdjones): This shouldn't be able to happen, but does occasionally during layout.
        //                Fix the race condition that is making this happen.
        if (state == SHEET_STATE_NONE) {
            setSheetState(getTargetSheetState(getSheetOffsetFromBottom(), 0), false);
            return;
        }

        mCurrentState = state;

        if (mCurrentState == SHEET_STATE_HALF || mCurrentState == SHEET_STATE_FULL) {
            announceForAccessibility(mCurrentState == SHEET_STATE_FULL
                            ? getResources().getString(R.string.bottom_sheet_opened_full)
                            : getResources().getString(R.string.bottom_sheet_opened_half));

            // TalkBack will announce the content description if it has changed, so wait to set the
            // content description until after announcing full/half height.
            setFocusable(true);
            setFocusableInTouchMode(true);
            setContentDescription(
                    getResources().getString(R.string.bottom_sheet_accessibility_description));
            if (getFocusedChild() == null) requestFocus();
        }

        for (BottomSheetObserver o : mObservers) {
            o.onSheetStateChanged(mCurrentState);
        }

        if (state == SHEET_STATE_PEEK) {
            onSheetClosed(reason);
        } else {
            onSheetOpened(reason);
        }
    }

    /**
     * If the animation to settle the sheet in one of its states is running.
     * @return True if the animation is running.
     */
    public boolean isRunningSettleAnimation() {
        return mSettleAnimator != null;
    }

    /**
     * @return The current sheet content, or null if there is no content.
     */
    @VisibleForTesting
    public @Nullable BottomSheetContent getCurrentSheetContent() {
        return mSheetContent;
    }

    /**
     * @return The {@link BottomSheetMetrics} used to record user actions and histograms.
     */
    public BottomSheetMetrics getBottomSheetMetrics() {
        return mMetrics;
    }

    /**
     * Gets the height of the bottom sheet based on a provided state.
     * @param state The state to get the height from.
     * @return The height of the sheet at the provided state.
     */
    public float getSheetHeightForState(@SheetState int state) {
        return mStateRatios[state] * mContainerHeight;
    }

    /**
     * Adds an observer to the bottom sheet.
     * @param observer The observer to add.
     */
    public void addObserver(BottomSheetObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes an observer to the bottom sheet.
     * @param observer The observer to remove.
     */
    public void removeObserver(BottomSheetObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Gets the target state of the sheet based on the sheet's height and velocity.
     * @param sheetHeight The current height of the sheet.
     * @param yVelocity The current Y velocity of the sheet. This is only used for determining the
     *                  scroll or fling direction. If this value is positive, the movement is from
     *                  bottom to top.
     * @return The target state of the bottom sheet.
     */
    @SheetState
    private int getTargetSheetState(float sheetHeight, float yVelocity) {
        if (sheetHeight <= getMinOffset()) return SHEET_STATE_PEEK;
        if (sheetHeight >= getMaxOffset()) return SHEET_STATE_FULL;

        boolean isMovingDownward = yVelocity < 0;
        boolean shouldSkipHalfState = isMovingDownward || isSmallScreen();

        // First, find the two states that the sheet height is between.
        @SheetState
        int nextState = sStates[0];

        @SheetState
        int prevState = nextState;
        for (int i = 0; i < sStates.length; i++) {
            if (sStates[i] == SHEET_STATE_HALF && shouldSkipHalfState) continue;
            prevState = nextState;
            nextState = sStates[i];
            // The values in PanelState are ascending, they should be kept that way in order for
            // this to work.
            if (sheetHeight >= getSheetHeightForState(prevState)
                    && sheetHeight < getSheetHeightForState(nextState)) {
                break;
            }
        }

        // If the desired height is close enough to a certain state, depending on the direction of
        // the velocity, move to that state.
        float lowerBound = getSheetHeightForState(prevState);
        float distance = getSheetHeightForState(nextState) - lowerBound;

        float threshold =
                shouldSkipHalfState ? THRESHOLD_TO_NEXT_STATE_2 : THRESHOLD_TO_NEXT_STATE_3;
        float thresholdToNextState = yVelocity < 0.0f ? 1.0f - threshold : threshold;

        if ((sheetHeight - lowerBound) / distance > thresholdToNextState) {
            return nextState;
        }
        return prevState;
    }

    public boolean isSmallScreen() {
        // A small screen is defined by there being less than 160dp between half and full states.
        float fullToHalfDiff = (getFullRatio() - getHalfRatio()) * mContainerHeight;
        return fullToHalfDiff < mMinHalfFullDistance;
    }

    @Override
    public void onFadingViewClick() {
        setSheetState(SHEET_STATE_PEEK, true, StateChangeReason.TAP_SCRIM);
    }

    @Override
    public void onFadingViewVisibilityChanged(boolean visible) {}

    /**
     * @return Whether the {@link BottomSheetNewTabController} is showing the new tab UI. This
     *         returns true if a normal or incognito new tab is showing.
     */
    public boolean isShowingNewTab() {
        return mNtpController.isShowingNewTabUi();
    }

    /**
     * Tells {@link BottomSheetNewTabController} to display the new tab UI.
     * @param isIncognito Whether to display the incognito new tab UI.
     */
    public void displayNewTabUi(boolean isIncognito) {
        mNtpController.displayNewTabUi(isIncognito);
    }

    /**
     * Tells {@link BottomSheetNewTabController} to display the specified content in a new tab.
     * @param isIncognito Whether to display the incognito new tab UI.
     * @param actionId The action id of the bottom sheet content to be displayed.
     */
    public void displayNewTabUi(boolean isIncognito, int actionId) {
        mNtpController.displayNewTabUi(isIncognito, actionId);
    }

    /**
     * @return Whether or not the browser is in overview mode.
     */
    private boolean isInOverviewMode() {
        return mActivity != null && mActivity.isInOverviewMode();
    }

    /**
     * @return Whether the Google 'G' logo should be shown in the location bar.
     */
    public boolean shouldShowGoogleGInLocationBar() {
        return mNtpController.shouldShowGoogleGInLocationBar();
    }

    /**
     * @return Whether a tall bottom navigation bar should be used.
     */
    public boolean useTallBottomNav() {
        return mUseTallBottomNav;
    }

    /**
     * Checks whether the sheet can be moved. It cannot be moved when the activity is in overview
     * mode, when "find in page" is visible, or when the toolbar is hidden.
     */
    private boolean canMoveSheet() {
        if (mFindInPageView == null) mFindInPageView = findViewById(R.id.find_toolbar);
        boolean isFindInPageVisible =
                mFindInPageView != null && mFindInPageView.getVisibility() == View.VISIBLE;

        return !isToolbarAndroidViewHidden()
                && (!isInOverviewMode() || mNtpController.isShowingNewTabUi())
                && !isFindInPageVisible;
    }

    /**
     * Show the in-product help bubble for the {@link BottomSheet} if it has not already been shown.
     * This method must be called after the toolbar has had at least one layout pass.
     */
    public void showHelpBubbleIfNecessary() {
        // If FRE is not complete, the FRE screen is likely covering ChromeTabbedActivity so the
        // help bubble should not be shown. Also skip showing if the bottom sheet is already open,
        // the UI has not been initialized (indicated by mLayoutManager == null), or the tab
        // switcher is showing.
        if (isSheetOpen() || mLayoutManager == null || mLayoutManager.overviewVisible()
                || !FirstRunStatus.getFirstRunFlowComplete()) {
            return;
        }

        final Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());
        tracker.addOnInitializedCallback(new Callback<Boolean>() {
            @Override
            public void onResult(Boolean success) {
                if (!success) return;

                showHelpBubble(false);
            }
        });
    }

    /**
     * Show the in-product help bubble for the {@link BottomSheet} regardless of whether it has
     * been shown before. This method must be called after the toolbar has had at least one layout
     * pass and ChromeFeatureList has been initialized.
     * @param fromMenu Whether the help bubble is being displayed in response to a click on the
     *                 IPH menu header.
     */
    public void showHelpBubble(boolean fromMenu) {
        final Tracker tracker = TrackerFactory.getTrackerForProfile(Profile.getLastUsedProfile());

        boolean showColdStartIph = !fromMenu
                && tracker.shouldTriggerHelpUI(FeatureConstants.CHROME_HOME_EXPAND_FEATURE);
        if (!fromMenu && !showColdStartIph) return;

        boolean showExpandButtonHelpBubble = mDefaultToolbarView.isUsingExpandButton();

        View anchorView = showExpandButtonHelpBubble
                ? mControlContainer.findViewById(R.id.expand_sheet_button)
                : mControlContainer;
        int stringId = showExpandButtonHelpBubble
                ? R.string.bottom_sheet_expand_button_help_bubble_message
                : R.string.bottom_sheet_help_bubble_message;
        int accessibilityStringId = showExpandButtonHelpBubble
                ? R.string.bottom_sheet_accessibility_expand_button_help_bubble_message
                : stringId;

        ViewAnchoredTextBubble helpBubble = new ViewAnchoredTextBubble(
                getContext(), anchorView, stringId, accessibilityStringId);
        helpBubble.setDismissOnTouchInteraction(true);
        helpBubble.addOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                if (fromMenu) {
                    tracker.dismissed(FeatureConstants.CHROME_HOME_MENU_HEADER_FEATURE);
                } else {
                    tracker.dismissed(FeatureConstants.CHROME_HOME_EXPAND_FEATURE);
                }

                ViewHighlighter.turnOffHighlight(anchorView);
            }
        });

        if (showExpandButtonHelpBubble) {
            ViewHighlighter.turnOnHighlight(anchorView, true);
        }

        int inset = getContext().getResources().getDimensionPixelSize(
                R.dimen.bottom_sheet_help_bubble_inset);
        helpBubble.setInsetPx(0, inset, 0, inset);
        helpBubble.show();
    }

    /**
     * Called when the sheet content has changed, to update dependent state and notify observers.
     * @param content The new sheet content, or null if the sheet has no content.
     */
    private void onSheetContentChanged(@Nullable final BottomSheetContent content) {
        mSheetContent = content;
        for (BottomSheetObserver o : mObservers) {
            o.onSheetContentChanged(content);
        }
        updateHandleTint();
        mToolbarHolder.setBackgroundColor(Color.TRANSPARENT);
    }

    /**
     * @return The default toolbar view.
     */
    @VisibleForTesting
    public @Nullable View getDefaultToolbarView() {
        return mDefaultToolbarView;
    }

    /**
     * @return The height of the toolbar holder.
     */
    public int getToolbarContainerHeight() {
        return mToolbarHolder != null ? mToolbarHolder.getHeight() : 0;
    }

    /**
     * @return The height of the bottom navigation menu.
     */
    public float getBottomNavHeight() {
        return mBottomNavHeight;
    }

    /**
     * @return The height of the toolbar shadow.
     */
    public int getToolbarShadowHeight() {
        return mToolbarShadowHeight;
    }

    /**
     * @return Whether or not the bottom sheet's toolbar is using the expand button.
     */
    public boolean isUsingExpandButton() {
        return mDefaultToolbarView.isUsingExpandButton();
    }
}
