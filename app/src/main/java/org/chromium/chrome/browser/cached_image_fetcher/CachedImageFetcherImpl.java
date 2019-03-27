// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.cached_image_fetcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.task.AsyncTask;
import org.chromium.chrome.browser.profiles.Profile;

import java.io.File;

/**
 * Implementation that uses a disk cache.
 */
public class CachedImageFetcherImpl implements CachedImageFetcher {
    private static CachedImageFetcherImpl sInstance;

    public static CachedImageFetcherImpl getInstance() {
        ThreadUtils.assertOnUiThread();

        if (sInstance == null) {
            sInstance = new CachedImageFetcherImpl(Profile.getLastUsedProfile());
        }

        return sInstance;
    }

    // The native bridge.
    private CachedImageFetcherBridge mCachedImageFetcherBridge;

    /**
     * Creates a CachedImageFetcher for the current user.
     *
     * @param profile Profile of the user we are fetching for.
     */
    private CachedImageFetcherImpl(Profile profile) {
        this(new CachedImageFetcherBridge(profile));
    }

    /**
     * Creates a CachedImageFetcher for testing.
     *
     * @param bridge Mock bridge to use.
     */
    @VisibleForTesting
    CachedImageFetcherImpl(CachedImageFetcherBridge bridge) {
        mCachedImageFetcherBridge = bridge;
    }

    @Override
    public void reportEvent(@CachedImageFetcherEvent int eventId) {
        mCachedImageFetcherBridge.reportEvent(eventId);
    }

    @Override
    public void destroy() {
        // Do nothing, this lives for the lifetime of the application.
    }

    @Override
    public void fetchImage(String url, int width, int height, Callback<Bitmap> callback) {
        fetchImageImpl(url, width, height, callback);
    }

    @Override
    public void fetchImage(String url, Callback<Bitmap> callback) {
        fetchImageImpl(url, 0, 0, callback);
    }

    /**
     * Starts an AsyncTask to first check the disk for the desired image, then
     * fetches from the network if it isn't found.
     *
     * @param url The url to fetch the image from.
     * @param width The new bitmap's desired width (in pixels).
     * @param height The new bitmap's desired height (in pixels).
     * @param callback The function which will be called when the image is ready.
     */
    @VisibleForTesting
    void fetchImageImpl(String url, int width, int height, Callback<Bitmap> callback) {
        long startTimeMillis = System.currentTimeMillis();
        String filePath = mCachedImageFetcherBridge.getFilePath(url);
        new AsyncTask<Bitmap>() {
            @Override
            protected Bitmap doInBackground() {
                return tryToLoadImageFromDisk(filePath);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    callback.onResult(bitmap);
                    reportEvent(CachedImageFetcherEvent.JAVA_DISK_CACHE_HIT);
                    mCachedImageFetcherBridge.reportCacheHitTime(startTimeMillis);
                } else {
                    mCachedImageFetcherBridge.fetchImage(url, width, height, callback);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Wrapper function to decode a file for disk, useful for testing. */
    @VisibleForTesting
    Bitmap tryToLoadImageFromDisk(String filePath) {
        if (new File(filePath).exists()) {
            return BitmapFactory.decodeFile(filePath, null);
        } else {
            return null;
        }
    }
}
