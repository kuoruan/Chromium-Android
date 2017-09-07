// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement_tracker;

/**
 * FeatureConstants contains the String name of all base::Feature in-product help features declared
 * in //components/feature_engagement_tracker/public/feature_constants.h.
 */
public final class FeatureConstants {
    public static final String DOWNLOAD_PAGE_FEATURE = "IPH_DownloadPage";
    public static final String DOWNLOAD_PAGE_SCREENSHOT_FEATURE = "IPH_DownloadPageScreenshot";
    public static final String DOWNLOAD_HOME_FEATURE = "IPH_DownloadHome";

    public static final String DATA_SAVER_PREVIEW_FEATURE = "IPH_DataSaverPreview";
    public static final String DATA_SAVER_DETAIL_FEATURE = "IPH_DataSaverDetail";

    /**
     * Do not instantiate.
     */
    private FeatureConstants() {}
}
