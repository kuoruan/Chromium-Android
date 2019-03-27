// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.developer;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.components.version_info.Channel;
import org.chromium.components.version_info.VersionConstants;

/**
 * Settings fragment containing preferences aimed at Chrome and web developers.
 */
public class DeveloperPreferences extends PreferenceFragment {
    private static final String PREF_DEVELOPER_ENABLED = "developer";

    // Non-translated strings:
    private static final String MSG_DEVELOPER_OPTIONS_TITLE = "Developer options";

    public static boolean shouldShowDeveloperPreferences() {
        // Always enabled on canary, dev and local builds, otherwise can be enabled by tapping the
        // Chrome version in Settings>About multiple times.
        if (VersionConstants.CHANNEL <= Channel.DEV) return true;
        return ContextUtils.getAppSharedPreferences().getBoolean(PREF_DEVELOPER_ENABLED, false);
    }

    public static void setDeveloperPreferencesEnabled() {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PREF_DEVELOPER_ENABLED, true)
                .apply();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(MSG_DEVELOPER_OPTIONS_TITLE);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.developer_preferences);
    }
}
