// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home;

import org.chromium.base.ContextUtils;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.ui.base.DeviceFormFactor;

/** Provides the configuration params required by the download home UI. */
public class DownloadManagerUiConfig {
    /** Whether or not the UI should include off the record items. */
    public final boolean isOffTheRecord;

    /** Whether or not the UI should be shown as part of a separate activity. */
    public final boolean isSeparateActivity;

    /** Whether generic view types should be used wherever possible. Used for low end devices. */
    public final boolean useGenericViewTypes;

    /** Whether showing full width images should be supported. */
    public final boolean supportFullWidthImages;
    /**
     * The time interval during which a download update is considered recent enough to show
     * in Just Now section.
     */
    public final long justNowThresholdSeconds;

    /** Constructor. */
    private DownloadManagerUiConfig(Builder builder) {
        isOffTheRecord = builder.mIsOffTheRecord;
        isSeparateActivity = builder.mIsSeparateActivity;
        useGenericViewTypes = builder.mUseGenericViewTypes;
        supportFullWidthImages = builder.mSupportFullWidthImages;
        justNowThresholdSeconds = builder.mJustNowThresholdSeconds;
    }

    /** Helper class for building a {@link DownloadManagerUiConfig}. */
    public static class Builder {
        private static final String JUST_NOW_THRESHOLD_SECONDS_PARAM = "just_now_threshold";

        /** Default value for threshold time interval to show up in Just Now section. */
        private static final int JUST_NOW_THRESHOLD_SECONDS_DEFAULT = 30 * 60;

        private boolean mIsOffTheRecord;
        private boolean mIsSeparateActivity;
        private boolean mUseGenericViewTypes;
        private boolean mSupportFullWidthImages;
        private long mJustNowThresholdSeconds;

        public Builder() {
            readParamsFromFinch();
            mSupportFullWidthImages = !DeviceFormFactor.isNonMultiDisplayContextOnTablet(
                    ContextUtils.getApplicationContext());
            mUseGenericViewTypes = SysUtils.isLowEndDevice();
        }

        public Builder setIsOffTheRecord(boolean isOffTheRecord) {
            mIsOffTheRecord = isOffTheRecord;
            return this;
        }

        public Builder setIsSeparateActivity(boolean isSeparateActivity) {
            mIsSeparateActivity = isSeparateActivity;
            return this;
        }

        public Builder setUseGenericViewTypes(boolean useGenericViewTypes) {
            mUseGenericViewTypes = useGenericViewTypes;
            return this;
        }

        public Builder setSupportFullWidthImages(boolean supportFullWidthImages) {
            mSupportFullWidthImages = supportFullWidthImages;
            return this;
        }

        public DownloadManagerUiConfig build() {
            return new DownloadManagerUiConfig(this);
        }

        private void readParamsFromFinch() {
            mJustNowThresholdSeconds = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                    ChromeFeatureList.DOWNLOAD_HOME_V2, JUST_NOW_THRESHOLD_SECONDS_PARAM,
                    JUST_NOW_THRESHOLD_SECONDS_DEFAULT);
        }
    }
}
