// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement;

/**
 * FeatureConstants contains the String name of all base::Feature in-product help features declared
 * in //components/feature_engagement/public/feature_constants.h.
 */
public final class FeatureConstants {
    public static final String DOWNLOAD_PAGE_FEATURE = "IPH_DownloadPage";
    public static final String DOWNLOAD_PAGE_SCREENSHOT_FEATURE = "IPH_DownloadPageScreenshot";
    public static final String DOWNLOAD_HOME_FEATURE = "IPH_DownloadHome";
    public static final String CHROME_HOME_EXPAND_FEATURE = "IPH_ChromeHomeExpand";
    public static final String CHROME_HOME_MENU_HEADER_FEATURE = "IPH_ChromeHomeMenuHeader";
    public static final String DATA_SAVER_PREVIEW_FEATURE = "IPH_DataSaverPreview";
    public static final String DATA_SAVER_DETAIL_FEATURE = "IPH_DataSaverDetail";

    public static final String MEDIA_DOWNLOAD_FEATURE = "IPH_MediaDownload";

    /**
     * An IPH feature that encourages users who search a query from a web page in a new tab, to use
     * Contextual Search instead.
     */
    public static final String CONTEXTUAL_SEARCH_FEATURE = "IPH_ContextualSearch";

    /**
     * An IPH feature for promoting tap over longpress for activating Contextual Search.
     */
    public static final String CONTEXTUAL_SEARCH_TAP_FEATURE = "IPH_ContextualSearchTap";

    /**
     * An IPH feature for encouraging users to open the Contextual Search Panel.
     */
    public static final String CONTEXTUAL_SEARCH_PANEL_FEATURE = "IPH_ContextualSearchPanel";

    /**
     * An IPH feature for encouraging users to opt-in for Contextual Search.
     */
    public static final String CONTEXTUAL_SEARCH_OPT_IN_FEATURE = "IPH_ContextualSearchOptIn";

    /**
     * Do not instantiate.
     */
    private FeatureConstants() {}
}
