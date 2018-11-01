// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.graphics.Bitmap;

/**
 * An object encapsulating info for a website.
 */
public class ExploreSitesSite {
    private String mSiteTitle;
    private Bitmap mIcon;
    private String mUrl;

    public ExploreSitesSite(String title, String url) {
        mSiteTitle = title;
        mUrl = url;
    }

    public void setIcon(Bitmap icon) {
        mIcon = icon;
    }

    public String getTitle() {
        return mSiteTitle;
    }

    public String getUrl() {
        return mUrl;
    }

    public Bitmap getIcon() {
        return mIcon;
    }
}
