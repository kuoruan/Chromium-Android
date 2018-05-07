// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.MainDex;


/**
 * This is an internal implementation of the C++ counterpart.
 * It registers a ComponentCallbacks2 with the system, and dispatches into
 * native for levels that are considered actionable.
 */
@MainDex
public class MemoryPressureListener {
    /**
     * Sending an intent with this action to Chrome will cause it to issue a call to onLowMemory
     * thus simulating a low memory situations.
     */
    private static final String ACTION_LOW_MEMORY = "org.chromium.base.ACTION_LOW_MEMORY";

    /**
     * Sending an intent with this action to Chrome will cause it to issue a call to onTrimMemory
     * thus simulating a low memory situations.
     */
    private static final String ACTION_TRIM_MEMORY = "org.chromium.base.ACTION_TRIM_MEMORY";

    /**
     * Sending an intent with this action to Chrome will cause it to issue a call to onTrimMemory
     * with notification level TRIM_MEMORY_RUNNING_CRITICAL thus simulating a low memory situation
     */
    private static final String ACTION_TRIM_MEMORY_RUNNING_CRITICAL =
            "org.chromium.base.ACTION_TRIM_MEMORY_RUNNING_CRITICAL";

    /**
     * Sending an intent with this action to Chrome will cause it to issue a call to onTrimMemory
     * with notification level TRIM_MEMORY_MODERATE thus simulating a low memory situation
     */
    private static final String ACTION_TRIM_MEMORY_MODERATE =
            "org.chromium.base.ACTION_TRIM_MEMORY_MODERATE";

    private static OnMemoryPressureCallbackForTesting sOnMemoryPressureCallbackForTesting;

    @VisibleForTesting
    @CalledByNative
    public static void registerSystemCallback() {
        ContextUtils.getApplicationContext().registerComponentCallbacks(
                new ComponentCallbacks2() {
                    @Override
                    public void onTrimMemory(int level) {
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                            onMemoryPressure(MemoryPressureLevel.CRITICAL);
                        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                            // Don't notifiy on TRIM_MEMORY_UI_HIDDEN, since this class only
                            // dispatches actionable memory pressure signals to native.
                            onMemoryPressure(MemoryPressureLevel.MODERATE);
                        }
                    }

                    @Override
                    public void onLowMemory() {
                        onMemoryPressure(MemoryPressureLevel.CRITICAL);
                    }

                    @Override
                    public void onConfigurationChanged(Configuration configuration) {
                    }
                });
    }

    /** Memory pressure callback that is called before calling native. Use for testing only.
     */
    @VisibleForTesting
    @FunctionalInterface
    public interface OnMemoryPressureCallbackForTesting {
        public void apply(@MemoryPressureLevel int pressure);
    }

    @VisibleForTesting
    public static void setOnMemoryPressureCallbackForTesting(
            OnMemoryPressureCallbackForTesting callback) {
        sOnMemoryPressureCallbackForTesting = callback;
    }

    /** Reports memory pressure.
     */
    public static void onMemoryPressure(@MemoryPressureLevel int pressure) {
        if (sOnMemoryPressureCallbackForTesting != null) {
            sOnMemoryPressureCallbackForTesting.apply(pressure);
        }
        nativeOnMemoryPressure(pressure);
    }

    /**
     * Used by applications to simulate a memory pressure signal. By throwing certain intent
     * actions.
     */
    public static boolean handleDebugIntent(Activity activity, String action) {
        if (ACTION_LOW_MEMORY.equals(action)) {
            simulateLowMemoryPressureSignal(activity);
        } else if (ACTION_TRIM_MEMORY.equals(action)) {
            simulateTrimMemoryPressureSignal(activity, ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        } else if (ACTION_TRIM_MEMORY_RUNNING_CRITICAL.equals(action)) {
            simulateTrimMemoryPressureSignal(activity,
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
        } else if (ACTION_TRIM_MEMORY_MODERATE.equals(action)) {
            simulateTrimMemoryPressureSignal(activity, ComponentCallbacks2.TRIM_MEMORY_MODERATE);
        } else {
            return false;
        }

        return true;
    }

    private static void simulateLowMemoryPressureSignal(Activity activity) {
        // The Application and the Activity each have a list of callbacks they notify when this
        // method is called.  Notifying these will simulate the event at the App/Activity level
        // as well as trigger the listener bound from native in this process.
        activity.getApplication().onLowMemory();
        activity.onLowMemory();
    }

    private static void simulateTrimMemoryPressureSignal(Activity activity, int level) {
        // The Application and the Activity each have a list of callbacks they notify when this
        // method is called.  Notifying these will simulate the event at the App/Activity level
        // as well as trigger the listener bound from native in this process.
        activity.getApplication().onTrimMemory(level);
        activity.onTrimMemory(level);
    }

    // Don't call directly, always go through onMemoryPressure().
    private static native void nativeOnMemoryPressure(@MemoryPressureLevel int pressure);
}
