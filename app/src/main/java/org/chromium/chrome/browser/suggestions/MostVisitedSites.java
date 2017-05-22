// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ntp.MostVisitedTileType.MostVisitedTileTypeEnum;
import org.chromium.chrome.browser.ntp.NTPTileSource.NTPTileSourceEnum;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Methods to bridge into native history to provide most recent urls, titles and thumbnails.
 */
public class MostVisitedSites {
    private long mNativeMostVisitedSitesBridge;

    /**
     * An interface for handling events in {@link MostVisitedSites}.
     */
    public interface Observer {
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
         * @param sources For each tile, the {@code NTPTileSource} that generated the tile.
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
     * MostVisitedSites constructor requires a valid user profile object.
     *
     * @param profile The profile for which to fetch most visited sites.
     */
    public MostVisitedSites(Profile profile) {
        mNativeMostVisitedSitesBridge = nativeInit(profile);
    }

    /**
     * Cleans up the C++ side of this class. This instance must not be used after calling destroy().
     */
    public void destroy() {
        assert mNativeMostVisitedSitesBridge != 0;
        nativeDestroy(mNativeMostVisitedSitesBridge);
        mNativeMostVisitedSitesBridge = 0;
    }

    /**
     * Sets the recipient for events from {@link MostVisitedSites}. The observer may be notified
     * synchronously or asynchronously.
     * @param observer The observer to be notified.
     * @param numSites The maximum number of sites to return.
     */
    public void setObserver(final Observer observer, int numSites) {
        Observer wrappedObserver = new Observer() {
            @Override
            public void onMostVisitedURLsAvailable(
                    String[] titles, String[] urls, String[] whitelistIconPaths, int[] sources) {
                // Don't notify observer if we've already been destroyed.
                if (mNativeMostVisitedSitesBridge != 0) {
                    observer.onMostVisitedURLsAvailable(titles, urls, whitelistIconPaths, sources);
                }
            }
            @Override
            public void onIconMadeAvailable(String siteUrl) {
                // Don't notify observer if we've already been destroyed.
                if (mNativeMostVisitedSitesBridge != 0) {
                    observer.onIconMadeAvailable(siteUrl);
                }
            }
        };
        nativeSetObserver(mNativeMostVisitedSitesBridge, wrappedObserver, numSites);
    }

    /**
     * Blacklists a URL from the most visited URLs list.
     */
    public void addBlacklistedUrl(String url) {
        nativeAddOrRemoveBlacklistedUrl(mNativeMostVisitedSitesBridge, url, true);
    }

    /**
     * Removes a URL from the most visited URLs blacklist.
     */
    public void removeBlacklistedUrl(String url) {
        nativeAddOrRemoveBlacklistedUrl(mNativeMostVisitedSitesBridge, url, false);
    }

    /**
     * Records metrics about an impression, including the sources (local, server, ...) and visual
     * types of the tiles that are shown.
     * @param tileTypes An array of values from MostVisitedTileType indicating the type of each
     *                  tile that's currently showing.
     * @param sources An array of values from NTPTileSource indicating the source of each tile
     *                that's currently showing.
     * @param tileUrls An array of strings indicating the URL of each tile.
     */
    public void recordPageImpression(int[] tileTypes, int[] sources, String[] tileUrls) {
        nativeRecordPageImpression(mNativeMostVisitedSitesBridge, tileTypes, sources, tileUrls);
    }

    /**
     * Records the opening of a Most Visited Item.
     * @param index The index of the item that was opened.
     * @param type The visual type of the item as defined in {@code MostVisitedTileType}.
     * @param source The {@code NTPTileSource} that generated this item.
     */
    public void recordOpenedMostVisitedItem(
            int index, @MostVisitedTileTypeEnum int type, @NTPTileSourceEnum int source) {
        nativeRecordOpenedMostVisitedItem(mNativeMostVisitedSitesBridge, index, type, source);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeMostVisitedSitesBridge);
    private native void nativeSetObserver(
            long nativeMostVisitedSitesBridge, Observer observer, int numSites);
    private native void nativeAddOrRemoveBlacklistedUrl(
            long nativeMostVisitedSitesBridge, String url, boolean addUrl);
    private native void nativeRecordPageImpression(
            long nativeMostVisitedSitesBridge, int[] tileTypes, int[] sources, String[] tileUrls);
    private native void nativeRecordOpenedMostVisitedItem(
            long nativeMostVisitedSitesBridge, int index, int tileType, int source);
}
