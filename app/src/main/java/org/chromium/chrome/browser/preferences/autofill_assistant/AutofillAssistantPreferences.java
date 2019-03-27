// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.autofill_assistant;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;

/** The "Autofill Assistant" preferences screen in Settings. */
public class AutofillAssistantPreferences extends PreferenceFragment {
    /** Autofill Assistant switch preference key name. */
    public static final String PREF_AUTOFILL_ASSISTANT_SWITCH = "autofill_assistant_switch";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_autofill_assistant_title);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
        createAutofillAssistantSwitch();
    }

    private void createAutofillAssistantSwitch() {
        ChromeSwitchPreference autofillAssistantSwitch =
                new ChromeSwitchPreference(getActivity(), null);
        autofillAssistantSwitch.setKey(PREF_AUTOFILL_ASSISTANT_SWITCH);
        autofillAssistantSwitch.setTitle(R.string.prefs_autofill_assistant_switch);
        autofillAssistantSwitch.setSummaryOn(R.string.text_on);
        autofillAssistantSwitch.setSummaryOff(R.string.text_off);
        autofillAssistantSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ContextUtils.getAppSharedPreferences()
                        .edit()
                        .putBoolean(PREF_AUTOFILL_ASSISTANT_SWITCH, (boolean) newValue)
                        .apply();
                return true;
            }
        });
        getPreferenceScreen().addPreference(autofillAssistantSwitch);

        // Note: setting the switch state before the preference is added to the screen results in
        // some odd behavior where the switch state doesn't always match the internal enabled state
        // (e.g. the switch will say "On" when it is really turned off), so .setChecked() should be
        // called after .addPreference()
        autofillAssistantSwitch.setChecked(ContextUtils.getAppSharedPreferences().getBoolean(
                PREF_AUTOFILL_ASSISTANT_SWITCH, true));
    }
}
