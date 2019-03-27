// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.drawable;

import android.annotation.TargetApi;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.graphics.drawable.Animatable2Compat;

import org.chromium.base.ThreadUtils;

/**
 * Encapsulates the logic to loop animated drawables from both Android Framework (using Animatable2)
 * and Support Library (using Animatable2Compat). Should be instantiated by {@link #create}. The
 * animation should be started and stopped using {@link #start()} and {@link #stop()}.
 */
public interface AnimationLooper {
    /** Starts the animation of the associated drawable. */
    void start();

    /** Stops the animation of the associated drawable. */
    void stop();

    /**
     * Instantiates proper implementation of AnimationLooper for the drawable.
     * @param drawable The drawable to be animated. Created AnimationLooper instance will be
     *         associated with this drawable. The drawable should implement either
     *         {@link Animatable2} or {@link Animatable2Compat}.
     * @return The new AnimationLooper.
     */
    static AnimationLooper create(Drawable drawable) {
        // Animatable2 was added in API level 23 (Android M).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new Animatable2CompatImpl((Animatable2Compat) drawable);
        }
        if (drawable instanceof Animatable2Compat) {
            return new Animatable2CompatImpl((Animatable2Compat) drawable);
        }
        return new Animatable2Impl((Animatable2) drawable);
    }

    /** Used with drawables that implement {@link Animatable2Compat}. */
    class Animatable2CompatImpl implements AnimationLooper {
        private final Animatable2Compat mAnimatable;
        private final Animatable2Compat.AnimationCallback mAnimationCallback;
        private boolean mRunning;

        Animatable2CompatImpl(Animatable2Compat animatable) {
            mAnimatable = animatable;
            mAnimationCallback = new Animatable2Compat.AnimationCallback() {
                @Override
                public void onAnimationEnd(Drawable drawable) {
                    restartAnimation(animatable);
                }
            };
        }

        @Override
        public void start() {
            assert !mRunning : "The animation is already running!";
            mRunning = true;
            mAnimatable.registerAnimationCallback(mAnimationCallback);
            mAnimatable.start();
        }

        @Override
        public void stop() {
            assert mRunning : "The animation isn't running!";
            mRunning = false;
            mAnimatable.unregisterAnimationCallback(mAnimationCallback);
            mAnimatable.stop();
        }

        private void restartAnimation(Animatable2Compat animatable) {
            ThreadUtils.postOnUiThread(() -> {
                if (mRunning) animatable.start();
            });
        }
    }

    /** Used with drawables that implement {@link Animatable2}. */
    @TargetApi(Build.VERSION_CODES.M)
    class Animatable2Impl implements AnimationLooper {
        private final Animatable2 mAnimatable;
        private final Animatable2.AnimationCallback mAnimationCallback;
        private boolean mRunning;

        Animatable2Impl(Animatable2 animatable) {
            mAnimatable = animatable;
            mAnimationCallback = new Animatable2.AnimationCallback() {
                @Override
                public void onAnimationEnd(Drawable drawable) {
                    restartAnimation(animatable);
                }
            };
        }

        @Override
        public void start() {
            assert !mRunning : "The animation is already running!";
            mRunning = true;
            mAnimatable.registerAnimationCallback(mAnimationCallback);
            mAnimatable.start();
        }

        @Override
        public void stop() {
            assert mRunning : "The animation isn't running!";
            mRunning = false;
            mAnimatable.unregisterAnimationCallback(mAnimationCallback);
            mAnimatable.stop();
        }

        private void restartAnimation(Animatable2 animatable) {
            ThreadUtils.postOnUiThread(() -> {
                if (mRunning) animatable.start();
            });
        }
    }
}
