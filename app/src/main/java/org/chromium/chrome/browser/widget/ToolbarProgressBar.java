// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ProgressBar;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.UiUtils;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Progress bar for use in the Toolbar view. If no progress updates are received for 5 seconds, an
 * indeterminate animation will begin playing and the animation will move across the screen smoothly
 * instead of jumping.
 */
public class ToolbarProgressBar extends ClipDrawableProgressBar {

    /**
     * Interface for progress bar animation interpolation logics.
     */
    interface AnimationLogic {
        /**
         * Resets internal data. It must be called on every loading start.
         * @param startProgress The progress for the animation to start at. This is used when the
         *                      animation logic switches.
         */
        void reset(float startProgress);

        /**
         * Returns interpolated progress for animation.
         *
         * @param targetProgress Actual page loading progress.
         * @param frameTimeSec   Duration since the last call.
         * @param resolution     Resolution of the displayed progress bar. Mainly for rounding.
         */
        float updateProgress(float targetProgress, float frameTimeSec, int resolution);
    }

    /**
     * The amount of time in ms that the progress bar has to be stopped before the indeterminate
     * animation starts.
     */
    private static final long ANIMATION_START_THRESHOLD = 5000;

    private static final float THEMED_BACKGROUND_WHITE_FRACTION = 0.2f;
    private static final float ANIMATION_WHITE_FRACTION = 0.4f;

    private static final long PROGRESS_THROTTLE_UPDATE_INTERVAL = 30;
    private static final float PROGRESS_THROTTLE_MAX_UPDATE_AMOUNT = 0.03f;

    private static final long PROGRESS_FRAME_TIME_CAP_MS = 50;
    private long mAlphaAnimationDurationMs = 140;
    private long mHidingDelayMs = 100;

    /** Whether or not the progress bar has started processing updates. */
    private boolean mIsStarted;

    /** The target progress the smooth animation should move to (when animating smoothly). */
    private float mTargetProgress;

    /** The logic used to animate the progress bar during smooth animation. */
    private AnimationLogic mAnimationLogic;

    /** Whether or not the animation has been initialized. */
    private boolean mAnimationInitialized;

    /** The progress bar's top margin. */
    private int mMarginTop;

    /** The parent view of the progress bar. */
    private ViewGroup mProgressBarContainer;

    /** The number of times the progress bar has started (used for testing). */
    private int mProgressStartCount;

    /** The theme color currently being used. */
    private int mThemeColor;

    /** Whether or not to use the status bar color as the background of the toolbar. */
    private boolean mUseStatusBarColorAsBackground;

    /** Whether the smooth animation should be started if it is stopped. */
    private boolean mStartSmoothAnimation;

    /** The animator responsible for updating progress once it has been throttled. */
    private TimeAnimator mProgressThrottle;

    /** The listener for the progress throttle. */
    private ThrottleTimeListener mProgressThrottleListener;

    /**
     * The indeterminate animating view for the progress bar. This will be null for Android
     * versions < J.
     */
    private ToolbarProgressBarAnimatingView mAnimatingView;

    /** Whether or not the progress bar is attached to the window. */
    private boolean mIsAttachedToWindow;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            animateAlphaTo(0.0f);
            mStartSmoothAnimation = false;
            if (mAnimatingView != null) mAnimatingView.cancelAnimation();
        }
    };

    private final Runnable mStartSmoothIndeterminate = new Runnable() {
        @Override
        public void run() {
            if (!mIsStarted) return;
            mStartSmoothAnimation = true;
            mAnimationLogic.reset(getProgress());
            mProgressAnimator.start();

            if (mAnimatingView != null) {
                int width =
                        Math.abs(getDrawable().getBounds().right - getDrawable().getBounds().left);
                mAnimatingView.update(getProgress() * width);
                mAnimatingView.startAnimation();
            }
        }
    };

    private final TimeAnimator mProgressAnimator = new TimeAnimator();
    {
        mProgressAnimator.setTimeListener(new TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator animation, long totalTimeMs, long deltaTimeMs) {
                // Cap progress bar animation frame time so that it doesn't jump too much even when
                // the animation is janky.
                float progress = mAnimationLogic.updateProgress(mTargetProgress,
                        Math.min(deltaTimeMs, PROGRESS_FRAME_TIME_CAP_MS) * 0.001f, getWidth());
                progress = Math.max(progress, 0);
                ToolbarProgressBar.super.setProgress(progress);

                if (mAnimatingView != null) {
                    int width = Math.abs(
                            getDrawable().getBounds().right - getDrawable().getBounds().left);
                    mAnimatingView.update(progress * width);
                }

                if (getProgress() == mTargetProgress) {
                    if (!mIsStarted) postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
                    mProgressAnimator.end();
                    return;
                }
            }
        });
    }

    /** A {@link TimeListener} responsible for updating progress once throttling has started. */
    private final class ThrottleTimeListener
            extends AnimatorListenerAdapter implements TimeListener {
        /** Time interpolator for progress updates. */
        private final TimeInterpolator mAccelerateInterpolator = new AccelerateInterpolator();

        /** The target progress for the throttle animator. */
        private float mThrottledProgressTarget;

        /** The number of increments expected to reach the target progress since the last update. */
        private int mExpectedIncrements;

        /** Keeps track of the increment count since the last progress update. */
        private int mCurrentIncrementCount;

        /** The duration the progress update should take to complete. */
        private long mExpectedDuration;

        /** The amount of time until the next update. */
        private long mNextUpdateTime;

        @Override
        public void onAnimationStart(Animator animation) {
            float progressDiff = mThrottledProgressTarget - getProgress();
            mExpectedIncrements =
                    (int) Math.ceil(progressDiff / PROGRESS_THROTTLE_MAX_UPDATE_AMOUNT);
            mExpectedIncrements = Math.max(mExpectedIncrements, 1);
            mCurrentIncrementCount = 0;
            mNextUpdateTime = 0;
            mExpectedDuration = PROGRESS_THROTTLE_UPDATE_INTERVAL * mExpectedIncrements;
        }

        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            if (totalTime < mNextUpdateTime || mExpectedIncrements <= 0) return;

            mCurrentIncrementCount++;

            float completionFraction = mCurrentIncrementCount / (float) mExpectedIncrements;

            // This uses an accelerate interpolator to produce progressively longer times so the
            // progress bar appears to slow down.
            mNextUpdateTime = (long) (mAccelerateInterpolator.getInterpolation(completionFraction)
                    * mExpectedDuration);

            float updatedProgress = getProgress() + PROGRESS_THROTTLE_MAX_UPDATE_AMOUNT;
            setProgressInternal(MathUtils.clamp(updatedProgress, 0f, mThrottledProgressTarget));

            if (updatedProgress >= mThrottledProgressTarget) animation.end();

            if (updatedProgress >= 1f) finish(true);
        }
    }

    /**
     * Creates a toolbar progress bar.
     *
     * @param context The application environment.
     * @param height The height of the progress bar in px.
     * @param topMargin The top margin of the progress bar.
     * @param useStatusBarColorAsBackground Whether or not to use the status bar color as the
     *                                      background of the toolbar.
     */
    public ToolbarProgressBar(
            Context context, int height, int topMargin, boolean useStatusBarColorAsBackground) {
        super(context, height);
        setAlpha(0.0f);
        mMarginTop = topMargin;
        mUseStatusBarColorAsBackground = useStatusBarColorAsBackground;
        mAnimationLogic = new ProgressAnimationSmooth();

        // This tells accessibility services that progress bar changes are important enough to
        // announce to the user even when not focused.
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    /**
     * Set the top progress bar's top margin.
     * @param topMargin The top margin of the progress bar in px.
     */
    public void setTopMargin(int topMargin) {
        mMarginTop = topMargin;

        if (mIsAttachedToWindow) {
            assert getLayoutParams() != null;
            ((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin = mMarginTop;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsAttachedToWindow = true;

        ((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin = mMarginTop;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttachedToWindow = false;
    }

    /**
     * @param container The view containing the progress bar.
     */
    public void setProgressBarContainer(ViewGroup container) {
        mProgressBarContainer = container;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (mAnimatingView != null) mAnimatingView.setAlpha(alpha);
    }

    @Override
    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // If the size changed, the animation width needs to be manually updated.
        if (mAnimatingView != null) mAnimatingView.update(width * getProgress());
    }

    /**
     * Initializes animation based on command line configuration. This must be called when native
     * library is ready.
     */
    public void initializeAnimation() {
        if (mAnimationInitialized) return;

        mAnimationInitialized = true;

        // Only use the indeterminate animation if the Android version is > J.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            LayoutParams animationParams = new LayoutParams(getLayoutParams());
            animationParams.width = 1;
            animationParams.topMargin = mMarginTop;

            mAnimatingView = new ToolbarProgressBarAnimatingView(getContext(), animationParams);

            // The primary theme color may not have been set.
            if (mThemeColor != 0 || mUseStatusBarColorAsBackground) {
                setThemeColor(mThemeColor, false);
            } else {
                setForegroundColor(getForegroundColor());
            }
            UiUtils.insertAfter(mProgressBarContainer, mAnimatingView, this);
        }
    }

    /**
     * Start showing progress bar animation.
     */
    public void start() {
        mIsStarted = true;
        mProgressStartCount++;

        removeCallbacks(mStartSmoothIndeterminate);
        postDelayed(mStartSmoothIndeterminate, ANIMATION_START_THRESHOLD);
        mStartSmoothAnimation = false;

        super.setProgress(0.0f);
        mAnimationLogic.reset(0.0f);
        removeCallbacks(mHideRunnable);
        animateAlphaTo(1.0f);
    }

    /**
     * @return True if the progress bar is showing and started.
     */
    public boolean isStarted() {
        return mIsStarted;
    }

    /**
     * Start hiding progress bar animation.
     * @param delayed Whether a delayed fading out animation should be posted.
     */
    public void finish(boolean delayed) {
        if (mProgressThrottle != null && mProgressThrottle.isRunning()) return;

        mIsStarted = false;

        if (delayed) {
            updateVisibleProgress();
        } else {
            removeCallbacks(mHideRunnable);
            animate().cancel();
            if (mAnimatingView != null) {
                removeCallbacks(mStartSmoothIndeterminate);
                mAnimatingView.cancelAnimation();
            }
            mTargetProgress = 0;
            mStartSmoothAnimation = false;
            setAlpha(0.0f);
        }
    }

    /**
     * Set alpha show&hide animation duration. This is for faster testing.
     * @param alphaAnimationDurationMs Alpha animation duration in milliseconds.
     */
    @VisibleForTesting
    public void setAlphaAnimationDuration(long alphaAnimationDurationMs) {
        mAlphaAnimationDurationMs = alphaAnimationDurationMs;
    }

    /**
     * Set hiding delay duration. This is for faster testing.
     * @param hidngDelayMs Hiding delay duration in milliseconds.
     */
    @VisibleForTesting
    public void setHidingDelay(long hidngDelayMs) {
        mHidingDelayMs = hidngDelayMs;
    }

    /**
     * @return The number of times the progress bar has been triggered.
     */
    @VisibleForTesting
    public int getStartCountForTesting() {
        return mProgressStartCount;
    }

    /**
     * Reset the number of times the progress bar has been triggered.
     */
    @VisibleForTesting
    public void resetStartCountForTesting() {
        mProgressStartCount = 0;
    }

    private void animateAlphaTo(float targetAlpha) {
        float alphaDiff = targetAlpha - getAlpha();
        if (alphaDiff == 0.0f) return;

        long duration = (long) Math.abs(alphaDiff * mAlphaAnimationDurationMs);

        BakedBezierInterpolator interpolator = BakedBezierInterpolator.FADE_IN_CURVE;
        if (alphaDiff < 0) interpolator = BakedBezierInterpolator.FADE_OUT_CURVE;

        animate().alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(interpolator);

        if (mAnimatingView != null) {
            mAnimatingView.animate().alpha(targetAlpha)
                    .setDuration(duration)
                    .setInterpolator(interpolator);
        }
    }

    private void updateVisibleProgress() {
        if (mStartSmoothAnimation) {
            // The progress animator will stop if the animation reaches the target progress. If the
            // animation was running for the current page load, keep running it.
            if (!mProgressAnimator.isRunning()) mProgressAnimator.start();
        } else {
            super.setProgress(mTargetProgress);
            if (!mIsStarted) postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    // ClipDrawableProgressBar implementation.

    @Override
    public void setProgress(float progress) {
        // TODO(mdjones): Maybe subclass this to be ThrottledToolbarProgressBar.
        if (mProgressThrottle == null && ChromeFeatureList.isInitialized()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.PROGRESS_BAR_THROTTLE)) {
            mProgressThrottle = new TimeAnimator();
            mProgressThrottleListener = new ThrottleTimeListener();
            mProgressThrottle.addListener(mProgressThrottleListener);
            mProgressThrottle.setTimeListener(mProgressThrottleListener);
        }

        // Throttle progress if the increment was greater than 5%.
        if (mProgressThrottle != null
                && (progress - getProgress() > PROGRESS_THROTTLE_MAX_UPDATE_AMOUNT
                           || mProgressThrottle.isRunning())) {
            mProgressThrottleListener.mThrottledProgressTarget = progress;

            mProgressThrottle.cancel();
            mProgressThrottle.start();
        } else {
            setProgressInternal(progress);
        }
    }

    /**
     * Set the progress bar state based on the external updates coming in.
     * @param progress The current progress.
     */
    private void setProgressInternal(float progress) {
        if (!mIsStarted || mTargetProgress == progress) return;

        // If the progress bar was updated, reset the callback that triggers the
        // smooth-indeterminate animation.
        removeCallbacks(mStartSmoothIndeterminate);

        if (mAnimatingView != null) {
            if (progress == 1.0) {
                mAnimatingView.cancelAnimation();
            } else if (!mAnimatingView.isRunning()) {
                postDelayed(mStartSmoothIndeterminate, ANIMATION_START_THRESHOLD);
            }
        }

        mTargetProgress = progress;
        updateVisibleProgress();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mAnimatingView != null) mAnimatingView.setVisibility(visibility);
    }

    /**
     * Color the progress bar based on the toolbar theme color.
     * @param color The Android color the toolbar is using.
     */
    public void setThemeColor(int color, boolean isIncognito) {
        mThemeColor = color;
        boolean isDefaultTheme = ColorUtils.isUsingDefaultToolbarColor(
                getResources(), mUseStatusBarColorAsBackground, isIncognito, mThemeColor);

        // All colors use a single path if using the status bar color as the background.
        if (mUseStatusBarColorAsBackground) {
            if (isDefaultTheme) color = Color.BLACK;
            setForegroundColor(
                    ApiCompatibilityUtils.getColor(getResources(), R.color.white_alpha_70));
            setBackgroundColor(ColorUtils.getDarkenedColorForStatusBar(color));
            return;
        }

        // The default toolbar has specific colors to use.
        if ((isDefaultTheme || !ColorUtils.isValidThemeColor(color)) && !isIncognito) {
            setForegroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.progress_bar_foreground));
            setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(),
                    R.color.progress_bar_background));
            return;
        }

        setForegroundColor(ColorUtils.getThemedAssetColor(color, isIncognito));

        if (mAnimatingView != null
                && (ColorUtils.shouldUseLightForegroundOnBackground(color) || isIncognito)) {
            mAnimatingView.setColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                    ANIMATION_WHITE_FRACTION));
        }

        setBackgroundColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                THEMED_BACKGROUND_WHITE_FRACTION));
    }

    @Override
    public void setForegroundColor(int color) {
        super.setForegroundColor(color);
        if (mAnimatingView != null) {
            mAnimatingView.setColor(ColorUtils.getColorWithOverlay(color, Color.WHITE,
                    ANIMATION_WHITE_FRACTION));
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ProgressBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setCurrentItemIndex((int) (mTargetProgress * 100));
        event.setItemCount(100);
    }
}
