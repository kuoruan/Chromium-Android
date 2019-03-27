// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui.view;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PERSISTENT_NOTIFICATION;
import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PERSISTENT_NOTIFICATION_TAG;
import static org.chromium.chrome.browser.dependency_injection.ChromeCommonQualifiers.APP_CONTEXT;
import static org.chromium.chrome.browser.notifications.NotificationConstants.NOTIFICATION_ID_TWA_PERSISTENT;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.browserservices.BrowserSessionContentUtils;
import org.chromium.chrome.browser.browserservices.Origin;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PersistentNotificationData;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.Destroyable;
import org.chromium.chrome.browser.notifications.NotificationBuilderFactory;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.website.SettingsNavigationSource;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyObservable;
import org.chromium.ui.modelutil.PropertyObservable.PropertyObserver;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Publishes and dismisses the TWA persistent notification.
 */
@ActivityScope
public class PersistentNotificationView implements Destroyable, PropertyObserver<PropertyKey> {
    private final Context mAppContext;
    private final TrustedWebActivityModel mModel;

    @Nullable
    private Handler mHandler;

    @Inject
    public PersistentNotificationView(@Named(APP_CONTEXT) Context context,
            ActivityLifecycleDispatcher lifecycleDispatcher,
            TrustedWebActivityModel model) {
        mAppContext = context;
        mModel = model;
        lifecycleDispatcher.register(this);
        model.addObserver(this);
    }

    @Override
    public void onPropertyChanged(PropertyObservable<PropertyKey> source,
            @Nullable PropertyKey propertyKey) {
        if (propertyKey != PERSISTENT_NOTIFICATION) return;

        PersistentNotificationData data = mModel.get(PERSISTENT_NOTIFICATION);
        String tag = mModel.get(PERSISTENT_NOTIFICATION_TAG);
        Runnable task;
        if (data == null) {
            task = new DismissTask(mAppContext, tag);
        } else {
            task = new PublishTask(tag, data.origin,
                    mAppContext, data.customTabActivityIntent);
        }
        postToBackgroundThread(task);
    }

    @Override
    public void destroy() {
        killBackgroundThread();
    }

    private void postToBackgroundThread(Runnable task) {
        if (mHandler == null) {
            HandlerThread backgroundThread = new HandlerThread("TwaPersistentNotification");
            backgroundThread.start();
            mHandler = new Handler(backgroundThread.getLooper());
        }
        mHandler.post(task);
    }

    private void killBackgroundThread() {
        if (mHandler == null) {
            return;
        }
        Looper looper = mHandler.getLooper();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            looper.quitSafely();
        } else {
            looper.quit();
        }
    }

    private static class PublishTask implements Runnable {
        private final String mPackageName;
        private final Origin mOrigin;
        private final Context mAppContext;
        private final Intent mCustomTabsIntent;

        private PublishTask(String packageName, Origin origin, Context appContext,
                Intent customTabsIntent) {
            mPackageName = packageName;
            mOrigin = origin;
            mAppContext = appContext;
            mCustomTabsIntent = customTabsIntent;
        }

        @Override
        public void run() {
            Notification notification = createNotification();
            NotificationManager nm = (NotificationManager) mAppContext.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(mPackageName, NOTIFICATION_ID_TWA_PERSISTENT, notification);
            }
        }

        private Notification createNotification() {
            return NotificationBuilderFactory
                    .createChromeNotificationBuilder(true /* preferCompat */,
                            ChannelDefinitions.ChannelId.BROWSER)
                    .setSmallIcon(R.drawable.ic_chrome)
                    .setContentTitle(makeTitle())
                    .setContentText(
                            mAppContext.getString(R.string.app_running_in_chrome_disclosure))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriorityBeforeO(NotificationCompat.PRIORITY_LOW)
                    .addAction(0 /* icon */, // TODO(pshmakov): set the icons.
                            mAppContext.getString(R.string.share),
                            makeShareIntent())
                    .addAction(0 /* icon */,
                            mAppContext.getString(R.string.twa_manage_data),
                            makeManageDataIntent())
                    .build();
        }

        private String makeTitle() {
            PackageManager packageManager = mAppContext.getPackageManager();
            try {
                return packageManager
                        .getApplicationLabel(packageManager.getApplicationInfo(mPackageName, 0))
                        .toString();
            } catch (PackageManager.NameNotFoundException e) {
                assert false : mPackageName + " not found";
                return "";
            }
        }

        private PendingIntent makeManageDataIntent() {
            Intent settingsIntent = PreferencesLauncher.createIntentForSingleWebsitePreferences(
                    mAppContext, mOrigin.toString(), SettingsNavigationSource.OTHER);
            return PendingIntent.getActivity(mAppContext, 0, settingsIntent, FLAG_UPDATE_CURRENT);
        }

        private PendingIntent makeShareIntent() {
            Intent shareIntent =
                    BrowserSessionContentUtils.createShareIntent(mAppContext, mCustomTabsIntent);
            return PendingIntent.getActivity(mAppContext, 0, shareIntent, FLAG_UPDATE_CURRENT);
        }
    }

    private static class DismissTask implements Runnable {
        private final Context mAppContext;
        private final String mPackageName;

        private DismissTask(Context appContext, String packageName) {
            mAppContext = appContext;
            mPackageName = packageName;
        }

        @Override
        public void run() {
            NotificationManager nm = (NotificationManager) mAppContext.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(mPackageName, NOTIFICATION_ID_TWA_PERSISTENT);
            }
        }
    }
}
