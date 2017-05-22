// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

/**
 * A simpler version of {@link ClearBrowsingDataPreferences} with fewer dialog options and more
 * explanatory text.
 */
public class ClearBrowsingDataPreferencesBasic extends ClearBrowsingDataPreferencesTab {
    @Override
    protected DialogOption[] getDialogOptions() {
        return new DialogOption[] {
                DialogOption.CLEAR_HISTORY, DialogOption.CLEAR_COOKIES_AND_SITE_DATA,
                DialogOption.CLEAR_CACHE
        };
    }
}
