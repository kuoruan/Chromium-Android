// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.ListProperties;
import org.chromium.chrome.browser.download.home.view.SelectionView;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;
import org.chromium.components.offline_items_collection.VisualsCallback;

/**
 * Helper {@link RecyclerView.ViewHolder} that handles querying for thumbnails if necessary.
 */
abstract class ThumbnailAwareViewHolder extends MoreButtonViewHolder implements VisualsCallback {
    private final ImageView mThumbnail;
    private final SelectionView mSelectionView;

    /**
     * The {@link ContentId} of the associated thumbnail/request if any.
     */
    private @Nullable ContentId mId;

    /**
     * A {@link Runnable} to cancel an outstanding thumbnail request if any.
     */
    private @Nullable Runnable mCancellable;

    /**
     * Whether or not a request is outstanding to support synchronous responses.
     */
    private boolean mIsRequesting;

    /**
     * The ideal width of the queried thumbnail.
     */
    private int mWidthPx;

    /**
     * The ideal height of the queried thumbnail.
     */
    private int mHeightPx;

    /**
     * Creates a new instance of a {@link ThumbnailAwareViewHolder}.
     * @param view              The root {@link View} for this holder.
     * @param thumbnailWidthPx  The desired width of the thumbnail that will be retrieved.
     * @param thumbnailHeightPx The desired height of the thumbnail that will be retrieved.
     */
    public ThumbnailAwareViewHolder(View view, int thumbnailWidthPx, int thumbnailHeightPx) {
        super(view);

        mThumbnail = (ImageView) view.findViewById(R.id.thumbnail);
        mSelectionView = itemView.findViewById(R.id.selection);
        mWidthPx = thumbnailWidthPx;
        mHeightPx = thumbnailHeightPx;
    }

    // MoreButtonViewHolder implementation.
    @Override
    @CallSuper
    public void bind(PropertyModel properties, ListItem item) {
        super.bind(properties, item);
        // If we have no thumbnail to show just return early.
        if (mThumbnail == null) return;

        OfflineItem offlineItem = ((ListItem.OfflineItemListItem) item).item;

        // If we're rebinding the same item, ignore the bind.
        if (offlineItem.id.equals(mId) && !selectionStateHasChanged(properties, item)) {
            return;
        }

        if (mSelectionView != null) {
            mSelectionView.setSelectionState(item.selected,
                    properties.getValue(ListProperties.SELECTION_MODE_ACTIVE),
                    item.showSelectedAnimation);
        }

        itemView.setOnLongClickListener(v -> {
            properties.getValue(ListProperties.CALLBACK_SELECTION).onResult(item);
            return true;
        });

        itemView.setOnClickListener(v -> {
            if (mSelectionView != null && mSelectionView.isInSelectionMode()) {
                properties.getValue(ListProperties.CALLBACK_SELECTION).onResult(item);
            } else {
                properties.getValue(ListProperties.CALLBACK_OPEN).onResult(offlineItem);
            }
        });

        // Clear any associated bitmap from the thumbnail.
        if (mId != null) onVisualsChanged(mThumbnail, null);

        // Show the loading animation if we are in loading state.
        showLoadingView(mThumbnail);

        // Clear out any outstanding thumbnail request.
        if (mCancellable != null) mCancellable.run();

        // Start the new request.
        mId = offlineItem.id;
        mCancellable = properties.getValue(ListProperties.PROVIDER_VISUALS)
                               .getVisuals(offlineItem, mWidthPx, mHeightPx, this);

        // Make sure to update our state properly if we got a synchronous response.
        if (!mIsRequesting) mCancellable = null;
    }

    private boolean selectionStateHasChanged(PropertyModel properties, ListItem item) {
        if (mSelectionView == null) return false;

        return mSelectionView.isSelected() != item.selected
                || mSelectionView.isInSelectionMode()
                != properties.getValue(ListProperties.SELECTION_MODE_ACTIVE);
    }

    // VisualsCallback implementation.
    @Override
    public void onVisualsAvailable(ContentId id, OfflineItemVisuals visuals) {
        // Quit early if the request is not for our currently bound item.
        if (!id.equals(mId)) return;

        // Clear out the request state.
        mCancellable = null;
        mIsRequesting = false;

        // Hide the loading view.
        hideLoadingView();

        // Notify of the new visuals (if any).
        onVisualsChanged(mThumbnail, visuals);
    }

    /**
     * Show UI to indicate that thumbnail loading in progress.
     * @param view The view that should show the loading image.
     */
    protected void showLoadingView(ImageView view) {}

    /** Hide the loading view. */
    protected void hideLoadingView() {}

    /**
     * Called when the contents of the thumbnail should be changed to due an event (either this
     * {@link RecyclerView.ViewHolder} being rebound to another {@link ListItem} or a thumbnail
     * query returning results.
     * @param view    The {@link ImageView} that the thumbnail should be set on.
     * @param visuals The {@link OfflineItemVisuals} that were returned by the backend if any.
     */
    abstract void onVisualsChanged(ImageView view, @Nullable OfflineItemVisuals visuals);
}
