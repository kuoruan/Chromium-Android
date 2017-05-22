// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.banners.InstallerDelegate;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.chrome.browser.util.IntentUtils;

import java.io.File;

/**
 * Java counterpart to webapk_installer.h
 * Contains functionality to install / update WebAPKs.
 * This Java object is created by and owned by the native WebApkInstaller.
 */
public class WebApkInstaller {
    private static final String TAG = "WebApkInstaller";

    /** The WebAPK's package name. */
    private String mWebApkPackageName;

    /** Monitors for application state changes. */
    private ApplicationStatus.ApplicationStateListener mListener;

    /** Monitors installation progress. */
    private InstallerDelegate mInstallTask;

    /** Indicates whether to install or update a WebAPK. */
    private boolean mIsInstall;

    /** Weak pointer to the native WebApkInstaller. */
    private long mNativePointer;

    /** Talks to Google Play to install WebAPKs. */
    private GooglePlayWebApkInstallDelegate mGooglePlayWebApkInstallDelegate;

    private WebApkInstaller(long nativePtr) {
        mNativePointer = nativePtr;
        mGooglePlayWebApkInstallDelegate = AppHooks.get().getGooglePlayWebApkInstallDelegate();
    }

    @CalledByNative
    private static WebApkInstaller create(long nativePtr) {
        return new WebApkInstaller(nativePtr);
    }

    @CalledByNative
    private boolean canUseGooglePlayInstallService() {
        return ChromeWebApkHost.canUseGooglePlayToInstallWebApk();
    }

    @CalledByNative
    private void destroy() {
        if (mListener != null) {
            ApplicationStatus.unregisterApplicationStateListener(mListener);
        }
        mListener = null;
        mNativePointer = 0;
    }

    /**
     * Installs a WebAPK and monitors the installation process.
     * @param filePath File to install.
     * @param packageName Package name to install WebAPK at.
     * @return True if the install was started. A "true" return value does not guarantee that the
     *         install succeeds.
     */
    @CalledByNative
    private boolean installAsyncAndMonitorInstallationFromNative(
            String filePath, String packageName) {
        mIsInstall = true;
        mWebApkPackageName = packageName;

        // Start monitoring the installation.
        PackageManager packageManager = ContextUtils.getApplicationContext().getPackageManager();
        mInstallTask = new InstallerDelegate(Looper.getMainLooper(), packageManager,
                createInstallerDelegateObserver(), mWebApkPackageName);
        mInstallTask.start();
        // Start monitoring the application state changes.
        mListener = createApplicationStateListener();
        ApplicationStatus.registerApplicationStateListener(mListener);

        return installDownloadedWebApk(filePath);
    }

    /**
     * Installs a WebAPK from Google Play and monitors the installation.
     * @param packageName The package name of the WebAPK to install.
     * @param version The version of WebAPK to install.
     * @param title The title of the WebAPK to display during installation.
     * @param token The token from WebAPK Server.
     * @param url The start URL of the WebAPK to install.
     */
    @CalledByNative
    private void installWebApkFromGooglePlayAsync(
            String packageName, int version, String title, String token, String url) {
        // Check whether the WebAPK package is already installed. The WebAPK may have been installed
        // by another Chrome version (e.g. Chrome Dev). We have to do this check because the Play
        // install API fails silently if the package is already installed.
        if (isWebApkInstalled(packageName)) {
            notify(WebApkInstallResult.SUCCESS);
            return;
        }

        if (mGooglePlayWebApkInstallDelegate == null) {
            notify(WebApkInstallResult.FAILURE);
            WebApkUma.recordGooglePlayInstallResult(
                    WebApkUma.GOOGLE_PLAY_INSTALL_FAILED_NO_DELEGATE);
            return;
        }

        Callback<Integer> callback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                WebApkInstaller.this.notify(result);
            }
        };
        mGooglePlayWebApkInstallDelegate.installAsync(
                packageName, version, title, token, url, callback);
    }

    private void notify(@WebApkInstallResult.WebApkInstallResultEnum int result) {
        if (mNativePointer != 0) {
            nativeOnInstallFinished(mNativePointer, result);
        }
    }

    /**
     * Updates a WebAPK.
     * @param filePath File to update.
     * @return True if the update was started. A "true" return value does not guarantee that the
     *         update succeeds.
     */
    @CalledByNative
    private boolean updateAsyncFromNative(String filePath) {
        mIsInstall = false;
        return installDownloadedWebApk(filePath);
    }

    /**
     * Updates a WebAPK using Google Play.
     * @param packageName The package name of the WebAPK to install.
     * @param version The version of WebAPK to install.
     * @param title The title of the WebAPK to display during installation.
     * @param token The token from WebAPK Server.
     * @param url The start URL of the WebAPK to install.
     */
    @CalledByNative
    private void updateAsyncFromGooglePlay(
            String packageName, int version, String title, String token, String url) {
        if (mGooglePlayWebApkInstallDelegate == null) {
            notify(WebApkInstallResult.FAILURE);
            return;
        }

        Callback<Integer> callback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                WebApkInstaller.this.notify(result);
            }
        };
        mGooglePlayWebApkInstallDelegate.updateAsync(
                packageName, version, title, token, url, callback);
    }

    /**
     * Sends intent to Android to show prompt and install downloaded WebAPK.
     * @param filePath File to install.
     */
    private boolean installDownloadedWebApk(String filePath) {
        // TODO(pkotwicz|hanxi): For Chrome Stable figure out a different way of installing
        // WebAPKs which does not involve enabling "installation from Unsigned Sources".
        Context context = ContextUtils.getApplicationContext();
        Intent intent;
        File pathToInstall = new File(filePath);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri = Uri.fromFile(pathToInstall);
            intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            Uri source = ContentUriUtils.getContentUriFromFile(context, pathToInstall);
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setData(source);
        }
        return IntentUtils.safeStartActivity(context, intent);
    }

    private InstallerDelegate.Observer createInstallerDelegateObserver() {
        return new InstallerDelegate.Observer() {
            @Override
            public void onInstallFinished(InstallerDelegate task, boolean success) {
                if (mInstallTask != task) return;
                onInstallFinishedInternal(success);
            }
        };
    }

    private void onInstallFinishedInternal(boolean success) {
        ApplicationStatus.unregisterApplicationStateListener(mListener);
        mInstallTask = null;
        if (mNativePointer != 0) {
            nativeOnInstallFinished(mNativePointer,
                    success ? WebApkInstallResult.SUCCESS : WebApkInstallResult.FAILURE);
        }
        if (success && mIsInstall) {
            ShortcutHelper.addWebApkShortcut(ContextUtils.getApplicationContext(),
                    mWebApkPackageName);
        }
    }

    private ApplicationStatus.ApplicationStateListener createApplicationStateListener() {
        return new ApplicationStatus.ApplicationStateListener() {
            @Override
            public void onApplicationStateChange(int newState) {
                if (!ApplicationStatus.hasVisibleActivities()) return;
                /**
                 * Currently WebAPKs aren't installed by Play. A user can cancel the installation
                 * when the Android native installation dialog shows. The only way to detect the
                 * user cancelling the installation is to check whether the WebAPK is installed
                 * when Chrome is resumed. The monitoring of application state changes will be
                 * removed once WebAPKs are installed by Play.
                 */
                if (newState == ApplicationState.HAS_RUNNING_ACTIVITIES
                        && !isWebApkInstalled(mWebApkPackageName)) {
                    onInstallFinishedInternal(false);
                    return;
                }
            }
        };
    }

    private boolean isWebApkInstalled(String packageName) {
        PackageManager packageManager = ContextUtils.getApplicationContext().getPackageManager();
        return InstallerDelegate.isInstalled(packageManager, packageName);
    }

    private native void nativeOnInstallFinished(
            long nativeWebApkInstaller, @WebApkInstallResult.WebApkInstallResultEnum int result);
}
