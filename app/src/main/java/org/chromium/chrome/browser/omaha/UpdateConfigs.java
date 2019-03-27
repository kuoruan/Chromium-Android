// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omaha;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.omaha.UpdateStatusProvider.UpdateState;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * Helper class for retrieving experiment configuration values and for manually testing update
 * functionality.  Use the following switches to test locally:
 * - {@link ChromeSwitches#FORCE_UPDATE_MENU_UPDATE_TYPE} (required)
 * - {@link ChromeSwitches#FORCE_SHOW_UPDATE_MENU_BADGE} (optional)
 * - {@link ChromeSwitches#MARKET_URL_FOR_TESTING} (optional)
 */
class UpdateConfigs {
    // VariationsAssociatedData configs
    private static final String FIELD_TRIAL_NAME = "UpdateMenuItem";
    private static final String ENABLED_VALUE = "true";
    private static final String CUSTOM_SUMMARY = "custom_summary";
    private static final String MIN_REQUIRED_STORAGE_MB = "min_required_storage_for_update_mb";

    // Update state switch values.
    private static final String NONE_SWITCH_VALUE = "none";
    private static final String UPDATE_AVAILABLE_SWITCH_VALUE = "update_available";
    private static final String UNSUPPORTED_OS_VERSION_SWITCH_VALUE = "unsupported_os_version";
    private static final String INLINE_UPDATE_AVAILABLE_SWITCH_VALUE = "inline_update_available";
    private static final String INLINE_UPDATE_DOWNLOADING_SWITCH_VALUE =
            "inline_update_downloading";
    private static final String INLINE_UPDATE_READY_SWITCH_VALUE = "inline_update_ready";
    private static final String INLINE_UPDATE_FAILED_SWITCH_VALUE = "inline_update_failed";

    /**
     * @return The minimum required storage to show the update prompt or {@code -1} if there is no
     * minimum.
     */
    public static int getMinRequiredStorage() {
        String value = CommandLine.getInstance().getSwitchValue(MIN_REQUIRED_STORAGE_MB);
        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(
                    FIELD_TRIAL_NAME, MIN_REQUIRED_STORAGE_MB);
        }
        if (TextUtils.isEmpty(value)) return -1;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return A custom update menu summary to show.  This should override the default summary for
     * 'update available' menu items.
     */
    public static String getCustomSummary() {
        return getStringParamValue(CUSTOM_SUMMARY);
    }

    /**
     * @return Whether or not to always show the update badge on the menu depending on the update
     * state.
     */
    public static boolean getAlwaysShowMenuBadge() {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.FORCE_SHOW_UPDATE_MENU_BADGE)) {
            return true;
        }

        return false;
    }

    /** @return A test {@link UpdateState} to use or {@code null} if no test state was specified. */
    public static @UpdateState Integer getMockUpdateState() {
        String forcedUpdateType = getStringParamValue(ChromeSwitches.FORCE_UPDATE_MENU_UPDATE_TYPE);
        if (TextUtils.isEmpty(forcedUpdateType)) return null;

        switch (forcedUpdateType) {
            case NONE_SWITCH_VALUE:
                return UpdateState.NONE;
            case UPDATE_AVAILABLE_SWITCH_VALUE:
                return UpdateState.UPDATE_AVAILABLE;
            case UNSUPPORTED_OS_VERSION_SWITCH_VALUE:
                return UpdateState.UNSUPPORTED_OS_VERSION;
            case INLINE_UPDATE_AVAILABLE_SWITCH_VALUE:
                return UpdateState.INLINE_UPDATE_AVAILABLE;
            case INLINE_UPDATE_DOWNLOADING_SWITCH_VALUE:
                return UpdateState.INLINE_UPDATE_DOWNLOADING;
            case INLINE_UPDATE_FAILED_SWITCH_VALUE:
                return UpdateState.INLINE_UPDATE_FAILED;
            case INLINE_UPDATE_READY_SWITCH_VALUE:
                return UpdateState.INLINE_UPDATE_READY;
            default:
                return null;
        }
    }

    /**
     * @return A URL to use when an update is available if mocking out the update available menu
     * item.
     */
    public static String getMockMarketUrl() {
        return getStringParamValue(ChromeSwitches.MARKET_URL_FOR_TESTING);
    }

    /**
     * Gets a String VariationsAssociatedData parameter. Also checks for a command-line switch with
     * the same name, for easy local testing.
     * @param paramName The name of the parameter (or command-line switch) to get a value for.
     * @return The command-line flag value if present, or the param is value if present.
     */
    @Nullable
    private static String getStringParamValue(String paramName) {
        String value = CommandLine.getInstance().getSwitchValue(paramName);
        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName);
        }
        return value;
    }
}