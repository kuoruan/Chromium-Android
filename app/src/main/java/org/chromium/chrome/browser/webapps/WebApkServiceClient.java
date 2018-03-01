// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.chrome.browser.notifications.NotificationBuilderBase;
import org.chromium.webapk.lib.client.WebApkServiceConnectionManager;
import org.chromium.webapk.lib.runtime_library.IWebApkApi;

/**
 * Provides APIs for browsers to communicate with WebAPK services. Each WebAPK has its own "WebAPK
 * service".
 */
public class WebApkServiceClient {
    // Callback which catches RemoteExceptions thrown due to IWebApkApi failure.
    private abstract static class ApiUseCallback
            implements WebApkServiceConnectionManager.ConnectionCallback {
        public abstract void useApi(IWebApkApi api) throws RemoteException;

        @Override
        public void onConnected(IBinder api) {
            if (api == null) {
                WebApkUma.recordBindToWebApkServiceSucceeded(false);
                return;
            }

            try {
                useApi(IWebApkApi.Stub.asInterface(api));
                WebApkUma.recordBindToWebApkServiceSucceeded(true);
            } catch (RemoteException e) {
                Log.w(TAG, "WebApkAPI use failed.", e);
            }
        }
    }

    private static final String CATEGORY_WEBAPK_API = "android.intent.category.WEBAPK_API";
    private static final String TAG = "cr_WebApk";

    private static WebApkServiceClient sInstance;

    /** Manages connections between the browser application and WebAPK services. */
    private WebApkServiceConnectionManager mConnectionManager;

    public static WebApkServiceClient getInstance() {
        if (sInstance == null) {
            sInstance = new WebApkServiceClient();
        }
        return sInstance;
    }

    private WebApkServiceClient() {
        mConnectionManager =
                new WebApkServiceConnectionManager(CATEGORY_WEBAPK_API, null /* action */);
    }

    /**
     * Connects to a WebAPK's bound service, builds a notification and hands it over to the WebAPK
     * to display. Handing over the notification makes the notification look like it originated from
     * the WebAPK - not Chrome - in the Android UI.
     */
    public void notifyNotification(final String webApkPackage,
            final NotificationBuilderBase notificationBuilder, final String platformTag,
            final int platformID) {
        final ApiUseCallback connectionCallback = new ApiUseCallback() {
            @Override
            public void useApi(IWebApkApi api) throws RemoteException {
                int smallIconId = api.getSmallIconId();
                // Prior to Android M, the small icon had to be from the resources of the app whose
                // NotificationManager is used in {@link NotificationManager#notify()}. On Android
                // M+, the small icon has to be from the resources of the app whose context is
                // passed to the {@link Notification.Builder()} constructor.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android M+ introduced
                    // {@link Notification.Builder#setSmallIcon(Bitmap icon)}.
                    if (!notificationBuilder.hasSmallIconBitmap()) {
                        notificationBuilder.setSmallIcon(
                                decodeImageResource(webApkPackage, smallIconId));
                    }
                } else {
                    notificationBuilder.setSmallIcon(smallIconId);
                }
                boolean notificationPermissionEnabled = api.notificationPermissionEnabled();
                if (notificationPermissionEnabled) {
                    api.notifyNotification(platformTag, platformID, notificationBuilder.build());
                }
                WebApkUma.recordNotificationPermissionStatus(notificationPermissionEnabled);
            }
        };

        mConnectionManager.connect(
                ContextUtils.getApplicationContext(), webApkPackage, connectionCallback);
    }

    /** Cancels notification previously shown by WebAPK. */
    public void cancelNotification(
            String webApkPackage, final String platformTag, final int platformID) {
        final ApiUseCallback connectionCallback = new ApiUseCallback() {
            @Override
            public void useApi(IWebApkApi api) throws RemoteException {
                api.cancelNotification(platformTag, platformID);
            }
        };

        mConnectionManager.connect(
                ContextUtils.getApplicationContext(), webApkPackage, connectionCallback);
    }

    /** Disconnects all the connections to WebAPK services. */
    public static void disconnectAll() {
        if (sInstance == null) return;

        sInstance.mConnectionManager.disconnectAll(ContextUtils.getApplicationContext());
    }

    /** Decodes bitmap from WebAPK's resources. */
    private static Bitmap decodeImageResource(String webApkPackage, int resourceId) {
        PackageManager packageManager = ContextUtils.getApplicationContext().getPackageManager();
        try {
            Resources resources = packageManager.getResourcesForApplication(webApkPackage);
            return BitmapFactory.decodeResource(resources, resourceId);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
