// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.URLUtil;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.cards.InnerNode;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.ntp.cards.SuggestionsSection;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsCardLayout;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.util.UrlUtilities;

/**
 * Displays a {@link SuggestionsSection} containing contextual article suggestions.
 */
public class ContextualSuggestionsSection extends InnerNode implements SuggestionsSection.Delegate {
    private final SuggestionsUiDelegate mUiDelegate;
    private final SuggestionsSection mSection;
    private final TabModelSelectorTabModelObserver mTabModelObserver;
    private final TabObserver mTabObserver;

    private Tab mLastTab;

    @Nullable
    private String mCurrentContextUrl;

    public ContextualSuggestionsSection(SuggestionsUiDelegate uiDelegate,
            OfflinePageBridge offlinePageBridge, Context context,
            TabModelSelector tabModelSelector) {
        mUiDelegate = uiDelegate;

        SuggestionsCategoryInfo info = new SuggestionsCategoryInfo(KnownCategories.CONTEXTUAL,
                context.getString(R.string.contextual_suggestions_title),
                ContentSuggestionsCardLayout.FULL_CARD, ContentSuggestionsAdditionalAction.NONE,
                true, "");
        mSection = new SuggestionsSection(
                this, mUiDelegate, mUiDelegate.getSuggestionsRanker(), offlinePageBridge, info);
        mUiDelegate.getSuggestionsRanker().registerCategory(KnownCategories.CONTEXTUAL);
        addChild(mSection);

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onUpdateUrl(Tab tab, String url) {
                refresh(url);
            }
        };

        mTabModelObserver = new TabModelSelectorTabModelObserver(tabModelSelector) {
            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                updateCurrentTab(tab);
            }
        };

        updateCurrentTab(tabModelSelector.getCurrentTab());
    }

    /** Destroys the section. */
    public void destroy() {
        if (mLastTab != null) {
            mLastTab.removeObserver(mTabObserver);
            mLastTab = null;
        }
        mTabModelObserver.destroy();
    }

    /**
     * Fetches new suggestions if the context URL was changed from the last time that the carousel
     * was shown.
     */
    public void refresh(@Nullable final String newUrl) {
        if (!URLUtil.isNetworkUrl(newUrl)) {
            clearSuggestions();
            return;
        }

        // Do nothing if there are already suggestions in the suggestions list for the new context.
        if (isContextTheSame(newUrl)) return;

        mSection.setStatus(CategoryStatus.INITIALIZING);

        // Context has changed, so we want to remove any old suggestions from the section.
        clearSuggestions();
        mCurrentContextUrl = newUrl;

        mUiDelegate.getSuggestionsSource().fetchContextualSuggestions(newUrl, (suggestions) -> {
            // Avoiding double fetches causing suggestions for incorrect context.
            if (!TextUtils.equals(newUrl, mCurrentContextUrl)) return;

            if (suggestions.size() > 0) {
                mSection.appendSuggestions(suggestions, false, false);
            }

            mSection.setStatus(CategoryStatus.AVAILABLE);
        });
    }

    /**
     * @param visible Whether the section should be visible. If false, all suggestions are cleared.
     */
    public void setSectionVisiblity(boolean visible) {
        mSection.setHeaderVisibility(visible);
        if (!visible) clearSuggestions();
    }

    private boolean isContextTheSame(String newUrl) {
        return UrlUtilities.urlsMatchIgnoringFragments(newUrl, mCurrentContextUrl);
    }

    /**
     * Removes any suggestions that might be present in the section.
     */
    private void clearSuggestions() {
        mCurrentContextUrl = null;
        mSection.clearData();
    }

    /**
     * Update the current tab and refresh suggestions.
     * @param tab The current {@link Tab}.
     */
    private void updateCurrentTab(Tab tab) {
        if (mLastTab != null) mLastTab.removeObserver(mTabObserver);

        mLastTab = tab;
        if (mLastTab == null) return;

        mLastTab.addObserver(mTabObserver);
        refresh(mLastTab.getUrl());
    }

    @Override
    public void dismissSection(SuggestionsSection section) {
        // This section should not be dismissed.
        assert false;
    }

    @Override
    public boolean isResetAllowed() {
        return false;
    }
}
