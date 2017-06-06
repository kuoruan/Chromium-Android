// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Region;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.TabLoadStatus;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.FadingBackgroundView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController.ContentType;
import org.chromium.content_public.browser.LoadUrlParams;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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

    /**
     * The base duration of the settling animation of the sheet. 218 ms is a spec for material
     * design (this is the minimum time a user is guaranteed to pay attention to something).
     */
    private static final long BASE_ANIMATION_DURATION_MS = 218;

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

    /** The distance from the top the sheet should be when fully expanded. */
    private final float mFullHeightDistanceFromTop;

    /** The minimum distance between half and full states to allow the half state. */
    private final float mMinHalfFullDistance;

    /** The  {@link BottomSheetMetrics} used to record user actions and histograms. */
    private final BottomSheetMetrics mMetrics;

    /** For detecting scroll and fling events on the bottom sheet. */
    private GestureDetector mGestureDetector;

    /** Whether or not the user is scrolling the bottom sheet. */
    private boolean mIsScrolling;

    /** Track the velocity of the user's scrolls to determine up or down direction. */
    private VelocityTracker mVelocityTracker;

    /** The animator used to move the sheet to a fixed state when released by the user. */
    private ValueAnimator mSettleAnimator;

    /** The animator used for the toolbar fades. */
    private ValueAnimator mToolbarFadeAnimator;

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
    private BottomSheetContent mSheetContent;

    /** A handle to the toolbar control container. */
    private View mControlContainer;

    /** A placeholder for if there is no content in the bottom sheet. */
    private View mPlaceholder;

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
    private View mDefaultToolbarView;

    /** Whether the {@link BottomSheet} and its children should react to touch events. */
    private boolean mIsTouchEnabled = true;

    /** Whether the sheet is currently open. */
    private boolean mIsSheetOpen;

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
         * Get the {@link View} that contains the toolbar specific to the content being displayed.
         * If null is returned, the omnibox is used.
         * TODO(mdjones): This still needs implementation in the sheet.
         *
         * @return The toolbar view.
         */
        @Nullable
        View getToolbarView();

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
    }

    /**
     * This class is responsible for detecting swipe and scroll events on the bottom sheet or
     * ignoring them when appropriate.
     */
    private class BottomSheetSwipeDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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
                mIsScrolling = false;
                return false;
            }

            // If the sheet is in the max position, don't move the sheet if the scroll is upward.
            // Instead, allow the sheet's content to handle it if it needs to.
            if (isSheetInMaxPosition && distanceY > 0) {
                mIsScrolling = false;
                return false;
            }

            // Similarly, if the sheet is in the min position, don't move if the scroll is downward.
            if (currentShownRatio <= getPeekRatio() && distanceY < 0) {
                mIsScrolling = false;
                return false;
            }

            float newOffset = getSheetOffsetFromBottom() + distanceY;
            boolean wasOpenBeforeSwipe = mIsSheetOpen;
            setSheetOffsetFromBottom(MathUtils.clamp(newOffset, getMinOffset(), getMaxOffset()));
            setInternalCurrentState(SHEET_STATE_SCROLLING);

            if (!wasOpenBeforeSwipe && mIsSheetOpen) {
                mMetrics.recordSheetOpenReason(BottomSheetMetrics.OPENED_BY_SWIPE);
            }

            mIsScrolling = true;
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            cancelAnimation();
            boolean wasOpenBeforeSwipe = mIsSheetOpen;

            // Figure out the projected state of the sheet and animate there. Note that a swipe up
            // will have a negative velocity, swipe down will have a positive velocity. Negate this
            // values so that the logic is more intuitive.
            @SheetState
            int targetState = getTargetSheetState(
                    getSheetOffsetFromBottom() + getFlingDistance(-velocityY), -velocityY);
            setSheetState(targetState, true);
            mIsScrolling = false;

            if (!wasOpenBeforeSwipe && mIsSheetOpen) {
                mMetrics.recordSheetOpenReason(BottomSheetMetrics.OPENED_BY_SWIPE);
            }

            return true;
        }
    }

    /**
     * Constructor for inflation from XML.
     * @param context An Android context.
     * @param atts The XML attributes.
     */
    public BottomSheet(Context context, AttributeSet atts) {
        super(context, atts);

        mFullHeightDistanceFromTop =
                getResources().getDimensionPixelSize(R.dimen.chrome_home_full_height_from_top);

        mMinHalfFullDistance =
                getResources().getDimensionPixelSize(R.dimen.chrome_home_min_full_half_distance);

        mVelocityTracker = VelocityTracker.obtain();

        mGestureDetector = new GestureDetector(context, new BottomSheetSwipeDetector());
        mGestureDetector.setIsLongpressEnabled(false);

        mMetrics = new BottomSheetMetrics();
        addObserver(mMetrics);
    }

    /**
     * Sets whether the {@link BottomSheet} and its children should react to touch events.
     */
    public void setTouchEnabled(boolean enabled) {
        mIsTouchEnabled = enabled;
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
        if (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mIsScrolling = false;

            mVelocityTracker.computeCurrentVelocity(1000);

            for (BottomSheetObserver o : mObservers) o.onSheetReleased();

            // If an animation was not created to settle the sheet at some state, do it now.
            if (mSettleAnimator == null) {
                // Negate velocity so a positive number indicates a swipe up.
                float currentVelocity = -mVelocityTracker.getYVelocity();
                @SheetState
                int targetState = getTargetSheetState(getSheetOffsetFromBottom(), currentVelocity);

                setSheetState(targetState, true);
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
     * @param tabModelSelector A TabModelSelector for getting the current tab and activity.
     */
    public void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
    }

    /**
     * @param fullscreenManager Chrome's fullscreen manager for information about toolbar offsets.
     */
    public void setFullscreenManager(ChromeFullscreenManager fullscreenManager) {
        mFullscreenManager = fullscreenManager;
    }

    /**
     * Set the window's status bar color. On Android M and above, this will set the status bar color
     * to the default theme color with dark icons except in the case of the tab switcher and
     * incognito NTP. On Android versions < M, the status bar will always be black.
     * @param window The Android window.
     */
    public void setStatusBarColor(Window window) {
        Tab tab = getActiveTab();
        boolean isInOverviewMode = tab != null && tab.getActivity().isInOverviewMode();
        boolean isIncognitoNtp =
                tab != null && NewTabPage.isNTPUrl(tab.getUrl()) && tab.isIncognito();
        boolean isValidAndroidVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        int color = ApiCompatibilityUtils.getColor(getResources(), R.color.default_primary_color);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        // Special case the incognito NTP and the tab switcher.
        if (!isValidAndroidVersion || isIncognitoNtp || isInOverviewMode) {
            color = Color.BLACK;
            // The light status bar flag is always set above, meaning XORing that value with the
            // current flags will remove it.
            setSystemUiVisibility(getSystemUiVisibility() ^ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        ApiCompatibilityUtils.setStatusBarColor(window, color);
    }

    /**
     * @return Whether or not the toolbar Android View is hidden due to being scrolled off-screen.
     */
    private boolean isToolbarAndroidViewHidden() {
        return mFullscreenManager == null || mFullscreenManager.getBottomControlOffset() > 0
                || mControlContainer.getVisibility() != VISIBLE;
    }

    /**
     * Adds layout change listeners to the views that the bottom sheet depends on. Namely the
     * heights of the root view and control container are important as they are used in many of the
     * calculations in this class.
     * @param root The container of the bottom sheet.
     * @param controlContainer The container for the toolbar.
     */
    public void init(View root, View controlContainer) {
        mControlContainer = controlContainer;
        mToolbarHeight = mControlContainer.getHeight();

        mBottomSheetContentContainer = (FrameLayout) findViewById(R.id.bottom_sheet_content);

        // Listen to height changes on the root.
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Make sure the size of the layout actually changed.
                if (bottom - top == oldBottom - oldTop && right - left == oldRight - oldLeft) {
                    return;
                }

                mContainerWidth = right - left;
                mContainerHeight = bottom - top;
                updateSheetDimensions();

                cancelAnimation();
                setSheetState(mCurrentState, false);
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
                updateSheetDimensions();

                cancelAnimation();
                setSheetState(mCurrentState, false);
            }
        });

        mPlaceholder = new View(getContext());
        LayoutParams placeHolderParams =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mPlaceholder.setBackgroundColor(
                ApiCompatibilityUtils.getColor(getResources(), android.R.color.white));
        mBottomSheetContentContainer.addView(mPlaceholder, placeHolderParams);

        mToolbarHolder = (FrameLayout) mControlContainer.findViewById(R.id.toolbar_holder);
        mDefaultToolbarView = mControlContainer.findViewById(R.id.toolbar);
    }

    @Override
    public int loadUrl(LoadUrlParams params, boolean incognito) {
        for (BottomSheetObserver o : mObservers) o.onLoadUrl(params.getUrl());

        // Native page URLs in this context do not need to communicate with the tab.
        if (NativePageFactory.isNativePageUrl(params.getUrl(), incognito)) {
            return TabLoadStatus.PAGE_LOAD_FAILED;
        }

        // In all non-native cases, minimize the sheet.
        setSheetState(SHEET_STATE_PEEK, true);

        assert mTabModelSelector != null;

        // First try to get the tab behind the sheet.
        if (getActiveTab() != null && getActiveTab().isIncognito() == incognito) {
            return getActiveTab().loadUrl(params);
        }

        // If no compatible tab is active behind the sheet, open a new one.
        mTabModelSelector.openNewTab(
                params, TabModel.TabLaunchType.FROM_CHROME_UI, getActiveTab(), incognito);
        return TabLoadStatus.DEFAULT_PAGE_LOAD;
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
        return mTabModelSelector == null ? null : mTabModelSelector.getCurrentTab();
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
     * Show content in the bottom sheet's content area.
     * @param content The {@link BottomSheetContent} to show.
     */
    public void showContent(BottomSheetContent content) {
        // If the desired content is already showing, do nothing.
        if (mSheetContent == content) return;

        View newToolbar = content.getToolbarView();
        View oldToolbar = null;

        if (mSheetContent != null) {
            oldToolbar = mSheetContent.getToolbarView();
            mBottomSheetContentContainer.removeView(mSheetContent.getContentView());
            mSheetContent = null;
        }

        mBottomSheetContentContainer.removeView(mPlaceholder);
        mSheetContent = content;
        mBottomSheetContentContainer.addView(mSheetContent.getContentView());

        doToolbarSwap(newToolbar, oldToolbar);

        for (BottomSheetObserver o : mObservers) {
            o.onSheetContentChanged(mSheetContent);
        }
    }

    /**
     * Fade between a new toolbar and the old toolbar to be shown. A null parameter can be used to
     * refer to the default omnibox toolbar. Normally, the new toolbar is attached to the toolbar
     * container and faded in. In the case of the default toolbar, the old toolbar is faded out.
     * This is because the default toolbar is always attached to the view hierarchy and sits behind
     * the attach point for the other toolbars.
     * @param newToolbar The toolbar that will be shown.
     * @param oldToolbar The toolbar being replaced.
     */
    private void doToolbarSwap(View newToolbar, View oldToolbar) {
        if (mToolbarFadeAnimator != null) mToolbarFadeAnimator.end();

        final View targetToolbar = newToolbar != null ? newToolbar : mDefaultToolbarView;
        final View currentToolbar = oldToolbar != null ? oldToolbar : mDefaultToolbarView;

        if (targetToolbar == currentToolbar) return;

        if (targetToolbar != mDefaultToolbarView) {
            mToolbarHolder.addView(targetToolbar);
            targetToolbar.setAlpha(0f);
        } else {
            targetToolbar.setVisibility(View.VISIBLE);
            targetToolbar.setAlpha(1f);
        }

        mToolbarFadeAnimator = ObjectAnimator.ofFloat(0, 1);
        mToolbarFadeAnimator.setDuration(BASE_ANIMATION_DURATION_MS);
        mToolbarFadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                if (targetToolbar == mDefaultToolbarView) {
                    currentToolbar.setAlpha(1f - animator.getAnimatedFraction());
                } else {
                    targetToolbar.setAlpha(animator.getAnimatedFraction());
                }
            }
        });
        mToolbarFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                targetToolbar.setAlpha(1f);
                currentToolbar.setAlpha(0f);
                if (currentToolbar != mDefaultToolbarView) {
                    mToolbarHolder.removeView(currentToolbar);
                } else {
                    currentToolbar.setVisibility(View.GONE);
                }
                mToolbarFadeAnimator = null;
            }
        });

        mToolbarFadeAnimator.start();
    }

    /**
     * Determines if a touch event is inside the toolbar. This assumes the toolbar is the full
     * width of the screen and that the toolbar is at the top of the bottom sheet.
     * @param e The motion event to test.
     * @return True if the event occured in the toolbar region.
     */
    private boolean isTouchEventInToolbar(MotionEvent e) {
        if (mControlContainer == null) return false;

        mControlContainer.getLocationInWindow(mLocationArray);

        return e.getRawY() < mLocationArray[1] + mToolbarHeight;
    }

    /**
     * A notification that the sheet is exiting the peek state into one that shows content.
     */
    private void onSheetOpened() {
        if (mIsSheetOpen) return;

        mIsSheetOpen = true;
        for (BottomSheetObserver o : mObservers) o.onSheetOpened();
    }

    /**
     * A notification that the sheet has returned to the peeking state.
     */
    private void onSheetClosed() {
        if (!mIsSheetOpen) return;

        mIsSheetOpen = false;
        for (BottomSheetObserver o : mObservers) o.onSheetClosed();
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
     * Updates the bottom sheet's peeking and content height.
     */
    private void updateSheetDimensions() {
        if (mContainerHeight <= 0) return;

        // Though mStateRatios is a static constant, the peeking ratio is computed here because
        // the correct toolbar height and container height are not know until those views are
        // inflated. The other views are a specific DP distance from the top and bottom and are
        // also updated.
        mStateRatios[0] = mToolbarHeight / mContainerHeight;
        mStateRatios[1] = HALF_HEIGHT_RATIO;
        mStateRatios[2] = (mContainerHeight - mFullHeightDistanceFromTop) / mContainerHeight;

        // Compute the height that the content section of the bottom sheet.
        float contentHeight = (mContainerHeight * getFullRatio()) - mToolbarHeight;

        MarginLayoutParams sheetContentParams =
                (MarginLayoutParams) mBottomSheetContentContainer.getLayoutParams();
        sheetContentParams.width = (int) mContainerWidth;
        sheetContentParams.height = (int) contentHeight;
        sheetContentParams.topMargin = (int) mToolbarHeight;

        MarginLayoutParams toolbarShadowParams =
                (MarginLayoutParams) findViewById(R.id.toolbar_shadow).getLayoutParams();
        toolbarShadowParams.topMargin = (int) mToolbarHeight;

        mBottomSheetContentContainer.requestLayout();
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
     */
    private void createSettleAnimation(@SheetState int targetState) {
        mTargetState = targetState;
        mSettleAnimator = ValueAnimator.ofFloat(
                getSheetOffsetFromBottom(), getSheetHeightForState(targetState));
        mSettleAnimator.setDuration(BASE_ANIMATION_DURATION_MS);
        mSettleAnimator.setInterpolator(mInterpolator);

        // When the animation is canceled or ends, reset the handle to null.
        mSettleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mSettleAnimator = null;
                setInternalCurrentState(mTargetState);
                mTargetState = SHEET_STATE_NONE;
            }
        });

        mSettleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                setSheetOffsetFromBottom((Float) animator.getAnimatedValue());
            }
        });

        setInternalCurrentState(SHEET_STATE_SCROLLING);
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
        if (MathUtils.areFloatsEqual(getSheetOffsetFromBottom(), getMinOffset())
                && offset > getMinOffset()) {
            onSheetOpened();
        } else if (MathUtils.areFloatsEqual(offset, getMinOffset())
                && getSheetOffsetFromBottom() > getMinOffset()) {
            onSheetClosed();
        }

        setTranslationY(mContainerHeight - offset);
        sendOffsetChangeEvents();
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
     * Moves the sheet to the provided state.
     * @param state The state to move the panel to. This cannot be SHEET_STATE_SCROLLING or
     *              SHEET_STATE_NONE.
     * @param animate If true, the sheet will animate to the provided state, otherwise it will
     *                move there instantly.
     */
    public void setSheetState(@SheetState int state, boolean animate) {
        assert state != SHEET_STATE_SCROLLING && state != SHEET_STATE_NONE;
        mTargetState = state;

        if (animate) {
            createSettleAnimation(state);
        } else {
            setSheetOffsetFromBottom(getSheetHeightForState(state));
            setInternalCurrentState(mTargetState);
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
     */
    private void setInternalCurrentState(@SheetState int state) {
        if (state == mCurrentState) return;
        mCurrentState = state;

        for (BottomSheetObserver o : mObservers) {
            o.onSheetStateChanged(mCurrentState);
        }
    }

    /**
     * If the animation to settle the sheet in one of its states is running.
     * @return True if the animation is running.
     */
    public boolean isRunningSettleAnimation() {
        return mSettleAnimator != null;
    }

    @VisibleForTesting
    public BottomSheetContent getCurrentSheetContent() {
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
    private float getSheetHeightForState(@SheetState int state) {
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

        float fullToHalfDiff = (getFullRatio() - getHalfRatio()) * mContainerHeight;
        boolean isMovingDownward = yVelocity < 0;

        // A small screen is defined by there being less than 160dp between half and full states.
        boolean isSmallScreen = fullToHalfDiff < mMinHalfFullDistance;

        boolean shouldSkipHalfState = isMovingDownward || isSmallScreen;

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

    @Override
    public void onFadingViewClick() {
        setSheetState(SHEET_STATE_PEEK, true);
    }

    @Override
    public void onFadingViewVisibilityChanged(boolean visible) {}

    /**
     * Checks whether the sheet can be moved. It cannot be moved when the activity is in overview
     * mode, when "find in page" is visible, or when the toolbar is hidden.
     */
    private boolean canMoveSheet() {
        boolean isInOverviewMode = mTabModelSelector != null
                && (mTabModelSelector.getCurrentTab() == null
                           || mTabModelSelector.getCurrentTab().getActivity().isInOverviewMode());

        if (mFindInPageView == null) mFindInPageView = findViewById(R.id.find_toolbar);
        boolean isFindInPageVisible =
                mFindInPageView != null && mFindInPageView.getVisibility() == View.VISIBLE;
        return !isToolbarAndroidViewHidden() && !isInOverviewMode && !isFindInPageVisible;
    }
}
