// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.Date;

/**
 * A header that presents users the option to view or hide the suggested offline pages.
 * TODO(shaktisahu): Make this layout selectable.
 */
public class OfflineGroupHeaderView extends FrameLayout {
    private Date mDate;
    private DownloadHistoryAdapter mAdapter;

    private TextView mPageCountHeader;
    private TextView mFileSizeView;
    private ImageView mImageView;

    public OfflineGroupHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newState = !mAdapter.isSubsectionExpanded(mDate);
                mAdapter.setSubsectionExpanded(mDate, newState);
                updateImageView(newState);
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPageCountHeader = (TextView) findViewById(R.id.page_count_text);
        mFileSizeView = (TextView) findViewById(R.id.filesize_view);
        mImageView = (ImageView) findViewById(R.id.expand_icon);
    }

    /**
     * @param adapter The adapter associated with this header.
     */
    public void setAdapter(DownloadHistoryAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Updates the properties of this header.
     * @param date The date associated with this header.
     * @param expanded Whether the items should be expanded or not.
     * @param pageCount The total number of associated pages.
     * @param fileSize The total file size of all the associated pages.
     */
    public void update(Date date, boolean expanded, int pageCount, long fileSize) {
        mDate = new Date(date.getTime());
        mPageCountHeader.setText(getResources().getString(
                R.string.download_manager_offline_header_title, pageCount));
        mFileSizeView.setText(Formatter.formatFileSize(getContext(), fileSize));
        updateImageView(expanded);
    }

    private void updateImageView(boolean expanded) {
        mImageView.setImageResource(expanded ? R.drawable.ic_collapsed : R.drawable.ic_expanded);
        mImageView.setContentDescription(
                getResources().getString(expanded ? R.string.accessibility_collapse_offline_pages
                                                  : R.string.accessibility_expand_offline_pages));
    }
}
