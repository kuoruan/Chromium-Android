// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list.holder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.download.home.list.ListItem;
import org.chromium.chrome.browser.download.home.list.ListProperties;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.components.offline_items_collection.OfflineItemState;

/**
 * A {@link RecyclerView.ViewHolder} specifically meant to display an in-progress {@code
 * OfflineItem}.
 */
public class InProgressViewHolder extends ListItemViewHolder {
    private final ProgressBar mProgressBar;
    private final TextView mTitle;
    private final TextView mCaption;
    private final TintedImageButton mPauseResumeButton;
    private final TintedImageButton mCancelButton;

    /**
     * Creates a new {@link InProgressViewHolder} instance.
     */
    public static InProgressViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.download_manager_in_progress_item, null);
        return new InProgressViewHolder(view);
    }

    /** Constructor. */
    public InProgressViewHolder(View view) {
        super(view);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mTitle = view.findViewById(R.id.title);
        mCaption = view.findViewById(R.id.caption);
        mPauseResumeButton = view.findViewById(R.id.pause_button);
        mCancelButton = view.findViewById(R.id.cancel_button);
    }

    // ListItemViewHolder implementation.
    @Override
    public void bind(PropertyModel properties, ListItem item) {
        ListItem.OfflineItemListItem offlineItem = (ListItem.OfflineItemListItem) item;
        mTitle.setText(offlineItem.item.title);
        mCancelButton.setOnClickListener(v
                -> properties.getValue(ListProperties.CALLBACK_CANCEL).onResult(offlineItem.item));

        if (offlineItem.item.state == OfflineItemState.PAUSED) {
            mPauseResumeButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            mPauseResumeButton.setContentDescription(
                    itemView.getContext().getString(R.string.download_notification_resume_button));
        } else {
            mPauseResumeButton.setImageResource(R.drawable.ic_pause_white_24dp);
            mPauseResumeButton.setContentDescription(
                    itemView.getContext().getString(R.string.download_notification_pause_button));
        }

        // TODO(shaktisahu): Create status string for the new specs.
        mCaption.setText(DownloadUtils.getProgressTextForNotification(offlineItem.item.progress));
        mPauseResumeButton.setOnClickListener(view -> {
            if (offlineItem.item.state == OfflineItemState.PAUSED) {
                properties.getValue(ListProperties.CALLBACK_RESUME).onResult(offlineItem.item);
            } else {
                properties.getValue(ListProperties.CALLBACK_PAUSE).onResult(offlineItem.item);
            }
        });

        boolean showIndeterminate = offlineItem.item.progress.isIndeterminate()
                && offlineItem.item.state != OfflineItemState.PAUSED
                && offlineItem.item.state != OfflineItemState.PENDING;
        if (showIndeterminate) {
            mProgressBar.setIndeterminate(true);
            mProgressBar.setIndeterminateDrawable(itemView.getContext().getResources().getDrawable(
                    R.drawable.download_circular_progress_bar));
        } else {
            mProgressBar.setIndeterminate(false);
        }

        if (!offlineItem.item.progress.isIndeterminate()) {
            mProgressBar.setProgress(offlineItem.item.progress.getPercentage());
        }
    }
}
