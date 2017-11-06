// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import static org.chromium.chrome.browser.download.DownloadSnackbarController.INVALID_NOTIFICATION_ID;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Manager to stop and start the foreground service associated with downloads.
 */
public class DownloadForegroundServiceManager {
    public enum DownloadStatus { PAUSE, CANCEL, COMPLETE, IN_PROGRESS, FAIL }
    private static class DownloadUpdate {
        int mNotificationId;
        Notification mNotification;
        DownloadStatus mDownloadStatus;
        Context mContext;

        DownloadUpdate(int notificationId, Notification notification, DownloadStatus downloadStatus,
                Context context) {
            mNotificationId = notificationId;
            mNotification = notification;
            mDownloadStatus = downloadStatus;
            mContext = context;
        }
    }

    private static final String TAG = "DownloadFgSManager";

    @VisibleForTesting
    final Map<Integer, DownloadUpdate> mDownloadUpdateQueue = new HashMap<>();
    // Used to track existing notifications for UMA stats.
    private final List<Integer> mExistingNotifications = new ArrayList<>();

    private int mPinnedNotificationId = INVALID_NOTIFICATION_ID;

    // This is true when context.bindService has been called and before context.unbindService.
    private boolean mIsServiceBound;
    // This is non-null when onServiceConnected has been called (aka service is active).
    private DownloadForegroundService mBoundService;

    public DownloadForegroundServiceManager() {}

    public void updateDownloadStatus(Context context, DownloadStatus downloadStatus,
            int notificationId, Notification notification) {
        mDownloadUpdateQueue.put(notificationId,
                new DownloadUpdate(notificationId, notification, downloadStatus, context));
        processDownloadUpdateQueue(false /* not isProcessingPending */);

        // Record instance where notification is being launched for the first time.
        if (!mExistingNotifications.contains(notificationId)) {
            mExistingNotifications.add(notificationId);
            if (Build.VERSION.SDK_INT < 24) {
                DownloadNotificationUmaHelper.recordNotificationFlickerCountHistogram(
                        DownloadNotificationUmaHelper.LaunchType.LAUNCH);
            }
        }
    }

    /**
     * Process the notification queue for all cases and initiate any needed actions.
     * In the happy path, the logic should be:
     * bindAndStartService -> startOrUpdateForegroundService -> stopAndUnbindService.
     * @param isProcessingPending Whether the call was made to process pending notifications that
     *                            have accumulated in the queue during the startup process or if it
     *                            was made based on during a basic update.
     */
    @VisibleForTesting
    void processDownloadUpdateQueue(boolean isProcessingPending) {
        DownloadUpdate downloadUpdate = findInterestingDownloadUpdate();
        if (downloadUpdate == null) return;

        // When nothing has been initialized, just bind the service.
        if (!mIsServiceBound) {
            // If the download update is not active at the onset, don't even start the service!
            if (!isActive(downloadUpdate.mDownloadStatus)) {
                cleanDownloadUpdateQueue();
                return;
            }
            startAndBindService(downloadUpdate.mContext);
            return;
        }

        // Skip everything that happens while waiting for startup.
        if (mBoundService == null) return;

        // In the pending case, start foreground with specific notificationId and notification.
        if (isProcessingPending) {
            startOrUpdateForegroundService(
                    downloadUpdate.mNotificationId, downloadUpdate.mNotification);
        }

        // If the selected downloadUpdate is not active, there are no active downloads left.
        // Stop the foreground service.
        // In the pending case, this will stop the foreground immediately after it was started.
        if (!isActive(downloadUpdate.mDownloadStatus)) {
            stopAndUnbindService(downloadUpdate.mDownloadStatus == DownloadStatus.CANCEL);
            cleanDownloadUpdateQueue();
            return;
        }

        // Make sure the pinned notification is still active, if not, update.
        if (mDownloadUpdateQueue.get(mPinnedNotificationId) == null
                || !isActive(mDownloadUpdateQueue.get(mPinnedNotificationId).mDownloadStatus)) {
            startOrUpdateForegroundService(
                    downloadUpdate.mNotificationId, downloadUpdate.mNotification);
        }

        // Clear out inactive download updates in queue if there is at least one active download.
        cleanDownloadUpdateQueue();
    }

    /** Helper code to process download update queue. */

    @Nullable
    private DownloadUpdate findInterestingDownloadUpdate() {
        Iterator<Map.Entry<Integer, DownloadUpdate>> entries =
                mDownloadUpdateQueue.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, DownloadUpdate> entry = entries.next();
            // Return an active entry if possible.
            if (isActive(entry.getValue().mDownloadStatus)) return entry.getValue();
            // If there are no active entries, just return the last entry.
            if (!entries.hasNext()) return entry.getValue();
        }
        // If there's no entries, return null.
        return null;
    }

    private boolean isActive(DownloadStatus downloadStatus) {
        return downloadStatus == DownloadStatus.IN_PROGRESS;
    }

    private void cleanDownloadUpdateQueue() {
        Iterator<Map.Entry<Integer, DownloadUpdate>> entries =
                mDownloadUpdateQueue.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, DownloadUpdate> entry = entries.next();
            // Remove entry that is not active.
            if (!isActive(entry.getValue().mDownloadStatus)) entries.remove();
        }
    }

    /** Helper code to bind service. */

    @VisibleForTesting
    void startAndBindService(Context context) {
        mIsServiceBound = true;
        startAndBindServiceInternal(context);
    }

    @VisibleForTesting
    void startAndBindServiceInternal(Context context) {
        DownloadForegroundService.startDownloadForegroundService(context);
        context.bindService(new Intent(context, DownloadForegroundService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (!(service instanceof DownloadForegroundService.LocalBinder)) {
                Log.w(TAG,
                        "Not from DownloadNotificationService, do not connect."
                                + " Component name: " + className);
                return;
            }
            mBoundService = ((DownloadForegroundService.LocalBinder) service).getService();
            DownloadForegroundServiceObservers.addObserver(
                    DownloadNotificationServiceObserver.class);
            processDownloadUpdateQueue(true /* isProcessingPending */);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBoundService = null;
        }
    };

    /** Helper code to start or update foreground service. */

    @VisibleForTesting
    void startOrUpdateForegroundService(int notificationId, Notification notification) {
        if (mBoundService != null && notificationId != INVALID_NOTIFICATION_ID
                && notification != null) {
            mBoundService.startOrUpdateForegroundService(notificationId, notification);

            // In the case that there was another notification pinned to the foreground, re-launch
            // that notification because it gets cancelled in the switching process.
            // This does not happen for API >= 24.
            if (mPinnedNotificationId != notificationId && Build.VERSION.SDK_INT < 24) {
                relaunchPinnedNotification();
            }

            mPinnedNotificationId = notificationId;
        }
    }

    private void relaunchPinnedNotification() {
        if (mPinnedNotificationId != INVALID_NOTIFICATION_ID
                && mDownloadUpdateQueue.containsKey(mPinnedNotificationId)
                && mDownloadUpdateQueue.get(mPinnedNotificationId).mDownloadStatus
                        != DownloadStatus.CANCEL) {
            NotificationManager notificationManager =
                    (NotificationManager) ContextUtils.getApplicationContext().getSystemService(
                            Context.NOTIFICATION_SERVICE);
            notificationManager.notify(mPinnedNotificationId,
                    mDownloadUpdateQueue.get(mPinnedNotificationId).mNotification);

            // Record the need to relaunch the notification.
            DownloadNotificationUmaHelper.recordNotificationFlickerCountHistogram(
                    DownloadNotificationUmaHelper.LaunchType.RELAUNCH);
        }
    }

    /** Helper code to stop and unbind service. */

    @VisibleForTesting
    void stopAndUnbindService(boolean isCancelled) {
        mIsServiceBound = false;

        if (mBoundService != null) {
            stopAndUnbindServiceInternal(isCancelled);
            mBoundService = null;
        }

        // If the download isn't cancelled,  need to relaunch the notification so it is no longer
        // pinned to the foreground service.
        if (!isCancelled && Build.VERSION.SDK_INT < 24) {
            relaunchPinnedNotification();
        }
        mPinnedNotificationId = INVALID_NOTIFICATION_ID;
    }

    @VisibleForTesting
    void stopAndUnbindServiceInternal(boolean isCancelled) {
        mBoundService.stopDownloadForegroundService(isCancelled);
        ContextUtils.getApplicationContext().unbindService(mConnection);
        DownloadForegroundServiceObservers.removeObserver(
                DownloadNotificationServiceObserver.class);
    }

    /** Helper code for testing. */

    @VisibleForTesting
    void setBoundService(DownloadForegroundService service) {
        mBoundService = service;
    }
}
