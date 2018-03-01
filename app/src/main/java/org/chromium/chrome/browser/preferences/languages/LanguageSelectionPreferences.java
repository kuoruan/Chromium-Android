// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.languages;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;

/**
 * Language selection fragment that lists all languages Chrome supports and allows users to
 * add one of them into their preferred language list.
 */
public class LanguageSelectionPreferences extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_add_language);
    }
}
