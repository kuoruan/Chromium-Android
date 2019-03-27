// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeBasePreference;

/**
 * Custom preference to enable programmatically overriding the Preference's title string.
 */
public class DataReductionPreference extends ChromeBasePreference {
    /**
     * Constructor for inflating from XML.
     */
    public DataReductionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setTitle(DataReductionBrandingResourceProvider.getDataSaverBrandedString(
                R.string.data_reduction_title));
    }
}
