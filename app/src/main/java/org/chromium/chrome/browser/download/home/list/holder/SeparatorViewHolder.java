// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.modelutil.PropertyModel;

/**
 * A {@link ViewHolder} specifically meant to display a separator.
 */
public class SeparatorViewHolder extends ListItemViewHolder {
    /**
     * Creates a new {@link SeparatorViewHolder} instance.
     * @param isDateDivider Whether the divider is between dates or individual sections.
     */
    public static SeparatorViewHolder create(ViewGroup parent, boolean isDateDivider) {
        View dividerView =
                LayoutInflater.from(parent.getContext())
                        .inflate(isDateDivider ? R.layout.download_manager_date_separator
                                               : R.layout.download_manager_section_separator,
                                null);
        return new SeparatorViewHolder(dividerView);
    }

    private SeparatorViewHolder(View view) {
        super(view);
    }

    // ListItemViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {}
}
