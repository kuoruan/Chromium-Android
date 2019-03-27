// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.banners;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.PackageUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.webapps.AddToHomescreenDialog;

/**
 * Handles the promotion and installation of an app specified by the current web page. This object
 * is created by and owned by the native AppBannerUiDelegate.
 */
@JNINamespace("banners")
public class AppBannerUiDelegateAndroid implements AddToHomescreenDialog.Delegate {
    private static final String TAG = "AppBannerUi";

    /** Pointer to the native AppBannerUiDelegateAndroid. */
    private long mNativePointer;

    private Tab mTab;

    private AddToHomescreenDialog mDialog;

    private boolean mAddedToHomescreen;

    private AppBannerUiDelegateAndroid(long nativePtr, Tab tab) {
        mNativePointer = nativePtr;
        mTab = tab;
    }

    @Override
    public void addToHomescreen(String title) {
        mAddedToHomescreen = true;
        // The title is ignored for app banners as we respect the developer-provided title.
        if (mNativePointer != 0) {
            nativeAddToHomescreen(mNativePointer);
        }
    }

    @Override
    public void onNativeAppDetailsRequested() {
        if (mNativePointer != 0) {
            nativeShowNativeAppDetails(mNativePointer);
        }
    }

    @Override
    public void onDialogDismissed() {
        if (!mAddedToHomescreen && mNativePointer != 0) {
            nativeOnUiCancelled(mNativePointer);
        }

        mDialog = null;
        mAddedToHomescreen = false;
    }

    @CalledByNative
    private AddToHomescreenDialog getDialogForTesting() {
        return mDialog;
    }

    @CalledByNative
    private void destroy() {
        mNativePointer = 0;
        mAddedToHomescreen = false;
    }

    @CalledByNative
    private boolean installOrOpenNativeApp(AppData appData) {
        Context context = ContextUtils.getApplicationContext();
        Intent launchIntent;
        if (PackageUtils.isPackageInstalled(context, appData.packageName())) {
            launchIntent =
                    context.getPackageManager().getLaunchIntentForPackage(appData.packageName());
        } else {
            launchIntent = appData.installIntent();
        }
        if (launchIntent != null && mTab.getActivity() != null) {
            try {
                mTab.getActivity().startActivity(launchIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Failed to install or open app : %s!", appData.packageName(), e);
                return false;
            }
        }
        return true;
    }

    @CalledByNative
    private void showAppDetails(AppData appData) {
        mTab.getWindowAndroid().showIntent(appData.detailsIntent(), null, null);
    }

    @CalledByNative
    private boolean showNativeAppDialog(String title, Bitmap iconBitmap, AppData appData) {
        mDialog = new AddToHomescreenDialog(mTab.getActivity(), this);
        mDialog.show();
        mDialog.onUserTitleAvailable(title, appData.installButtonText(), appData.rating());
        mDialog.onIconAvailable(iconBitmap);
        return true;
    }

    @CalledByNative
    private boolean showWebAppDialog(String title, Bitmap iconBitmap, String url) {
        mDialog = new AddToHomescreenDialog(mTab.getActivity(), this);
        mDialog.show();
        mDialog.onUserTitleAvailable(title, url, true /* isWebapp */);
        mDialog.onIconAvailable(iconBitmap);
        return true;
    }

    @CalledByNative
    private static AppBannerUiDelegateAndroid create(long nativePtr, Tab tab) {
        return new AppBannerUiDelegateAndroid(nativePtr, tab);
    }

    private native void nativeAddToHomescreen(long nativeAppBannerUiDelegateAndroid);
    private native void nativeOnUiCancelled(long nativeAppBannerUiDelegateAndroid);
    private native void nativeShowNativeAppDetails(long nativeAppBannerUiDelegateAndroid);
}
