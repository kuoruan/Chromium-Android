// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;

/**
 * This class provides the locale related methods for Chrome.
 */
public class ChromeLocalizationUtils {
    /**
     * @return the current Chromium locale used to display UI elements.
     *
     * This matches what the Android framework resolves localized string resources to, using the
     * system locale and the application's resources. For example, if the system uses a locale
     * that is not supported by Chromium resources (e.g. 'fur-rIT'), Android will likely fallback
     * to 'en-rUS' strings when Resources.getString() is called, and this method will return the
     * matching Chromium name (i.e. 'en-US').
     *
     * Using this value is necessary to ensure that the strings accessed from the locale .pak files
     * from C++ match the resources displayed by the Java-based UI views.
     */
    public static String getUiLocaleStringForCompressedPak() {
        return ContextUtils.getApplicationContext().getResources().getString(
                R.string.current_detected_ui_locale_name);
    }

    private ChromeLocalizationUtils() {
        /* cannot be instantiated */
    }
}
