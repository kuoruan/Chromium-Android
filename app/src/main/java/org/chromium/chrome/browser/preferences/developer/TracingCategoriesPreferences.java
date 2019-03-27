// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.developer;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeBaseCheckBoxPreference;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.tracing.TracingController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings fragment that configures chrome tracing categories of a specific type. The type is
 * passed to the fragment via an extra (EXTRA_CATEGORY_TYPE).
 */
public class TracingCategoriesPreferences
        extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    public static final String EXTRA_CATEGORY_TYPE = "type";

    private @TracingPreferences.CategoryType int mType;
    private Set<String> mEnabledCategories;

    // Non-translated strings:
    private static final String MSG_CATEGORY_SELECTION_TITLE = "Select categories";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(MSG_CATEGORY_SELECTION_TITLE);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.blank_preference_fragment_screen);
        getPreferenceScreen().setOrderingAsAdded(true);

        mType = getArguments().getInt(EXTRA_CATEGORY_TYPE);
        mEnabledCategories = new HashSet<>(TracingPreferences.getEnabledCategories(mType));

        List<String> sortedCategories =
                new ArrayList<>(TracingController.getInstance().getKnownCategories());
        Collections.sort(sortedCategories);
        for (String category : sortedCategories) {
            if (TracingPreferences.getCategoryType(category) == mType) createPreference(category);
        }
    }

    private void createPreference(String category) {
        CheckBoxPreference preference = new ChromeBaseCheckBoxPreference(getActivity(), null);
        preference.setKey(category);
        preference.setTitle(category.startsWith(TracingPreferences.NON_DEFAULT_CATEGORY_PREFIX)
                        ? category.substring(
                                  TracingPreferences.NON_DEFAULT_CATEGORY_PREFIX.length())
                        : category);
        preference.setChecked(mEnabledCategories.contains(category));
        preference.setPersistent(false); // We persist the preference value ourselves.
        preference.setOnPreferenceChangeListener(this);
        getPreferenceScreen().addPreference(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (boolean) newValue;
        if (value) {
            mEnabledCategories.add(preference.getKey());
        } else {
            mEnabledCategories.remove(preference.getKey());
        }
        TracingPreferences.setEnabledCategories(mType, mEnabledCategories);
        return true;
    }
}
