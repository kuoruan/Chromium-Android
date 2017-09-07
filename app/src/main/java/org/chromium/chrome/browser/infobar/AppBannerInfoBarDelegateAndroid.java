// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.os.Looper;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.banners.AppData;
import org.chromium.chrome.browser.banners.InstallerDelegate;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Handles the promotion and installation of an app specified by the current web page. This object
 * is created by and owned by the native AppBannerInfoBarDelegateAndroid.
 */
@JNINamespace("banners")
public class AppBannerInfoBarDelegateAndroid implements InstallerDelegate.Observer {
    /** Weak pointer to the native AppBannerInfoBarDelegateAndroid. */
    private long mNativePointer;

    /** Delegate which does the actual monitoring of an in-progress installation. */
    private InstallerDelegate mInstallerDelegate;

    private AppBannerInfoBarDelegateAndroid(long nativePtr) {
        mNativePointer = nativePtr;
        mInstallerDelegate = new InstallerDelegate(Looper.getMainLooper(), this);
    }

    @Override
    public void onInstallIntentCompleted(InstallerDelegate delegate, boolean isInstalling) {
        if (mInstallerDelegate != delegate) return;
        nativeOnInstallIntentReturned(mNativePointer, isInstalling);
    }

    @Override
    public void onInstallFinished(InstallerDelegate delegate, boolean success) {
        if (mInstallerDelegate != delegate) return;
        nativeOnInstallFinished(mNativePointer, success);
    }

    @Override
    public void onApplicationStateChanged(InstallerDelegate delegate, int newState) {
        if (mInstallerDelegate != delegate) return;
        nativeUpdateInstallState(mNativePointer);
    }

    @CalledByNative
    private void destroy() {
        mInstallerDelegate.destroy();
        mInstallerDelegate = null;
        mNativePointer = 0;
    }

    @CalledByNative
    private boolean installOrOpenNativeApp(Tab tab, AppData appData, String referrer) {
        return mInstallerDelegate.installOrOpenNativeApp(tab, appData, referrer);
    }

    @CalledByNative
    private void openApp(String packageName) {
        mInstallerDelegate.openApp(packageName);
    }

    @CalledByNative
    private void showAppDetails(Tab tab, AppData appData) {
        tab.getWindowAndroid().showIntent(appData.detailsIntent(), null, null);
    }

    @CalledByNative
    private int determineInstallState(String packageName) {
        return mInstallerDelegate.determineInstallState(packageName);
    }

    @CalledByNative
    private static AppBannerInfoBarDelegateAndroid create(long nativePtr) {
        return new AppBannerInfoBarDelegateAndroid(nativePtr);
    }

    private native void nativeOnInstallIntentReturned(
            long nativeAppBannerInfoBarDelegateAndroid, boolean isInstalling);
    private native void nativeOnInstallFinished(
            long nativeAppBannerInfoBarDelegateAndroid, boolean success);
    private native void nativeUpdateInstallState(long nativeAppBannerInfoBarDelegateAndroid);
}
