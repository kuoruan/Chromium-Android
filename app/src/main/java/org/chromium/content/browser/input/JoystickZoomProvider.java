// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.view.InputDevice;
import android.view.MotionEvent;

import org.chromium.content.browser.ContentViewCore;

/**
 * This class controls page zoomin/out using trigger joystick events.
 * Page Zoomin is proportional to RTRIGGER axis movement.
 * Page Zoomout is proportional to LTRIGGER axis movement.
 */
public class JoystickZoomProvider {
    private static final String TAG = "JoystickZoomProvider";

    private static final float JOYSTICK_NOISE_THRESHOLD = 0.2f;

    private static final float ZOOM_SPEED = 1.65f;

    private long mLastAnimateTimeMillis;

    private float mZoomInVelocity;

    private float mZoomOutVelocity;

    protected final ContentViewCore mContentViewCore;

    protected float mDeviceScaleFactor;

    private int mZoomXcoord;

    private int mZoomYcoord;

    protected Runnable mZoomRunnable;

    private AnimationIntervalProvider mSystemAnimationIntervalProvider;

    /**
     * Constructs a new JoystickZoomProvider.
     *
     * @param cvc The ContentViewCore used to create this.
     */
    public JoystickZoomProvider(
            ContentViewCore cvc, AnimationIntervalProvider animationTimeProvider) {
        mContentViewCore = cvc;
        mDeviceScaleFactor = mContentViewCore.getRenderCoordinates().getDeviceScaleFactor();
        mZoomXcoord = mContentViewCore.getViewportWidthPix() / 2;
        mZoomYcoord = mContentViewCore.getViewportHeightPix() / 2;
        mSystemAnimationIntervalProvider = animationTimeProvider;
    }

    /**
     * This function processes motion event and computes new
     * page scale factor which is proportional to *_TRIGGER axes movement.
     * It also starts runnable to update current page scale to new page scale.
     *
     * @param event Motion event to be processed for zooming.
     * @return Whether zooming using *_TRIGGER axes is performed or not.
     */
    public boolean onMotion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0) return false;

        computeNewZoomVelocity(event);
        if (mZoomInVelocity == 0 && mZoomOutVelocity == 0) {
            stop();
            return false;
        }
        if (mZoomRunnable == null) {
            mZoomRunnable = new Runnable() {
                @Override
                public void run() {
                    animateZoom();
                }
            };
        }
        if (mLastAnimateTimeMillis == 0) {
            mLastAnimateTimeMillis =
                    mSystemAnimationIntervalProvider.getLastAnimationFrameInterval();
            mContentViewCore.getContainerView().postOnAnimation(mZoomRunnable);
            mContentViewCore.pinchBegin(mZoomXcoord, mZoomYcoord);
        }
        return true;
    }

    protected void stop() {
        if (mLastAnimateTimeMillis != 0) {
            mContentViewCore.pinchEnd();
            mLastAnimateTimeMillis = 0;
        }
    }

    private void computeNewZoomVelocity(MotionEvent event) {
        mZoomInVelocity = getFilteredAxisValue(event, MotionEvent.AXIS_RTRIGGER);
        mZoomOutVelocity = getFilteredAxisValue(event, MotionEvent.AXIS_LTRIGGER);
    }

    protected void animateZoom() {
        if (!mContentViewCore.getContainerView().hasFocus()) {
            stop();
            return;
        }
        if (mLastAnimateTimeMillis == 0) return;

        final long timeMillis = mSystemAnimationIntervalProvider.getLastAnimationFrameInterval();
        final long dt = timeMillis - mLastAnimateTimeMillis;
        final float zoomFactor = (float) Math.pow(
                ZOOM_SPEED, mDeviceScaleFactor * (mZoomInVelocity - mZoomOutVelocity) * dt / 1000f);
        mContentViewCore.pinchBy(mZoomXcoord, mZoomYcoord, zoomFactor);
        mLastAnimateTimeMillis = timeMillis;
        mContentViewCore.getContainerView().postOnAnimation(mZoomRunnable);
    }

    /**
     * This function removes noise from motion events.
     * Joystick is very senstitive, it produces value (noise) along X/Y directions
     * even if gamepad button is pressed which is not acceptable.
     * Returns non-zero value only if event value is above noise threshold.
     *
     * @param event Motion event which needs noise processing.
     * @param axis Joystick axis (whether X_AXIS of Y_AXIS)
     * @return Processed joystick value.
     */
    private float getFilteredAxisValue(MotionEvent event, int axis) {
        float axisValWithNoise = event.getAxisValue(axis);
        return (axisValWithNoise > JOYSTICK_NOISE_THRESHOLD) ? axisValWithNoise : 0f;
    }
}
