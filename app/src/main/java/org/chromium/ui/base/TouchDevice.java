// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.InputDevice;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

/**
 * Simple proxy for querying input device properties from C++.
 */
@JNINamespace("ui")
public class TouchDevice {

    /**
     * Static methods only so make constructor private.
     */
    private TouchDevice() { }

    /**
     * @return Maximum supported touch points.
     */
    @CalledByNative
    private static int maxTouchPoints(Context context) {
        // Android only tells us if the device belongs to a "Touchscreen Class" which only
        // guarantees a minimum number of touch points. Be conservative and return the minimum,
        // checking membership from the highest class down.

        if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)) {
            return 5;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
            return 2;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) {
            return 2;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN)) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * @return the pointer-types supported by the device, as the union (bitwise OR) of PointerType
     *         bits in result[0]
     *         the hover-types supported by the device, as the union (bitwise OR) of HoverType bits
     *         in result[1].
     */
    @CalledByNative
    private static int[] availablePointerAndHoverTypes(Context context) {
        int[] result = new int[2];
        result[0] = result[1] = 0;

        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice inputDevice = InputDevice.getDevice(deviceId);
            if (inputDevice == null) continue;

            int sources = inputDevice.getSources();

            if (hasSource(sources, InputDevice.SOURCE_MOUSE)
                    || hasSource(sources, InputDevice.SOURCE_STYLUS)
                    || hasSource(sources, InputDevice.SOURCE_TOUCHPAD)
                    || hasSource(sources, InputDevice.SOURCE_TRACKBALL)) {
                result[0] |= PointerType.FINE;
            } else if (hasSource(sources, InputDevice.SOURCE_TOUCHSCREEN)) {
                result[0] |= PointerType.COARSE;
            }

            if (hasSource(sources, InputDevice.SOURCE_MOUSE)
                    || hasSource(sources, InputDevice.SOURCE_TOUCHPAD)
                    || hasSource(sources, InputDevice.SOURCE_TRACKBALL)) {
                result[1] |= HoverType.HOVER;
            } else if (hasSource(sources, InputDevice.SOURCE_STYLUS)
                    || hasSource(sources, InputDevice.SOURCE_TOUCHSCREEN)) {
                result[1] |= HoverType.ON_DEMAND;
            }

            // Remaining InputDevice sources: SOURCE_DPAD, SOURCE_GAMEPAD, SOURCE_JOYSTICK,
            // SOURCE_KEYBOARD, SOURCE_TOUCH_NAVIGATION, SOURCE_UNKNOWN
        }

        if (result[0] == 0) result[0] = PointerType.NONE;
        if (result[1] == 0) result[1] = HoverType.NONE;

        return result;
    }

    private static boolean hasSource(int sources, int inputDeviceSource) {
        return (sources & inputDeviceSource) == inputDeviceSource;
    }
}
