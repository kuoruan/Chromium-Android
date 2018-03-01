// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.infobar.InfoBarContainer.InfoBarContainerObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.ToolbarProgressBar;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetObserver;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Phone specific toolbar that exists at the bottom of the screen.
 */
public class BottomToolbarPhone extends ToolbarPhone {
    /**
     * The observer used to listen to {@link BottomSheet} events.
     */
    private final BottomSheetObserver mBottomSheetObserver = new EmptyBottomSheetObserver() {
        @Override
        public void onSheetOpened(@StateChangeReason int reason) {
            onPrimaryColorChanged(true);
            // If the toolbar is focused, switch focus to the bottom sheet before changing the
            // content description. If the content description is changed while the view is
            // focused, the new content description is read immediately.
            if (hasFocus() && !urlHasFocus()) mBottomSheet.requestFocus();

            mLocationBar.updateLoadingState(true);
        }

        @Override
        public void onSheetClosed(@StateChangeReason int reason) {
            onPrimaryColorChanged(true);
            updateMenuButtonClickableState();

            mLocationBar.updateLoadingState(true);
        }

        @Override
        public void onSheetReleased() {
            onPrimaryColorChanged(true);
        }

        @Override
        public void onSheetOffsetChanged(float heightFraction) {
            boolean isMovingDown = heightFraction < mLastHeightFraction;
            mLastHeightFraction = heightFraction;

            // The only time the omnibox should have focus is when the sheet is fully expanded. Any
            // movement of the sheet should unfocus it.
            if (isMovingDown && getLocationBar().isUrlBarFocused()) {
                getLocationBar().setUrlBarFocus(false);
                // Revert the URL to match the current page.
                getLocationBar().setUrlToPageUrl();
            }
            boolean buttonsClickable = heightFraction == 0.f;
            mToggleTabStackButton.setClickable(buttonsClickable);
            updateMenuButtonClickableState();
            mExpandButton.setClickable(buttonsClickable);
        }

        @Override
        public void onTransitionPeekToHalf(float transitionFraction) {
            if (mLastPeekToHalfHeightFraction == transitionFraction) return;

            boolean isMovingUp = transitionFraction > mLastPeekToHalfHeightFraction;
            mLastPeekToHalfHeightFraction = transitionFraction;
            updateToolbarButtonAnimation(isMovingUp);
        }
    };

    /** The time a transition for the top toolbar shadow should take in ms. */
    private static final int DURATION_SHADOW_TRANSITION_MS = 250;

    /** The background alpha for the tab switcher in Chrome Modern. */
    private static final float MODERN_TAB_SWITCHER_TOOLBAR_ALPHA = 0.9f;

    /** The white version of the toolbar handle; used for dark themes and incognito. */
    private final Drawable mHandleLight;

    /** The dark version of the toolbar handle; this is the default handle to use. */
    private final Drawable mHandleDark;

    /** A handle to the bottom sheet. */
    private BottomSheet mBottomSheet;

    /** A handle to the expand button that Chrome Home may or may not use. */
    private TintedImageButton mExpandButton;

    /**
     * Whether some of the toolbar buttons are hidden regardless of whether the URL bar is focused.
     * If {@link #mShowMenuButtonWhenSheetOpen} is false, all buttons are hidden.
     * If {@link #mShowMenuButtonWhenSheetOpen} is true, all buttons besides the menu button are
     * hidden.
     */
    private boolean mHidingSomeToolbarButtons;

    /**
     * This tracks the height fraction of the bottom bar to determine if it is moving up or down.
     */
    private float mLastHeightFraction;

    /**
     * This tracks the peek-to-half height fraction of the bottom bar to determine if it is moving
     * up or down.
     */
    private float mLastPeekToHalfHeightFraction;

    /** The toolbar handle view that indicates the toolbar can be pulled upward. */
    private ImageView mToolbarHandleView;

    /** Whether or not the expand button should be used. */
    private boolean mUseExpandButton;

    /** The shadow above the bottom toolbar. */
    private ImageView mBottomToolbarTopShadow;

    /**
     * Tracks whether the toolbar buttons are hidden, with 1.f being fully visible and 0.f being
     * fully hidden.
     */
    private float mToolbarButtonVisibilityPercent;

    /**
     * The interpolator for the toolbar button animation. It will either be a fade-in or fade-out
     * curve depending on whether the buttons are being shown or hidden.
     */
    private Interpolator mToolbarButtonAnimationIterpolator;

    /** Whether the appearance of the toolbar buttons is currently animating. */
    private boolean mAnimatingToolbarButtonAppearance;

    /** Whether the disappearance of the toolbar buttons is currently animating. */
    private boolean mAnimatingToolbarButtonDisappearance;

    /** Whether the menu button should be shown while the sheet is open. */
    private boolean mShowMenuButtonWhenSheetOpen;

    /** The height of the location bar background. */
    private float mLocationBarBackgroundHeight;

    /**
     * The float used to inset the rect returned by {@link #getLocationBarContentRect(Rect)}.
     * This extra vertical inset is needed to ensure the anonymize layer doesn't draw outside of the
     * background bounds.
     */
    private float mLocationBarContentVerticalInset;

    /**
     * The float used to inset the rect returned by {@link #getLocationBarContentRect(Rect)}.
     * This extra lateral inset is needed to ensure the anonymize layer doesn't draw outside of the
     * background bounds.
     */
    private float mLocationBarContentLateralInset;

    /**
     * The extra margin to apply to the left side of the location bar when it is focused.
     */
    private int mLocationBarExtraFocusedLeftMargin;

    /** The top shadow drawable of the bottom toolbar if it exists. */
    private LayerDrawable mBottomToolbarTopShadowDrawable;

    /** Observer of the infobar container to change the toolbar shadow. */
    private InfoBarContainerObserver mInfoBarContainerObserver;

    /**
     * A tab observer to attach/detach the {@link InfoBarContainerObserver} for the bottom toolbar
     * top shadow.
     */
    private TabObserver mTopShadowTabObserver;

    /** A handle to the {@link ChromeActivity} this toolbar exists in. */
    private ChromeActivity mActivity;

    /**
     * Constructs a BottomToolbarPhone object.
     * @param context The Context in which this View object is created.
     * @param attrs The AttributeSet that was specified with this View.
     */
    public BottomToolbarPhone(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = context.getResources();
        mHandleDark = ApiCompatibilityUtils.getDrawable(res, R.drawable.toolbar_handle_dark);
        mHandleLight = ApiCompatibilityUtils.getDrawable(res, R.drawable.toolbar_handle_light);
        mLocationBarContentLateralInset =
                res.getDimensionPixelSize(R.dimen.bottom_location_bar_content_lateral_inset);
        mLocationBarContentVerticalInset =
                res.getDimensionPixelSize(R.dimen.bottom_location_bar_content_vertical_inset);
        mLocationBarExtraFocusedLeftMargin =
                res.getDimensionPixelSize(R.dimen.bottom_toolbar_background_focused_left_margin);

        mToolbarShadowPermanentlyHidden = true;
        mToolbarButtonVisibilityPercent = 1.f;
        mToolbarButtonAnimationIterpolator = BakedBezierInterpolator.FADE_OUT_CURVE;

        mInfoBarContainerObserver = new InfoBarContainerObserver() {
            @Override
            public void onAddInfoBar(InfoBarContainer c, InfoBar infoBar, boolean isFirst) {
                if (!isFirst) return;
                createShadowTransitionAnimator(1, 0).start();
            }

            @Override
            public void onRemoveInfoBar(InfoBarContainer c, InfoBar infoBar, boolean isLast) {
                if (!isLast) return;
                createShadowTransitionAnimator(0, 1).start();
            }

            @Override
            public void onInfoBarContainerAttachedToWindow(boolean hasInfobars) {}

            @Override
            public void onInfoBarContainerShownRatioChanged(InfoBarContainer c, float shownRatio) {
                if (c.isAnimating()) return;
                mBottomToolbarTopShadowDrawable.getDrawable(0).setAlpha(
                        (int) (255 * (1 - shownRatio)));
                mBottomToolbarTopShadowDrawable.getDrawable(1).setAlpha((int) (255 * shownRatio));
            }
        };

        mTopShadowTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab) {
                if (tab.getInfoBarContainer() == null) return;
                tab.getInfoBarContainer().addObserver(mInfoBarContainerObserver);
            }

            @Override
            public void onHidden(Tab tab) {
                if (tab.getInfoBarContainer() == null) return;
                tab.getInfoBarContainer().removeObserver(mInfoBarContainerObserver);
            }

            @Override
            public void onContentChanged(Tab tab) {
                if (tab.getInfoBarContainer() == null) return;
                tab.getInfoBarContainer().addObserver(mInfoBarContainerObserver);
            }
        };
    }

    /**
     * Get the view and drawable for the bottom toolbar's top shadow and initialized the drawable
     * state.
     */
    private void initBottomToolbarTopShadow() {
        mBottomToolbarTopShadow =
                (ImageView) getRootView().findViewById(R.id.bottom_toolbar_shadow);
        mBottomToolbarTopShadowDrawable = (LayerDrawable) ApiCompatibilityUtils.getDrawable(
                getResources(), R.drawable.modern_bottom_toolbar_shadow);

        mBottomToolbarTopShadowDrawable.getDrawable(0).setAlpha(255);
        mBottomToolbarTopShadowDrawable.getDrawable(1).setAlpha(0);

        mBottomToolbarTopShadow.setImageDrawable(mBottomToolbarTopShadowDrawable);
    }

    /**
     * @param activity The {@link ChromeActivity} displaying this toolbar.
     */
    public void setActivity(ChromeActivity activity) {
        mActivity = activity;
    }

    /**
     * @return The expand button if it is being used.
     */
    public View getExpandButton() {
        return mExpandButton;
    }

    /**
     * @return Whether the expand button is currently being used.
     */
    public boolean isUsingExpandButton() {
        return mUseExpandButton;
    }

    /**
     * Set the color of the pull handle used by the toolbar.
     * @param useLightDrawable If the handle color should be light.
     */
    public void updateHandleTint(boolean useLightDrawable) {
        mToolbarHandleView.setImageDrawable(useLightDrawable ? mHandleLight : mHandleDark);
    }

    /**
     * @return Whether or not the toolbar is currently using a light theme color.
     */
    public boolean isLightTheme() {
        return !ColorUtils.shouldUseLightForegroundOnBackground(getTabThemeColor());
    }

    @Override
    public boolean isInTabSwitcherMode() {
        return super.isInTabSwitcherMode() && (mBottomSheet == null || !mBottomSheet.isSheetOpen());
    }

    @Override
    protected boolean shouldDrawShadow() {
        return mBottomSheet.isSheetOpen() || super.shouldDrawShadow();
    }

    @Override
    public boolean isReadyForTextureCapture() {
        return super.isReadyForTextureCapture() && !mBottomSheet.isShowingNewTab();
    }

    /** Shows the tab switcher toolbar. */
    public void showTabSwitcherToolbar() {
        setTabSwitcherMode(true, true, false);
    }

    /** Shows the normal toolbar. */
    public void showNormalToolbar() {
        // TODO(twellington): Add animation.
        setTabSwitcherMode(false, true, false, false);

        // Typically #onTabSwitcherTransitionFinished() is called when the tab switcher is finished
        // hiding. In this scenario, however, we are showing the normal toolbar without hiding
        // the tab switcher. Call #onTabSwitcherTransitionFinished() directly so that ToolbarPhone
        // updates its state.
        onTabSwitcherTransitionFinished();
    }

    @Override
    protected void setTabSwitcherMode(boolean inTabSwitcherMode, boolean showToolbar,
            boolean delayAnimation, boolean animate) {
        super.setTabSwitcherMode(inTabSwitcherMode, showToolbar, delayAnimation, animate);
        mExpandButton.setClickable(!inTabSwitcherMode);

        // Reset top shadow drawable state.
        if (inTabSwitcherMode) {
            mBottomToolbarTopShadowDrawable.getDrawable(0).setAlpha(255);
            mBottomToolbarTopShadowDrawable.getDrawable(1).setAlpha(0);
        }
    }

    @Override
    protected void onTabOrModelChanged() {
        super.onTabOrModelChanged();
        attachShadowTabObserverToCurrentTab();
    }

    @Override
    public void onStateRestored() {
        super.onStateRestored();
        attachShadowTabObserverToCurrentTab();
    }

    /**
     * Attempt to attach the tab observer that controls the top shadow to the current tab.
     */
    private void attachShadowTabObserverToCurrentTab() {
        Tab currentTab = getToolbarDataProvider().getTab();
        if (currentTab == null) return;

        currentTab.addObserver(mTopShadowTabObserver);

        if (currentTab.getInfoBarContainer() == null) return;
        currentTab.getInfoBarContainer().addObserver(mInfoBarContainerObserver);
    }

    /**
     * Create a transition animation for the top shadow.
     * @param start The start opacity of the primary drawable (the shadow rather than the line).
     * @param end The end opacity of the primary drawable.
     * @return An {@link Animator} that runs the transition.
     */
    private Animator createShadowTransitionAnimator(float start, float end) {
        ValueAnimator transition = ValueAnimator.ofFloat(start, end);
        transition.setDuration(DURATION_SHADOW_TRANSITION_MS);
        transition.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float value = (float) valueAnimator.getAnimatedValue();
                mBottomToolbarTopShadowDrawable.getDrawable(0).setAlpha((int) (value * 255));
                mBottomToolbarTopShadowDrawable.getDrawable(1).setAlpha((int) ((1 - value) * 255));
            }
        });
        return transition;
    }

    @Override
    protected int getProgressBarColor() {
        int color = super.getProgressBarColor();
        if (getToolbarDataProvider().getTab() != null) {
            // ToolbarDataProvider itself accounts for Chrome Home and will return default colors,
            // so pull the progress bar color from the tab.
            color = getToolbarDataProvider().getTab().getThemeColor();
        }
        return color;
    }

    @Override
    protected int getProgressBarTopMargin() {
        // In the case where the toolbar is at the bottom of the screen, the progress bar should
        // be at the top of the screen.
        return 0;
    }

    @Override
    protected int getProgressBarHeight() {
        return getResources().getDimensionPixelSize(R.dimen.chrome_home_progress_bar_height);
    }

    @Override
    protected ToolbarProgressBar createProgressBar() {
        return new ToolbarProgressBar(
                getContext(), getProgressBarHeight(), getProgressBarTopMargin(), true);
    }

    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        mActivity.getBottomSheetContentController().onOmniboxFocusChange(hasFocus);

        super.onUrlFocusChange(hasFocus);
    }

    @Override
    protected void triggerUrlFocusAnimation(final boolean hasFocus) {
        super.triggerUrlFocusAnimation(hasFocus);

        if (mBottomSheet == null || !hasFocus) return;

        mBottomSheet.setSheetState(
                BottomSheet.SHEET_STATE_FULL, true, StateChangeReason.OMNIBOX_FOCUS);
    }

    @Override
    public void setBottomSheet(BottomSheet sheet) {
        assert mBottomSheet == null;

        mBottomSheet = sheet;
        getLocationBar().setBottomSheet(mBottomSheet);
        mBottomSheet.addObserver(mBottomSheetObserver);
    }

    @Override
    public boolean shouldIgnoreSwipeGesture() {
        // Only detect swipes if the bottom sheet in the peeking state and not animating.
        return mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_PEEK
                || mBottomSheet.isRunningSettleAnimation() || super.shouldIgnoreSwipeGesture();
    }

    @Override
    protected void addProgressBarToHierarchy() {
        if (mProgressBar == null) return;

        ViewGroup coordinator = (ViewGroup) getRootView().findViewById(R.id.coordinator);
        UiUtils.insertBefore(coordinator, mProgressBar, mBottomSheet);

        mProgressBar.setProgressBarContainer(coordinator);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!mDisableLocationBarRelayout && !isInTabSwitcherMode()
                && (mAnimatingToolbarButtonAppearance || mAnimatingToolbarButtonDisappearance)) {
            // ToolbarPhone calls #updateUrlExpansionAnimation() in its #onMeasure(). If the toolbar
            // button visibility animation is running, call #updateToolbarButtonVisibility() to
            // ensure that view properties are set correctly.
            updateToolbarButtonVisibility();
        }
    }

    /**
     * @return The extra top margin that should be applied to the browser controls views to
     *         correctly offset them from the handle that sits above them.
     */
    private int getExtraTopMargin() {
        return getResources().getDimensionPixelSize(R.dimen.bottom_toolbar_top_margin);
    }

    @Override
    public void getLocationBarContentRect(Rect outRect) {
        super.getLocationBarContentRect(outRect);

        outRect.left += mLocationBarContentLateralInset;
        outRect.top += mLocationBarContentVerticalInset;
        outRect.right -= mLocationBarContentLateralInset;
        outRect.bottom -= mLocationBarContentVerticalInset;
    }

    @Override
    protected int getFocusedLocationBarWidth(int containerWidth, int priorVisibleWidth) {
        return super.getFocusedLocationBarWidth(containerWidth, priorVisibleWidth)
                - mLocationBarExtraFocusedLeftMargin - mLocationBarBackgroundPadding.left
                - mLocationBarBackgroundPadding.right;
    }

    @Override
    protected int getFocusedLocationBarLeftMargin(int priorVisibleWidth) {
        int baseMargin = mToolbarSidePadding + mLocationBarExtraFocusedLeftMargin;
        if (ApiCompatibilityUtils.isLayoutRtl(mLocationBar)) {
            return baseMargin - mLocationBarBackgroundPadding.right;
        } else {
            return baseMargin - priorVisibleWidth + mLocationBarBackgroundPadding.left;
        }
    }

    @Override
    protected int getLocationBarBackgroundVerticalMargin(float expansion) {
        return (int) ((mLocationBar.getHeight() - mLocationBarBackgroundHeight) / 2);
    }

    @Override
    protected int getLeftPositionOfLocationBarBackground(VisualState visualState) {
        if (!mAnimatingToolbarButtonAppearance && !mAnimatingToolbarButtonDisappearance) {
            return super.getLeftPositionOfLocationBarBackground(visualState);
        }

        int currentPosition = getViewBoundsLeftOfLocationBar(visualState);
        int targetPosition = currentPosition + getLocationBarBackgroundLeftOffset();
        return (int) MathUtils.interpolate(
                currentPosition, targetPosition, mToolbarButtonVisibilityPercent);
    }

    @Override
    protected int getFocusedLeftPositionOfLocationBarBackground() {
        return mToolbarSidePadding;
    }

    @Override
    protected int getRightPositionOfLocationBarBackground(VisualState visualState) {
        if (!mAnimatingToolbarButtonAppearance && !mAnimatingToolbarButtonDisappearance) {
            return super.getRightPositionOfLocationBarBackground(visualState);
        }

        int currentPosition = getViewBoundsRightOfLocationBar(visualState);
        int targetPosition = currentPosition - getLocationBarBackgroundRightOffset();
        return (int) MathUtils.interpolate(
                currentPosition, targetPosition, mToolbarButtonVisibilityPercent);
    }

    @Override
    protected int getFocusedRightPositionOfLocationBarBackground() {
        return getWidth() - mToolbarSidePadding;
    }

    private int getToolbarButtonsWidthForBackgroundOffset() {
        return mShowMenuButtonWhenSheetOpen
                ? mToolbarButtonsContainer.getMeasuredWidth() - mMenuButton.getMeasuredWidth()
                : mToolbarButtonsContainer.getMeasuredWidth();
    }

    private int getLocationBarBackgroundLeftOffset() {
        return !ApiCompatibilityUtils.isLayoutRtl(this)
                ? 0
                : getToolbarButtonsWidthForBackgroundOffset() - mToolbarSidePadding;
    }

    private int getLocationBarBackgroundRightOffset() {
        return !ApiCompatibilityUtils.isLayoutRtl(this)
                ? getToolbarButtonsWidthForBackgroundOffset() - mToolbarSidePadding
                : 0;
    }

    @Override
    protected int getBoundsAfterAccountingForRightButtons() {
        if (!mHidingSomeToolbarButtons) return super.getBoundsAfterAccountingForRightButtons();

        return !mShowMenuButtonWhenSheetOpen ? mToolbarSidePadding
                                             : mMenuButton.getMeasuredWidth() + mToolbarSidePadding;
    }

    @Override
    protected boolean isChildLeft(View child) {
        return (child == mNewTabButton || child == mExpandButton) ^ LocalizationUtils.isLayoutRtl();
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        // Chrome Home does not support a home button. Remove the View to save memory.
        removeView(mHomeButton);
        mBrowsingModeViews.remove(mHomeButton);
        mHomeButton = null;

        mExpandButton = (TintedImageButton) findViewById(R.id.expand_sheet_button);
        mExpandButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBottomSheet != null && mTabSwitcherState == STATIC_TAB) {
                    mBottomSheet.onExpandButtonPressed();
                }
            }
        });
        mExpandButton.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                AccessibilityNodeInfoCompat infoCompat = new AccessibilityNodeInfoCompat(info);
                infoCompat.setClickable(true);
                infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        getResources().getString(
                                R.string.bottom_sheet_expand_button_accessibility)));
            }
        });

        // Add extra top margin to the URL bar to compensate for the change to location bar's
        // vertical margin in the constructor.
        ((MarginLayoutParams) mLocationBar.findViewById(R.id.url_bar).getLayoutParams()).topMargin =
                getResources().getDimensionPixelSize(R.dimen.bottom_toolbar_url_bar_top_margin);

        // Exclude the location bar from the list of browsing mode views. This prevents its
        // visibility from changing during transitions.
        mBrowsingModeViews.remove(mLocationBar);

        updateToolbarTopMargin();
    }

    @Override
    protected void initLocationBarBackground() {
        Resources res = getResources();
        mLocationBarBackgroundHeight =
                res.getDimensionPixelSize(R.dimen.modern_toolbar_background_size);
        mLocationBarBackground =
                ApiCompatibilityUtils.getDrawable(res, R.drawable.modern_toolbar_background);
        mLocationBarBackground.getPadding(mLocationBarBackgroundPadding);
        mLocationBarBackground.mutate();
        mLocationBar.setPadding(mLocationBarBackgroundPadding.left,
                mLocationBarBackgroundPadding.top, mLocationBarBackgroundPadding.right,
                mLocationBarBackgroundPadding.bottom);
    }

    @Override
    public void initialize(ToolbarDataProvider toolbarDataProvider,
            ToolbarTabController tabController, AppMenuButtonHelper appMenuButtonHelper) {
        super.initialize(toolbarDataProvider, tabController, appMenuButtonHelper);
        mAppMenuButtonHelper.setShowMenuOnUp(true);
    }

    /**
     * Update the top margin of all the components inside the toolbar. If the toolbar handle is
     * being used, extra margin is added.
     */
    private void updateToolbarTopMargin() {
        // Programmatically apply a top margin to all the children of the toolbar container. This
        // is done so the view hierarchy does not need to be changed.
        int topMarginForControls = getExtraTopMargin();

        View topShadow = findViewById(R.id.bottom_toolbar_shadow);

        for (int i = 0; i < getChildCount(); i++) {
            View curView = getChildAt(i);

            // Skip the shadow that sits at the top of the toolbar since this needs to sit on top
            // of the toolbar.
            if (curView == topShadow) continue;

            ((MarginLayoutParams) curView.getLayoutParams()).topMargin = topMarginForControls;
        }
    }

    @Override
    protected boolean shouldDrawLocationBarBackground() {
        return mLocationBar.getAlpha() > 0 || mForceDrawLocationBarBackground;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // The toolbar handle is part of the control container so it can draw on top of the
        // other toolbar views, this way there is only a single handle instead of each having its
        // own. Get the root view and search for the handle.
        mToolbarHandleView = (ImageView) getRootView().findViewById(R.id.toolbar_handle);
        mToolbarHandleView.setImageDrawable(mHandleDark);

        setUseExpandButton();

        initBottomToolbarTopShadow();

        if (mToolbarShadowPermanentlyHidden) mToolbarShadow.setVisibility(View.GONE);
    }

    @Override
    public void onNativeLibraryReady() {
        super.onNativeLibraryReady();

        mNewTabButton.setIsModern();
    }

    @Override
    protected void updateVisualsForToolbarState() {
        super.updateVisualsForToolbarState();

        getProgressBar().setThemeColor(getProgressBarColor(), isIncognito());

        // TODO(mdjones): Creating a new tab from the tab switcher skips the
        // drawTabSwitcherFadeAnimation which would otherwise make this line unnecessary.
        if (mTabSwitcherState == STATIC_TAB) mToolbarHandleView.setAlpha(1f);

        // The tab switcher's background color should not affect the toolbar handle; it should only
        // switch color based on the static tab's theme color. This is done so fade in/out looks
        // correct.
        mToolbarHandleView.setImageDrawable(isLightTheme() ? mHandleDark : mHandleLight);
        if (mUseExpandButton) {
            ColorStateList tint = isIncognito() ? mLightModeTint : mDarkModeTint;
            mExpandButton.setTint(tint);
        }

        if (mBottomSheet != null && mBottomSheet.isSheetOpen()) {
            mShowMenuButtonWhenSheetOpen = mBottomSheet.isShowingNewTab();
            updateButtonsContainerVisibilityAndTranslation();
            updateMenuButtonClickableState();
        }

        mToggleTabStackButton.setClickable(mBottomSheet == null || !mBottomSheet.isShowingNewTab());

        DrawableCompat.setTint(mLocationBarBackground,
                isIncognito() ? Color.WHITE
                              : ApiCompatibilityUtils.getColor(
                                        getResources(), R.color.modern_light_grey));
    }

    @Override
    protected void onPrimaryColorChanged(boolean shouldAnimate) {
        // Intentionally not calling super to avoid needless work.
        getProgressBar().setThemeColor(getProgressBarColor(), isIncognito());
    }

    @Override
    protected boolean shouldDrawLocationBar() {
        return true;
    }

    @Override
    protected void drawTabSwitcherFadeAnimation(boolean animationFinished, float progress) {
        mNewTabButton.setAlpha(progress);

        mLocationBar.setAlpha(1f - progress);
        mLocationBar.setVisibility(MathUtils.areFloatsEqual(mLocationBar.getAlpha(), 0f)
                        ? View.INVISIBLE
                        : View.VISIBLE);

        mToolbarHandleView.setAlpha(1f - progress);
        if (mUseExpandButton) mExpandButton.setAlpha(1f - progress);

        updateToolbarBackground(ColorUtils.getColorWithOverlay(
                getTabThemeColor(), getToolbarColorForVisualState(mVisualState), progress));

        mBottomToolbarTopShadow.setAlpha(1f - progress);

        // Don't use transparency for accessibility mode or low-end devices since the
        // {@link OverviewListLayout} will be used instead of the normal tab switcher.
        if (!DeviceClassManager.enableAccessibilityLayout()) {
            float toolbarAlpha = MODERN_TAB_SWITCHER_TOOLBAR_ALPHA;
            float alphaTransition = 1f - toolbarAlpha;
            mToolbarBackground.setAlpha((int) ((1f - (alphaTransition * progress)) * 255));
        }
    }

    @Override
    public void finishAnimations() {
        super.finishAnimations();
        drawTabSwitcherFadeAnimation(true, mTabSwitcherModePercent);
    }

    @Override
    protected void drawTabSwitcherAnimationOverlay(Canvas canvas, float animationProgress) {
        // Intentionally overridden to block everything but the compositor screen shot. Otherwise
        // the toolbar in Chrome Home does not have an animation overlay component.
        if (mTextureCaptureMode) {
            super.drawTabSwitcherAnimationOverlay(canvas, 0f);
            if (mUseExpandButton && mExpandButton.getVisibility() != View.GONE) {
                canvas.save();
                translateCanvasToView(this, mToolbarButtonsContainer, canvas);
                drawChild(canvas, mExpandButton, SystemClock.uptimeMillis());
                canvas.restore();
            }
        }
    }

    @Override
    protected void resetNtpAnimationValues() {
        // The NTP animations don't matter if the browser is in tab switcher mode.
        if (mTabSwitcherState != ToolbarPhone.STATIC_TAB) return;
        super.resetNtpAnimationValues();
    }

    @Override
    protected void updateToolbarBackground(VisualState visualState) {
        if (visualState == VisualState.TAB_SWITCHER_NORMAL
                || visualState == VisualState.TAB_SWITCHER_INCOGNITO) {
            // drawTabSwitcherFadeAnimation will handle the background color transition.
            if (DeviceClassManager.enableAccessibilityLayout()) {
                drawTabSwitcherFadeAnimation(true, mTabSwitcherModePercent);
            }
            return;
        }

        super.updateToolbarBackground(visualState);
    }

    @Override
    protected int getToolbarButtonVisibility() {
        if (mUrlExpansionPercent == 1f) return INVISIBLE;
        if (mShowMenuButtonWhenSheetOpen) return VISIBLE;
        if (mHidingSomeToolbarButtons) return INVISIBLE;
        return VISIBLE;
    }

    @Override
    protected float getUrlActionsTranslationXForExpansionAnimation(
            boolean isLocationBarRtl, float locationBarBaseTranslationX) {
        if (!mHidingSomeToolbarButtons) {
            return super.getUrlActionsTranslationXForExpansionAnimation(
                    isLocationBarRtl, locationBarBaseTranslationX);
        }

        float urlActionsTranslationX = 0;
        // When the end toolbar buttons are not hidden, URL actions are shown and hidden due to
        // a change in location bar's width. When the end toolbar buttons are hidden, the
        // location bar's width does not change by as much, causing the end location for the URL
        // actions to be immediately visible. Translate the URL action container so that their
        // appearance is animated.
        float urlActionsTranslationXOffset =
                mUrlActionContainer.getWidth() * (1 - mUrlExpansionPercent);
        if (isLocationBarRtl) {
            urlActionsTranslationX -= urlActionsTranslationXOffset;
        } else {
            urlActionsTranslationX += urlActionsTranslationXOffset;
        }

        return urlActionsTranslationX;
    }

    @Override
    protected void onHomeButtonUpdate(boolean homeButtonEnabled) {
        // Intentionally does not call super. Chrome Home does not support a home button.
    }

    /**
     * Sets the height and title text appearance of the provided toolbar so that its style is
     * consistent with BottomToolbarPhone.
     * @param otherToolbar The other {@link Toolbar} to style.
     */
    public void setOtherToolbarStyle(Toolbar otherToolbar) {
        // Android's Toolbar class typically changes its height based on device orientation.
        // BottomToolbarPhone has a fixed height. Update |toolbar| to match.
        otherToolbar.getLayoutParams().height = getHeight();

        // Android Toolbar action buttons are aligned based on the minimum height.
        int extraTopMargin = getExtraTopMargin();
        otherToolbar.setMinimumHeight(getHeight() - extraTopMargin);

        otherToolbar.setTitleTextAppearance(otherToolbar.getContext(), R.style.BlackHeadline1);
        ApiCompatibilityUtils.setPaddingRelative(otherToolbar,
                ApiCompatibilityUtils.getPaddingStart(otherToolbar),
                otherToolbar.getPaddingTop() + extraTopMargin,
                ApiCompatibilityUtils.getPaddingEnd(otherToolbar), otherToolbar.getPaddingBottom());
    }

    @Override
    protected void onAccessibilityStatusChanged(boolean enabled) {
        setUseExpandButton();
    }

    /**
     * Sets whether or not the expand button is used and updates the handle view and expand button
     * accordingly.
     */
    private void setUseExpandButton() {
        mUseExpandButton = AccessibilityUtil.isAccessibilityEnabled();

        // This method may be called due to an accessibility state change. Return early if the
        // needed views are null.
        if (mToolbarHandleView == null || mExpandButton == null) return;

        mExpandButton.setVisibility(mUseExpandButton ? View.VISIBLE : View.GONE);

        updateVisualsForToolbarState();
    }

    /**
     * Called when the sheet is transitioning from peek <-> half to update the toolbar button
     * animation.
     * @param isMovingUp Whether the sheet is currently moving up.
     */
    private void updateToolbarButtonAnimation(boolean isMovingUp) {
        // Update the interpolator if the toolbar buttons are fully visible or fully hidden.
        if (mToolbarButtonVisibilityPercent == 0.f || mToolbarButtonVisibilityPercent == 1.f) {
            mToolbarButtonAnimationIterpolator = isMovingUp ? BakedBezierInterpolator.FADE_OUT_CURVE
                                                            : BakedBezierInterpolator.FADE_IN_CURVE;
        }

        if (isMovingUp && !mAnimatingToolbarButtonDisappearance
                && mToolbarButtonVisibilityPercent != 0.f) {
            onToolbarButtonAnimationStart(false);
        } else if (!isMovingUp && !mAnimatingToolbarButtonAppearance
                && mToolbarButtonVisibilityPercent != 1.f) {
            onToolbarButtonAnimationStart(true);
        }

        if (!mAnimatingToolbarButtonDisappearance && !mAnimatingToolbarButtonAppearance) return;

        mToolbarButtonVisibilityPercent = mToolbarButtonAnimationIterpolator.getInterpolation(
                1.f - mLastPeekToHalfHeightFraction);
        updateToolbarButtonVisibility();

        if ((mAnimatingToolbarButtonDisappearance
                    && MathUtils.areFloatsEqual(mLastPeekToHalfHeightFraction, 1.f))
                || (mAnimatingToolbarButtonAppearance
                           && MathUtils.areFloatsEqual(mLastPeekToHalfHeightFraction, 0.f))) {
            onToolbarButtonAnimationEnd(mAnimatingToolbarButtonAppearance);
        }
    }

    private void onToolbarButtonAnimationStart(boolean visible) {
        if (mAnimatingToolbarButtonAppearance || mAnimatingToolbarButtonDisappearance) {
            // Cancel any previously running animations.
            if (mAnimatingToolbarButtonAppearance) mDisableLocationBarRelayout = false;

            mAnimatingToolbarButtonDisappearance = false;
            mAnimatingToolbarButtonAppearance = false;
        }

        if (mUrlFocusChangeInProgress) {
            if (visible) {
                mHidingSomeToolbarButtons = false;
                mShowMenuButtonWhenSheetOpen = false;
                mToolbarButtonVisibilityPercent = 1.f;

                mToolbarButtonsContainer.setAlpha(1.f);
                mToolbarButtonsContainer.setVisibility(View.VISIBLE);
                mToolbarButtonsContainer.setTranslationX(0);

                mToggleTabStackButton.setAlpha(1.f);
                mToggleTabStackButton.setVisibility(View.VISIBLE);

                if (mUseExpandButton) {
                    if (mTabSwitcherState != ENTERING_TAB_SWITCHER) mExpandButton.setAlpha(1.f);
                    mExpandButton.setVisibility(View.VISIBLE);
                }

                post(() -> requestLayout());
            } else {
                mToolbarButtonVisibilityPercent = 0.f;
                // Wait to set mShouldHideToolbarButtons until URL focus finishes.
            }

            return;
        }

        mAnimatingToolbarButtonDisappearance = !visible;
        mAnimatingToolbarButtonAppearance = visible;

        if (!visible) {
            mShowMenuButtonWhenSheetOpen = mBottomSheet.isShowingNewTab();
            mHidingSomeToolbarButtons = true;
            mLayoutLocationBarInFocusedMode = !mShowMenuButtonWhenSheetOpen;
            requestLayout();
        } else {
            mDisableLocationBarRelayout = true;
        }
    }

    private void onToolbarButtonAnimationEnd(boolean visible) {
        if (visible) {
            mHidingSomeToolbarButtons = false;
            mDisableLocationBarRelayout = false;
            mLayoutLocationBarInFocusedMode = false;
            mShowMenuButtonWhenSheetOpen = false;
            requestLayout();
        }

        mAnimatingToolbarButtonDisappearance = false;
        mAnimatingToolbarButtonAppearance = false;
        mLocationBar.scrollUrlBarToTld();
    }

    @Override
    protected void onUrlFocusChangeAnimationFinished() {
        if (urlHasFocus()) {
            mHidingSomeToolbarButtons = true;
            mToolbarButtonVisibilityPercent = 0.f;
            updateButtonsContainerVisibilityAndTranslation();
        }
        updateMenuButtonClickableState();
    }

    @Override
    protected int getToolbarColorForVisualState(final VisualState visualState) {
        if (visualState == VisualState.TAB_SWITCHER_INCOGNITO) {
            return ApiCompatibilityUtils.getColor(getResources(),
                    DeviceClassManager.enableAccessibilityLayout() ? R.color.incognito_primary_color
                                                                   : R.color.modern_primary_color);
        } else if (visualState == VisualState.NORMAL
                || visualState == VisualState.TAB_SWITCHER_NORMAL) {
            return ApiCompatibilityUtils.getColor(getResources(), R.color.modern_primary_color);
        }

        return super.getToolbarColorForVisualState(visualState);
    }

    /**
     * Updates the visibility and translation of the toolbar buttons by calling
     * {@link #updateButtonsContainerVisibilityAndTranslation()} and manipulating the LocationBar's
     * translation X.
     */
    private void updateToolbarButtonVisibility() {
        updateButtonsContainerVisibilityAndTranslation();

        float locationBarTranslationX;
        boolean isLocationBarRtl = ApiCompatibilityUtils.isLayoutRtl(mLocationBar);
        FrameLayout.LayoutParams locationBarLayoutParams = getFrameLayoutParams(mLocationBar);
        int currentLeftMargin = locationBarLayoutParams.leftMargin;
        int currentWidth = locationBarLayoutParams.width;

        if (isLocationBarRtl) {
            // The location bar contents should be aligned with the right side of the toolbar.
            // If RTL text is displayed in an LTR toolbar, the right position of the location bar
            // background will change as the location bar background expands/contracts.
            locationBarTranslationX =
                    -currentWidth + getRightPositionOfLocationBarBackground(mVisualState);
            if (!mHasVisibleViewPriorToUrlBar) locationBarTranslationX -= mToolbarSidePadding;
        } else {
            // The location bar contents should be aligned with the left side of the location bar
            // background. If LTR text is displayed in an RTL toolbar, the current left position of
            // the location bar background will change as the location bar background
            // expands/contracts.
            locationBarTranslationX = mUnfocusedLocationBarLayoutLeft
                    + getLeftPositionOfLocationBarBackground(mVisualState) - mToolbarSidePadding;
        }

        locationBarTranslationX -= currentLeftMargin;

        // Get the padding straight from the location bar instead of
        // |mLocationBarBackgroundPadding|, because it might be different in incognito mode.
        if (isLocationBarRtl) {
            locationBarTranslationX -= mLocationBar.getPaddingRight();
        } else {
            locationBarTranslationX += mLocationBar.getPaddingLeft();
        }

        mLocationBar.setTranslationX(locationBarTranslationX);

        // Force an invalidation of the location bar to properly handle the clipping of the URL
        // bar text as a result of the bounds changing.
        mLocationBar.invalidate();
        invalidate();
    }

    /**
     * Updates the visibility, alpha and translation of the buttons container based on
     * {@link #mToolbarButtonVisibilityPercent}. If {@link #mShowMenuButtonWhenSheetOpen} is true,
     * the tab switcher button and, if present, the expand button are faded out; nothing is
     * translated. If {@link #mShowMenuButtonWhenSheetOpen} is false, the entire
     * {@link #mToolbarButtonsContainer} is faded out and translated so that the buttons appear to
     * slide off the toolbar.
     */
    private void updateButtonsContainerVisibilityAndTranslation() {
        if (mShowMenuButtonWhenSheetOpen) {
            mToolbarButtonsContainer.setTranslationX(0);
            mToolbarButtonsContainer.setAlpha(1.f);
            mToolbarButtonsContainer.setVisibility(View.VISIBLE);

            float buttonAlpha = mToolbarButtonVisibilityPercent <= 0.5
                    ? 0
                    : 1.f - ((1.f - mToolbarButtonVisibilityPercent) * 2);
            mToggleTabStackButton.setAlpha(buttonAlpha);
            mToggleTabStackButton.setVisibility(
                    mToolbarButtonVisibilityPercent > 0.f ? View.VISIBLE : View.INVISIBLE);

            if (mUseExpandButton) {
                if (mTabSwitcherState != ENTERING_TAB_SWITCHER) mExpandButton.setAlpha(buttonAlpha);
                mExpandButton.setVisibility(
                        mToolbarButtonVisibilityPercent > 0.f ? View.VISIBLE : View.INVISIBLE);
            }
        } else {
            mToggleTabStackButton.setAlpha(1.f);
            mToggleTabStackButton.setVisibility(View.VISIBLE);

            if (mUseExpandButton) {
                if (mTabSwitcherState != ENTERING_TAB_SWITCHER) mExpandButton.setAlpha(1.f);
                mExpandButton.setVisibility(View.VISIBLE);
            }

            boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(this);

            float toolbarButtonsContainerWidth = mToolbarButtonsContainer.getMeasuredWidth();
            float toolbarButtonsTranslationX =
                    toolbarButtonsContainerWidth * (1.f - mToolbarButtonVisibilityPercent);
            if (isRtl) toolbarButtonsTranslationX *= -1;

            mToolbarButtonsContainer.setTranslationX(toolbarButtonsTranslationX);
            mToolbarButtonsContainer.setAlpha(mToolbarButtonVisibilityPercent);
            mToolbarButtonsContainer.setVisibility(
                    mToolbarButtonVisibilityPercent > 0.f ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateMenuButtonClickableState() {
        mMenuButton.setClickable(
                !urlHasFocus() && (!mBottomSheet.isSheetOpen() || mBottomSheet.isShowingNewTab()));
    }
}
