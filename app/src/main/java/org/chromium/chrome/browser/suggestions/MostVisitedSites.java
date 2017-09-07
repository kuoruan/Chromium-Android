// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.annotations.CalledByNative;

import javax.annotation.Nullable;

/**
 * Methods to provide most recent urls, titles and thumbnails.
 */
public interface MostVisitedSites {
    /**
     * An interface for handling events in {@link MostVisitedSites}.
     */
    interface Observer {
        /**
         * This is called when the list of most visited URLs is initially available or updated.
         * Parameters guaranteed to be non-null.
         *
         * @param titles Array of most visited url page titles.
         * @param urls Array of most visited URLs, including popular URLs if
         *             available and necessary (i.e. there aren't enough most
         *             visited URLs).
         * @param whitelistIconPaths The paths to the icon image files for whitelisted tiles, empty
         *                           strings otherwise.
         * @param sources For each tile, the {@code TileSource} that generated the tile.
         */
        @CalledByNative("Observer")
        void onMostVisitedURLsAvailable(
                String[] titles, String[] urls, String[] whitelistIconPaths, int[] sources);

        /**
         * This is called when a previously uncached icon has been fetched.
         * Parameters guaranteed to be non-null.
         *
         * @param siteUrl URL of site with newly-cached icon.
         */
        @CalledByNative("Observer")
        void onIconMadeAvailable(String siteUrl);
    }

    /**
     * An interface to provide {@link MostVisitedSites} with platform-specific home page data.
     */
    interface HomePageClient {
        /**
         * @return True if a home page is active and set.
         */
        @CalledByNative("HomePageClient")
        boolean isHomePageEnabled();

        /**
         * @return True if the new tab page was set as home page.
         */
        @CalledByNative("HomePageClient")
        boolean isNewTabPageUsedAsHomePage();

        /**
         * @return The raw URL of the currently set home page.
         */
        @CalledByNative("HomePageClient")
        @Nullable
        String getHomePageUrl();
    }

    /**
     * This instance must not be used after calling destroy().
     */
    void destroy();

    /**
     * Sets the recipient for events from {@link MostVisitedSites}. The observer may be notified
     * synchronously or asynchronously.
     * @param observer The observer to be notified.
     * @param numSites The maximum number of sites to return.
     */
    void setObserver(Observer observer, int numSites);

    /**
     * Blacklists a URL from the most visited URLs list.
     */
    void addBlacklistedUrl(String url);

    /**
     * Removes a URL from the most visited URLs blacklist.
     */
    void removeBlacklistedUrl(String url);

    /**
     * Records metrics about an impression of the surface with tiles.
     * @param tilesCount Count of tiles available on the surface at the moment.
     */
    void recordPageImpression(int tilesCount);

    /**
     * Records metrics about an impression of a tile including its source (local, server, ...) and
     * its visual type.
     * @param index The index of the tile that was impressed (0-based).
     * @param type The visual type of the item as defined in {@link TileVisualType}.
     * @param source The {@link TileSource} that generated this item.
     * @param url The URL of the tile.
     */
    void recordTileImpression(
            int index, @TileVisualType int type, @TileSource int source, String url);

    /**
     * Records the opening of a Most Visited Item.
     * @param index The index of the item that was opened.
     * @param type The visual type of the item as defined in {@link TileVisualType}.
     * @param source The {@link TileSource} that generated this item.
     */
    void recordOpenedMostVisitedItem(int index, @TileVisualType int type, @TileSource int source);
}
