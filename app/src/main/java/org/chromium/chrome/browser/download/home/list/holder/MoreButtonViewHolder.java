// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.support.annotation.CallSuper;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.ListProperties;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.widget.ListMenuButton;

/**
 * Helper {@link RecyclerView.ViewHolder} that handles showing a 3-dot menu with preset actions.
 */
class MoreButtonViewHolder extends ListItemViewHolder implements ListMenuButton.Delegate {
    private final ListMenuButton mMore;

    private Runnable mShareCallback;
    private Runnable mDeleteCallback;

    /**
     * Creates a new instance of a {@link MoreButtonViewHolder}.
     */
    public MoreButtonViewHolder(View view) {
        super(view);
        mMore = (ListMenuButton) view.findViewById(R.id.more);
        if (mMore != null) mMore.setDelegate(this);
    }

    // ListItemViewHolder implementation.
    @CallSuper
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;
        mShareCallback =
                () -> properties.getValue(ListProperties.CALLBACK_SHARE).onResult(offlineItem.item);
        mDeleteCallback = ()
                -> properties.getValue(ListProperties.CALLBACK_REMOVE).onResult(offlineItem.item);
        if (mMore != null) {
            mMore.setClickable(!properties.getValue(ListProperties.SELECTION_MODE_ACTIVE));
        }
    }

    // ListMenuButton.Delegate implementation.
    @Override
    public ListMenuButton.Item[] getItems() {
        return new ListMenuButton.Item[] {
                new ListMenuButton.Item(itemView.getContext(), R.string.share, true),
                new ListMenuButton.Item(itemView.getContext(), R.string.delete, true)};
    }

    @Override
    public void onItemSelected(ListMenuButton.Item item) {
        if (item.getTextId() == R.string.share) {
            if (mShareCallback != null) mShareCallback.run();
        } else if (item.getTextId() == R.string.delete) {
            if (mDeleteCallback != null) mDeleteCallback.run();
        }
    }
}
