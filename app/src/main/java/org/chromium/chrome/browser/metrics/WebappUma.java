// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.os.SystemClock;
import android.support.annotation.IntDef;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.webapps.SplashscreenObserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for web apps.
 */
public class WebappUma implements SplashscreenObserver {
    // SplashscreenColorStatus defined in tools/metrics/histograms/enums.xml.
    // NUM_ENTRIES is intentionally included into @IntDef.
    @IntDef({SplashScreenColorStatus.DEFAULT, SplashScreenColorStatus.CUSTOM,
            SplashScreenColorStatus.NUM_ENTRIES})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SplashScreenColorStatus {
        int DEFAULT = 0;
        int CUSTOM = 1;
        int NUM_ENTRIES = 2;
    }

    // SplashscreenHidesReason defined in tools/metrics/histograms/enums.xml.
    @IntDef({SplashScreenHidesReason.PAINT, SplashScreenHidesReason.LOAD_FINISHED,
            SplashScreenHidesReason.LOAD_FAILED, SplashScreenHidesReason.CRASH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SplashScreenHidesReason {
        int PAINT = 0;
        int LOAD_FINISHED = 1;
        int LOAD_FAILED = 2;
        int CRASH = 3;
        int NUM_ENTRIES = 4;
    }

    // SplashscreenBackgroundColorType defined in tools/metrics/histograms/enums.xml.
    // NUM_ENTRIES is intentionally included into @IntDef.
    @IntDef({SplashScreenIconType.NONE, SplashScreenIconType.FALLBACK, SplashScreenIconType.CUSTOM,
            SplashScreenIconType.CUSTOM_SMALL, SplashScreenIconType.NUM_ENTRIES})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SplashScreenIconType {
        int NONE = 0;
        int FALLBACK = 1;
        int CUSTOM = 2;
        int CUSTOM_SMALL = 3;
        int NUM_ENTRIES = 4;
    }

    // Histogram names are defined in tools/metrics/histograms/histograms.xml.
    public static final String HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR =
            "Webapp.Splashscreen.BackgroundColor";
    public static final String HISTOGRAM_SPLASHSCREEN_DURATION =
            "Webapp.Splashscreen.Duration";
    public static final String HISTOGRAM_SPLASHSCREEN_HIDES =
            "Webapp.Splashscreen.Hides";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_TYPE =
            "Webapp.Splashscreen.Icon.Type";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_SIZE =
            "Webapp.Splashscreen.Icon.Size";
    public static final String HISTOGRAM_SPLASHSCREEN_THEMECOLOR =
            "Webapp.Splashscreen.ThemeColor";

    private int mSplashScreenBackgroundColor = SplashScreenColorStatus.NUM_ENTRIES;
    private int mSplashScreenIconType = SplashScreenIconType.NUM_ENTRIES;
    private int mSplashScreenIconSize = -1;
    private int mSplashScreenThemeColor = SplashScreenColorStatus.NUM_ENTRIES;
    private long mSplashScreenVisibleTime;

    private boolean mCommitted;

    /**
     * Signal that the splash screen is now visible. This is being used to
     * record for how long the splash screen is left visible.
     */
    @Override
    public void onSplashscreenShown() {
        assert mSplashScreenVisibleTime == 0;
        mSplashScreenVisibleTime = SystemClock.elapsedRealtime();
    }

    /**
     * Records the type of background color on the splash screen.
     * @param type flag representing the type of color.
     */
    public void recordSplashscreenBackgroundColor(@SplashScreenColorStatus int type) {
        assert !mCommitted;
        mSplashScreenBackgroundColor = type;
    }

    /**
     * Signal that the splash screen is now hidden. It is used to record for how
     * long the splash screen was left visible. It is also used to know what
     * event triggered the splash screen to be hidden.
     * @param reason enum representing the reason why the splash screen was hidden.
     */
    @Override
    public void onSplashscreenHidden(@SplashScreenHidesReason int reason) {
        RecordHistogram.recordEnumeratedHistogram(
                HISTOGRAM_SPLASHSCREEN_HIDES, reason, SplashScreenHidesReason.NUM_ENTRIES);

        assert mSplashScreenVisibleTime != 0;
        RecordHistogram.recordMediumTimesHistogram(HISTOGRAM_SPLASHSCREEN_DURATION,
                SystemClock.elapsedRealtime() - mSplashScreenVisibleTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Records the type of icon on the splash screen.
     * @param type flag representing the type of icon.
     */
    public void recordSplashscreenIconType(@SplashScreenIconType int type) {
        assert !mCommitted;
        mSplashScreenIconType = type;
    }

    public void recordSplashscreenIconSize(int size) {
        assert !mCommitted;
        assert size >= 0;
        mSplashScreenIconSize = size;
    }

    /**
     * Records the type of theme color on the splash screen.
     * @param type flag representing the type of color.
     */
    public void recordSplashscreenThemeColor(@SplashScreenColorStatus int type) {
        assert !mCommitted;
        mSplashScreenThemeColor = type;
    }

    /**
     * Records all metrics that could not be recorded because the native library
     * was not loaded yet.
     */
    public void commitMetrics() {
        if (mCommitted) return;

        mCommitted = true;

        assert mSplashScreenBackgroundColor != SplashScreenColorStatus.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR,
                mSplashScreenBackgroundColor, SplashScreenColorStatus.NUM_ENTRIES);
        mSplashScreenBackgroundColor = SplashScreenColorStatus.NUM_ENTRIES;

        assert mSplashScreenIconType != SplashScreenIconType.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_ICON_TYPE,
                mSplashScreenIconType, SplashScreenIconType.NUM_ENTRIES);

        if (mSplashScreenIconType == SplashScreenIconType.NONE) {
            assert mSplashScreenIconSize == -1;
        } else {
            assert mSplashScreenIconSize >= 0;
            RecordHistogram.recordCount1000Histogram(HISTOGRAM_SPLASHSCREEN_ICON_SIZE,
                    mSplashScreenIconSize);
        }
        mSplashScreenIconType = SplashScreenIconType.NUM_ENTRIES;
        mSplashScreenIconSize = -1;

        assert mSplashScreenThemeColor != SplashScreenColorStatus.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_THEMECOLOR,
                mSplashScreenThemeColor, SplashScreenColorStatus.NUM_ENTRIES);
        mSplashScreenThemeColor = SplashScreenColorStatus.NUM_ENTRIES;
    }
}
