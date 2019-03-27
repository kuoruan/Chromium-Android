// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;

/**
 * Contains local media metadata and thumbnails.
 */
public class DownloadMediaData {
    /**
     * The duration of the media file in seconds. Or -1 if no duration in the metadata.
     */
    public final double duration;

    /**
     * Title of the media.
     */
    public final String title;

    /**
     * Artist of the media.
     */
    public final String artist;

    /**
     * A thumbnail represents the media. Can be a poster image retrieved with metadata for both
     * audio and video file. Or retrieved from a video key frame for video files.
     */
    public final Bitmap thumbnail;

    @CalledByNative
    private DownloadMediaData(double duration, String title, String artist, Bitmap thumbnail) {
        this.duration = duration;
        this.title = title;
        this.artist = artist;
        this.thumbnail = thumbnail;
    }
}
