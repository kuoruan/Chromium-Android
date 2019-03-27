// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.cached_image_fetcher;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;

/**
 * Provides cached image fetching capabilities. Uses getLastUsedProfile, which
 * will need to be changed when supporting multi-profile.
 */
public interface CachedImageFetcher {
    static CachedImageFetcher getInstance() {
        ThreadUtils.assertOnUiThread();
        return CachedImageFetcherImpl.getInstance();
    }

    /**
     * Report an event metric.
     *
     * @param eventId The event to be reported
     */
    void reportEvent(@CachedImageFetcherEvent int eventId);

    /**
     * Fetches the image at url with the desired size. Image is null if not
     * found or fails decoding.
     *
     * @param url The url to fetch the image from.
     * @param width The new bitmap's desired width (in pixels). If the given value is <= 0, the
     * image won't be scaled.
     * @param height The new bitmap's desired height (in pixels). If the given value is <= 0, the
     * image won't be scaled.
     * @param callback The function which will be called when the image is ready; will be called
     * with null result if fetching fails;
     */
    void fetchImage(String url, int width, int height, Callback<Bitmap> callback);

    /**
     * Alias of fetchImage that ignores scaling.
     *
     * @param url The url to fetch the image from.
     * @param callback The function which will be called when the image is ready; will be called
     * with null result if fetching fails;
     */
    void fetchImage(String url, Callback<Bitmap> callback);

    /**
     * Destroy method, called to clear resources to prevent leakage.
     */
    void destroy();
}
