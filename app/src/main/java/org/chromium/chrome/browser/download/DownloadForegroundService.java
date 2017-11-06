// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import static org.chromium.chrome.browser.download.DownloadSnackbarController.INVALID_NOTIFICATION_ID;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.AppHooks;

/**
 * Keep-alive foreground service for downloads.
 */
public class DownloadForegroundService extends Service {
    private final IBinder mBinder = new LocalBinder();
    // Only tracking for UMA purposes.
    private int mPinnedNotification = INVALID_NOTIFICATION_ID;

    /**
     * Start the foreground service with this given context.
     * @param context The context used to start service.
     */
    public static void startDownloadForegroundService(Context context) {
        AppHooks.get().startForegroundService(new Intent(context, DownloadForegroundService.class));
    }

    /**
     * Update the foreground service to be pinned to a different notification.
     * @param notificationId The id of the new notification to be pinned to.
     * @param notification The new notification to be pinned to.
     */
    public void startOrUpdateForegroundService(int notificationId, Notification notification) {
        // If possible, detach notification so it doesn't get cancelled by accident.
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_DETACH);
        }

        startForeground(notificationId, notification);

        // Record when starting foreground and when updating pinned notification.
        if (mPinnedNotification == INVALID_NOTIFICATION_ID) {
            DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                    DownloadNotificationUmaHelper.ForegroundLifecycle.START);
        } else {
            if (mPinnedNotification != notificationId) {
                DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                        DownloadNotificationUmaHelper.ForegroundLifecycle.UPDATE);
            }
        }
        mPinnedNotification = notificationId;
    }

    /**
     * Stop the foreground service that is running.
     */
    public void stopDownloadForegroundService(boolean isCancelled) {
        // Record when stopping foreground.
        DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                DownloadNotificationUmaHelper.ForegroundLifecycle.STOP);
        DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                DownloadNotificationUmaHelper.ServiceStopped.STOPPED, true /* withForeground */);
        mPinnedNotification = INVALID_NOTIFICATION_ID;

        // If it's not cancelled, just detach the notification from the service, if possible.
        if (!isCancelled && Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_DETACH);
            return;
        }

        // Otherwise, just stop the foreground and correct it elsewhere.
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // In the case the service was restarted when the intent is null.
        if (intent == null) {
            DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                    DownloadNotificationUmaHelper.ServiceStopped.START_STICKY, true);

            DownloadForegroundServiceObservers.alertObserversServiceRestarted();

            // Allow observers to restart service on their own, if needed.
            stopSelf();
        }

        // This should restart service after Chrome gets killed (except for Android 4.4.2).
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                DownloadNotificationUmaHelper.ServiceStopped.DESTROYED, true /* withForeground */);
        DownloadForegroundServiceObservers.alertObserversServiceDestroyed();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                DownloadNotificationUmaHelper.ServiceStopped.TASK_REMOVED, true /*withForeground*/);
        DownloadForegroundServiceObservers.alertObserversTaskRemoved();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onLowMemory() {
        DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                DownloadNotificationUmaHelper.ServiceStopped.LOW_MEMORY, true /* withForeground */);
        super.onLowMemory();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Class for clients to access.
     */
    public class LocalBinder extends Binder {
        DownloadForegroundService getService() {
            return DownloadForegroundService.this;
        }
    }
}
