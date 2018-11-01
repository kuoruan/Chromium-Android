// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list;

import android.content.Context;
import android.view.View;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.download.home.PrefetchStatusProvider;
import org.chromium.chrome.browser.download.home.empty.EmptyCoordinator;
import org.chromium.chrome.browser.download.home.filter.FilterCoordinator;
import org.chromium.chrome.browser.download.home.filter.Filters.FilterType;
import org.chromium.chrome.browser.download.home.list.ListItem.ViewListItem;
import org.chromium.chrome.browser.download.home.storage.StorageCoordinator;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.components.offline_items_collection.OfflineContentProvider;
import org.chromium.components.offline_items_collection.OfflineItem;

import java.util.List;

/**
 * The top level coordinator for the download home UI.  This is currently an in progress class and
 * is not fully fleshed out yet.
 */
public class DateOrderedListCoordinator {
    /**
     * A helper interface for exposing the decision for whether or not to delete
     * {@link OfflineItem}s to an external layer.
     */
    @FunctionalInterface
    public interface DeleteController {
        /**
         * Will be called whenever {@link OfflineItem}s are in the process of being removed from the
         * UI.  This method will be called to determine if that removal should actually happen.
         * Based on the result passed to {@code callback}, the removal might be reverted instead of
         * being committed.  It is expected that {@code callback} will always be triggered no matter
         * what happens to the controller itself.
         *
         * @param items    The list of {@link OfflineItem}s that were explicitly slated for removal.
         * @param callback The {@link Callback} to notify when the deletion decision is finalized.
         *                 The callback value represents whether or not the deletion should occur.
         */
        void canDelete(List<OfflineItem> items, Callback<Boolean> callback);
    }

    private final StorageCoordinator mStorageCoordinator;
    private final FilterCoordinator mFilterCoordinator;
    private final EmptyCoordinator mEmptyCoordinator;
    private final DateOrderedListMediator mMediator;
    private final DateOrderedListView mView;

    /** Creates an instance of a DateOrderedListCoordinator, which will visually represent
     * {@code provider} as a list of items.
     * @param context          The {@link Context} to use to build the views.
     * @param offTheRecord     Whether or not to include off the record items.
     * @param provider         The {@link OfflineContentProvider} to visually represent.
     * @param deleteController A class to manage whether or not items can be deleted.
     * @param filterObserver   A {@link FilterCoordinator.Observer} that should be notified of
     *                         filter changes.  This is meant to be used for external components
     *                         that need to take action based on the visual state of the list.
     */
    public DateOrderedListCoordinator(Context context, Boolean offTheRecord,
            OfflineContentProvider provider, DeleteController deleteController,
            SelectionDelegate<ListItem> selectionDelegate,
            FilterCoordinator.Observer filterObserver) {
        // TODO(shaktisahu): Use a real provider/have this provider query the real data source.
        PrefetchStatusProvider prefetchProvider = new PrefetchStatusProvider();

        ListItemModel model = new ListItemModel();
        DecoratedListItemModel decoratedModel = new DecoratedListItemModel(model);
        mView = new DateOrderedListView(context, decoratedModel);
        mMediator = new DateOrderedListMediator(offTheRecord, provider, context::startActivity,
                deleteController, selectionDelegate, model);

        mEmptyCoordinator =
                new EmptyCoordinator(context, prefetchProvider, mMediator.getEmptySource());

        mStorageCoordinator = new StorageCoordinator(context, mMediator.getFilterSource());

        mFilterCoordinator =
                new FilterCoordinator(context, prefetchProvider, mMediator.getFilterSource());
        mFilterCoordinator.addObserver(mMediator::onFilterTypeSelected);
        mFilterCoordinator.addObserver(filterObserver);
        mFilterCoordinator.addObserver(mEmptyCoordinator);

        decoratedModel.addHeader(
                new ViewListItem(Long.MAX_VALUE - 1L, mStorageCoordinator.getView()));
        decoratedModel.addHeader(
                new ViewListItem(Long.MAX_VALUE - 2L, mFilterCoordinator.getView()));
    }

    /** Tears down this coordinator. */
    public void destroy() {
        mMediator.destroy();
    }

    /** @return The {@link View} representing downloads home. */
    public View getView() {
        return mView.getView();
    }

    /** Sets the string filter query to {@code query}. */
    public void setSearchQuery(String query) {
        mMediator.onFilterStringChanged(query);
    }

    /** Sets the UI and list to filter based on the {@code filter} {@link FilterType}. */
    public void setSelectedFilter(@FilterType int filter) {
        mFilterCoordinator.setSelectedFilter(filter);
    }

    /** Called to delete a list of items specified by {@code items}. */
    public void onDeletionRequested(List<ListItem> items) {
        mMediator.onDeletionRequested(items);
    }

    /** Called to share a list of items specified by {@code items}. */
    public void onShareRequested(List<ListItem> items) {
        mMediator.onShareRequested(items);
    }
}
