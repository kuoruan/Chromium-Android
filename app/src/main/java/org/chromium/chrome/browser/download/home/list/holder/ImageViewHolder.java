// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.view.LoadingBackground;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;

/** A {@link RecyclerView.ViewHolder} specifically meant to display an image {@code OfflineItem}. */
public class ImageViewHolder extends ThumbnailAwareViewHolder {
    private final int mImageHeightPx;
    private LoadingBackground mLoadingBackground;

    public static ImageViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_image_item, null);
        int imageSize = parent.getContext().getResources().getDimensionPixelSize(
                R.dimen.download_manager_image_width);
        return new ImageViewHolder(view, imageSize);
    }

    public ImageViewHolder(View view, int thumbnailSizePx) {
        super(view, thumbnailSizePx, thumbnailSizePx);
        mImageHeightPx = thumbnailSizePx;
        mLoadingBackground = new LoadingBackground(view.getContext());
    }

    // ThumbnailAwareViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        super.bind(properties, item);
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;
        View imageView = itemView.findViewById(R.id.thumbnail);
        imageView.setContentDescription(offlineItem.item.title);
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        layoutParams.height =
                offlineItem.spanFullWidth ? ViewGroup.LayoutParams.WRAP_CONTENT : mImageHeightPx;
    }

    @Override
    void onVisualsChanged(ImageView view, OfflineItemVisuals visuals) {
        view.setImageBitmap(visuals == null ? null : visuals.icon);
    }

    @Override
    protected void showLoadingView(ImageView view) {
        mLoadingBackground.show(view);
    }

    @Override
    protected void hideLoadingView() {
        mLoadingBackground.hide();
    }
}
