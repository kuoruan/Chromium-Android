// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import static org.chromium.chrome.browser.download.DownloadSnackbarController.INVALID_NOTIFICATION_ID;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ServiceCompat;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.AppHooks;

/**
 * Keep-alive foreground service for downloads.
 */
public class DownloadForegroundService extends Service {
    private static final String KEY_PINNED_NOTIFICATION_ID = "PinnedNotificationId";
    private final IBinder mBinder = new LocalBinder();

    /**
     * Start the foreground service with this given context.
     * @param context The context used to start service.
     */
    public static void startDownloadForegroundService(Context context) {
        // TODO(crbug.com/770389): Grab a WakeLock here until the service has started.
        AppHooks.get().startForegroundService(new Intent(context, DownloadForegroundService.class));
    }

    /**
     * Update the foreground service to be pinned to a different notification.
     * @param notificationId The id of the new notification to be pinned to.
     * @param notification The new notification to be pinned to.
     */
    public void startOrUpdateForegroundService(int notificationId, Notification notification) {
        // If possible, detach notification so it doesn't get cancelled by accident.
        if (isSdkAtLeast24()) stopForegroundInternal(ServiceCompat.STOP_FOREGROUND_DETACH);

        startForegroundInternal(notificationId, notification);

        // Record when starting foreground and when updating pinned notification.
        int pinnedNotificationId = getPinnedNotificationId();
        if (pinnedNotificationId == INVALID_NOTIFICATION_ID) {
            DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                    DownloadNotificationUmaHelper.ForegroundLifecycle.START);
        } else {
            if (pinnedNotificationId != notificationId) {
                DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                        DownloadNotificationUmaHelper.ForegroundLifecycle.UPDATE);
            }
        }
        updatePinnedNotificationId(notificationId);
    }

    /**
     * Stop the foreground service that is running.
     *
     * @param detachNotification Whether to try to detach the notification from the service (only
     *                           works for API >= 24).
     * @param killNotification   Whether to kill the notification with the service.
     * @return                   Whether the notification was handled properly (ie. it was detached
     *                           or killed as intended). If this returns false, the notification id
     *                           will be persisted in shared preferences to be handled later.
     */
    public boolean stopDownloadForegroundService(
            boolean detachNotification, boolean killNotification) {
        // Record when stopping foreground.
        DownloadNotificationUmaHelper.recordForegroundServiceLifecycleHistogram(
                DownloadNotificationUmaHelper.ForegroundLifecycle.STOP);
        DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                DownloadNotificationUmaHelper.ServiceStopped.STOPPED, true /* withForeground */);

        boolean notificationDetached = detachNotification && isSdkAtLeast24();
        boolean notificationDetachedOrKilled = notificationDetached || killNotification;

        // Reset pinned notification if notification is properly detached or killed.
        if (notificationDetachedOrKilled) clearPinnedNotificationId();

        // Detach notification from foreground if possible.
        if (notificationDetached) {
            stopForegroundInternal(ServiceCompat.STOP_FOREGROUND_DETACH);
            return true;
        }

        // Otherwise, just stop the foreground, kill notification and/or correct it elsewhere.
        stopForegroundInternal(killNotification);

        return notificationDetachedOrKilled;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // In the case the service was restarted when the intent is null.
        if (intent == null) {
            DownloadNotificationUmaHelper.recordServiceStoppedHistogram(
                    DownloadNotificationUmaHelper.ServiceStopped.START_STICKY, true);

            // Alert observers that the service restarted with null intent.
            // Pass along the id of the notification that was pinned to the service when it died so
            // that the observers can do any corrections (ie. relaunch notification) if needed.
            DownloadForegroundServiceObservers.alertObserversServiceRestarted(
                    getPinnedNotificationId());

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
    class LocalBinder extends Binder {
        DownloadForegroundService getService() {
            return DownloadForegroundService.this;
        }
    }

    /**
     * Get stored value for the id of the notification pinned to the service.
     * This has to be persisted in the case that the service dies and the notification dies with it.
     * @return Id of the notification pinned to the service.
     */
    @VisibleForTesting
    static int getPinnedNotificationId() {
        SharedPreferences sharedPrefs = ContextUtils.getAppSharedPreferences();
        return sharedPrefs.getInt(KEY_PINNED_NOTIFICATION_ID, INVALID_NOTIFICATION_ID);
    }

    /**
     * Set stored value for the id of the notification pinned to the service.
     * This has to be persisted in the case that the service dies and the notification dies with it.
     * @param pinnedNotificationId Id of the notification pinned to the service.
     */
    private static void updatePinnedNotificationId(int pinnedNotificationId) {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putInt(KEY_PINNED_NOTIFICATION_ID, pinnedNotificationId)
                .apply();
    }

    /**
     * Clear stored value for the id of the notification pinned to the service.
     */
    @VisibleForTesting
    static void clearPinnedNotificationId() {
        ContextUtils.getAppSharedPreferences().edit().remove(KEY_PINNED_NOTIFICATION_ID).apply();
    }

    /** Methods for testing. */

    @VisibleForTesting
    void startForegroundInternal(int notificationId, Notification notification) {
        startForeground(notificationId, notification);
    }

    @VisibleForTesting
    void stopForegroundInternal(int flags) {
        ServiceCompat.stopForeground(this, flags);
    }

    @VisibleForTesting
    void stopForegroundInternal(boolean removeNotification) {
        stopForeground(removeNotification);
    }

    @VisibleForTesting
    boolean isSdkAtLeast24() {
        return Build.VERSION.SDK_INT >= 24;
    }
}
