// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.annotation.SuppressLint;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.TouchEnabledDelegate;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;
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

    private final View mView;
    private final FadingShadowView mShadowView;
    private final SuggestionsRecyclerView mRecyclerView;
    private final ContextMenuManager mContextMenuManager;
    private final SuggestionsUiDelegateImpl mSuggestionsManager;
    private final TileGroup.Delegate mTileGroupDelegate;

    public SuggestionsBottomSheetContent(final ChromeActivity activity, final BottomSheet sheet,
            TabModelSelector tabModelSelector, SnackbarManager snackbarManager) {
        Profile profile = Profile.getLastUsedProfile();
        SuggestionsNavigationDelegate navigationDelegate =
                new SuggestionsNavigationDelegateImpl(activity, profile, sheet, tabModelSelector);
        mTileGroupDelegate = new TileGroupDelegateImpl(
                activity, profile, tabModelSelector, navigationDelegate, snackbarManager);
        mSuggestionsManager = createSuggestionsDelegate(profile, navigationDelegate, sheet);

        mView = LayoutInflater.from(activity).inflate(
                R.layout.suggestions_bottom_sheet_content, null);
        mRecyclerView = (SuggestionsRecyclerView) mView.findViewById(R.id.recycler_view);

        TouchEnabledDelegate touchEnabledDelegate = new TouchEnabledDelegate() {
            @Override
            public void setTouchEnabled(boolean enabled) {
                activity.getBottomSheet().setTouchEnabled(enabled);
            }
        };
        mContextMenuManager =
                new ContextMenuManager(activity, navigationDelegate, touchEnabledDelegate);
        activity.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        mSuggestionsManager.addDestructionObserver(new DestructionObserver() {
            @Override
            public void onDestroy() {
                activity.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
            }
        });

        UiConfig uiConfig = new UiConfig(mRecyclerView);

        final NewTabPageAdapter adapter = new NewTabPageAdapter(mSuggestionsManager,
                /* aboveTheFoldView = */ null, uiConfig, OfflinePageBridge.getForProfile(profile),
                mContextMenuManager, mTileGroupDelegate);
        mRecyclerView.init(uiConfig, mContextMenuManager, adapter);

        final SuggestionsSource suggestionsSource = mSuggestionsManager.getSuggestionsSource();
        activity.getBottomSheet().addObserver(new EmptyBottomSheetObserver() {
            @Override
            public void onSheetOpened() {
                mRecyclerView.scrollTo(0, 0);

                // TODO(https://crbug.com/689962) Ensure this call does not discard all suggestions
                // every time the sheet is opened.
                adapter.refreshSuggestions();
                suggestionsSource.onNtpInitialized();
            }
        });
        adapter.refreshSuggestions();
        suggestionsSource.onNtpInitialized();

        mShadowView = (FadingShadowView) mView.findViewById(R.id.shadow);
        mShadowView.init(
                ApiCompatibilityUtils.getColor(mView.getResources(), R.color.toolbar_shadow_color),
                FadingShadow.POSITION_TOP);

        mRecyclerView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                boolean shadowVisible = mRecyclerView.canScrollVertically(-1);
                mShadowView.setVisibility(shadowVisible ? View.VISIBLE : View.GONE);
            }
        });

        final LocationBar locationBar = (LocationBar) sheet.findViewById(R.id.location_bar);
        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (locationBar != null && locationBar.isUrlBarFocused()) {
                    locationBar.setUrlBarFocus(false);
                }

                // Never intercept the touch event.
                return false;
            }
        });
    }

    @Override
    public View getContentView() {
        return mView;
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mRecyclerView.computeVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        mSuggestionsManager.onDestroy();
        mTileGroupDelegate.destroy();
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_SUGGESTIONS;
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
