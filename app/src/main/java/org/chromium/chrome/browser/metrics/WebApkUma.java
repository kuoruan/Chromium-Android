// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import android.content.ContentResolver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.preferences.website.SiteSettingsCategory;
import org.chromium.chrome.browser.preferences.website.Website;
import org.chromium.chrome.browser.preferences.website.WebsitePermissionsFetcher;
import org.chromium.webapk.lib.common.WebApkConstants;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for WebAPKs. NOTE: Histogram names and values are defined in
 * tools/metrics/histograms/histograms.xml. Please update that file if any change is made.
 */
public class WebApkUma {
    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    public static final int UPDATE_REQUEST_SENT_FIRST_TRY = 0;
    public static final int UPDATE_REQUEST_SENT_ONSTOP = 1;
    public static final int UPDATE_REQUEST_SENT_WHILE_WEBAPK_IN_FOREGROUND = 2;
    public static final int UPDATE_REQUEST_SENT_MAX = 3;

    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    // The queued request times shouldn't exceed three.
    public static final int UPDATE_REQUEST_QUEUED_ONCE = 0;
    public static final int UPDATE_REQUEST_QUEUED_TWICE = 1;
    public static final int UPDATE_REQUEST_QUEUED_THREE_TIMES = 2;
    public static final int UPDATE_REQUEST_QUEUED_MAX = 3;

    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    public static final int GOOGLE_PLAY_INSTALL_SUCCESS = 0;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_NO_DELEGATE = 1;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_TO_CONNECT_TO_SERVICE = 2;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_CALLER_VERIFICATION_FAILURE = 3;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_POLICY_VIOLATION = 4;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_API_DISABLED = 5;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_REQUEST_FAILED = 6;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_DOWNLOAD_CANCELLED = 7;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_DOWNLOAD_ERROR = 8;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_INSTALL_ERROR = 9;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_INSTALL_TIMEOUT = 10;
    public static final int GOOGLE_PLAY_INSTALL_REQUEST_FAILED_POLICY_DISABLED = 11;
    public static final int GOOGLE_PLAY_INSTALL_REQUEST_FAILED_UNKNOWN_ACCOUNT = 12;
    public static final int GOOGLE_PLAY_INSTALL_REQUEST_FAILED_NETWORK_ERROR = 13;
    public static final int GOOGLE_PLAY_INSTALL_REQUSET_FAILED_RESOLVE_ERROR = 14;
    public static final int GOOGLE_PLAY_INSTALL_RESULT_MAX = 14;

    public static final String HISTOGRAM_UPDATE_REQUEST_SENT =
            "WebApk.Update.RequestSent";

    public static final String HISTOGRAM_UPDATE_REQUEST_QUEUED = "WebApk.Update.RequestQueued";

    private static final int WEBAPK_OPEN_MAX = 3;
    public static final int WEBAPK_OPEN_LAUNCH_SUCCESS = 0;
    // Obsolete: WEBAPK_OPEN_NO_LAUNCH_INTENT = 1;
    public static final int WEBAPK_OPEN_ACTIVITY_NOT_FOUND = 2;

    /**
     * Records the time point when a request to update a WebAPK is sent to the WebAPK Server.
     * @param type representing when the update request is sent to the WebAPK server.
     */
    public static void recordUpdateRequestSent(int type) {
        assert type >= 0 && type < UPDATE_REQUEST_SENT_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_UPDATE_REQUEST_SENT,
                type, UPDATE_REQUEST_SENT_MAX);
    }

    /**
     * Records the times that an update request has been queued once, twice and three times before
     * sending to WebAPK server.
     * @param times representing the times that an update has been queued.
     */
    public static void recordUpdateRequestQueued(int times) {
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_UPDATE_REQUEST_QUEUED, times,
                UPDATE_REQUEST_QUEUED_MAX);
    }

    /**
     * When a user presses on the "Open WebAPK" menu item, this records whether the WebAPK was
     * opened successfully.
     * @param type Result of trying to open WebAPK.
     */
    public static void recordWebApkOpenAttempt(int type) {
        assert type >= 0 && type < WEBAPK_OPEN_MAX;
        RecordHistogram.recordEnumeratedHistogram("WebApk.OpenFromMenu", type, WEBAPK_OPEN_MAX);
    }

    /** Records whether a WebAPK has permission to display notifications. */
    public static void recordNotificationPermissionStatus(boolean permissionEnabled) {
        int status = permissionEnabled ? 1 : 0;
        RecordHistogram.recordEnumeratedHistogram(
                "WebApk.Notification.Permission.Status", status, 2);
    }

    /**
     * Records whether installing a WebAPK from Google Play succeeded. If not, records the reason
     * that the install failed.
     */
    public static void recordGooglePlayInstallResult(int result) {
        assert result >= 0 && result < GOOGLE_PLAY_INSTALL_RESULT_MAX;
        RecordHistogram.recordEnumeratedHistogram(
                "WebApk.Install.GooglePlayInstallResult", result, GOOGLE_PLAY_INSTALL_RESULT_MAX);
    }

    /** Records the error code if installing a WebAPK via Google Play fails. */
    public static void recordGooglePlayInstallErrorCode(int errorCode) {
        // Don't use an enumerated histogram as there are > 30 potential error codes. In practice,
        // a given client will always get the same error code.
        RecordHistogram.recordSparseSlowlyHistogram(
                "WebApk.Install.GooglePlayErrorCode", Math.min(errorCode, 1000));
    }

    /**
     * Records whether updating a WebAPK from Google Play succeeded. If not, records the reason
     * that the update failed.
     */
    public static void recordGooglePlayUpdateResult(int result) {
        assert result >= 0 && result < GOOGLE_PLAY_INSTALL_RESULT_MAX;
        RecordHistogram.recordEnumeratedHistogram(
                "WebApk.Update.GooglePlayUpdateResult", result, GOOGLE_PLAY_INSTALL_RESULT_MAX);
    }

    /** Records the duration of a WebAPK session (from launch/foreground to background). */
    public static void recordWebApkSessionDuration(long duration) {
        RecordHistogram.recordLongTimesHistogram(
                "WebApk.Session.TotalDuration", duration, TimeUnit.MILLISECONDS);
    }

    /** Records the amount of time that it takes to bind to the play install service. */
    public static void recordGooglePlayBindDuration(long durationMs) {
        RecordHistogram.recordTimesHistogram(
                "WebApk.Install.GooglePlayBindDuration", durationMs, TimeUnit.MILLISECONDS);
    }

    /** Records the current Shell APK version. */
    public static void recordShellApkVersion(int shellApkVersion, String packageName) {
        String name = packageName.startsWith(WebApkConstants.WEBAPK_PACKAGE_PREFIX)
                ? "WebApk.ShellApkVersion.BrowserApk"
                : "WebApk.ShellApkVersion.UnboundApk";
        RecordHistogram.recordSparseSlowlyHistogram(name, shellApkVersion);
    }

    /**
     * Recorded when a WebAPK is launched from the homescreen. Records the time elapsed since the
     * previous WebAPK launch. Not recorded the first time that a WebAPK is launched.
     */
    public static void recordLaunchInterval(long intervalMs) {
        RecordHistogram.recordCustomTimesHistogram("WebApk.LaunchInterval", intervalMs,
                TimeUnit.HOURS.toMillis(1), TimeUnit.DAYS.toMillis(30), TimeUnit.MILLISECONDS, 50);
    }

    /**
     * Log the estimated amount of space above the minimum free space threshold that can be used
     * for WebAPK installation in UMA.
     */
    @SuppressWarnings("deprecation")
    public static void logAvailableSpaceAboveLowSpaceLimitInUMA(boolean installSucceeded) {
        // ContentResolver APIs are usually heavy, do it in AsyncTask.
        new AsyncTask<Void, Void, Long>() {
            long mPartitionAvailableBytes;
            @Override
            protected Long doInBackground(Void... params) {
                StatFs partitionStats =
                        new StatFs(Environment.getDataDirectory().getAbsolutePath());
                long partitionTotalBytes;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mPartitionAvailableBytes = partitionStats.getAvailableBytes();
                    partitionTotalBytes = partitionStats.getTotalBytes();
                } else {
                    // these APIs were deprecated in API level 18.
                    long blockSize = partitionStats.getBlockSize();
                    mPartitionAvailableBytes = blockSize
                            * (long) partitionStats.getAvailableBlocks();
                    partitionTotalBytes = blockSize * (long) partitionStats.getBlockCount();
                }
                return getLowSpaceLimitBytes(partitionTotalBytes);
            }

            @Override
            protected void onPostExecute(Long minimumFreeBytes) {
                long availableBytesForInstallation = mPartitionAvailableBytes - minimumFreeBytes;
                int availableSpaceMb = (int) (availableBytesForInstallation / 1024L / 1024L);
                // Bound the number to [-1000, 500] and round down to the nearest multiple of 10MB
                // to avoid exploding the histogram.
                availableSpaceMb = Math.max(-1000, availableSpaceMb);
                availableSpaceMb = Math.min(500, availableSpaceMb);
                availableSpaceMb = availableSpaceMb / 10 * 10;

                if (installSucceeded) {
                    RecordHistogram.recordSparseSlowlyHistogram(
                            "WebApk.Install.AvailableSpace.Success", availableSpaceMb);
                } else {
                    RecordHistogram.recordSparseSlowlyHistogram(
                            "WebApk.Install.AvailableSpace.Fail", availableSpaceMb);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void logCacheSizeInUMA() {
        new AsyncTask<Void, Void, Integer>() {
            private long getDirectorySizeInByte(File dir) {
                if (dir == null) return 0;
                if (!dir.isDirectory()) return dir.length();

                long sizeInByte = 0;
                try {
                    File[] files = dir.listFiles();
                    if (files == null) return 0;

                    for (File file : files) {
                        sizeInByte += getDirectorySizeInByte(file);
                    }
                } catch (SecurityException e) {
                    return 0;
                }
                return sizeInByte;
            }

            @Override
            protected Integer doInBackground(Void... params) {
                long cacheSizeInByte =
                        getDirectorySizeInByte(ContextUtils.getApplicationContext().getCacheDir());
                return Math.min(2000, (int) (cacheSizeInByte / 1024L / 1024L / 10L * 10L));
            }

            @Override
            protected void onPostExecute(Integer cacheSizeInMb) {
                RecordHistogram.recordSparseSlowlyHistogram(
                        "WebApk.Install.ChromeCacheSize.Fail", cacheSizeInMb);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void logUnimportantStorageSizeInUMA() {
        WebsitePermissionsFetcher fetcher =
                new WebsitePermissionsFetcher(new UnimportantStorageSizeCalculator());
        fetcher.fetchPreferencesForCategory(
                SiteSettingsCategory.fromString(SiteSettingsCategory.CATEGORY_USE_STORAGE));
    }

    /**
     * Mirror the system-derived calculation of reserved bytes and return that value.
     */
    private static long getLowSpaceLimitBytes(long partitionTotalBytes) {
        // Copied from android/os/storage/StorageManager.java
        final int defaultThresholdPercentage = 10;
        // Copied from android/os/storage/StorageManager.java
        final long defaultThresholdMaxBytes = 500 * 1024 * 1024;
        // Copied from android/provider/Settings.java
        final String sysStorageThresholdPercentage = "sys_storage_threshold_percentage";
        // Copied from android/provider/Settings.java
        final String sysStorageThresholdMaxBytes = "sys_storage_threshold_max_bytes";

        ContentResolver resolver = ContextUtils.getApplicationContext().getContentResolver();
        int minFreePercent = 0;
        long minFreeBytes = 0;

        // Retrieve platform-appropriate values first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            minFreePercent = Settings.Global.getInt(
                    resolver, sysStorageThresholdPercentage, defaultThresholdPercentage);
            minFreeBytes = Settings.Global.getLong(
                    resolver, sysStorageThresholdMaxBytes, defaultThresholdMaxBytes);
        } else {
            minFreePercent = Settings.Secure.getInt(
                    resolver, sysStorageThresholdPercentage, defaultThresholdPercentage);
            minFreeBytes = Settings.Secure.getLong(
                    resolver, sysStorageThresholdMaxBytes, defaultThresholdMaxBytes);
        }

        long minFreePercentInBytes = (partitionTotalBytes * minFreePercent) / 100;

        return Math.min(minFreeBytes, minFreePercentInBytes);
    }

    private static class UnimportantStorageSizeCalculator
            implements WebsitePermissionsFetcher.WebsitePermissionsCallback {
        @Override
        public void onWebsitePermissionsAvailable(Collection<Website> sites) {
            long siteStorageSize = 0;
            long importantSiteStorageTotal = 0;
            for (Website site : sites) {
                siteStorageSize += site.getTotalUsage();
                if (site.getLocalStorageInfo() != null
                        && site.getLocalStorageInfo().isDomainImportant()) {
                    importantSiteStorageTotal += site.getTotalUsage();
                }
            }
            long unimportantSiteStorageTotal = siteStorageSize - importantSiteStorageTotal;
            int unimportantSiteStorageTotalMb =
                    (int) (unimportantSiteStorageTotal / 1024L / 1024L / 10L * 10L);
            unimportantSiteStorageTotalMb = Math.min(unimportantSiteStorageTotalMb, 1000);

            RecordHistogram.recordSparseSlowlyHistogram(
                    "WebApk.Install.ChromeUnimportantStorage.Fail", unimportantSiteStorageTotalMb);
        }
    }
}
