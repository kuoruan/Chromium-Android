// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list;

import android.support.annotation.IntDef;
import android.support.annotation.StringRes;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.home.list.ListItem.DateListItem;
import org.chromium.chrome.browser.download.home.list.ListItem.OfflineItemListItem;
import org.chromium.chrome.browser.download.home.list.ListItem.ViewListItem;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItemFilter;
import org.chromium.components.offline_items_collection.OfflineItemState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for representing {@link ListItem}s in a {@link RecyclerView} list. */
public class ListUtils {
    /** The potential types of list items that could be displayed. */
    @IntDef({ViewType.DATE, ViewType.IN_PROGRESS, ViewType.GENERIC, ViewType.VIDEO, ViewType.IMAGE,
            ViewType.CUSTOM_VIEW, ViewType.PREFETCH, ViewType.SECTION_HEADER,
            ViewType.SEPARATOR_DATE, ViewType.SEPARATOR_SECTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int DATE = 0;
        int IN_PROGRESS = 1;
        int GENERIC = 2;
        int VIDEO = 3;
        int IMAGE = 4;
        int CUSTOM_VIEW = 5;
        int PREFETCH = 6;
        int SECTION_HEADER = 7;
        int SEPARATOR_DATE = 8;
        int SEPARATOR_SECTION = 9;
    }

    /** Converts a given list of {@link ListItem}s to a list of {@link OfflineItem}s. */
    public static List<OfflineItem> toOfflineItems(List<ListItem> items) {
        List<OfflineItem> offlineItems = new ArrayList<>();
        for (ListItem item : items) {
            if (item instanceof ListItem.OfflineItemListItem) {
                offlineItems.add(((ListItem.OfflineItemListItem) item).item);
            }
        }
        return offlineItems;
    }

    /**
     * Analyzes a {@link ListItem} and finds the most appropriate {@link ViewType} based on the
     * current state.
     * @param item The {@link ListItem} to determine the {@link ViewType} for.
     * @return     The type of {@link ViewType} to use for a particular {@link ListItem}.
     * @see        ViewType
     */
    public static @ViewType int getViewTypeForItem(ListItem item) {
        if (item instanceof ViewListItem) return ViewType.CUSTOM_VIEW;
        if (item instanceof ListItem.SectionHeaderListItem) return ViewType.SECTION_HEADER;
        if (item instanceof ListItem.SeparatorViewListItem) {
            ListItem.SeparatorViewListItem separator = (ListItem.SeparatorViewListItem) item;
            return separator.isDateDivider() ? ViewType.SEPARATOR_DATE : ViewType.SEPARATOR_SECTION;
        }

        if (item instanceof DateListItem) {
            if (item instanceof OfflineItemListItem) {
                OfflineItemListItem offlineItem = (OfflineItemListItem) item;

                if (offlineItem.item.isSuggested) return ViewType.PREFETCH;
                if (offlineItem.item.state == OfflineItemState.IN_PROGRESS
                        || offlineItem.item.state == OfflineItemState.PAUSED
                        || offlineItem.item.state == OfflineItemState.INTERRUPTED
                        || offlineItem.item.state == OfflineItemState.PENDING
                        || offlineItem.item.state == OfflineItemState.FAILED) {
                    return ViewType.IN_PROGRESS;
                }

                switch (offlineItem.item.filter) {
                    case OfflineItemFilter.FILTER_VIDEO:
                        return ViewType.VIDEO;
                    case OfflineItemFilter.FILTER_IMAGE:
                        return ViewType.IMAGE;
                    // case OfflineItemFilter.FILTER_PAGE:
                    // case OfflineItemFilter.FILTER_AUDIO:
                    // case OfflineItemFilter.FILTER_OTHER:
                    // case OfflineItemFilter.FILTER_DOCUMENT:
                    default:
                        return ViewType.GENERIC;
                }
            } else {
                return ViewType.DATE;
            }
        }

        assert false;
        return ViewType.GENERIC;
    }

    /**
     * @return The id of the string to be displayed as the section header for the given filter.
     */
    public static @StringRes int getTextForSection(int filter) {
        switch (filter) {
            case OfflineItemFilter.FILTER_PAGE:
                return R.string.download_manager_ui_pages;
            case OfflineItemFilter.FILTER_IMAGE:
                return R.string.download_manager_ui_images;
            case OfflineItemFilter.FILTER_VIDEO:
                return R.string.download_manager_ui_video;
            case OfflineItemFilter.FILTER_AUDIO:
                return R.string.download_manager_ui_audio;
            case OfflineItemFilter.FILTER_OTHER:
                return R.string.download_manager_ui_other;
            case OfflineItemFilter.FILTER_DOCUMENT:
                return R.string.download_manager_ui_documents;
            default:
                return R.string.download_manager_ui_all_downloads;
        }
    }

    /**
     * Analyzes a {@link ListItem} and finds the best span size based on the current state.  Span
     * size determines how many columns this {@link ListItem}'s {@link View} will take up in the
     * overall list.
     * @param item      The {@link ListItem} to determine the span size for.
     * @param spanCount The maximum span amount of columns {@code item} can take up.
     * @return          The number of columns {@code item} should take.
     * @see             GridLayoutManager.SpanSizeLookup
     */
    public static int getSpanSize(ListItem item, int spanCount) {
        if (item instanceof OfflineItemListItem && ((OfflineItemListItem) item).spanFullWidth) {
            return spanCount;
        }

        return getViewTypeForItem(item) == ViewType.IMAGE ? 1 : spanCount;
    }
}
