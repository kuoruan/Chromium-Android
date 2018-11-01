// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.UiUtils;
import org.chromium.chrome.browser.download.home.view.LoadingBackground;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;

/**
 * A {@link RecyclerView.ViewHolder} specifically meant to display a video {@code OfflineItem}.
 */
public class VideoViewHolder extends ThumbnailAwareViewHolder {
    private final TextView mTitle;
    private final TextView mCaption;
    private final ImageView mThumbnailView;
    private LoadingBackground mLoadingBackground;

    /**
     * Creates a new {@link VideoViewHolder} instance.
     */
    public static VideoViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_video_item, null);
        int thumbnailWidth = parent.getContext().getResources().getDimensionPixelSize(
                R.dimen.download_manager_image_width);
        int thumbnailHeight = parent.getContext().getResources().getDimensionPixelSize(
                R.dimen.download_manager_image_width);
        return new VideoViewHolder(view, thumbnailWidth, thumbnailHeight);
    }

    public VideoViewHolder(View view, int thumbnailWidthPx, int thumbnailHeightPx) {
        super(view, thumbnailWidthPx, thumbnailHeightPx);

        mTitle = (TextView) itemView.findViewById(R.id.title);
        mCaption = (TextView) itemView.findViewById(R.id.caption);
        mThumbnailView = (ImageView) itemView.findViewById(R.id.thumbnail);
        mLoadingBackground = new LoadingBackground(view.getContext());
    }

    @Override
    public void bind(PropertyModel properties, ListItem item) {
        super.bind(properties, item);
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;

        mTitle.setText(offlineItem.item.title);
        mCaption.setText(UiUtils.generateGenericCaption(offlineItem.item));
        mThumbnailView.setContentDescription(offlineItem.item.title);
    }

    @Override
    void onVisualsChanged(ImageView view, @Nullable OfflineItemVisuals visuals) {
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
