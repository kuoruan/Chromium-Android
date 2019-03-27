// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.support.annotation.IntDef;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.webapk.lib.common.splash.SplashLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used by SameActivityWebappSplashDelegate to cache webapp splash screen UMA values. Records UMA
 * when {@link #commitMetrics()} is called.
 */
public class SameActivityWebappUmaCache {
    // SplashColorStatus defined in tools/metrics/histograms/enums.xml.
    // NUM_ENTRIES is intentionally included into @IntDef.
    @IntDef({SplashColorStatus.DEFAULT, SplashColorStatus.CUSTOM, SplashColorStatus.NUM_ENTRIES})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SplashColorStatus {
        int DEFAULT = 0;
        int CUSTOM = 1;
        int NUM_ENTRIES = 2;
    }

    // SplashIconType defined in tools/metrics/histograms/enums.xml.
    // NUM_ENTRIES is intentionally included into @IntDef.
    @IntDef({SplashIconType.NONE, SplashIconType.FALLBACK, SplashIconType.CUSTOM,
            SplashIconType.CUSTOM_SMALL, SplashIconType.NUM_ENTRIES})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SplashIconType {
        int NONE = 0;
        int FALLBACK = 1;
        int CUSTOM = 2;
        int CUSTOM_SMALL = 3;
        int NUM_ENTRIES = 4;
    }

    // Histogram names are defined in tools/metrics/histograms/histograms.xml.
    public static final String HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR =
            "Webapp.Splashscreen.BackgroundColor";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_TYPE = "Webapp.Splashscreen.Icon.Type";
    public static final String HISTOGRAM_SPLASHSCREEN_ICON_SIZE = "Webapp.Splashscreen.Icon.Size";
    public static final String HISTOGRAM_SPLASHSCREEN_THEMECOLOR = "Webapp.Splashscreen.ThemeColor";

    private int mSplashScreenBackgroundColor = SplashColorStatus.NUM_ENTRIES;
    private int mSplashScreenIconType = SplashIconType.NUM_ENTRIES;
    private int mSplashScreenIconSize = -1;
    private int mSplashScreenThemeColor = SplashColorStatus.NUM_ENTRIES;

    private boolean mCommitted;

    /**
     * Records the type of background color on the splash screen.
     * @param type flag representing the type of color.
     */
    public void recordSplashscreenBackgroundColor(@SplashColorStatus int type) {
        assert !mCommitted;
        mSplashScreenBackgroundColor = type;
    }

    /**
     * Records the type of icon on the splash screen.
     * @param selectedIconClassification
     * @param usingDedicatedIcon Whether the PWA provides different icons for the splash screen and
     *                           for the app icon.
     */
    public void recordSplashscreenIconType(
            @SplashLayout.IconClassification int selectedIconClassification,
            boolean usingDedicatedIcon) {
        assert !mCommitted;
        mSplashScreenIconType =
                determineIconTypeForUma(selectedIconClassification, usingDedicatedIcon);
    }

    private static @SplashIconType int determineIconTypeForUma(
            @SplashLayout.IconClassification int selectedIconClassification,
            boolean usingDedicatedIcon) {
        if (selectedIconClassification == SplashLayout.IconClassification.INVALID) {
            return SplashIconType.NONE;
        }
        if (!usingDedicatedIcon) {
            return SplashIconType.FALLBACK;
        }
        if (selectedIconClassification == SplashLayout.IconClassification.SMALL) {
            return SplashIconType.CUSTOM_SMALL;
        }
        return SplashIconType.CUSTOM;
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
    public void recordSplashscreenThemeColor(@SplashColorStatus int type) {
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

        assert mSplashScreenBackgroundColor != SplashColorStatus.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_BACKGROUNDCOLOR,
                mSplashScreenBackgroundColor, SplashColorStatus.NUM_ENTRIES);
        mSplashScreenBackgroundColor = SplashColorStatus.NUM_ENTRIES;

        assert mSplashScreenIconType != SplashIconType.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_ICON_TYPE,
                mSplashScreenIconType, SplashIconType.NUM_ENTRIES);

        if (mSplashScreenIconType == SplashIconType.NONE) {
            assert mSplashScreenIconSize == -1;
        } else {
            assert mSplashScreenIconSize >= 0;
            RecordHistogram.recordCount1000Histogram(
                    HISTOGRAM_SPLASHSCREEN_ICON_SIZE, mSplashScreenIconSize);
        }
        mSplashScreenIconType = SplashIconType.NUM_ENTRIES;
        mSplashScreenIconSize = -1;

        assert mSplashScreenThemeColor != SplashColorStatus.NUM_ENTRIES;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_SPLASHSCREEN_THEMECOLOR,
                mSplashScreenThemeColor, SplashColorStatus.NUM_ENTRIES);
        mSplashScreenThemeColor = SplashColorStatus.NUM_ENTRIES;
    }
}
