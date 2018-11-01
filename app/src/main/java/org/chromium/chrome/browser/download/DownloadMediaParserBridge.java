// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import org.chromium.base.Callback;

/**
 * A JNI bridge that owns a native side DownloadMediaParser, which parses media file safely in an
 * utility process.
 */
public class DownloadMediaParserBridge {
    private long mNativeDownloadMediaParserBridge;

    public DownloadMediaParserBridge() {
        mNativeDownloadMediaParserBridge = nativeInit();
    }

    /**
     * Destroys the native object of DownloadMediaParser. This will result in the utility process
     * being destroyed.
     */
    public void destory() {
        nativeDestory(mNativeDownloadMediaParserBridge);
        mNativeDownloadMediaParserBridge = 0;
    }

    /**
     * Parses a media file to retrieve media metadata and video thumbnail.
     * @param mimeType The mime type of the media file.
     * @param filePath The absolute path of the media file.
     * @param totalSize Total size of the media file.
     * @param callback Callback to get the result.
     */
    public void parseMediaFile(
            String mimeType, String filePath, long totalSize, Callback<Boolean> callback) {
        if (mNativeDownloadMediaParserBridge != 0) {
            nativeParseMediaFile(
                    mNativeDownloadMediaParserBridge, mimeType, filePath, totalSize, callback);
        }
    }

    private native long nativeInit();
    private native void nativeDestory(long nativeDownloadMediaParserBridge);
    private native void nativeParseMediaFile(long nativeDownloadMediaParserBridge, String mimeType,
            String filePath, long totalSize, Callback<Boolean> callback);
}
