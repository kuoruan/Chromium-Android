// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.List;

/**
 * Handles toolbar functionality for the {@link DownloadManagerUi}.
 */
public class DownloadManagerToolbar extends SelectableListToolbar<DownloadHistoryItemWrapper>
        implements DownloadUiObserver {
    private int mFilter = DownloadFilter.FILTER_ALL;

    public DownloadManagerToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflateMenu(R.menu.download_manager_menu);
    }

    @Override
    public void initialize(SelectionDelegate<DownloadHistoryItemWrapper> delegate, int titleResId,
            @Nullable DrawerLayout drawerLayout, int normalGroupResId, int selectedGroupResId,
            @Nullable Integer normalBackgroundColorResId) {
        if (DeviceFormFactor.isTablet(getContext())) {
            getMenu().removeItem(R.id.close_menu_id);
        }

        super.initialize(delegate, titleResId, drawerLayout, normalGroupResId, selectedGroupResId,
                normalBackgroundColorResId);
    }

    @Override
    public void onFilterChanged(int filter) {
        mFilter = filter;
        if (!mIsSelectionEnabled) updateTitle();
    }

    @Override
    public void onSelectionStateChange(List<DownloadHistoryItemWrapper> selectedItems) {
        boolean wasSelectionEnabled = mIsSelectionEnabled;
        super.onSelectionStateChange(selectedItems);

        if (!mIsSelectionEnabled) {
            updateTitle();
        } else {
            int numSelected = mSelectionDelegate.getSelectedItems().size();

            // If the share or delete menu items are shown in the overflow menu instead of as an
            // action, there may not be views associated with them.
            View shareButton = findViewById(R.id.selection_mode_share_menu_id);
            if (shareButton != null) {
                shareButton.setContentDescription(getResources().getQuantityString(
                        R.plurals.accessibility_share_selected_items,
                                numSelected, numSelected));
            }

            View deleteButton = findViewById(R.id.selection_mode_delete_menu_id);
            if (deleteButton != null) {
                deleteButton.setContentDescription(getResources().getQuantityString(
                        R.plurals.accessibility_remove_selected_items,
                        numSelected, numSelected));
            }

            if (!wasSelectionEnabled) {
                RecordUserAction.record("Android.DownloadManager.SelectionEstablished");
            }
        }
    }

    @Override
    public void onManagerDestroyed() { }

    @Override
    public void hideSearchView() {
        super.hideSearchView();
        updateTitle();
    }

    private void updateTitle() {
        if (mFilter == DownloadFilter.FILTER_ALL) {
            setTitle(R.string.menu_downloads);
        } else {
            setTitle(DownloadFilter.getStringIdForFilter(mFilter));
        }
    }
}
