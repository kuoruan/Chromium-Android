// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.searchwidget;

import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.omnibox.LocationBarLayout;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;

class SearchBoxDataProvider implements ToolbarDataProvider, TemplateUrlService.LoadListener {
    private Tab mTab;
    private String mVerbatimUrl;

    /**
     * Called when native library is loaded and a tab has been initialized.
     * @param tab The tab to use.
     */
    public void onNativeLibraryReady(Tab tab) {
        assert LibraryLoader.isInitialized();

        mTab = tab;

        TemplateUrlService service = TemplateUrlService.getInstance();
        service.registerLoadListener(this);
        service.load();
    }

    @Override
    public void onTemplateUrlServiceLoaded() {
        // For zero suggest, the default search engine's URL is used as the first suggestion.
        TemplateUrlService service = TemplateUrlService.getInstance();
        String searchEngineUrl = service.getSearchEngineUrlFromTemplateUrl(
                service.getDefaultSearchEngineTemplateUrl().getKeyword());
        mVerbatimUrl = LocationBarLayout.splitPathFromUrlDisplayText(searchEngineUrl).first;
    }

    @Override
    public boolean isUsingBrandColor() {
        return false;
    }

    @Override
    public boolean isIncognito() {
        if (mTab == null) return false;
        return mTab.isIncognito();
    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public Tab getTab() {
        return mTab;
    }

    @Override
    public int getPrimaryColor() {
        return 0;
    }

    @Override
    public NewTabPage getNewTabPageForCurrentTab() {
        return null;
    }

    @Override
    public String getCurrentUrl() {
        return mVerbatimUrl;
    }
}
