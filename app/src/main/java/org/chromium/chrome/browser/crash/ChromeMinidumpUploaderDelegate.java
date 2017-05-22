// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.crash;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PersistableBundle;

import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.components.minidump_uploader.MinidumpUploaderDelegate;
import org.chromium.components.minidump_uploader.util.CrashReportingPermissionManager;

import java.io.File;

/**
 * Chrome-specific implementations for minidump uploading logic.
 */
@TargetApi(Build.VERSION_CODES.M)
public class ChromeMinidumpUploaderDelegate implements MinidumpUploaderDelegate {
    // PersistableBundle keys:
    static final String IS_CLIENT_IN_METRICS_SAMPLE = "isClientInMetricsSample";
    static final String IS_CRASH_UPLOAD_DISABLED_BY_COMMAND_LINE =
            "isCrashUploadDisabledByCommandLine";
    static final String IS_UPLOAD_ENABLED_FOR_TESTS = "isUploadEnabledForTests";

    /**
     * The application context in which minidump uploads are running.
     */
    private final Context mContext;

    /**
     * The cached crash reporting permissions. These are cached because the upload job might run
     * outside of a context in which the original permissions are easily accessible.
     */
    private final PersistableBundle mPermissions;

    /**
     * The system connectivity manager service, used to determine the network state.
     */
    private final ConnectivityManager mConnectivityManager;

    /**
     * Constructs a new Chrome-specific minidump uploader delegate.
     * @param context The application context in which minidump uploads are running.
     * @param permissions The cached crash reporting permissions.
     */
    ChromeMinidumpUploaderDelegate(Context context, PersistableBundle permissions) {
        mContext = context;
        mPermissions = permissions;
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public File getCrashParentDir() {
        return mContext.getCacheDir();
    }

    @Override
    public CrashReportingPermissionManager createCrashReportingPermissionManager() {
        return new CrashReportingPermissionManager() {
            @Override
            public boolean isClientInMetricsSample() {
                return mPermissions.getBoolean(IS_CLIENT_IN_METRICS_SAMPLE, true);
            }

            @Override
            public boolean isNetworkAvailableForCrashUploads() {
                // TODO(isherman): This code should really be shared with the Android Webview
                // implementation, which tests whether the connection is metered, rather than
                // testing the type of the connection. Implement this change in M59 -- for M58, it's
                // more important to maintain consistency with the previous implementation. When
                // changing this, note that forced uploads do *not* require unmetered connections.
                NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                if (networkInfo == null || !networkInfo.isConnected()) return false;
                return networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET;
            }

            @Override
            public boolean isCrashUploadDisabledByCommandLine() {
                return mPermissions.getBoolean(IS_CRASH_UPLOAD_DISABLED_BY_COMMAND_LINE, false);
            }

            @Override
            public boolean isMetricsUploadPermitted() {
                // This method is already represented by isClientInMetricsSample() and
                // isNetworkAvailableForCrashUploads(), so it's fine to return a dummy value.
                return true;
            }

            @Override
            public boolean isUsageAndCrashReportingPermittedByUser() {
                return PrivacyPreferencesManager.getInstance()
                        .isUsageAndCrashReportingPermittedByUser();
            }

            @Override
            public boolean isUploadEnabledForTests() {
                return mPermissions.getBoolean(IS_UPLOAD_ENABLED_FOR_TESTS, false);
            }
        };
    }

    @Override
    public void prepareToUploadMinidumps(final Runnable startUploads) {
        startUploads.run();
    }

    @Override
    public void recordUploadSuccess(File minidump) {
        MinidumpUploadService.incrementCrashSuccessUploadCount(minidump.getAbsolutePath());
    }

    @Override
    public void recordUploadFailure(File minidump) {
        MinidumpUploadService.incrementCrashFailureUploadCount(minidump.getAbsolutePath());
    }
}
