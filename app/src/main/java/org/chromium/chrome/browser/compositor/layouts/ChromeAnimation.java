// Copyright 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.animation.Interpolator;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.compositor.animation.CompositorAnimator;
import org.chromium.chrome.browser.util.MathUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class to handle simple animation sets.  This can animate any object passed in by overriding
 * the ChromeAnimation.Animation object.
 *
 * @param <T> The type of Object being animated by this ChromeAnimation set.
 */
@SuppressWarnings("unchecked")
public class ChromeAnimation<T> {
    /**
     * The amount we jump into the animation for the first frame.  We do this as we assume
     * the object be animated is already resting in the initial value specified.  To avoid wasting
     * a frame of animation on something that will look exactly the same, we jump into the
     * animation by this frame offset (calculated by the desired time required to draw one frame
     * at 60 FPS).
     */
    private static final int FIRST_FRAME_OFFSET_MS = 1000 / 60;

    /**
     * Multiplier for animation durations for debugging. Can be set in Developer Options and cached
     * here.
     */
    private static Float sAnimationMultiplier;

    private final AtomicBoolean mFinishCalled = new AtomicBoolean();
    private final ArrayList<Animation<T>> mAnimations = new ArrayList<Animation<T>>();
    private long mCurrentTime;

    private static final Object sLock = new Object();

    /**
     * Adds a ChromeAnimation.Animation instance to this ChromeAnimation set.  This Animation will
     * be managed by this ChromeAnimation from now on.
     *
     * @param a The ChromeAnimation.Animation object to be controlled and updated by this
     *         ChromeAnimation.
     */
    public void add(ChromeAnimation.Animation<T> a) {
        mAnimations.add(a);
    }

    /**
     * Starts all of the Animation instances in this ChromeAnimation.  This sets up the appropriate
     * state so that calls to update() can properly track how much time has passed and set the
     * initial values for each Animation.
     */
    public void start() {
        mFinishCalled.set(false);
        mCurrentTime = 0;
        for (int i = 0; i < mAnimations.size(); ++i) {
            Animation<T> a = mAnimations.get(i);
            a.start();
        }
    }

    /**
     * Aborts all animations of a specific type. This does not call finish on the animation.
     * So the animation will not reach its final value.
     * @param object   object to find animations to be aborted. If null, matches all the animations.
     * @param property property to find animations to be aborted.
     */
    public void cancel(T object, int property) {
        for (int i = mAnimations.size() - 1; i >= 0; i--) {
            Animation<T> animation = mAnimations.get(i);
            if ((object == null || animation.getAnimatedObject() == object)
                    && animation.checkProperty(property)) {
                mAnimations.remove(i);
            }
        }
    }

    /**
     * Forces each Animation to finish itself, setting the properties to the final value of the
     * Animation.
     */
    public void updateAndFinish() {
        for (int i = 0; i < mAnimations.size(); ++i) {
            mAnimations.get(i).updateAndFinish();
        }
        finishInternal();
    }

    /**
     * Updates each Animation based on how much time has passed.  Each animation gets passed the
     * delta since the last call to update() and can appropriately interpolate their values. The
     * time reference is implicitly the actual uptime of the app.
     *
     * @return Whether or not this ChromeAnimation is finished animating.
     */
    public boolean update() {
        return update(SystemClock.uptimeMillis());
    }

    /**
     * Updates each Animation based on how much time has passed.  Each animation gets passed the
     * delta since the last call to update() and can appropriately interpolate their values.
     *
     * @param time The current time of the app in ms.
     * @return     Whether or not this ChromeAnimation is finished animating.
     */
    public boolean update(long time) {
        if (mFinishCalled.get()) {
            return true;
        }
        if (mCurrentTime == 0) mCurrentTime = time - FIRST_FRAME_OFFSET_MS;
        long dtMs = time - mCurrentTime;
        mCurrentTime += dtMs;
        boolean finished = true;
        for (int i = 0; i < mAnimations.size(); ++i) {
            mAnimations.get(i).update(dtMs);
            finished &= mAnimations.get(i).finished();
        }

        if (finished) updateAndFinish();
        return false;
    }

    /**
     * @return Whether or not this ChromeAnimation is finished animating.
     */
    public boolean finished() {
        if (mFinishCalled.get()) return true;

        for (int i = 0; i < mAnimations.size(); ++i) {
            if (!mAnimations.get(i).finished()) return false;
        }

        return true;
    }

    private void finishInternal() {
        if (mFinishCalled.get()) return;

        finish();
        mFinishCalled.set(true);
    }

    /**
     * Callback to handle any necessary cleanups upon finishing the animation.
     *
     * <p>
     * Called as part of {@link #update()} if the end of the animation is reached or
     * {@link #updateAndFinish()}.
     */
    protected void finish() {
    }

    /**
     * A particular animation instance, meant to be managed by a ChromeAnimation object.
     *
     * @param <T> The type of Object being animated by this Animation instance.  This object should
     *         be accessed inside setProperty() where it can be manipulated by the new value.
     */
    public abstract static class Animation<T> {
        protected T mAnimatedObject;
        private float mStart;
        private float mEnd;

        private long mCurrentTime;
        private long mDuration;
        private long mStartDelay;
        private boolean mDelayStartValue;
        private boolean mHasFinished;
        private Interpolator mInterpolator = CompositorAnimator.DECELERATE_INTERPOLATOR;

        /**
         * Creates a new Animation object with a custom Interpolator.
         *
         * @param t The object to be Animated.
         * @param start The starting value of the animation.
         * @param end The ending value of the animation.
         * @param duration The duration of the animation.  This does not include the startTime.
         *                 The duration must be strictly positive.
         * @param startTime The time at which this animation should start.
         * @param interpolator The Interpolator instance to use for animating the property from
         *         start to finish.
         */
        public Animation(T t, float start, float end, long duration,
                long startTime, Interpolator interpolator) {
            this(t, start, end, duration, startTime);
            mInterpolator = interpolator;
        }

        /**
         * Creates a new Animation object.
         *
         * @param t The object to be Animated.
         * @param start The starting value of the animation.
         * @param end The ending value of the animation.
         * @param duration The duration of the animation.  This does not include the startTime.
         * @param startTime The time at which this animation should start.
         */
        public Animation(T t, float start, float end, long duration,
                long startTime) {
            mAnimatedObject = t;
            mStart = start;
            mEnd = end;
            float animationMultiplier = getAnimationMultiplier();
            mDuration = (long) (duration * animationMultiplier);
            mStartDelay = (long) (startTime * animationMultiplier);
            mCurrentTime = 0;
        }

        public static void setAnimationMultiplierForTesting(float animationMultiplier) {
            synchronized (sLock) {
                sAnimationMultiplier = animationMultiplier;
            }
        }

        public static void unsetAnimationMultiplierForTesting() {
            synchronized (sLock) {
                sAnimationMultiplier = null;
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public static float getAnimationMultiplier() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return 1f;

            synchronized (sLock) {
                if (sAnimationMultiplier == null) {
                    sAnimationMultiplier = Settings.Global.getFloat(
                            ContextUtils.getApplicationContext().getContentResolver(),
                            Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
                }
                return sAnimationMultiplier;
            }
        }

        /**
         * Returns the object being animated.
         */
        protected T getAnimatedObject() {
            return mAnimatedObject;
        }

        /**
         * @param delayStartValue Whether to delay setting the animation's initial value until
         *        after the specified start delay (Default = false).
         */
        public void setStartValueAfterStartDelay(boolean delayStartValue) {
            mDelayStartValue = delayStartValue;
        }

        /**
         * Sets the internal timer to be at the end of the animation and calls setProperty with the
         * final value.
         */
        public void updateAndFinish() {
            mCurrentTime = mDuration + mStartDelay;
            setProperty(mEnd);
        }

        /**
         * Updates the internal timer based on the time delta passed in.  The proper property value
         * is interpolated and passed to setProperty.
         *
         * @param dtMs The amount of time, in milliseconds, that this animation should be
         *         progressed.
         */
        public void update(long dtMs) {
            mCurrentTime += dtMs;

            // Bound our time here so that our scale never goes above 1.0.
            mCurrentTime = Math.min(mCurrentTime, mDuration + mStartDelay);

            if (mDelayStartValue && mCurrentTime < mStartDelay) return;

            // Figure out the relative fraction of time we need to animate.
            long relativeTime = MathUtils.clamp(mCurrentTime - mStartDelay, 0, mDuration);

            if (mDuration > 0) {
                setProperty(MathUtils.interpolate(mStart, mEnd,
                        mInterpolator.getInterpolation((float) relativeTime / (float) mDuration)));
            } else {
                setProperty(mEnd);
            }
        }

        /**
         * Starts the animation and calls setProperty() with the initial value.
         */
        public void start() {
            mHasFinished = false;
            mCurrentTime = 0;
            update(0);
        }

        /**
         * @return Whether or not this current animation is finished.
         */
        public boolean finished() {
            if (!mHasFinished && mCurrentTime >= mDuration + mStartDelay) {
                mHasFinished = true;
                onPropertyAnimationFinished();
            }

            return mHasFinished;
        }

        /**
         * Checks if the given property is being animated.
         */
        public boolean checkProperty(int prop) {
            return true;
        }

        /**
         * The abstract method that gets called with the new interpolated value based on the
         * current time.  This gives inheriting classes the chance to set a property on the
         * animating object.
         *
         * @param p The current animated value based on the current time and the Interpolator.
         */
        public abstract void setProperty(float p);

        /**
         * The abstract method that gets called when the property animation finished.
         */
        public abstract void onPropertyAnimationFinished();
    }

    /**
     * Provides a interface for updating animatible properties.
     */
    public static interface Animatable {
        /**
         * Updates an animatable property.
         *
         * @param prop The property to update
         * @param val The new value
         */
        public void setProperty(int prop, float val);

        /**
         * Notifies that the animation for a certain property has finished.
         *
         * @param prop The property that has finished animating.
         */
        public void onPropertyAnimationFinished(int prop);
    }

    /**
     * An animation that can be applied to {@link ChromeAnimation.Animatable} objects.
     */
    public static class AnimatableAnimation extends Animation<Animatable> {
        private final int mProperty;

        /**
         * @param animatable The object being animated
         * @param property The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation.  This does not include the startTime.
         * @param startTime The time at which this animation should start.
         * @param interpolator The interpolator to use for the animation
         */
        public AnimatableAnimation(Animatable animatable, int property, float start, float end,
                long duration, long startTime, Interpolator interpolator) {
            super(animatable, start, end, duration, startTime, interpolator);
            mProperty = property;
        }

        @Override
        public void setProperty(float p) {
            mAnimatedObject.setProperty(mProperty, p);
        }

        @Override
        public void onPropertyAnimationFinished() {
            mAnimatedObject.onPropertyAnimationFinished(mProperty);
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         */
        public static void addAnimation(ChromeAnimation<Animatable> set, Animatable object,
                int prop, float start, float end, long duration, long startTime) {
            addAnimation(set, object, prop, start, end, duration, startTime, false);
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         */
        public static void addAnimation(ChromeAnimation<Animatable> set, Animatable object,
                int prop, float start, float end, long duration, long startTime,
                boolean setStartValueAfterStartDelay) {
            addAnimation(set, object, prop, start, end, duration, startTime,
                    setStartValueAfterStartDelay, CompositorAnimator.DECELERATE_INTERPOLATOR);
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         * @param interpolator The interpolator to use for the animation
         */
        public static void addAnimation(ChromeAnimation<Animatable> set, Animatable object,
                int prop, float start, float end, long duration, long startTime,
                boolean setStartValueAfterStartDelay, Interpolator interpolator) {
            if (duration <= 0) return;
            Animation<Animatable> animation = createAnimation(object, prop, start, end, duration,
                    startTime, setStartValueAfterStartDelay, interpolator);
            set.add(animation);
        }

        /**
         * Helper method to create an {@link ChromeAnimation.AnimatableAnimation}
         *
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         * @param interpolator The interpolator to use for the animation
         */
        public static Animation<Animatable> createAnimation(Animatable object, int prop,
                float start, float end, long duration, long startTime,
                boolean setStartValueAfterStartDelay, Interpolator interpolator) {
            Animation<Animatable> animation = new AnimatableAnimation(
                    object, prop, start, end, duration, startTime, interpolator);
            animation.setStartValueAfterStartDelay(setStartValueAfterStartDelay);
            return animation;
        }

        /**
         * Checks if the given property is being animated.
         */
        @Override
        public boolean checkProperty(int prop) {
            return mProperty == prop;
        }
    }
}
