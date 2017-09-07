// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.graphics.Bitmap;

/** Provides thumbnails that represent different files. */
public interface ThumbnailProvider {
    /** Used to request the retrieval of a thumbnail. */
    public static interface ThumbnailRequest {
        /** Local storage path to the file. */
        String getFilePath();

        /** Called when a thumbnail is ready. */
        void onThumbnailRetrieved(String filePath, Bitmap thumbnail);

        /** The size of the thumbnail to be retrieved. */
        int getIconSize();
    }

    /** Destroys the class. */
    void destroy();

    /**
     * Calls {@link ThumbnailRequest#onThumbnailRetrieved} immediately if the thumbnail is cached.
     * Otherwise, asynchronously fetches the thumbnail from the provider and calls
     * {@link ThumbnailRequest#onThumbnailRetrieved} when the result is ready.
     * @param request Parameters that describe the thumbnail being retrieved.
     */
    void getThumbnail(ThumbnailRequest request);

    /** Removes a particular request from the pending queue. */
    void cancelRetrieval(ThumbnailRequest request);
}
