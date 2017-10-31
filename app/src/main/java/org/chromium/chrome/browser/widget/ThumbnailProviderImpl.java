// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.graphics.Bitmap;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.DiscardableReferencePool;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Concrete implementation of {@link ThumbnailProvider}.
 *
 * Thumbnails are cached and shared across all ThumbnailProviderImpls. There are two levels
 * of caches: One static cache for deduplication (or canonicalization) of bitmaps, and one
 * per-object cache for storing recently used bitmaps. The deduplication cache uses weak references
 * to allow bitmaps to be garbage-collected once they are no longer in use. As long as there is at
 * least one strong reference to a bitmap, it is not going to be GC'd and will therefore stay in the
 * cache. This ensures that there is never more than one (reachable) copy of a bitmap in memory.
 * The {@link RecentlyUsedCache} is limited in size and dropped under memory pressure, or when the
 * object is destroyed.
 *
 * A queue of requests is maintained in FIFO order.  Missing thumbnails are retrieved asynchronously
 * by the native ThumbnailProvider, which is owned and destroyed by the Java class.
 *
 * TODO(dfalcantara): Figure out how to send requests simultaneously to the utility process without
 *                    duplicating work to decode the same image for two different requests.
 */
public class ThumbnailProviderImpl implements ThumbnailProvider {
    /** 5 MB of thumbnails should be enough for everyone. */
    private static final int MAX_CACHE_BYTES = 5 * 1024 * 1024;

    /**
     * Least-recently-used cache that falls back to the deduplication cache on misses.
     * This propagates bitmaps that were only in the deduplication cache back into the LRU cache
     * and also moves them to the front to ensure correct eviction order.
     * Cache key is a pair of the filepath and the height/width of the thumbnail. Value is
     * the thumbnail.
     */
    private static class RecentlyUsedCache extends LruCache<Pair<String, Integer>, Bitmap> {
        private RecentlyUsedCache() {
            super(MAX_CACHE_BYTES);
        }

        @Override
        protected Bitmap create(Pair<String, Integer> key) {
            WeakReference<Bitmap> cachedBitmap = sDeduplicationCache.get(key);
            return cachedBitmap == null ? null : cachedBitmap.get();
        }

        @Override
        protected int sizeOf(Pair<String, Integer> key, Bitmap thumbnail) {
            return thumbnail.getByteCount();
        }
    }

    /**
     * Discardable reference to the {@link RecentlyUsedCache} that can be dropped under memory
     * pressure.
     */
    private DiscardableReferencePool.DiscardableReference<RecentlyUsedCache> mBitmapCache;

    /**
     * The reference pool that contains the {@link #mBitmapCache}. Used to recreate a new cache
     * after the old one has been dropped.
     */
    private final DiscardableReferencePool mReferencePool;

    /**
     * Static cache used for deduplicating bitmaps. The key is a pair of file name and thumbnail
     * size (as for the {@link #mBitmapCache}.
     */
    private static Map<Pair<String, Integer>, WeakReference<Bitmap>> sDeduplicationCache =
            new HashMap<>();

    /** Queue of files to retrieve thumbnails for. */
    private final Deque<ThumbnailRequest> mRequestQueue = new ArrayDeque<>();

    /** The native side pointer that is owned and destroyed by the Java class. */
    private long mNativeThumbnailProvider;

    /** Request that is currently having its thumbnail retrieved. */
    private ThumbnailRequest mCurrentRequest;

    public ThumbnailProviderImpl(DiscardableReferencePool referencePool) {
        ThreadUtils.assertOnUiThread();
        mReferencePool = referencePool;
        mBitmapCache = referencePool.put(new RecentlyUsedCache());
        mNativeThumbnailProvider = nativeInit();
    }

    @Override
    public void destroy() {
        ThreadUtils.assertOnUiThread();
        nativeDestroy(mNativeThumbnailProvider);
        mNativeThumbnailProvider = 0;
    }

    /**
     * The returned bitmap will have at least one of its dimensions smaller than or equal to the
     * size specified in the request.
     *
     * @param request Parameters that describe the thumbnail being retrieved.
     */
    @Override
    public void getThumbnail(ThumbnailRequest request) {
        ThreadUtils.assertOnUiThread();
        String filePath = request.getFilePath();
        if (TextUtils.isEmpty(filePath)) return;

        Bitmap cachedBitmap = getBitmapFromCache(filePath, request.getIconSize());
        if (cachedBitmap != null) {
            request.onThumbnailRetrieved(filePath, cachedBitmap);
            return;
        }

        mRequestQueue.offer(request);
        processQueue();
    }

    /** Removes a particular file from the pending queue. */
    @Override
    public void cancelRetrieval(ThumbnailRequest request) {
        ThreadUtils.assertOnUiThread();
        if (mRequestQueue.contains(request)) mRequestQueue.remove(request);
    }

    private void processQueue() {
        ThreadUtils.postOnUiThread(this::processNextRequest);
    }

    private RecentlyUsedCache getBitmapCache() {
        RecentlyUsedCache bitmapCache = mBitmapCache.get();
        if (bitmapCache == null) {
            bitmapCache = new RecentlyUsedCache();
            mBitmapCache = mReferencePool.put(bitmapCache);
        }
        return bitmapCache;
    }

    private Bitmap getBitmapFromCache(String filepath, int bitmapSizePx) {
        Bitmap cachedBitmap = getBitmapCache().get(Pair.create(filepath, bitmapSizePx));
        assert cachedBitmap == null || !cachedBitmap.isRecycled();
        return cachedBitmap;
    }

    private void processNextRequest() {
        ThreadUtils.assertOnUiThread();
        if (!isInitialized() || mCurrentRequest != null) return;
        if (mRequestQueue.isEmpty()) {
            // If the request queue is empty, schedule compaction for when the main loop is idling.
            Looper.myQueue().addIdleHandler(() -> {
                compactDeduplicationCache();
                return false;
            });
            return;
        }

        mCurrentRequest = mRequestQueue.poll();
        String currentFilePath = mCurrentRequest.getFilePath();

        Bitmap cachedBitmap = getBitmapFromCache(currentFilePath, mCurrentRequest.getIconSize());
        if (cachedBitmap == null) {
            // Asynchronously process the file to make a thumbnail.
            nativeRetrieveThumbnail(
                    mNativeThumbnailProvider, currentFilePath, mCurrentRequest.getIconSize());
        } else {
            // Send back the already-processed file.
            onThumbnailRetrieved(currentFilePath, cachedBitmap);
        }
    }

    @CalledByNative
    private void onThumbnailRetrieved(String filePath, @Nullable Bitmap bitmap) {
        if (bitmap != null) {
            // The bitmap returned here is retrieved from the native side. The image decoder there
            // scales down the image (if it is too big) so that one of its sides is smaller than or
            // equal to the required size. We check here that the returned image satisfies this
            // criteria.
            assert Math.min(bitmap.getWidth(), bitmap.getHeight()) <= mCurrentRequest.getIconSize();
            assert TextUtils.equals(mCurrentRequest.getFilePath(), filePath);

            // We set the key pair to contain the required size instead of the minimal dimension
            // so that future fetches of this thumbnail can recognize the key in the cache.
            Pair<String, Integer> key = Pair.create(filePath, mCurrentRequest.getIconSize());
            if (!SysUtils.isLowEndDevice()) {
                getBitmapCache().put(key, bitmap);
            }
            sDeduplicationCache.put(key, new WeakReference<>(bitmap));
            mCurrentRequest.onThumbnailRetrieved(filePath, bitmap);
        }

        mCurrentRequest = null;
        processQueue();
    }

    /**
     * Compacts the deduplication cache by removing all entries that have been cleared by the
     * garbage collector.
     */
    private void compactDeduplicationCache() {
        // Too many angle brackets for clang-format :-(
        // clang-format off
        for (Iterator<Map.Entry<Pair<String, Integer>, WeakReference<Bitmap>>> it =
                sDeduplicationCache.entrySet().iterator(); it.hasNext();) {
            // clang-format on
            if (it.next().getValue().get() == null) it.remove();
        }
    }

    private boolean isInitialized() {
        return mNativeThumbnailProvider != 0;
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeThumbnailProvider);
    private native void nativeRetrieveThumbnail(
            long nativeThumbnailProvider, String filePath, int thumbnailSize);
}
