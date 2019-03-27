// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * This is a utility class for managing experiments related to returning to Chrome.
 */
final class ReturnToChromeExperimentsUtil {
    private static final String TAB_SWITCHER_ON_RETURN_MS = "tab_switcher_on_return_time_ms";

    private ReturnToChromeExperimentsUtil() {}

    /**
     * Determine if we should show the tab switcher on returning to Chrome.
     *   Returns true if enough time has elapsed since the app was last backgrounded.
     *   The threshold time in milliseconds is set by experiment "enable-tab-switcher-on-return"
     *
     * @param lastBackgroundedTimeMillis The last time the application was backgrounded. Set in
     *                                   ChromeTabbedActivity::onStopWithNative
     * @return true if past threshold, false if not past threshold or experiment cannot be loaded.
     */
    static boolean shouldShowTabSwitcher(final long lastBackgroundedTimeMillis) {
        if (lastBackgroundedTimeMillis == -1) {
            // No last background timestamp set, use control behavior.
            return false;
        }

        int tabSwitcherAfterMillis = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.TAB_SWITCHER_ON_RETURN, TAB_SWITCHER_ON_RETURN_MS, -1);

        if (tabSwitcherAfterMillis < 0) {
            // If no value for experiment, use control behavior.
            return false;
        }

        long expirationTime = lastBackgroundedTimeMillis + tabSwitcherAfterMillis;

        return System.currentTimeMillis() > expirationTime;
    }
}
