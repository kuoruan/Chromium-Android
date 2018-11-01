// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.support.annotation.IntDef;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.download.ui.DownloadFilter;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Records download related metrics on Android.
 */
public class DownloadMetrics {
    // Tracks where the users interact with download files on Android. Used in histogram.
    // See AndroidDownloadOpenSource in enums.xml. The values used by this enum will be persisted
    // to server logs and should not be deleted, changed or reused.
    @IntDef({DownloadOpenSource.UNKNOWN, DownloadOpenSource.ANDROID_DOWNLOAD_MANAGER,
            DownloadOpenSource.DOWNLOAD_HOME, DownloadOpenSource.NOTIFICATION,
            DownloadOpenSource.NEW_TAP_PAGE, DownloadOpenSource.INFO_BAR,
            DownloadOpenSource.SNACK_BAR, DownloadOpenSource.AUTO_OPEN,
            DownloadOpenSource.DOWNLOAD_PROGRESS_INFO_BAR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DownloadOpenSource {
        int UNKNOWN = 0;
        int ANDROID_DOWNLOAD_MANAGER = 1;
        int DOWNLOAD_HOME = 2;
        int NOTIFICATION = 3;
        int NEW_TAP_PAGE = 4;
        int INFO_BAR = 5;
        int SNACK_BAR = 6;
        int AUTO_OPEN = 7;
        int DOWNLOAD_PROGRESS_INFO_BAR = 8;
        int NUM_ENTRIES = 9;
    }

    private static final String TAG = "DownloadMetrics";
    private static final int MAX_VIEW_RETENTION_MINUTES = 30 * 24 * 60;

    /**
     * Records download open source.
     * @param source The source where the user opened the download media file.
     * @param mimeType The mime type of the download.
     */
    public static void recordDownloadOpen(@DownloadOpenSource int source, String mimeType) {
        if (!isNativeLoaded()) {
            Log.w(TAG, "Native is not loaded, dropping download open metrics.");
            return;
        }

        @DownloadFilter.Type
        int type = DownloadFilter.fromMimeType(mimeType);
        if (type == DownloadFilter.Type.VIDEO) {
            RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.OpenSource.Video",
                    source, DownloadOpenSource.NUM_ENTRIES);
        } else if (type == DownloadFilter.Type.AUDIO) {
            RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.OpenSource.Audio",
                    source, DownloadOpenSource.NUM_ENTRIES);
        }
    }

    /**
     * Records how long does the user keep the download file on disk when the user tries to open
     * the file.
     * @param mimeType The mime type of the download.
     * @param startTime The start time of the download.
     */
    public static void recordDownloadViewRetentionTime(String mimeType, long startTime) {
        if (!isNativeLoaded()) {
            Log.w(TAG, "Native is not loaded, dropping download view retention metrics.");
            return;
        }

        @DownloadFilter.Type
        int type = DownloadFilter.fromMimeType(mimeType);
        int viewRetentionTimeMinutes = (int) ((System.currentTimeMillis() - startTime) / 60000);

        if (type == DownloadFilter.Type.VIDEO) {
            RecordHistogram.recordCustomCountHistogram(
                    "Android.DownloadManager.ViewRetentionTime.Video", viewRetentionTimeMinutes, 1,
                    MAX_VIEW_RETENTION_MINUTES, 50);
        } else if (type == DownloadFilter.Type.AUDIO) {
            RecordHistogram.recordCustomCountHistogram(
                    "Android.DownloadManager.ViewRetentionTime.Audio", viewRetentionTimeMinutes, 1,
                    MAX_VIEW_RETENTION_MINUTES, 50);
        }
    }

    /**
     * Records download directory type when a download is completed.
     * @param filePath The absolute file path of the download.
     */
    public static void recordDownloadDirectoryType(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;

        DownloadDirectoryProvider.getInstance().getAllDirectoriesOptions(
                (ArrayList<DirectoryOption> dirs) -> {
                    for (DirectoryOption dir : dirs) {
                        if (filePath.contains(dir.location)) {
                            RecordHistogram.recordEnumeratedHistogram(
                                    "MobileDownload.Location.Download.DirectoryType", dir.type,
                                    DirectoryOption.DownloadLocationDirectoryType.NUM_ENTRIES);
                            return;
                        }
                    }
                });
    }

    private static boolean isNativeLoaded() {
        return ChromeBrowserInitializer.getInstance(ContextUtils.getApplicationContext())
                .hasNativeInitializationCompleted();
    }
}
