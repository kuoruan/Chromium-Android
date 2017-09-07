// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.util.Pair;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Concrete implementation of {@link ThumbnailProvider}.
 *
 * Thumbnails are cached and shared across all ThumbnailProviderImpls.  The cache itself is LRU and
 * limited in size.  It is automatically garbage collected under memory pressure.
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
     *  Weakly referenced cache containing thumbnails that can be deleted under memory pressure.
     *  Key in the cache is a pair of the filepath and the height/width of the thumbnail. Value is
     *  a pair of the thumbnail and its byte size.
     * */
    private static WeakReference<LruCache<Pair<String, Integer>, Pair<Bitmap, Integer>>>
            sBitmapCache = new WeakReference<>(null);

    /** Enqueues requests. */
    private final Handler mHandler;

    /** Queue of files to retrieve thumbnails for. */
    private final Deque<ThumbnailRequest> mRequestQueue;

    /** The native side pointer that is owned and destroyed by the Java class. */
    private long mNativeThumbnailProvider;

    /** Request that is currently having its thumbnail retrieved. */
    private ThumbnailRequest mCurrentRequest;

    public ThumbnailProviderImpl() {
        mHandler = new Handler(Looper.getMainLooper());
        mRequestQueue = new ArrayDeque<>();
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
        if (mRequestQueue.contains(request)) mRequestQueue.remove(request);
    }

    private void processQueue() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                processNextRequest();
            }
        });
    }

    private Bitmap getBitmapFromCache(String filepath, int bitmapSizePx) {
        Pair<Bitmap, Integer> cachedBitmapPair =
                getBitmapCache().get(Pair.create(filepath, bitmapSizePx));
        if (cachedBitmapPair == null) return null;
        Bitmap cachedBitmap = cachedBitmapPair.first;

        if (cachedBitmap == null) return null;
        assert !cachedBitmap.isRecycled();
        return cachedBitmap;
    }

    private void processNextRequest() {
        if (!isInitialized() || mCurrentRequest != null || mRequestQueue.isEmpty()) return;

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

            // We set the key pair to contain the required size instead of the minimal dimension so
            // that future fetches of this thumbnail can recognise the key in the cache.
            getBitmapCache().put(Pair.create(filePath, mCurrentRequest.getIconSize()),
                    Pair.create(bitmap, bitmap.getByteCount()));
            mCurrentRequest.onThumbnailRetrieved(filePath, bitmap);
        }

        mCurrentRequest = null;
        processQueue();
    }

    private boolean isInitialized() {
        return mNativeThumbnailProvider != 0;
    }

    private static LruCache<Pair<String, Integer>, Pair<Bitmap, Integer>> getBitmapCache() {
        ThreadUtils.assertOnUiThread();

        LruCache<Pair<String, Integer>, Pair<Bitmap, Integer>> cache =
                sBitmapCache == null ? null : sBitmapCache.get();
        if (cache != null) return cache;

        // Create a new weakly-referenced cache.
        cache = new LruCache<Pair<String, Integer>, Pair<Bitmap, Integer>>(MAX_CACHE_BYTES) {
            @Override
            protected int sizeOf(
                    Pair<String, Integer> thumbnailIdPair, Pair<Bitmap, Integer> thumbnail) {
                return thumbnail == null ? 0 : thumbnail.second;
            }
        };
        sBitmapCache = new WeakReference<>(cache);
        return cache;
    }

    /**
     * Evicts all cached thumbnails from previous fetches.
     */
    public static void clearCache() {
        getBitmapCache().evictAll();
    }

    private native long nativeInit();
    private native void nativeDestroy(long nativeThumbnailProvider);
    private native void nativeRetrieveThumbnail(
            long nativeThumbnailProvider, String filePath, int thumbnailSize);
}
