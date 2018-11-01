// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home;

import android.app.Activity;
import android.content.Intent;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.download.home.filter.Filters;
import org.chromium.chrome.browser.download.home.filter.Filters.FilterType;
import org.chromium.chrome.browser.download.home.list.DateOrderedListCoordinator;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.snackbars.DeleteUndoCoordinator;
import org.chromium.chrome.browser.download.home.toolbar.DownloadHomeToolbar;
import org.chromium.chrome.browser.download.items.OfflineContentAggregatorFactory;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.download.DownloadPreferences;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;

import java.io.Closeable;

/**
 * The top level coordinator for the download home UI.  This is currently an in progress class and
 * is not fully fleshed out yet.
 */
class DownloadManagerCoordinatorImpl
        implements DownloadManagerCoordinator, Toolbar.OnMenuItemClickListener {
    private final ObserverList<Observer> mObservers = new ObserverList<>();
    private final DateOrderedListCoordinator mListCoordinator;
    private final DeleteUndoCoordinator mDeleteCoordinator;

    private SelectableListLayout<ListItem> mSelectableListLayout;
    private ViewGroup mMainView;
    private DownloadHomeToolbar mToolbar;
    private Activity mActivity;

    private boolean mMuteFilterChanges;
    private boolean mIsSeparateActivity;
    private int mSearchMenuId;

    private SelectionDelegate<ListItem> mSelectionDelegate;

    private SelectableListToolbar.SearchDelegate mSearchDelegate =
            new SelectableListToolbar.SearchDelegate() {
                @Override
                public void onSearchTextChanged(String query) {
                    mListCoordinator.setSearchQuery(query);
                }

                @Override
                public void onEndSearch() {
                    mSelectableListLayout.onEndSearch();
                    mListCoordinator.setSearchQuery(null);
                }
            };

    /** Builds a {@link DownloadManagerCoordinatorImpl} instance. */
    @SuppressWarnings({"unchecked"}) // mSelectableListLayout
    public DownloadManagerCoordinatorImpl(Profile profile, Activity activity, boolean offTheRecord,
            boolean isSeparateActivity, SnackbarManager snackbarManager) {
        mActivity = activity;
        mDeleteCoordinator = new DeleteUndoCoordinator(snackbarManager);
        mSelectionDelegate = new SelectionDelegate<ListItem>();
        mListCoordinator = new DateOrderedListCoordinator(mActivity, offTheRecord,
                OfflineContentAggregatorFactory.forProfile(profile),
                mDeleteCoordinator::showSnackbar, mSelectionDelegate, this ::notifyFilterChanged);

        mMainView =
                (ViewGroup) LayoutInflater.from(mActivity).inflate(R.layout.download_main, null);
        mSelectableListLayout =
                (SelectableListLayout<ListItem>) mMainView.findViewById(R.id.selectable_list);

        // TODO(shaktisahu): Maybe refactor SelectableListLayout to work without supplying empty
        // view.
        mSelectableListLayout.initializeEmptyView(
                VectorDrawableCompat.create(
                        mActivity.getResources(), R.drawable.downloads_big, mActivity.getTheme()),
                R.string.download_manager_ui_empty, R.string.download_manager_no_results);

        RecyclerView recyclerView = (RecyclerView) mListCoordinator.getView();
        mSelectableListLayout.initializeRecyclerView(recyclerView.getAdapter(), recyclerView);

        boolean isLocationEnabled =
                ChromeFeatureList.isEnabled(ChromeFeatureList.DOWNLOADS_LOCATION_CHANGE);
        int normalGroupId =
                isLocationEnabled ? R.id.with_settings_normal_menu_group : R.id.normal_menu_group;
        mSearchMenuId = isLocationEnabled ? R.id.with_settings_search_menu_id : R.id.search_menu_id;

        mToolbar = (DownloadHomeToolbar) mSelectableListLayout.initializeToolbar(
                R.layout.download_home_toolbar, mSelectionDelegate, 0, null, normalGroupId,
                R.id.selection_mode_menu_group, R.color.modern_primary_color, this, true,
                isSeparateActivity);
        mToolbar.getMenu().setGroupVisible(normalGroupId, true);
        mToolbar.initializeSearchView(
                mSearchDelegate, R.string.download_manager_search, mSearchMenuId);

        mIsSeparateActivity = isSeparateActivity;
        if (!mIsSeparateActivity) mToolbar.removeCloseButton();

        RecordUserAction.record("Android.DownloadManager.Open");
    }

    // DownloadManagerCoordinator implementation.
    @Override
    public void destroy() {
        mDeleteCoordinator.destroy();
        mListCoordinator.destroy();
    }

    @Override
    public View getView() {
        return mMainView;
    }

    @Override
    public boolean onBackPressed() {
        // TODO(dtrainor): Clear selection if multi-select is supported.
        return false;
    }

    @Override
    public void updateForUrl(String url) {
        try (FilterChangeBlock block = new FilterChangeBlock()) {
            mListCoordinator.setSelectedFilter(Filters.fromUrl(url));
        }
    }

    @Override
    public void showPrefetchSection() {
        updateForUrl(Filters.toUrl(Filters.FilterType.PREFETCHED));
    }

    @Override
    public void addObserver(Observer observer) {
        mObservers.addObserver(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        mObservers.removeObserver(observer);
    }

    private void notifyFilterChanged(@FilterType int filter) {
        mSelectionDelegate.clearSelection();
        if (mMuteFilterChanges) return;

        String url = Filters.toUrl(filter);
        for (Observer observer : mObservers) observer.onUrlChanged(url);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if ((item.getItemId() == R.id.close_menu_id
                    || item.getItemId() == R.id.with_settings_close_menu_id)
                && mIsSeparateActivity) {
            DownloadManagerUi.recordMenuActionHistogram(DownloadManagerUi.MenuAction.CLOSE);
            mActivity.finish();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_delete_menu_id) {
            DownloadManagerUi.recordMenuActionHistogram(DownloadManagerUi.MenuAction.MULTI_DELETE);
            RecordHistogram.recordCount100Histogram(
                    "Android.DownloadManager.Menu.Delete.SelectedCount",
                    mSelectionDelegate.getSelectedItems().size());
            mListCoordinator.onDeletionRequested(mSelectionDelegate.getSelectedItemsAsList());
            mSelectionDelegate.clearSelection();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_share_menu_id) {
            // TODO(twellington): ideally the intent chooser would be started with
            //                    startActivityForResult() and the selection would only be cleared
            //                    after receiving an OK response. See https://crbug.com/638916.

            DownloadManagerUi.recordMenuActionHistogram(DownloadManagerUi.MenuAction.MULTI_SHARE);
            RecordHistogram.recordCount100Histogram(
                    "Android.DownloadManager.Menu.Share.SelectedCount",
                    mSelectionDelegate.getSelectedItems().size());

            // TODO(shaktisahu): Share selected items.
            mSelectionDelegate.clearSelection();
            return true;
        } else if (item.getItemId() == mSearchMenuId) {
            // The header should be removed as soon as a search is started. Also it should be added
            // back when the search is ended.
            // TODO(shaktisahu): Check with UX and remove header.
            mSelectableListLayout.onStartSearch();
            mToolbar.showSearchView();
            DownloadManagerUi.recordMenuActionHistogram(DownloadManagerUi.MenuAction.SEARCH);
            RecordUserAction.record("Android.DownloadManager.Search");
            return true;
        } else if (item.getItemId() == R.id.settings_menu_id) {
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                    mActivity, DownloadPreferences.class.getName());
            mActivity.startActivity(intent);
            RecordUserAction.record("Android.DownloadManager.Settings");
            return true;
        }
        return false;
    }

    /**
     * Helper class to mute state changes when processing a state change request from an external
     * source.
     */
    private class FilterChangeBlock implements Closeable {
        private final boolean mOriginalMuteFilterChanges;

        public FilterChangeBlock() {
            mOriginalMuteFilterChanges = mMuteFilterChanges;
            mMuteFilterChanges = true;
        }

        @Override
        public void close() {
            mMuteFilterChanges = mOriginalMuteFilterChanges;
        }
    }
}
