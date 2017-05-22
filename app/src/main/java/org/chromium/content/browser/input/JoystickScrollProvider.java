// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.display.DisplayAndroid.DisplayAndroidObserver;

/**
 * This class implements auto scrolling and panning for gamepad left joystick motion event.
 */
@JNINamespace("content")
public class JoystickScrollProvider {
    private static final String TAG = "JoystickScroll";

    private static final float JOYSTICK_SCROLL_FACTOR_MULTIPLIER = 20f;
    // Joystick produces "noise", 0.20f has proven a safe value to
    // remove noise and still allow reasonable input range.
    private static final float JOYSTICK_SCROLL_DEADZONE = 0.2f;
    private static final float SCROLL_FACTOR_FALLBACK = 128f;

    private class JoystickScrollDisplayObserver implements DisplayAndroidObserver {
        @Override
        public void onRotationChanged(int rotation) {}

        @Override
        public void onDIPScaleChanged(float dipScale) {
            mDipScale = dipScale;
            updateScrollFactor();
        }
    }

    private WindowAndroid mWindowAndroid;
    private View mContainerView;
    private long mNativeJoystickScrollProvider;
    private JoystickScrollDisplayObserver mDisplayObserver;

    private float mScrollVelocityX;
    private float mScrollVelocityY;
    private float mScrollFactor;
    private float mDipScale = 1.0f;

    private long mLastAnimateTimeMillis;

    private boolean mEnabled;

    private Runnable mScrollRunnable;

    /**
     * Constructs a new JoystickScrollProvider.
     */
    public JoystickScrollProvider(
            WebContents webContents, View containerView, WindowAndroid windowAndroid) {
        mNativeJoystickScrollProvider = nativeInit(webContents);
        mContainerView = containerView;
        mWindowAndroid = windowAndroid;
        mEnabled = true;
        mDisplayObserver = new JoystickScrollDisplayObserver();
    }

    @CalledByNative
    private void onNativeObjectDestroyed(long nativePointer) {
        assert nativePointer == mNativeJoystickScrollProvider;
        mNativeJoystickScrollProvider = 0;
    }

    /**
     * This function enables or disables scrolling through joystick.
     * @param enabled Decides whether joystick scrolling should be
     *                enabled or not.
     */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) stop();
    }

    public void onViewAttachedToWindow() {
        addDisplayAndroidObserver();
    }

    public void onViewDetachedFromWindow() {
        removeDisplayAndroidObserver();
    }

    public void updateWindowAndroid(WindowAndroid windowAndroid) {
        removeDisplayAndroidObserver();
        mWindowAndroid = windowAndroid;
        addDisplayAndroidObserver();
    }

    private void addDisplayAndroidObserver() {
        if (mWindowAndroid == null) return;

        DisplayAndroid display = mWindowAndroid.getDisplay();
        display.addObserver(mDisplayObserver);
        mDisplayObserver.onDIPScaleChanged(display.getDipScale());
    }

    private void removeDisplayAndroidObserver() {
        if (mWindowAndroid == null) return;
        mWindowAndroid.getDisplay().removeObserver(mDisplayObserver);
    }

    private void updateScrollFactor() {
        Context context = mWindowAndroid == null ? null : mWindowAndroid.getContext().get();
        TypedValue outValue = new TypedValue();

        if (context != null && context.getTheme().resolveAttribute(
                android.R.attr.listPreferredItemHeight, outValue, true)) {
            mScrollFactor = outValue.getDimension(context.getResources().getDisplayMetrics());
        } else {
            if (context != null) {
                Log.d(TAG, "Theme attribute listPreferredItemHeight not defined"
                                + " switching to fallback scroll factor");
            }
            mScrollFactor = SCROLL_FACTOR_FALLBACK * mDipScale;
        }
    }

    /**
     * This function processes motion event and computes new
     * scroll offest in pixels which is propertional to left joystick
     * axes movement.
     * It also starts runnable to scroll content view core equal to
     * scroll offset pixels.
     *
     * @param event Motion event to be processed for scrolling.
     * @return Whether scrolling using left joystick is performed or not.
     */
    public boolean onMotion(MotionEvent event) {
        if (!mEnabled) return false;
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0) return false;
        Log.d(TAG, "Joystick left stick axis: " + event.getAxisValue(MotionEvent.AXIS_X) + ","
                + event.getAxisValue(MotionEvent.AXIS_Y));

        assert mScrollFactor != 0;

        mScrollVelocityX = getFilteredAxisValue(event, MotionEvent.AXIS_X) * mScrollFactor
                * JOYSTICK_SCROLL_FACTOR_MULTIPLIER;
        mScrollVelocityY = getFilteredAxisValue(event, MotionEvent.AXIS_Y) * mScrollFactor
                * JOYSTICK_SCROLL_FACTOR_MULTIPLIER;

        if (mScrollVelocityX == 0 && mScrollVelocityY == 0) {
            stop();
            return false;
        }
        if (mScrollRunnable == null) {
            mScrollRunnable = new Runnable() {
                @Override
                public void run() {
                    animateScroll();
                }
            };
        }
        if (mLastAnimateTimeMillis == 0) {
            mContainerView.postOnAnimation(mScrollRunnable);
            mLastAnimateTimeMillis = AnimationUtils.currentAnimationTimeMillis();
        }
        return true;
    }

    private void animateScroll() {
        if (mLastAnimateTimeMillis == 0) {
            return;
        }
        final long timeMillis = AnimationUtils.currentAnimationTimeMillis();
        final long dt = timeMillis - mLastAnimateTimeMillis;
        final float dx = (mScrollVelocityX * dt / 1000.f);
        final float dy = (mScrollVelocityY * dt / 1000.f);

        if (mNativeJoystickScrollProvider != 0) {
            nativeScrollBy(
                    mNativeJoystickScrollProvider, timeMillis, dx / mDipScale, dy / mDipScale);
        }

        mLastAnimateTimeMillis = timeMillis;
        mContainerView.postOnAnimation(mScrollRunnable);
    }

    private void stop() {
        mLastAnimateTimeMillis = 0;
    }

    /**
     * Removes noise from joystick motion events.
     */
    private float getFilteredAxisValue(MotionEvent event, int axis) {
        float axisValWithNoise = event.getAxisValue(axis);
        if (axisValWithNoise > JOYSTICK_SCROLL_DEADZONE
                || axisValWithNoise < -JOYSTICK_SCROLL_DEADZONE) {
            return axisValWithNoise;
        }
        return 0f;
    }

    private native long nativeInit(WebContents webContents);
    private native void nativeScrollBy(
            long nativeJoystickScrollProvider, long timeMs, float dxDip, float dyDip);
}
