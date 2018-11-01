// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.view.View;

import org.chromium.chrome.browser.profiles.Profile;

/**
 * Class to manage layout of explore sites page.
 */
public class ExploreSitesPageLayout {
    private View mParent;
    private Profile mProfile;

    public ExploreSitesPageLayout(View parent, Profile profile) {
        mParent = parent;
        mProfile = profile;
    }
}
