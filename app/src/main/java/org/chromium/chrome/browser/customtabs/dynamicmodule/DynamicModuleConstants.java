// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs.dynamicmodule;

/**
 * Class to keep constants specific to the CCT dynamic module.
 */
public class DynamicModuleConstants {
    /**
     * The custom header's name used for module managed URLs.
     */
    public static final String MANAGED_URL_HEADER = "X-CCT-Client-Data";

    /**
     * The module version when {@link IActivityDelegate#onBackPressedAsync} is introduced.
     */
    public static final int ON_BACK_PRESSED_ASYNC_API_VERSION = 2;

    /**
     * The module version when {@link IActivityDelegate#onNavigationEvent} is introduced.
     */
    public static final int ON_NAVIGATION_EVENT_MODULE_API_VERSION = 4;

    /**
     * The module version when {@link IActivityDelegate#onPageMetricEvent} is introduced.
     */
    public static final int ON_PAGE_LOAD_METRIC_API_VERSION = 10;
}
