// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataTab;

/**
 * A more advanced version of {@link ClearBrowsingDataPreferences} with more dialog options and less
 * explanatory text.
 */
public class ClearBrowsingDataPreferencesAdvanced extends ClearBrowsingDataPreferencesTab {
    // TODO(dullweber): Add more options.

    @Override
    protected int getPreferenceType() {
        return ClearBrowsingDataTab.ADVANCED;
    }
}
