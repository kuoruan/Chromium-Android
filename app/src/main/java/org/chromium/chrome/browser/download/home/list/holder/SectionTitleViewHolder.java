// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.ListUtils;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.components.offline_items_collection.OfflineItemFilter;

/**
 * A {@link ViewHolder} specifically meant to display a section header.
 */
public class SectionTitleViewHolder extends ListItemViewHolder {
    /** Create a new {@link SectionTitleViewHolder} instance. */
    public static SectionTitleViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_section_header, null);
        return new SectionTitleViewHolder(view);
    }

    private SectionTitleViewHolder(View view) {
        super(view);
    }

    // ListItemViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        ListItem.SectionHeaderListItem sectionItem = (ListItem.SectionHeaderListItem) item;
        ((TextView) itemView).setText(ListUtils.getTextForSection(sectionItem.filter));

        boolean isPhoto = sectionItem.filter == OfflineItemFilter.FILTER_IMAGE;
        Resources resources = itemView.getContext().getResources();

        int paddingTop = resources.getDimensionPixelSize(isPhoto
                        ? R.dimen.download_manager_section_title_padding_image
                        : R.dimen.download_manager_section_title_padding_top);
        int paddingBottom = resources.getDimensionPixelSize(isPhoto
                        ? R.dimen.download_manager_section_title_padding_image
                        : R.dimen.download_manager_section_title_padding_bottom);

        if (sectionItem.isFirstSectionOfDay) {
            paddingTop = resources.getDimensionPixelSize(
                    R.dimen.download_manager_section_title_padding_top_condensed);
        }

        itemView.setPadding(
                itemView.getPaddingLeft(), paddingTop, itemView.getPaddingRight(), paddingBottom);
    }
}
