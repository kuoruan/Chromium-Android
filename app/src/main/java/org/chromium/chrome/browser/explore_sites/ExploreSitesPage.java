// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.native_page.BasicNativePage;
import org.chromium.chrome.browser.native_page.NativePageHost;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.Collections;
import java.util.List;

/**
 * Provides functionality when the user interacts with the explore sites page.
 */
public class ExploreSitesPage extends BasicNativePage {
    private ViewGroup mView;
    private String mTitle;
    private Activity mActivity;
    private List<ExploreSitesCategory> mCategoryData;
    private ExploreSitesPageLayout mLayout;

    /**
     * Create a new instance of the explore sites page.
     */
    public ExploreSitesPage(ChromeActivity activity, NativePageHost host) {
        super(activity, host);
        mCategoryData = Collections.emptyList();
    }

    @Override
    protected void initialize(ChromeActivity activity, NativePageHost host) {
        mActivity = activity;
        mTitle = mActivity.getString(R.string.explore_sites_title);
        mView = (ViewGroup) mActivity.getLayoutInflater().inflate(
                R.layout.explore_sites_main, null);

        Profile profile = host.getActiveTab().getProfile();
        ExploreSitesBridge.getEspCatalog(profile, this::translateToModel);
        mLayout = new ExploreSitesPageLayout(mView, profile);
        // TODO(chili): Set layout to be an observer of list model
    }

    private void translateToModel(List<ExploreSitesCategory> categoryList) {
        // TODO(chili): Call listmodel.addAll
    }

    @Override
    public String getHost() {
        return UrlConstants.EXPLORE_HOST;
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }
}
