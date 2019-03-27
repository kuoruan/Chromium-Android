// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.cached_image_fetcher;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.support.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.DiscardableReferencePool;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.BitmapCache;
import org.chromium.chrome.browser.util.ConversionUtils;

/**
 * A wrapper around the CachedImageFetcher that also provides in-memory caching.
 */
public class InMemoryCachedImageFetcher implements CachedImageFetcher {
    private static final int DEFAULT_CACHE_SIZE = 20 * ConversionUtils.BYTES_PER_MEGABYTE; // 20mb
    private static final float PORTION_OF_AVAILABLE_MEMORY = 1.f / 8.f;

    // Will do the work if the image isn't cached in memory.
    private CachedImageFetcher mCachedImageFetcher;
    private BitmapCache mBitmapCache;

    /**
     * Create an instance with the default max cache size.
     *
     * @param referencePool Pool used to discard references when under memory pressure.
     */
    public InMemoryCachedImageFetcher(DiscardableReferencePool referencePool) {
        this(referencePool, DEFAULT_CACHE_SIZE);
    }

    /**
     * Create an instance with a custom max cache size.
     *
     * @param referencePool Pool used to discard references when under memory pressure.
     * @param cacheSize The cache size to use (in bytes), may be smaller depending on the device's
     *         memory.
     */
    public InMemoryCachedImageFetcher(DiscardableReferencePool referencePool, int cacheSize) {
        int actualCacheSize = determineCacheSize(cacheSize);
        mBitmapCache = new BitmapCache(referencePool, actualCacheSize);
        mCachedImageFetcher = CachedImageFetcher.getInstance();
    }

    @Override
    public void reportEvent(@CachedImageFetcherEvent int eventId) {
        mCachedImageFetcher.reportEvent(eventId);
    }

    @Override
    public void destroy() {
        mCachedImageFetcher.destroy();
        mCachedImageFetcher = null;

        mBitmapCache.destroy();
        mBitmapCache = null;
    }

    @Override
    public void fetchImage(String url, int width, int height, Callback<Bitmap> callback) {
        Bitmap cachedBitmap = tryToGetBitmap(url, width, height);
        if (cachedBitmap == null) {
            if (mCachedImageFetcher == null) {
                callback.onResult(null);
                return;
            }

            mCachedImageFetcher.fetchImage(url, width, height, (@Nullable Bitmap bitmap) -> {
                bitmap = tryToResizeImage(bitmap, width, height);
                storeBitmap(bitmap, url, width, height);
                callback.onResult(bitmap);
            });
        } else {
            reportEvent(CachedImageFetcherEvent.JAVA_IN_MEMORY_CACHE_HIT);
            callback.onResult(cachedBitmap);
        }
    }

    @Override
    public void fetchImage(String url, Callback<Bitmap> callback) {
        fetchImage(url, 0, 0, callback);
    }

    /**
     * Try to get a bitmap from the in-memory cache.
     *
     * @param url The url of the image.
     * @param width The width (in pixels) of the image.
     * @param height The height (in pixels) of the image.
     * @return The Bitmap stored in memory or null.
     */
    @VisibleForTesting
    Bitmap tryToGetBitmap(String url, int width, int height) {
        if (mBitmapCache == null) return null;

        String key = encodeCacheKey(url, width, height);
        return mBitmapCache.getBitmap(key);
    }

    /**
     * Store the bitmap in memory.
     *
     * @param url The url of the image.
     * @param width The width (in pixels) of the image.
     * @param height The height (in pixels) of the image.
     */
    private void storeBitmap(@Nullable Bitmap bitmap, String url, int width, int height) {
        if (bitmap == null || mBitmapCache == null) {
            return;
        }

        String key = encodeCacheKey(url, width, height);
        mBitmapCache.putBitmap(key, bitmap);
    }

    /**
     * Use the given parameters to encode a key used in the String -> Bitmap mapping.
     *
     * @param url The url of the image.
     * @param width The width (in pixels) of the image.
     * @param height The height (in pixels) of the image.
     * @return The key for the BitmapCache.
     */
    private String encodeCacheKey(String url, int width, int height) {
        // Encoding for cache key is:
        // <url>/<width>/<height>.
        return url + "/" + width + "/" + height;
    }

    /**
     * Size the cache size depending on available memory and the client's preferred cache size.
     *
     * @param preferredCacheSize The preferred cache size (in bytes).
     * @return The actual size of the cache (in bytes).
     */
    private int determineCacheSize(int preferredCacheSize) {
        final Runtime runtime = Runtime.getRuntime();
        final long usedMem = runtime.totalMemory() - runtime.freeMemory();
        final long maxHeapSize = runtime.maxMemory();
        final long availableMemory = maxHeapSize - usedMem;

        // Try to size the cache according to client's wishes. If there's not enough space, then
        // take a portion of available memory.
        final int maxCacheUsage = (int) (availableMemory * PORTION_OF_AVAILABLE_MEMORY);
        return Math.min(maxCacheUsage, preferredCacheSize);
    }

    /**
     * Try to resize the given image if the conditions are met.
     *
     * @param bitmap The input bitmap, will be recycled if scaled.
     * @param width The desired width of the output.
     * @param height The desired height of the output.
     *
     * @return The resized image, or the original image if the  conditions aren't met.
     */
    @VisibleForTesting
    Bitmap tryToResizeImage(@Nullable Bitmap bitmap, int width, int height) {
        if (bitmap != null && width > 0 && height > 0 && bitmap.getWidth() != width
                && bitmap.getHeight() != height) {
            /* The resizing rules are the as follows:
               (1) The image will be scaled up (if smaller) in a way that maximizes the area of the
               source bitmap that's in the destination bitmap.
               (2) A crop is made in the middle of the bitmap for the given size (width, height).
               The x/y are placed appropriately (conceptually just think of it as a properly sized
               chunk taken from the middle). */
            return ThumbnailUtils.extractThumbnail(
                    bitmap, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        } else {
            return bitmap;
        }
    }

    /** Test constructor. */
    @VisibleForTesting
    InMemoryCachedImageFetcher(BitmapCache bitmapCache, CachedImageFetcher cachedImageFetcher) {
        mBitmapCache = bitmapCache;
        mCachedImageFetcher = cachedImageFetcher;
    }
}