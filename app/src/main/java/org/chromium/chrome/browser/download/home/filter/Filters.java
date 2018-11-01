// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.filter;

import android.support.annotation.IntDef;
import android.text.TextUtils;

import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.download.ui.DownloadFilter;
import org.chromium.components.offline_items_collection.OfflineItemFilter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Helper containing a list of Downloads Home filter types and conversion methods. */
public class Filters {
    /** A list of possible filter types on offlined items. */
    @IntDef({FilterType.NONE, FilterType.VIDEOS, FilterType.MUSIC, FilterType.IMAGES,
            FilterType.SITES, FilterType.OTHER, FilterType.PREFETCHED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterType {
        int NONE = 0;
        int VIDEOS = 1;
        int MUSIC = 2;
        int IMAGES = 3;
        int SITES = 4;
        int OTHER = 5;
        int PREFETCHED = 6;
        int NUM_ENTRIES = 7;
    }

    /**
     * Converts from a {@link OfflineItem#filter} to a {@link FilterType}.  Note that not all
     * {@link OfflineItem#filter} types have a corresponding match and may return {@link #NONE}
     * as they don't correspond to any UI filter.
     *
     * @param filter The {@link OfflineItem#filter} type to convert.
     * @return       The corresponding {@link FilterType}.
     */
    public static @FilterType Integer fromOfflineItem(@OfflineItemFilter int filter) {
        switch (filter) {
            case OfflineItemFilter.FILTER_PAGE:
                return FilterType.SITES;
            case OfflineItemFilter.FILTER_VIDEO:
                return FilterType.VIDEOS;
            case OfflineItemFilter.FILTER_AUDIO:
                return FilterType.MUSIC;
            case OfflineItemFilter.FILTER_IMAGE:
                return FilterType.IMAGES;
            // case OfflineItemFilter.FILTER_OTHER
            // case OfflineItemFilter.FILTER_DOCUMENT
            default:
                return FilterType.OTHER;
        }
    }

    /** Converts between a {@link OfflineItemFilter} and a {@link DownloadFilter.Type}. */
    public static @DownloadFilter.Type int offlineItemFilterToDownloadFilter(
            @OfflineItemFilter int filter) {
        switch (filter) {
            case OfflineItemFilter.FILTER_PAGE:
                return DownloadFilter.Type.PAGE;
            case OfflineItemFilter.FILTER_VIDEO:
                return DownloadFilter.Type.VIDEO;
            case OfflineItemFilter.FILTER_AUDIO:
                return DownloadFilter.Type.AUDIO;
            case OfflineItemFilter.FILTER_IMAGE:
                return DownloadFilter.Type.IMAGE;
            case OfflineItemFilter.FILTER_DOCUMENT:
                return DownloadFilter.Type.DOCUMENT;
            // case OfflineItemFilter.FILTER_OTHER
            default:
                return DownloadFilter.Type.OTHER;
        }
    }

    /**
     * Converts {@code filter} into a url.
     * @see DownloadFilter#getUrlForFilter(int)
     */
    public static String toUrl(@FilterType int filter) {
        return filter == FilterType.NONE ? UrlConstants.DOWNLOADS_URL
                                         : UrlConstants.DOWNLOADS_FILTER_URL + filter;
    }

    /**
     * Converts {@code url} to a {@link FilterType}.
     * @see DownloadFilter#getFilterFromUrl(String)
     */
    public static @FilterType int fromUrl(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith(UrlConstants.DOWNLOADS_FILTER_URL)) {
            return FilterType.NONE;
        }

        @FilterType
        int filter = FilterType.NONE;
        try {
            filter = Integer.parseInt(url.substring(UrlConstants.DOWNLOADS_FILTER_URL.length()));
            if (filter < 0 || filter >= FilterType.NUM_ENTRIES) filter = FilterType.NONE;
        } catch (NumberFormatException ex) {
        }

        return filter;
    }

    private Filters() {}
}