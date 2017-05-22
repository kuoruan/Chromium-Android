// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Browser;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInStateObserver;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar.SearchDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.List;

/**
 * Displays and manages the UI for browsing history.
 */
public class HistoryManager implements OnMenuItemClickListener, SignInStateObserver,
                                       SelectionObserver<HistoryItem>, SearchDelegate,
                                       SnackbarController {
    private static final int FAVICON_MAX_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int MEGABYTES_TO_BYTES =  1024 * 1024;
    private static final String METRICS_PREFIX = "Android.HistoryPage.";

    private static HistoryProvider sProviderForTests;

    private final int mListItemLateralShadowSizePx;

    private final Activity mActivity;
    private final boolean mIsDisplayedInNativePage;
    private final SelectableListLayout<HistoryItem> mSelectableListLayout;
    private final HistoryAdapter mHistoryAdapter;
    private final SelectionDelegate<HistoryItem> mSelectionDelegate;
    private final HistoryManagerToolbar mToolbar;
    private final TextView mEmptyView;
    private final RecyclerView mRecyclerView;
    private LargeIconBridge mLargeIconBridge;

    private boolean mIsSearching;

    /**
     * Creates a new HistoryManager.
     * @param activity The Activity associated with the HistoryManager.
     */
    @SuppressWarnings("unchecked")  // mSelectableListLayout
    public HistoryManager(Activity activity, @Nullable NativePage nativePage) {
        mActivity = activity;
        mIsDisplayedInNativePage = nativePage != null;

        mSelectionDelegate = new SelectionDelegate<>();
        mSelectionDelegate.addObserver(this);
        mHistoryAdapter = new HistoryAdapter(mSelectionDelegate, this,
                sProviderForTests != null ? sProviderForTests : new BrowsingHistoryBridge());

        // 1. Create SelectableListLayout.
        mSelectableListLayout =
                (SelectableListLayout<HistoryItem>) LayoutInflater.from(activity).inflate(
                        R.layout.history_main, null);

        // 2. Initialize RecyclerView.
        mRecyclerView = mSelectableListLayout.initializeRecyclerView(mHistoryAdapter);

        // 3. Initialize toolbar.
        mToolbar = (HistoryManagerToolbar) mSelectableListLayout.initializeToolbar(
                R.layout.history_toolbar, mSelectionDelegate, R.string.menu_history, null,
                R.id.normal_menu_group, R.id.selection_mode_menu_group,
                R.color.default_primary_color, false, this);
        mToolbar.setManager(this);
        mToolbar.initializeSearchView(this, R.string.history_manager_search, R.id.search_menu_id);

        // 4. Configure values for HorizontalDisplayStyle.WIDE and HorizontalDisplayStyle.REGULAR.
        // The list item shadow is part of the drawable nine-patch used as the list item background.
        // Use the dimensions of the shadow (from the drawable's padding) to calculate the margins
        // to use in the regular and wide display styles.
        Rect listItemShadow = new Rect();
        ApiCompatibilityUtils.getDrawable(
                mActivity.getResources(), R.drawable.card_middle).getPadding(listItemShadow);

        assert listItemShadow.left == listItemShadow.right;
        // The list item shadow size is used in HorizontalDisplayStyle.WIDE to visually align other
        // elements with the edge of the list items.
        mListItemLateralShadowSizePx = listItemShadow.left;

        mSelectableListLayout.setHasWideDisplayStyle(mListItemLateralShadowSizePx);

        // 5. Initialize empty view.
        mEmptyView = mSelectableListLayout.initializeEmptyView(
                VectorDrawableCompat.create(
                        mActivity.getResources(), R.drawable.history_big, mActivity.getTheme()),
                R.string.history_manager_empty, R.string.history_manager_no_results);
        // TODO(twellington): remove this after unifying bookmarks and downloads UI with history.
        mEmptyView.setTextColor(ApiCompatibilityUtils.getColor(mActivity.getResources(),
                R.color.google_grey_500));

        // 6. Create large icon bridge.
        mLargeIconBridge = new LargeIconBridge(Profile.getLastUsedProfile().getOriginalProfile());
        ActivityManager activityManager = ((ActivityManager) ContextUtils
                .getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE));
        int maxSize = Math.min((activityManager.getMemoryClass() / 4) * MEGABYTES_TO_BYTES,
                FAVICON_MAX_CACHE_SIZE_BYTES);
        mLargeIconBridge.createCache(maxSize);

        // 7. Initialize the adapter to load items.
        mHistoryAdapter.initialize();

        // 8. Add scroll listener to page in more items when necessary.
        mRecyclerView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!mHistoryAdapter.canLoadMoreItems()) return;

                // Load more items if the scroll position is close to the bottom of the list.
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager.findLastVisibleItemPosition()
                        > (mHistoryAdapter.getItemCount() - 25)) {
                    mHistoryAdapter.loadMoreItems();
                    recordUserActionWithOptionalSearch("LoadMoreOnScroll");
                }
            }});

        // 9. Listen to changes in sign in state.
        SigninManager.get(mActivity).addSignInStateObserver(this);

        recordUserAction("Show");
    }

    /**
     * @return Whether the history manager UI is displayed in a native page.
     */
    public boolean isDisplayedInNativePage() {
        return mIsDisplayedInNativePage;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        mToolbar.hideOverflowMenu();

        if (item.getItemId() == R.id.close_menu_id && !isDisplayedInNativePage()) {
            mActivity.finish();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_open_in_new_tab) {
            openItemsInNewTabs(mSelectionDelegate.getSelectedItems(), false);
            mSelectionDelegate.clearSelection();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_copy_link) {
            recordUserActionWithOptionalSearch("CopyLink");
            Clipboard clipboard = new Clipboard(mActivity);
            clipboard.setText(mSelectionDelegate.getSelectedItems().get(0).getUrl());
            mSelectionDelegate.clearSelection();
            Snackbar snackbar = Snackbar.make(mActivity.getString(R.string.copied), this,
                    Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_HISTORY_LINK_COPIED);
            ((SnackbarManageable) mActivity).getSnackbarManager().showSnackbar(snackbar);
            return true;
        } else if (item.getItemId() == R.id.selection_mode_open_in_incognito) {
            openItemsInNewTabs(mSelectionDelegate.getSelectedItems(), true);
            mSelectionDelegate.clearSelection();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_delete_menu_id) {
            recordSelectionCountHistorgram("Remove");
            recordUserActionWithOptionalSearch("RemoveSelected");

            for (HistoryItem historyItem : mSelectionDelegate.getSelectedItems()) {
                mHistoryAdapter.markItemForRemoval(historyItem);
            }
            mHistoryAdapter.removeItems();
            mSelectionDelegate.clearSelection();
            return true;
        } else if (item.getItemId() == R.id.search_menu_id) {
            mHistoryAdapter.removeHeader();
            mToolbar.showSearchView();
            mSelectableListLayout.onStartSearch();
            recordUserAction("Search");
            mIsSearching = true;
            return true;
        }
        return false;
    }

    /**
     * @return The view that shows the main browsing history UI.
     */
    public ViewGroup getView() {
        return mSelectableListLayout;
    }

    /**
     * Called when the activity/native page is destroyed.
     */
    public void onDestroyed() {
        mSelectableListLayout.onDestroyed();
        mHistoryAdapter.onDestroyed();
        mLargeIconBridge.destroy();
        mLargeIconBridge = null;
        SigninManager.get(mActivity).removeSignInStateObserver(this);
    }

    /**
     * Removes the HistoryItem from the history backend and the HistoryAdapter.
     * @param item The HistoryItem to remove.
     */
    public void removeItem(HistoryItem item) {
        if (mSelectionDelegate.isItemSelected(item)) {
            mSelectionDelegate.toggleSelectionForItem(item);
        }
        mHistoryAdapter.markItemForRemoval(item);
        mHistoryAdapter.removeItems();
    }

    /**
     * Open the provided url.
     * @param url The url to open.
     * @param isIncognito Whether to open the url in an incognito tab. If null, the tab
     *                    will open in the current tab model.
     * @param createNewTab Whether a new tab should be created. If false, the item will clobber the
     *                     the current tab.
     */
    public void openUrl(String url, Boolean isIncognito, boolean createNewTab) {
        IntentHandler.startActivityForTrustedIntent(
                getOpenUrlIntent(url, isIncognito, createNewTab));
    }

    @VisibleForTesting
    Intent getOpenUrlIntent(String url, Boolean isIncognito, boolean createNewTab) {
        // Construct basic intent.
        Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        viewIntent.putExtra(Browser.EXTRA_APPLICATION_ID,
                mActivity.getApplicationContext().getPackageName());
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Determine component or class name.
        ComponentName component;
        if (DeviceFormFactor.isTablet(mActivity)) {
            component = mActivity.getComponentName();
        } else {
            component = IntentUtils.safeGetParcelableExtra(
                    mActivity.getIntent(), IntentHandler.EXTRA_PARENT_COMPONENT);
        }
        if (component != null) {
            viewIntent.setComponent(component);
        } else {
            viewIntent.setClass(mActivity, ChromeLauncherActivity.class);
        }

        // Set other intent extras.
        if (isIncognito != null) {
            viewIntent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, isIncognito);
        }
        if (createNewTab) viewIntent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);

        return viewIntent;
    }

    /**
     * Opens the clear browsing data preference.
     */
    public void openClearBrowsingDataPreference() {
        recordUserAction("ClearBrowsingData");
        Intent intent = PreferencesLauncher.createIntentForClearBrowsingDataPage(mActivity);
        IntentUtils.safeStartActivity(mActivity, intent);
    }

    @Override
    public void onSearchTextChanged(String query) {
        mHistoryAdapter.search(query);
    }

    @Override
    public void onEndSearch() {
        mHistoryAdapter.onEndSearch();
        mSelectableListLayout.onEndSearch();
        mIsSearching = false;
    }

    /**
     * @return The {@link LargeIconBridge} used to fetch large favicons.
     */
    public LargeIconBridge getLargeIconBridge() {
        return mLargeIconBridge;
    }

    /**
     * @return The SelectableListLayout that displays HistoryItems.
     */
    public SelectableListLayout<HistoryItem> getSelectableListLayout() {
        return mSelectableListLayout;
    }

    /**
     * @return The px size of the lateral shadow in the 9-patch used for the list item background.
     *         This value should be used in the regular horizontal display style to visually align
     *         elements with the edge of the list items.
     * @see org.chromium.chrome.browser.widget.displaystyle.HorizontalDisplayStyle#REGULAR
     */
    public int getListItemLateralShadowSizePx() {
        return mListItemLateralShadowSizePx;
    }

    private void openItemsInNewTabs(List<HistoryItem> items, boolean isIncognito) {
        recordSelectionCountHistorgram("Open");
        recordUserActionWithOptionalSearch("OpenSelected" + (isIncognito ? "Incognito" : ""));

        for (HistoryItem item : items) {
            openUrl(item.getUrl(), isIncognito, true);
        }
    }

    /**
     * Sets a {@link HistoryProvider} that is used in place of a real one.
     */
    @VisibleForTesting
    public static void setProviderForTests(HistoryProvider provider) {
        sProviderForTests = provider;
    }

    @VisibleForTesting
    SelectionDelegate<HistoryItem> getSelectionDelegateForTests() {
        return mSelectionDelegate;
    }

    @VisibleForTesting
    HistoryManagerToolbar getToolbarForTests() {
        return mToolbar;
    }

    @VisibleForTesting
    TextView getEmptyViewForTests() {
        return mEmptyView;
    }

    @VisibleForTesting
    HistoryAdapter getAdapterForTests() {
        return mHistoryAdapter;
    }

    /**
     * @param action The user action string to record.
     */
    static void recordUserAction(String action) {
        RecordUserAction.record(METRICS_PREFIX + action);
    }

    /**
     * Records the user action with "Search" prepended if the user is currently searching.
     * @param action The user action string to record.
     */
    void recordUserActionWithOptionalSearch(String action) {
        recordUserAction((mIsSearching ? "Search." : "") + action);
    }

    /**
     * Records the number of selected items when a multi-select action is performed.
     * @param action The multi-select action that was performed.
     */
    private void recordSelectionCountHistorgram(String action) {
        List<HistoryItem> selectedItems = mSelectionDelegate.getSelectedItems();
        RecordHistogram.recordCount100Histogram(
                METRICS_PREFIX + action + "Selected", selectedItems.size());
    }

    @Override
    public void onSignedIn() {
        mToolbar.onSignInStateChange();
        mHistoryAdapter.onSignInStateChange();
    }

    @Override
    public void onSignedOut() {
        mToolbar.onSignInStateChange();
        mHistoryAdapter.onSignInStateChange();
    }

    @Override
    public void onSelectionStateChange(List<HistoryItem> selectedItems) {
        mHistoryAdapter.onSelectionStateChange(mSelectionDelegate.isSelectionEnabled());
    }

    @Override
    public void onAction(Object actionData) {
        // Handler for the link copied snackbar. Do nothing.
    }

    @Override
    public void onDismissNoAction(Object actionData) {
        // Handler for the link copied snackbar. Do nothing.
    }
}
