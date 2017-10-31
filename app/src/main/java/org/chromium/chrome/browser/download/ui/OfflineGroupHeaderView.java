// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.download.ui.DownloadHistoryAdapter.SubsectionHeader;
import org.chromium.chrome.browser.download.ui.DownloadItemSelectionDelegate.SubsectionHeaderSelectionObserver;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.chrome.browser.widget.selection.SelectableItemView;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * A header that presents users the option to view or hide the suggested offline pages.
 */
public class OfflineGroupHeaderView
        extends SelectableItemView<TimedItem> implements SubsectionHeaderSelectionObserver {
    private final int mIconBackgroundColorSelected;
    private final int mIconBackgroundColor;
    private final int mIconBackgroundResId;
    private final ColorStateList mIconForegroundColorList;
    private final ColorStateList mCheckedIconForegroundColorList;

    private SubsectionHeader mHeader;
    private DownloadHistoryAdapter mAdapter;
    private DownloadItemSelectionDelegate mSelectionDelegate;

    private TextView mDescriptionView;
    private ImageView mExpandImage;
    private TintedImageView mIconView;

    public OfflineGroupHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIconBackgroundColor = DownloadUtils.getIconBackgroundColor(context);
        mIconBackgroundColorSelected =
                ApiCompatibilityUtils.getColor(getResources(), R.color.google_grey_600);
        mCheckedIconForegroundColorList = DownloadUtils.getIconForegroundColorList(context);
        mIconBackgroundResId = R.drawable.list_item_icon_modern_bg;

        if (FeatureUtilities.isChromeHomeEnabled()) {
            mIconForegroundColorList = ApiCompatibilityUtils.getColorStateList(
                    context.getResources(), R.color.dark_mode_tint);
        } else {
            mIconForegroundColorList = DownloadUtils.getIconForegroundColorList(context);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIconView = (TintedImageView) findViewById(R.id.icon_view);
        mDescriptionView = (TextView) findViewById(R.id.description);
        mExpandImage = (ImageView) findViewById(R.id.expand_icon);
    }

    /**
     * @param adapter The adapter associated with this header.
     */
    public void setAdapter(DownloadHistoryAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        updateCheckIcon(checked);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelectionDelegate != null) {
            setChecked(mSelectionDelegate.isHeaderSelected(mHeader));
        }
    }

    /**
     * Updates the properties of this view.
     * @param header The associated {@link SubsectionHeader}.
     */
    @SuppressLint("StringFormatMatches")
    public void displayHeader(SubsectionHeader header) {
        this.mHeader = header;
        // TODO(crbug.com/635567): Fix lint properly.
        String description = String.format(Locale.getDefault(), "%s - %s",
                Formatter.formatFileSize(getContext(), header.getTotalFileSize()),
                getContext().getString(R.string.download_manager_offline_header_description));
        mDescriptionView.setText(description);
        updateExpandIcon(header.isExpanded());
        setChecked(mSelectionDelegate.isHeaderSelected(header));
    }

    private void updateExpandIcon(boolean expanded) {
        mExpandImage.setImageResource(expanded ? R.drawable.ic_collapsed : R.drawable.ic_expanded);
        mExpandImage.setContentDescription(
                getResources().getString(expanded ? R.string.accessibility_collapse_offline_pages
                                                  : R.string.accessibility_expand_offline_pages));
    }

    private void updateCheckIcon(boolean checked) {
        if (checked) {
            if (FeatureUtilities.isChromeHomeEnabled()) {
                mIconView.setBackgroundResource(mIconBackgroundResId);
                mIconView.getBackground().setLevel(
                        getResources().getInteger(R.integer.list_item_level_selected));
            } else {
                mIconView.setBackgroundColor(mIconBackgroundColorSelected);
            }

            mIconView.setImageResource(R.drawable.ic_check_googblue_24dp);
            mIconView.setTint(mCheckedIconForegroundColorList);
        } else {
            if (FeatureUtilities.isChromeHomeEnabled()) {
                mIconView.setBackgroundResource(mIconBackgroundResId);
                mIconView.getBackground().setLevel(
                        getResources().getInteger(R.integer.list_item_level_default));
            } else {
                mIconView.setBackgroundColor(mIconBackgroundColor);
            }

            mIconView.setImageResource(R.drawable.ic_chrome);
            mIconView.setTint(mIconForegroundColorList);
        }
    }

    @Override
    public void onClick() {
        boolean newState = !mHeader.isExpanded();
        mAdapter.setSubsectionExpanded(new Date(mHeader.getTimestamp()), newState);
    }

    @Override
    protected boolean isSelectionModeActive() {
        return mSelectionDelegate.isSelectionEnabled();
    }

    @Override
    protected boolean toggleSelectionForItem(TimedItem item) {
        return mSelectionDelegate.toggleSelectionForSubsection(mHeader);
    }

    /**
     * Sets the selection delegate and registers |this| as
     * an observer. The delegate must be set before the item can respond to click events.
     * {@link SelectionDelegate} expects all the views to be of same type i.e.
     * SelectableItemView<DownloadHistoryItemWrapper>, whereas DownloadItemSelectionDelegate can
     * handle multiple types. This view being of type  SelectableItemView<TimedItem>, we need
     * to use a DownloadItemSelectionDelegate instead of SelectionDelegate.
     * @param delegate The selection delegate that will inform this item of selection changes.
     */
    public void setSelectionDelegate(DownloadItemSelectionDelegate delegate) {
        if (mSelectionDelegate == delegate) return;

        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
        mSelectionDelegate = delegate;
        mSelectionDelegate.addObserver(this);
    }

    @Override
    public void onSubsectionHeaderSelectionStateChanged(Set<SubsectionHeader> selectedHeaders) {
        boolean isChecked = selectedHeaders.contains(mHeader);
        setChecked(isChecked);
    }
}
