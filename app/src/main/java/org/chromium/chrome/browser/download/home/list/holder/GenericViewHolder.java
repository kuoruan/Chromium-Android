// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.content.res.AppCompatResources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.UiUtils;
import org.chromium.chrome.browser.download.home.view.SelectionView;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;

/** A {@link RecyclerView.ViewHolder} specifically meant to display a generic {@code OfflineItem}.
 */
public class GenericViewHolder extends ThumbnailAwareViewHolder {
    private static final int INVALID_ID = -1;

    private final TextView mTitle;
    private final TextView mCaption;
    private final TintedImageView mThumbnailView;

    private Bitmap mThumbnailBitmap;

    /** The icon to use when there is no thumbnail. */
    private @DrawableRes int mIconId = INVALID_ID;

    /** Creates a new {@link GenericViewHolder} instance. */
    public static GenericViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_generic_item, null);
        int imageSize = parent.getContext().getResources().getDimensionPixelSize(
                R.dimen.download_manager_generic_thumbnail_size);
        return new GenericViewHolder(view, imageSize);
    }

    private GenericViewHolder(View view, int thumbnailSizePx) {
        super(view, thumbnailSizePx, thumbnailSizePx);

        mTitle = (TextView) itemView.findViewById(R.id.title);
        mCaption = (TextView) itemView.findViewById(R.id.caption);
        mThumbnailView = (TintedImageView) itemView.findViewById(R.id.thumbnail);
    }

    // ListItemViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        super.bind(properties, item);
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;

        mTitle.setText(offlineItem.item.title);
        mCaption.setText(UiUtils.generateGenericCaption(offlineItem.item));

        mIconId = UiUtils.getIconForItem(offlineItem.item);
        updateThumbnailView();
    }

    @Override
    void onVisualsChanged(ImageView view, OfflineItemVisuals visuals) {
        mThumbnailBitmap = visuals == null ? null : visuals.icon;
        updateThumbnailView();
    }

    private void updateThumbnailView() {
        Resources resources = itemView.getContext().getResources();
        SelectionView selectionView = itemView.findViewById(R.id.selection);
        selectionView.setVisibility(selectionView.isSelected() ? View.VISIBLE : View.GONE);
        mThumbnailView.setVisibility(selectionView.isSelected() ? View.GONE : View.VISIBLE);
        if (mThumbnailBitmap != null) {
            assert !mThumbnailBitmap.isRecycled();

            mThumbnailView.setBackground(null);
            mThumbnailView.setTint(null);

            RoundedBitmapDrawable drawable =
                    RoundedBitmapDrawableFactory.create(resources, mThumbnailBitmap);
            drawable.setCircular(true);
            mThumbnailView.setImageDrawable(drawable);
        } else if (mIconId != INVALID_ID) {
            mThumbnailView.setBackgroundResource(R.drawable.list_item_icon_modern_bg);
            mThumbnailView.getBackground().setLevel(
                    resources.getInteger(R.integer.list_item_level_default));
            mThumbnailView.setImageResource(mIconId);
            mThumbnailView.setTint(AppCompatResources.getColorStateList(
                    mThumbnailView.getContext(), R.color.dark_mode_tint));
        }
    }
}
