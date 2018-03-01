// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.support.annotation.CallSuper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.CollectionUtil;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.toolbar.BottomToolbarPhone;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;

import java.util.List;

/**
 * An abstract {@link BottomSheetContent} for selectable list UI's displayed in the BottomSheet.
 *
 * @param <E> The type of the selectable items this bottom sheet content displays.
 */
public abstract class SelectableBottomSheetContent<E> implements BottomSheetContent {
    /**
     * The class managing the selectable list. Used to retrieve various pieces of the
     * selectable list UI.
     * @param <E> The type of the selectable items the manager manages.
     */
    public interface SelectableBottomSheetContentManager<E> {
        /**
         * @return The view that shows the list UI.
         */
        View getView();

        /**
         * See {@link SelectableListLayout#detachToolbarView()}.
         */
        SelectableListToolbar<E> detachToolbarView();

        /**
         * @return The {@link RecyclerView} that contains the list of selectable items.
         */
        RecyclerView getRecyclerView();

        /**
         * @return The {@link TextView} shown when there are no selectable items to be shown.
         */
        TextView getEmptyView();

        /**
         * Called when the bottom sheet content is destroyed.
         */
        void onDestroyed();
    }

    private SelectableBottomSheetContentManager<E> mManager;
    private SelectableListToolbar<E> mToolbarView;

    /**
     * Initialize the {@link SelectableBottomSheetContent}.
     * @param activity The activity displaying the bottom sheet that will hold this content.
     * @param manager The {@link SelectableBottomSheetContentManager} managing the selectable list.
     */
    public void initialize(
            final ChromeActivity activity, SelectableBottomSheetContentManager<E> manager) {
        mManager = manager;

        mToolbarView = manager.detachToolbarView();
        mToolbarView.setActionBarDelegate(activity.getBottomSheet().getActionBarDelegate());
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
    }

    @Override
    public abstract int getType();

    @Override
    public View getContentView() {
        return mManager.getView();
    }

    @Override
    public List<View> getViewsForPadding() {
        return CollectionUtil.newArrayList(mManager.getRecyclerView(), mManager.getEmptyView());
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
        return mManager.getRecyclerView().computeVerticalScrollOffset();
    }

    @Override
    public boolean applyDefaultTopPadding() {
        return false;
    }

    @Override
    public void scrollToTop() {
        mManager.getRecyclerView().smoothScrollToPosition(0);
    }

    @Override
    @CallSuper
    public void destroy() {
        mManager.onDestroyed();
        mManager = null;
    }
}
