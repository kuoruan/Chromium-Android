// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.cached_image_fetcher;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Provides access to native implementations of CachedImageFetcher for the given profile.
 */
@JNINamespace("image_fetcher")
class CachedImageFetcherBridge {
    private long mNativeCachedImageFetcherBridge;

    /**
     * Creates a CachedImageFetcherBridge for accessing the native CachedImageFetcher
     * implementation.
     */
    public CachedImageFetcherBridge(Profile profile) {
        mNativeCachedImageFetcherBridge = nativeInit(profile);
    }

    /** Cleans up native half of bridge. */
    public void destroy() {
        assert mNativeCachedImageFetcherBridge != 0;
        nativeDestroy(mNativeCachedImageFetcherBridge);
        mNativeCachedImageFetcherBridge = 0;
    }

    /**
     * Get the full path of the given url on disk.
     *
     * @param url The url to hash.
     * @return The full path to the resource on disk.
     */
    public String getFilePath(String url) {
        assert mNativeCachedImageFetcherBridge != 0;
        return nativeGetFilePath(mNativeCachedImageFetcherBridge, url);
    }

    /**
     * Fetch the image from native.
     *
     * @param url The url to fetch.
     * @param width The width to use when resizing the image.
     * @param height The height to use when resizing the image.
     * @param callback The callback to call when the image is ready.
     */
    public void fetchImage(String url, int width, int height, Callback<Bitmap> callback) {
        assert mNativeCachedImageFetcherBridge != 0;
        nativeFetchImage(mNativeCachedImageFetcherBridge, url, width, height, callback);
    }

    /**
     * Report a metrics event.
     *
     * @param eventId The event to report.
     */
    public void reportEvent(@CachedImageFetcherEvent int eventId) {
        assert mNativeCachedImageFetcherBridge != 0;
        nativeReportEvent(mNativeCachedImageFetcherBridge, eventId);
    }

    /**
     * Report a timing event for a cache hit.
     *
     * @param startTimeMillis The start time (in milliseconds) of the request, used to measure the
     * total duration.
     */
    public void reportCacheHitTime(long startTimeMillis) {
        assert mNativeCachedImageFetcherBridge != 0;
        nativeReportCacheHitTime(mNativeCachedImageFetcherBridge, startTimeMillis);
    }

    // Native methods
    private static native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeCachedImageFetcherBridge);
    private native String nativeGetFilePath(long nativeCachedImageFetcherBridge, String url);
    private native void nativeFetchImage(long nativeCachedImageFetcherBridge, String url,
            int widthPx, int heightPx, Callback<Bitmap> callback);
    private native void nativeReportEvent(long nativeCachedImageFetcherBridge, int eventId);
    private native void nativeReportCacheHitTime(
            long nativeCachedImageFetcherBridge, long startTimeMillis);
}
