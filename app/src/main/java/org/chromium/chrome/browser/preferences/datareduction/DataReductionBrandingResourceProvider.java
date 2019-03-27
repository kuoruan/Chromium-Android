// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.annotation.XmlRes;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;

/**
 * This class provides Android resource IDs for the Data Saver/Lite Mode feature. This feature is
 * being rebranded, but the rebrand is controlled by a feature flag.
 *
 * TODO(crbug.com/909915): Remove this class and callsites when fully rolled out.
 */
public final class DataReductionBrandingResourceProvider {
    /**
     * Given a DataSaver string resource, maybe return the string resource for Lite Mode rebranding
     * if it is enabled. If not, the same string resource is returned.
     *
     * @param resource The string resource to check.
     * @return The string resource to use with respect to the Lite Mode rebranding.
     */
    public static @StringRes int getDataSaverBrandedString(@StringRes int resource) {
        if (shouldUseLiteMode()) {
            return mapToLiteModeString(resource);
        }
        return resource;
    }

    /**
     * Given a DataSaver first run layout resource, maybe return the first run layout
     * resource for Lite Mode rebranding if it is enabled. If not, the same resource is returned.
     *
     * @param resource The layout resource to check.
     * @return The resource to use with respect to the Lite Mode rebranding.
     */
    public static @LayoutRes int getFirstRunLayout(@LayoutRes int resource) {
        if (shouldUseLiteMode()) {
            return mapToLiteModeLayout(resource);
        }
        return resource;
    }

    /**
     * Given a DataSaver preferences XML resource, maybe return the preferences XML
     * resource for Lite Mode rebranding if it is enabled. If not, the same resource is returned
     *
     * @param resource The XML resource to check.
     * @return The resource to use with respect to the Lite Mode rebranding.
     */
    public static @XmlRes int getPreferencesOffXml(@XmlRes int resource) {
        if (shouldUseLiteMode()) {
            return mapToLiteModeXml(resource);
        }
        return resource;
    }

    private static boolean shouldUseLiteMode() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.DATA_SAVER_LITE_MODE_REBRANDING);
    }

    private static @StringRes int mapToLiteModeString(@StringRes int resource) {
        // Enumerates all strings used by Data Saver or Lite Mode. Not all strings are changed in
        // the Lite Mode rebrand, so some strings are returned as is.
        if (resource == R.string.data_reduction_title) {
            return R.string.data_reduction_title_lite_mode;
        }
        if (resource == R.string.data_reduction_usage_reset_statistics_confirmation_title) {
            return R.string.data_reduction_usage_reset_statistics_confirmation_title_lite_mode;
        }
        if (resource == R.string.data_reduction_usage_reset_statistics_confirmation_dialog) {
            return R.string.data_reduction_usage_reset_statistics_confirmation_dialog_lite_mode;
        }
        if (resource == R.string.data_reduction_promo_title) {
            return R.string.data_reduction_promo_title_lite_mode;
        }
        if (resource == R.string.data_reduction_promo_summary) {
            return R.string.data_reduction_promo_summary_lite_mode;
        }
        if (resource == R.string.data_reduction_enable_button) {
            return R.string.data_reduction_enable_button_lite_mode;
        }
        if (resource == R.string.data_reduction_enabled_switch) {
            return R.string.data_reduction_enabled_switch_lite_mode;
        }
        if (resource == R.string.data_reduction_disabled_switch) {
            return R.string.data_reduction_disabled_switch_lite_mode;
        }
        if (resource == R.string.data_reduction_enabled_toast) {
            return R.string.data_reduction_enabled_toast_lite_mode;
        }
        return resource;
    }

    private static @LayoutRes int mapToLiteModeLayout(@LayoutRes int resource) {
        if (resource == R.layout.fre_data_reduction_proxy) {
            return R.layout.fre_data_reduction_proxy_lite_mode;
        }
        return resource;
    }

    private static @XmlRes int mapToLiteModeXml(@XmlRes int resource) {
        if (resource == R.xml.data_reduction_preferences_off) {
            return R.xml.data_reduction_preferences_off_lite_mode;
        }
        return resource;
    }
}
