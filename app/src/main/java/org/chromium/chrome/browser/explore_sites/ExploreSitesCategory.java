// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * An object representing a category in Explore Sites.
 */
public class ExploreSitesCategory {
    private String mCategoryTitle;
    // Populated only in NTP.
    private Bitmap mIcon;
    // Populated only for ESP.
    private List<ExploreSitesSite> mSites;

    public ExploreSitesCategory(String title) {
        mCategoryTitle = title;
        mSites = new ArrayList<>();
    }

    public void setIcon(Bitmap icon) {
        mIcon = icon;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void addSite(String title, String url) {
        mSites.add(new ExploreSitesSite(title, url));
    }

    public String getTitle() {
        return mCategoryTitle;
    }

    public List<ExploreSitesSite> getSites() {
        return mSites;
    }
}
