// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.BottomSheet;
import org.chromium.chrome.browser.widget.EmptyBottomSheetObserver;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

/**
 * Provides content to be displayed inside of the Home tab of bottom sheet.
 *
 * TODO(dgn): If the bottom sheet view is not recreated across tab changes, it will have to be
 * notified of it, at least when it is pulled up on the new tab.
 */
public class SuggestionsBottomSheetContent implements BottomSheet.BottomSheetContent {
    private static SuggestionsSource sSuggestionsSourceForTesting;
    private static SuggestionsMetricsReporter sMetricsReporterForTesting;

    private final NewTabPageRecyclerView mRecyclerView;
    private final ContextMenuManager mContextMenuManager;
    private final SuggestionsUiDelegateImpl mSuggestionsManager;
    private final TileGroup.Delegate mTileGroupDelegate;

    public SuggestionsBottomSheetContent(
            final ChromeActivity activity, NativePageHost host, TabModelSelector tabModelSelector) {

        Profile profile = Profile.getLastUsedProfile();
        SuggestionsNavigationDelegate navigationDelegate =
                new SuggestionsNavigationDelegateImpl(activity, profile, host, tabModelSelector);
        mTileGroupDelegate =
                new TileGroupDelegateImpl(activity, profile, tabModelSelector, navigationDelegate);
        mSuggestionsManager = createSuggestionsDelegate(profile, navigationDelegate, host);

        mRecyclerView = (NewTabPageRecyclerView) LayoutInflater.from(activity).inflate(
                R.layout.new_tab_page_recycler_view, null, false);
        mContextMenuManager = new ContextMenuManager(activity, navigationDelegate, mRecyclerView);
        activity.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        mSuggestionsManager.addDestructionObserver(new DestructionObserver() {
            @Override
            public void onDestroy() {
                activity.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
            }
        });

        UiConfig uiConfig = new UiConfig(mRecyclerView);

        // This mAdapter does not fetch until later requested, when the sheet is opened.
        final NewTabPageAdapter adapter = new NewTabPageAdapter(mSuggestionsManager,
                /* aboveTheFoldView = */ null, uiConfig, OfflinePageBridge.getForProfile(profile),
                mContextMenuManager, mTileGroupDelegate);
        mRecyclerView.setAdapter(adapter);

        activity.getBottomSheet().addObserver(new EmptyBottomSheetObserver() {
            @Override
            public void onSheetOpened() {
                adapter.refreshSuggestions();
            }
        });
    }

    @Override
    public RecyclerView getScrollingContentView() {
        return mRecyclerView;
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    public ContextMenuManager getContextMenuManager() {
        return mContextMenuManager;
    }

    public void destroy() {
        mSuggestionsManager.onDestroy();
        mTileGroupDelegate.destroy();
    }

    public static void setSuggestionsSourceForTesting(SuggestionsSource suggestionsSource) {
        sSuggestionsSourceForTesting = suggestionsSource;
    }

    public static void setMetricsReporterForTesting(SuggestionsMetricsReporter metricsReporter) {
        sMetricsReporterForTesting = metricsReporter;
    }

    private static SuggestionsUiDelegateImpl createSuggestionsDelegate(Profile profile,
            SuggestionsNavigationDelegate navigationDelegate, NativePageHost host) {
        SnippetsBridge snippetsBridge = null;
        SuggestionsSource suggestionsSource;
        SuggestionsMetricsReporter metricsReporter;

        if (sSuggestionsSourceForTesting == null) {
            snippetsBridge = new SnippetsBridge(profile);
            suggestionsSource = snippetsBridge;
        } else {
            suggestionsSource = sSuggestionsSourceForTesting;
        }

        if (sMetricsReporterForTesting == null) {
            if (snippetsBridge == null) snippetsBridge = new SnippetsBridge(profile);
            metricsReporter = snippetsBridge;
        } else {
            metricsReporter = sMetricsReporterForTesting;
        }

        SuggestionsUiDelegateImpl delegate = new SuggestionsUiDelegateImpl(
                suggestionsSource, metricsReporter, navigationDelegate, profile, host);
        if (snippetsBridge != null) delegate.addDestructionObserver(snippetsBridge);

        return delegate;
    }
}
