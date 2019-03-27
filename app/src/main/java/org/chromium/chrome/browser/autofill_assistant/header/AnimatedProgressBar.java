// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.header;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.View;

import org.chromium.chrome.browser.compositor.animation.CompositorAnimator;
import org.chromium.chrome.browser.widget.MaterialProgressBar;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Wrapper around {@link MaterialProgressBar} to animate progress changes and enable/disable
 * pulsing.
 */
class AnimatedProgressBar {
    // The number of ms the progress bar would take to go from 0 to 100%.
    private static final int PROGRESS_BAR_SPEED_MS = 3_000;
    private static final int PROGRESS_BAR_PULSING_DURATION_MS = 1_000;

    private final MaterialProgressBar mProgressBar;
    private final int mNormalColor;
    private final int mPulsedColor;

    private boolean mIsRunningProgressAnimation;
    private int mLastProgress;
    private Queue<ValueAnimator> mPendingIncreaseAnimations = new ArrayDeque<>();
    private ValueAnimator mPulseAnimation;

    AnimatedProgressBar(MaterialProgressBar progressBar, int normalColor, int pulsedColor) {
        mProgressBar = progressBar;
        mNormalColor = normalColor;
        mPulsedColor = pulsedColor;
    }

    public void show() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    /**
     * Set the progress to {@code progress} if it is higher than the current progress, or do nothing
     * if it is not (hence it is OK to call this method with the same value multiple times).
     */
    public void maybeIncreaseProgress(int progress) {
        if (progress > mLastProgress) {
            ValueAnimator progressAnimation = ValueAnimator.ofInt(mLastProgress, progress);
            progressAnimation.setDuration(PROGRESS_BAR_SPEED_MS * (progress - mLastProgress) / 100);
            progressAnimation.setInterpolator(CompositorAnimator.ACCELERATE_INTERPOLATOR);
            progressAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mPendingIncreaseAnimations.isEmpty()) {
                        mIsRunningProgressAnimation = false;
                    } else {
                        mIsRunningProgressAnimation = true;
                        mPendingIncreaseAnimations.poll().start();
                    }
                }
            });
            progressAnimation.addUpdateListener(
                    animation -> mProgressBar.setProgress((int) animation.getAnimatedValue()));
            mLastProgress = progress;

            if (mIsRunningProgressAnimation) {
                mPendingIncreaseAnimations.offer(progressAnimation);
            } else {
                mIsRunningProgressAnimation = true;
                progressAnimation.start();
            }
        }
    }

    public void enablePulsing() {
        if (mPulseAnimation == null) {
            mPulseAnimation = ValueAnimator.ofInt(mNormalColor, mPulsedColor);
            mPulseAnimation.setDuration(PROGRESS_BAR_PULSING_DURATION_MS);
            mPulseAnimation.setEvaluator(new ArgbEvaluator());
            mPulseAnimation.setRepeatCount(ValueAnimator.INFINITE);
            mPulseAnimation.setRepeatMode(ValueAnimator.REVERSE);
            mPulseAnimation.setInterpolator(CompositorAnimator.ACCELERATE_INTERPOLATOR);
            mPulseAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mProgressBar.setProgressColor(mNormalColor);
                }
            });
            mPulseAnimation.addUpdateListener(
                    animation -> mProgressBar.setProgressColor((int) animation.getAnimatedValue()));
            mPulseAnimation.start();
        }
    }

    public void disablePulsing() {
        if (mPulseAnimation != null) {
            mPulseAnimation.cancel();
            mPulseAnimation = null;
        }
    }
}
