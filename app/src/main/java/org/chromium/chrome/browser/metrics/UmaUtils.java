// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.os.SystemClock;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordHistogram;

import java.util.concurrent.TimeUnit;

/**
 * Utilities to support startup metrics - Android version.
 */
@JNINamespace("chrome::android")
public class UmaUtils {
    private static long sApplicationStartWallClockMs;
    private static boolean sRunningApplicationStart;

    // All these values originate from SystemClock.uptimeMillis().
    private static long sApplicationStartTimeMs;
    private static long sForegroundStartTimeMs;
    private static long sBackgroundTimeMs;

    // Event duration recorded from the |sApplicationStartTimeMs|.
    private static long sFirstCommitTimeMs;

    /**
     * Record the time in the application lifecycle at which Chrome code first runs
     * (Application.attachBaseContext()).
     */
    public static void recordMainEntryPointTime() {
        // We can't simply pass this down through a JNI call, since the JNI for chrome
        // isn't initialized until we start the native content browser component, and we
        // then need the start time in the C++ side before we return to Java. As such we
        // save it in a static that the C++ can fetch once it has initialized the JNI.
        sApplicationStartWallClockMs = System.currentTimeMillis();
        sApplicationStartTimeMs = SystemClock.uptimeMillis();
    }

    /**
     * Record the time at which Chrome was brought to foreground.
     */
    public static void recordForegroundStartTime() {
        // Since this can be called from multiple places (e.g. ChromeActivitySessionTracker
        // and FirstRunActivity), only set the time if it hasn't been set previously or if
        // Chrome has been sent to background since the last foreground time.
        if (sForegroundStartTimeMs == 0 || sForegroundStartTimeMs < sBackgroundTimeMs) {
            sForegroundStartTimeMs = SystemClock.uptimeMillis();
        }
    }

    /**
     * Record the time at which Chrome was sent to background.
     */
    public static void recordBackgroundTime() {
        sBackgroundTimeMs = SystemClock.uptimeMillis();
    }

    /**
     * Registers the fact that a navigation has finished. Based on this fact, may discard recording
     * histograms later.
     */
    public static void registerFinishNavigation(boolean isTrackedPage) {
        if (!isRunningApplicationStart()) return;

        if (isTrackedPage && hasComeToForeground() && !hasComeToBackground()) {
            sFirstCommitTimeMs = SystemClock.uptimeMillis() - sApplicationStartTimeMs;
            RecordHistogram.recordLongTimesHistogram100(
                    "Startup.Android.Experimental.Cold.TimeToFirstNavigationCommit",
                    sFirstCommitTimeMs, TimeUnit.MILLISECONDS);
        }
        setRunningApplicationStart(false);
    }

    /**
     * Record the First Contentful Paint time.
     *
     * @param firstContentfulPaintMs timestamp in uptime millis.
     */
    public static void recordFirstContentfulPaint(long firstContentfulPaintMs) {
        // First commit time histogram should be recorded before this one. We should discard a
        // record if the first commit time wasn't recorded.
        if (sFirstCommitTimeMs == 0) return;

        if (hasComeToForeground() && !hasComeToBackground()) {
            RecordHistogram.recordLongTimesHistogram100(
                    "Startup.Android.Experimental.Cold.TimeToFirstContentfulPaint",
                    firstContentfulPaintMs - sApplicationStartTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Determines if Chrome was brought to foreground.
     */
    public static boolean hasComeToForeground() {
        return sForegroundStartTimeMs != 0;
    }

    /**
     * Determines if Chrome was brought to background.
     */
    public static boolean hasComeToBackground() {
        return sBackgroundTimeMs != 0;
    }

    /**
     * Whether the application is in the early stage since the browser process start. Currently, the
     * very first finished navigation in the lifetime of the process ends the "application start".
     * Must only be called on the UI thread.
     */
    public static boolean isRunningApplicationStart() {
        return sRunningApplicationStart;
    }

    /**
     * Marks/unmarks the "application start" stage of the browser process lifetime.
     * Must only be called on the UI thread.
     */
    public static void setRunningApplicationStart(boolean isAppStart) {
        sRunningApplicationStart = isAppStart;
    }

    /**
     * Determines if this client is eligible to send metrics and crashes based on sampling. If it
     * is, and there was user consent, then metrics and crashes would be reported
     */
    public static boolean isClientInMetricsReportingSample() {
        return nativeIsClientInMetricsReportingSample();
    }

    /**
     * Sets whether metrics reporting was opt-in or not. If it was opt-in, then the enable checkbox
     * on first-run was default unchecked. If it was opt-out, then the checkbox was default checked.
     * This should only be set once, and only during first-run.
     */
    public static void recordMetricsReportingDefaultOptIn(boolean optIn) {
        nativeRecordMetricsReportingDefaultOptIn(optIn);
    }

    @CalledByNative
    public static long getMainEntryPointWallTime() {
        return sApplicationStartWallClockMs;
    }

    @CalledByNative
    public static long getMainEntryPointTicks() {
        return sApplicationStartTimeMs;
    }

    public static long getForegroundStartTicks() {
        assert sForegroundStartTimeMs != 0;
        return sForegroundStartTimeMs;
    }

    private static native boolean nativeIsClientInMetricsReportingSample();
    private static native void nativeRecordMetricsReportingDefaultOptIn(boolean optIn);
}
