// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.os.Build;
import android.view.MotionEvent;

/**
 * Support S-Pen event detection and conversion.
 */
public final class SPenSupport {

    // These values are obtained from Samsung.
    private static final int SPEN_ACTION_DOWN = 211;
    private static final int SPEN_ACTION_UP = 212;
    private static final int SPEN_ACTION_MOVE = 213;
    private static final int SPEN_ACTION_CANCEL = 214;
    private static Boolean sIsSPenSupported;

    /**
     * @return Whether SPen is supported on the device.
     */
    public static boolean isSPenSupported(Context context) {
        if (sIsSPenSupported == null) {
            sIsSPenSupported = detectSPenSupport(context);
        }
        return sIsSPenSupported.booleanValue();
    }

    private static boolean detectSPenSupport(Context context) {
        if (!"SAMSUNG".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }

        final FeatureInfo[] infos = context.getPackageManager().getSystemAvailableFeatures();
        for (FeatureInfo info : infos) {
            if ("com.sec.feature.spen_usp".equalsIgnoreCase(info.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert SPen event action into normal event action.
     *
     * @param eventActionMasked Input event action. It is assumed that it is masked as the values
                                cannot be ORed.
     * @return Event action after the conversion.
     */
    public static int convertSPenEventAction(int eventActionMasked) {
        // S-Pen support: convert to normal stylus event handling
        switch (eventActionMasked) {
            case SPEN_ACTION_DOWN:
                return MotionEvent.ACTION_DOWN;
            case SPEN_ACTION_UP:
                return MotionEvent.ACTION_UP;
            case SPEN_ACTION_MOVE:
                return MotionEvent.ACTION_MOVE;
            case SPEN_ACTION_CANCEL:
                return MotionEvent.ACTION_CANCEL;
            default:
                return eventActionMasked;
        }
    }
}
