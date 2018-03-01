// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.languages;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.widget.TintedDrawable;

/**
 * Settings fragment that displays information about Chrome languages, which allow users to
 * seamlessly find and manage their languages preferences across platforms.
 */
public class LanguagesPreferences extends PreferenceFragment {
    // The keys for each prefernce shown on the langauges page.
    static final String ADD_LANGUAGE_KEY = "add_language";
    static final String PREFERRED_LANGUAGES_KEY = "preferred_languages";
    static final String TRANSLATE_SWITCH_KEY = "translate_switch";

    private LanguageListPreference mLanguageListPref;
    private Preference mAddLanguagePref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.prefs_languages);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.languages_preferences);

        mAddLanguagePref = findPreference(ADD_LANGUAGE_KEY);
        mAddLanguagePref.setIcon(TintedDrawable.constructTintedDrawable(
                getResources(), R.drawable.plus, R.color.pref_accent_color));

        mLanguageListPref = (LanguageListPreference) findPreference(PREFERRED_LANGUAGES_KEY);

        ChromeSwitchPreference translateSwitch =
                (ChromeSwitchPreference) findPreference(TRANSLATE_SWITCH_KEY);
        boolean isTranslateEnabled = PrefServiceBridge.getInstance().isTranslateEnabled();
        translateSwitch.setChecked(isTranslateEnabled);

        translateSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setTranslateEnabled((boolean) newValue);
                return true;
            }
        });
        translateSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isTranslateManaged();
            }
        });
    }
}
