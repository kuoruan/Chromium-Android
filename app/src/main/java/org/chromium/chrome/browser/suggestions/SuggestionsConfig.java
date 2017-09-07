// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.res.Resources;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;

/**
 * Provides configuration details for suggestions.
 */
public final class SuggestionsConfig {
    private SuggestionsConfig() {}

    /**
     * @return Whether to use the modern layout for suggestions in Chrome Home.
     */
    public static boolean useModern() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.SUGGESTIONS_HOME_MODERN_LAYOUT);
    }

    /**
     * @param resources The resources to fetch the color from.
     * @return The background color for the suggestions sheet content.
     */
    public static int getBackgroundColor(Resources resources) {
        return useModern()
                ? ApiCompatibilityUtils.getColor(resources, R.color.suggestions_modern_bg)
                : ApiCompatibilityUtils.getColor(resources, R.color.ntp_bg);
    }
}
