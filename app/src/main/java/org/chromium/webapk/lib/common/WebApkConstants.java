// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.common;

/**
 * Stores WebAPK related constants.
 */
public final class WebApkConstants {
    public static final String WEBAPK_PACKAGE_PREFIX = "org.chromium.webapk";

    // WebAPK id prefix. The id is used for storing WebAPK data in Chrome's SharedPreferences.
    public static final String WEBAPK_ID_PREFIX = "webapk:";

    // These EXTRA_* values must stay in sync with
    // {@link org.chromium.chrome.browser.ShortcutHelper}.
    public static final String EXTRA_ID = "org.chromium.chrome.browser.webapp_id";
    public static final String EXTRA_URL = "org.chromium.chrome.browser.webapp_url";
    public static final String EXTRA_SOURCE = "org.chromium.chrome.browser.webapp_source";
    public static final String EXTRA_WEBAPK_PACKAGE_NAME =
            "org.chromium.chrome.browser.webapk_package_name";
}
