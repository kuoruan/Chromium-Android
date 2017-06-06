// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;

import org.chromium.base.VisibleForTesting;

/**
 * This class controls page zoomin/out using trigger joystick events.
 * Page Zoomin is proportional to RTRIGGER axis movement.
 * Page Zoomout is proportional to LTRIGGER axis movement.
 */
public class JoystickZoomProvider {
    private static final String TAG = "JoystickZoomProvider";

    private static final float JOYSTICK_NOISE_THRESHOLD = 0.2f;

    private static final float ZOOM_SPEED = 1.65f;

    /**
     * Interface that provides the implementation of pinch zoom action.
     */
    public interface PinchZoomHandler {
        /**
         * Send start of pinch zoom gesture.
         *
         * @param xPix X-coordinate of location from which pinch zoom would start.
         * @param yPix Y-coordinate of location from which pinch zoom would start.
         * @return whether the pinch zoom start gesture was sent.
         */
        boolean pinchBegin(int xPix, int yPix);

        /**
         * Send pinch zoom gesture.
         *
         * @param xPix X-coordinate of pinch zoom location.
         * @param yPix Y-coordinate of pinch zoom location.
         * @param delta the factor by which the current page scale should be multiplied by.
         * @return whether the pinchby gesture was sent.
         */
        boolean pinchBy(int xPix, int yPix, float delta);

        /**
         * Stop pinch zoom gesture.
         *
         * @return whether the pinch stop gesture was sent.
         */
        boolean pinchEnd();
    }
    ;

    /**
     * Interface that provides last animation interval. Overriden for testing.
     */
    public interface AnimationIntervalProvider {
        /**
         * Returns last animation interval.
         */
        public long getLastAnimationFrameInterval();
    }
    ;

    protected final float mDeviceScaleFactor;
    protected View mContainerView;
    protected Runnable mZoomRunnable;

    private final int mZoomXcoord;
    private final int mZoomYcoord;
    private final PinchZoomHandler mPinchZoomHandler;

    private AnimationIntervalProvider mSystemAnimationIntervalProvider;
    private long mLastAnimateTimeMillis;
    private float mZoomInVelocity;
    private float mZoomOutVelocity;

    /**
     * Constructs a new JoystickZoomProvider.
     *
     * @param containerView The view to which the zoom action will be posted.
     * @param scaleFactor Device scale factor.
     * @param xcoord X position of the zoom point.
     * @param ycoord Y position of the zoom point.
     * @param pinchZoomHandler {@link PinchZoomHandler} implementation.
     */
    public JoystickZoomProvider(View containerView, float scaleFactor, int xcoord, int ycoord,
            PinchZoomHandler pinchZoomHandler) {
        mContainerView = containerView;
        mDeviceScaleFactor = scaleFactor;
        mZoomXcoord = xcoord;
        mZoomYcoord = ycoord;
        mPinchZoomHandler = pinchZoomHandler;
        mSystemAnimationIntervalProvider = new AnimationIntervalProvider() {
            @Override
            public long getLastAnimationFrameInterval() {
                return AnimationUtils.currentAnimationTimeMillis();
            }
        };
    }

    @VisibleForTesting
    public void setAnimationIntervalProviderForTesting(AnimationIntervalProvider intervalProvider) {
        mSystemAnimationIntervalProvider = intervalProvider;
    }

    public void setContainerView(View containerView) {
        mContainerView = containerView;
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
            mContainerView.postOnAnimation(mZoomRunnable);
            mPinchZoomHandler.pinchBegin(mZoomXcoord, mZoomYcoord);
        }
        return true;
    }

    protected void stop() {
        if (mLastAnimateTimeMillis != 0) {
            mPinchZoomHandler.pinchEnd();
            mLastAnimateTimeMillis = 0;
        }
    }

    private void computeNewZoomVelocity(MotionEvent event) {
        mZoomInVelocity = getFilteredAxisValue(event, MotionEvent.AXIS_RTRIGGER);
        mZoomOutVelocity = getFilteredAxisValue(event, MotionEvent.AXIS_LTRIGGER);
    }

    protected void animateZoom() {
        if (!mContainerView.hasFocus()) {
            stop();
            return;
        }
        if (mLastAnimateTimeMillis == 0) return;

        final long timeMillis = mSystemAnimationIntervalProvider.getLastAnimationFrameInterval();
        final long dt = timeMillis - mLastAnimateTimeMillis;
        final float zoomFactor = (float) Math.pow(
                ZOOM_SPEED, mDeviceScaleFactor * (mZoomInVelocity - mZoomOutVelocity) * dt / 1000f);
        mPinchZoomHandler.pinchBy(mZoomXcoord, mZoomYcoord, zoomFactor);
        mLastAnimateTimeMillis = timeMillis;
        mContainerView.postOnAnimation(mZoomRunnable);
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
    private static float getFilteredAxisValue(MotionEvent event, int axis) {
        float axisValWithNoise = event.getAxisValue(axis);
        return (axisValWithNoise > JOYSTICK_NOISE_THRESHOLD) ? axisValWithNoise : 0f;
    }
}
