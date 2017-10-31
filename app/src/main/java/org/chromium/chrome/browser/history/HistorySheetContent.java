// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import android.view.View;

import org.chromium.base.CollectionUtil;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.toolbar.BottomToolbarPhone;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;

import java.util.List;

/**
 * A {@link BottomSheetContent} holding a {@link HistoryManager} for display in the BottomSheet.
 */
public class HistorySheetContent implements BottomSheetContent {
    private final View mContentView;
    private final SelectableListToolbar<HistoryItem> mToolbarView;
    private HistoryManager mHistoryManager;

    /**
     * @param activity The activity displaying the history manager UI.
     * @param snackbarManager The {@link SnackbarManager} used to display snackbars.
     */
    public HistorySheetContent(final ChromeActivity activity, SnackbarManager snackbarManager) {
        mHistoryManager = new HistoryManager(activity, false, snackbarManager,
                activity.getTabModelSelector().isIncognitoSelected());
        mContentView = mHistoryManager.getView();
        mToolbarView = mHistoryManager.detachToolbarView();
        mToolbarView.addObserver(new SelectableListToolbar.SelectableListToolbarObserver() {
            @Override
            public void onThemeColorChanged(boolean isLightTheme) {
                activity.getBottomSheet().updateHandleTint();
            }

            @Override
            public void onStartSearch() {
                activity.getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_FULL, true);
            }
        });
        ((BottomToolbarPhone) activity.getToolbarManager().getToolbar())
                .setOtherToolbarStyle(mToolbarView);

        mToolbarView.setActionBarDelegate(activity.getBottomSheet().getActionBarDelegate());
    }

    @Override
    public View getContentView() {
        return mContentView;
    }

    @Override
    public List<View> getViewsForPadding() {
        return CollectionUtil.newArrayList(
                mHistoryManager.getRecyclerView(), mHistoryManager.getEmptyView());
    }

    @Override
    public View getToolbarView() {
        return mToolbarView;
    }

    @Override
    public boolean isUsingLightToolbarTheme() {
        return mToolbarView.isLightTheme();
    }

    @Override
    public boolean isIncognitoThemedContent() {
        return false;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mHistoryManager.getVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        mHistoryManager.onDestroyed();
        mHistoryManager = null;
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_HISTORY;
    }

    @Override
    public boolean applyDefaultTopPadding() {
        return false;
    }

    @Override
    public void scrollToTop() {
        mHistoryManager.scrollToTop();
    }
}
