// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.UiUtils;
import org.chromium.chrome.browser.modelutil.PropertyModel;

/** A {@link RecyclerView.ViewHolder} specifically meant to display a date header. */
public class DateViewHolder extends ListItemViewHolder {
    /** Creates a new {@link DateViewHolder} instance. */
    public static org.chromium.chrome.browser.download.home.list.holder.DateViewHolder create(
            ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_date_item, null);
        return new org.chromium.chrome.browser.download.home.list.holder.DateViewHolder(view);
    }

    private DateViewHolder(View view) {
        super(view);
    }

    // ListItemViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        ListItem.DateListItem dateItem = (ListItem.DateListItem) item;
        ((TextView) itemView).setText(UiUtils.dateToHeaderString(dateItem.date));
    }
}
