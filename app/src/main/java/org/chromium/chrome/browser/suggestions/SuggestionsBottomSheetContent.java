// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;

import org.chromium.base.CollectionUtil;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.TouchEnabledDelegate;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.LoadingView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.content.browser.BrowserStartupController;

import java.util.List;

/**
 * Provides content to be displayed inside of the Home tab of bottom sheet.
 */
public class SuggestionsBottomSheetContent implements BottomSheet.BottomSheetContent {
    private final View mView;
    private final SuggestionsRecyclerView mRecyclerView;
    private final ChromeActivity mActivity;
    private final BottomSheet mSheet;

    private NewTabPageAdapter mAdapter;
    private ContextMenuManager mContextMenuManager;
    private SuggestionsUiDelegateImpl mSuggestionsUiDelegate;
    private TileGroup.Delegate mTileGroupDelegate;
    private SuggestionsSheetVisibilityChangeObserver mBottomSheetObserver;

    @Nullable
    private SuggestionsCarousel mSuggestionsCarousel;
    @Nullable
    private ContextualSuggestionsSection mContextualSuggestions;

    private boolean mSuggestionsInitialized;
    private boolean mIsDestroyed;

    public SuggestionsBottomSheetContent(final ChromeActivity activity, final BottomSheet sheet,
            TabModelSelector tabModelSelector, SnackbarManager snackbarManager) {
        mActivity = activity;
        mSheet = sheet;

        mView = LayoutInflater.from(activity).inflate(
                R.layout.suggestions_bottom_sheet_content, null);
        Resources resources = mView.getResources();
        int backgroundColor = SuggestionsConfig.getBackgroundColor(resources);
        mView.setBackgroundColor(backgroundColor);
        mRecyclerView = mView.findViewById(R.id.recycler_view);
        mRecyclerView.setBackgroundColor(backgroundColor);

        LoadingView loadingView = mView.findViewById(R.id.loading_view);

        if (mActivity.didFinishNativeInitialization()) {
            loadingView.setVisibility(View.GONE);
            initializeWithNative(tabModelSelector, snackbarManager);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            loadingView.showLoadingUI();
            // Only add a StartupCompletedObserver if native is not initialized to avoid
            // #initializeWithNative() being called twice.
            BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                    .addStartupCompletedObserver(new BrowserStartupController.StartupCallback() {
                        @Override
                        public void onSuccess(boolean alreadyStarted) {
                            // If this is destroyed before native initialization is finished, don't
                            // do anything. Otherwise this will be initialized based on out-of-date
                            // #mSheet and #mActivity, which causes a crash.
                            // See https://crbug.com/804296.
                            if (mIsDestroyed) return;

                            mRecyclerView.setVisibility(View.VISIBLE);
                            loadingView.hideLoadingUI();
                            initializeWithNative(tabModelSelector, snackbarManager);
                        }

                        @Override
                        public void onFailure() {}
                    });
        }
    }

    private void initializeWithNative(
            TabModelSelector tabModelSelector, SnackbarManager snackbarManager) {
        assert !mSuggestionsInitialized;
        mSuggestionsInitialized = true;

        SuggestionsDependencyFactory depsFactory = SuggestionsDependencyFactory.getInstance();
        Profile profile = Profile.getLastUsedProfile();
        SuggestionsNavigationDelegate navigationDelegate =
                new SuggestionsNavigationDelegateImpl(mActivity, profile, mSheet, tabModelSelector);

        mTileGroupDelegate =
                new TileGroupDelegateImpl(mActivity, profile, navigationDelegate, snackbarManager);
        mSuggestionsUiDelegate = new SuggestionsUiDelegateImpl(
                depsFactory.createSuggestionSource(profile), depsFactory.createEventReporter(),
                navigationDelegate, profile, mSheet, mActivity.getReferencePool(), snackbarManager);

        TouchEnabledDelegate touchEnabledDelegate = mActivity.getBottomSheet()::setTouchEnabled;
        mContextMenuManager = new ContextMenuManager(
                navigationDelegate, touchEnabledDelegate, mActivity::closeContextMenu);
        mActivity.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        mSuggestionsUiDelegate.addDestructionObserver(() -> {
            mActivity.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
        });

        UiConfig uiConfig = new UiConfig(mRecyclerView);
        mRecyclerView.init(uiConfig, mContextMenuManager);

        OfflinePageBridge offlinePageBridge = depsFactory.getOfflinePageBridge(profile);

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_CAROUSEL)) {
            mSuggestionsCarousel = new SuggestionsCarousel(
                    uiConfig, mSuggestionsUiDelegate, mContextMenuManager, offlinePageBridge);
        }

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_ABOVE_ARTICLES)) {
            mContextualSuggestions = new ContextualSuggestionsSection(mSuggestionsUiDelegate,
                    offlinePageBridge, mActivity, mActivity.getTabModelSelector());
        }

        mAdapter = new NewTabPageAdapter(mSuggestionsUiDelegate,
                /* aboveTheFoldView = */ null, /* logoView = */ null, uiConfig, offlinePageBridge,
                mContextMenuManager, mTileGroupDelegate, mSuggestionsCarousel,
                mContextualSuggestions);

        mBottomSheetObserver = new SuggestionsSheetVisibilityChangeObserver(this, mActivity) {
            @Override
            public void onContentShown(boolean isFirstShown) {
                // TODO(dgn): Temporary workaround to trigger an event in the backend when the
                // sheet is opened following inactivity. See https://crbug.com/760974. Should be
                // moved back to the "new opening of the sheet" path once we are able to trigger it
                // in that case.
                mSuggestionsUiDelegate.getEventReporter().onSurfaceOpened();
                SuggestionsMetrics.recordSurfaceVisible();

                if (isFirstShown) {
                    mAdapter.refreshSuggestions();

                    maybeUpdateContextualSuggestions();

                    // Set the adapter on the RecyclerView after updating it, to avoid sending
                    // notifications that might confuse its internal state.
                    // See https://crbug.com/756514.
                    mRecyclerView.setAdapter(mAdapter);
                    mRecyclerView.scrollToPosition(0);
                    mRecyclerView.getScrollEventReporter().reset();
                }
            }

            @Override
            public void onContentHidden() {
                SuggestionsMetrics.recordSurfaceHidden();
            }

            @Override
            public void onContentStateChanged(@BottomSheet.SheetState int contentState) {
                if (contentState == BottomSheet.SHEET_STATE_HALF) {
                    SuggestionsMetrics.recordSurfaceHalfVisible();
                    mRecyclerView.setScrollEnabled(false);
                } else if (contentState == BottomSheet.SHEET_STATE_FULL) {
                    SuggestionsMetrics.recordSurfaceFullyVisible();
                    mRecyclerView.setScrollEnabled(true);
                }
            }

            @Override
            public void onSheetClosed(@StateChangeReason int reason) {
                super.onSheetClosed(reason);

                if (ChromeFeatureList.isEnabled(
                            ChromeFeatureList.CHROME_HOME_DROP_ALL_BUT_FIRST_THUMBNAIL)) {
                    mAdapter.dropAllButFirstNArticleThumbnails(1);
                }
                mRecyclerView.setAdapter(null);
            }
        };
    }

    @Override
    public View getContentView() {
        return mView;
    }

    @Override
    public List<View> getViewsForPadding() {
        return CollectionUtil.newArrayList(mRecyclerView);
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    @Override
    public boolean isUsingLightToolbarTheme() {
        return false;
    }

    @Override
    public boolean isIncognitoThemedContent() {
        return false;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mRecyclerView.computeVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        mIsDestroyed = true;

        if (mSuggestionsInitialized) {
            mBottomSheetObserver.onDestroy();
            mSuggestionsUiDelegate.onDestroy();
            mTileGroupDelegate.destroy();

            if (mContextualSuggestions != null) mContextualSuggestions.destroy();
        }
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_SUGGESTIONS;
    }

    @Override
    public boolean applyDefaultTopPadding() {
        return false;
    }

    @Override
    public void scrollToTop() {
        mRecyclerView.smoothScrollToPosition(0);
    }

    private void maybeUpdateContextualSuggestions() {
        if (mSuggestionsCarousel == null && mContextualSuggestions == null) return;

        Tab activeTab = mSheet.getActiveTab();
        final String currentUrl = activeTab == null ? null : activeTab.getUrl();

        if (mSuggestionsCarousel != null) {
            assert ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_CAROUSEL);
            mSuggestionsCarousel.refresh(mSheet.getContext(), currentUrl);
        }

        if (mContextualSuggestions != null) {
            assert ChromeFeatureList.isEnabled(
                    ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_ABOVE_ARTICLES);
            mContextualSuggestions.setSectionVisiblity(true);
            mContextualSuggestions.refresh(currentUrl);
        }
    }
}
