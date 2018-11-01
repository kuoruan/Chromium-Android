// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.UiUtils;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;

/**
 * A {@link RecyclerView.ViewHolder} specifically meant to display a prefetch item.
 */
public class PrefetchViewHolder extends ThumbnailAwareViewHolder {
    private final TextView mTitle;
    private final TextView mCaption;
    private final TextView mTimestamp;

    /**
     * Creates a new instance of a {@link PrefetchViewHolder}.
     */
    public static PrefetchViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_prefetch_item, null);
        int imageSize = parent.getContext().getResources().getDimensionPixelSize(
                R.dimen.download_manager_prefetch_thumbnail_size);
        return new PrefetchViewHolder(view, imageSize);
    }

    private PrefetchViewHolder(View view, int thumbnailSizePx) {
        super(view, thumbnailSizePx, thumbnailSizePx);
        mTitle = (TextView) itemView.findViewById(R.id.title);
        mCaption = (TextView) itemView.findViewById(R.id.caption);
        mTimestamp = (TextView) itemView.findViewById(R.id.timestamp);
    }

    // ThumbnailAwareViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        super.bind(properties, item);
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;

        mTitle.setText(offlineItem.item.title);
        mCaption.setText(UiUtils.generatePrefetchCaption(offlineItem.item));
        mTimestamp.setText(UiUtils.generatePrefetchTimestamp(offlineItem.date));
    }

    @Override
    void onVisualsChanged(ImageView view, OfflineItemVisuals visuals) {
        view.setImageBitmap(visuals == null ? null : visuals.icon);
    }
}
