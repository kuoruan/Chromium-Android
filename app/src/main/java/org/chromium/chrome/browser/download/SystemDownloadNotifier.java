// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.IntDef;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.download.DownloadNotificationService.Observer;
import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.PendingState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * DownloadNotifier implementation that creates and updates download notifications.
 * This class creates the {@link DownloadNotificationService} when needed, and binds
 * to the latter to issue calls to show and update notifications.
 */
public class SystemDownloadNotifier implements DownloadNotifier, Observer {
    private static final String TAG = "DownloadNotifier";

    @IntDef({DownloadNotificationType.PROGRESS, DownloadNotificationType.SUCCESS,
            DownloadNotificationType.FAILURE, DownloadNotificationType.CANCEL,
            DownloadNotificationType.RESUME_ALL, DownloadNotificationType.PAUSE,
            DownloadNotificationType.INTERRUPT, DownloadNotificationType.REMOVE_NOTIFICATION})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DownloadNotificationType {
        int PROGRESS = 0;
        int SUCCESS = 1;
        int FAILURE = 2;
        int CANCEL = 3;
        int RESUME_ALL = 4;
        int PAUSE = 5;
        int INTERRUPT = 6;
        int REMOVE_NOTIFICATION = 7;
    }

    @Nullable
    private DownloadNotificationService mBoundService;
    private Set<String> mActiveDownloads = new HashSet<String>();
    private ArrayList<PendingNotificationInfo> mPendingNotifications = new ArrayList<>();

    private boolean mIsServiceBound;

    /**
     * Pending download notifications to be posted.
     */
    static class PendingNotificationInfo {
        // Pending download notifications to be posted.
        public final int type;
        public final DownloadInfo downloadInfo;
        public long startTime;
        public boolean isAutoResumable;
        public boolean canDownloadWhileMetered;
        public boolean canResolve;
        public long systemDownloadId;
        public boolean isSupportedMimeType;
        public int notificationId;

        public PendingNotificationInfo(int type, DownloadInfo downloadInfo) {
            this.type = type;
            this.downloadInfo = downloadInfo;
        }
    }

    /**
     * Object to receive information as the service is started and stopped.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (!(service instanceof DownloadNotificationService.LocalBinder)) {
                Log.w(TAG,
                        "Not from DownloadNotificationService, do not connect."
                                + " Component name: " + className);
                assert false;
                return;
            }
            mBoundService = ((DownloadNotificationService.LocalBinder) service).getService();
            mBoundService.addObserver(SystemDownloadNotifier.this);
            // updateDownloadNotification() may leave some outstanding notifications
            // before the service is connected, handle them now.
            handlePendingNotifications();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {}
    };

    /**
     * For tests only: sets the DownloadNotificationService.
     * @param service An instance of DownloadNotificationService.
     */
    @VisibleForTesting
    void setDownloadNotificationService(DownloadNotificationService service) {
        mBoundService = service;
    }

    /**
     * Handles all the pending notifications that hasn't been processed.
     */
    @VisibleForTesting
    void handlePendingNotifications() {
        if (mPendingNotifications.isEmpty()) return;
        Iterator<PendingNotificationInfo> iter = mPendingNotifications.iterator();
        while (iter.hasNext()) {
            // Get the next PendingNotification, set autoRelease to be true for the last element.
            PendingNotificationInfo nextPendingNotification = iter.next();
            boolean autoRelease = !iter.hasNext();
            updateDownloadNotification(nextPendingNotification, autoRelease);
        }
        mPendingNotifications.clear();
    }

    /**
     * Starts and binds to the download notification service if needed.
     */
    private void startAndBindToServiceIfNeeded() {
        if (mIsServiceBound) return;
        startAndBindService();
        mIsServiceBound = true;
    }

    /**
     * Stops the download notification service if there are no download in progress.
     */
    private void unbindServiceIfNeeded() {
        if (!mActiveDownloads.isEmpty() || !mIsServiceBound) return;
        if (mBoundService != null) mBoundService.removeObserver(this);
        unbindService();
        mBoundService = null;
        mIsServiceBound = false;
    }

    @VisibleForTesting
    void startAndBindService() {
        Context applicationContext = ContextUtils.getApplicationContext();
        DownloadNotificationService.startDownloadNotificationService(applicationContext, null);
        applicationContext.bindService(
                new Intent(applicationContext, DownloadNotificationService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @VisibleForTesting
    void unbindService() {
        ContextUtils.getApplicationContext().unbindService(mConnection);
    }

    @Override
    public void onDownloadCanceled(ContentId id) {
        mActiveDownloads.remove(id.id);
        if (mActiveDownloads.isEmpty()) unbindServiceIfNeeded();
    }

    @Override
    public void notifyDownloadCanceled(ContentId id) {
        DownloadInfo downloadInfo = new DownloadInfo.Builder().setContentId(id).build();
        updateDownloadNotification(
                new PendingNotificationInfo(DownloadNotificationType.CANCEL, downloadInfo), true);
    }

    @Override
    public void notifyDownloadSuccessful(DownloadInfo downloadInfo, long systemDownloadId,
            boolean canResolve, boolean isSupportedMimeType) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DownloadNotificationType.SUCCESS, downloadInfo);
        info.canResolve = canResolve;
        info.systemDownloadId = systemDownloadId;
        info.isSupportedMimeType = isSupportedMimeType;
        updateDownloadNotification(info, true);
    }

    @Override
    public void notifyDownloadFailed(DownloadInfo downloadInfo) {
        updateDownloadNotification(
                new PendingNotificationInfo(DownloadNotificationType.FAILURE, downloadInfo), true);
    }

    @Override
    public void notifyDownloadProgress(
            DownloadInfo downloadInfo, long startTime, boolean canDownloadWhileMetered) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DownloadNotificationType.PROGRESS, downloadInfo);
        info.startTime = startTime;
        info.canDownloadWhileMetered = canDownloadWhileMetered;
        updateDownloadNotification(info, true);
    }

    @Override
    public void notifyDownloadPaused(DownloadInfo downloadInfo) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DownloadNotificationType.PAUSE, downloadInfo);
        updateDownloadNotification(info, true);
    }

    @Override
    public void notifyDownloadInterrupted(
            DownloadInfo downloadInfo, boolean isAutoResumable, @PendingState int notUsed) {
        PendingNotificationInfo info =
                new PendingNotificationInfo(DownloadNotificationType.INTERRUPT, downloadInfo);
        info.isAutoResumable = isAutoResumable;
        updateDownloadNotification(info, true);
    }

    @Override
    public void removeDownloadNotification(int notificationId, DownloadInfo downloadInfo) {
        PendingNotificationInfo info = new PendingNotificationInfo(
                DownloadNotificationType.REMOVE_NOTIFICATION, downloadInfo);
        info.notificationId = notificationId;
        updateDownloadNotification(info, true);
    }

    @Override
    public void resumePendingDownloads() {
        if (DownloadNotificationService.isTrackingResumableDownloads(
                    ContextUtils.getApplicationContext())) {
            updateDownloadNotification(
                    new PendingNotificationInfo(DownloadNotificationType.RESUME_ALL, null), true);
        }
    }

    /**
     * Called when a successful notification is shown.
     * @param notificationInfo Pending notification information to be handled.
     * @param notificationId ID of the notification.
     */
    @VisibleForTesting
    void onSuccessNotificationShown(
            final PendingNotificationInfo notificationInfo, final int notificationId) {
        if (notificationInfo.downloadInfo == null
                || !notificationInfo.downloadInfo.getIsOpenable()) {
            return;
        }
        DownloadManagerService.getDownloadManagerService().onSuccessNotificationShown(
                notificationInfo.downloadInfo, notificationInfo.canResolve, notificationId,
                notificationInfo.systemDownloadId);
    }

    /**
     * Helper method to schedule download notification updates.
     * @param notificationInfo Pending notification information to be handled.
     * @param autoRelease Whether or not to allow unbinding the service after processing the action.
     */
    @VisibleForTesting
    void updateDownloadNotification(
            final PendingNotificationInfo notificationInfo, boolean autoRelease) {
        assert ThreadUtils.runningOnUiThread();
        startAndBindToServiceIfNeeded();

        if (mBoundService == null) {
            if (notificationInfo == null) return;
            if (notificationInfo.downloadInfo != null) {
                for (PendingNotificationInfo pendingNotification : mPendingNotifications) {
                    if (pendingNotification.downloadInfo != null
                            && pendingNotification.downloadInfo.getContentId().equals(
                                       notificationInfo.downloadInfo.getContentId())) {
                        mPendingNotifications.remove(pendingNotification);
                        break;
                    }
                }
            }
            mPendingNotifications.add(notificationInfo);
            return;
        }

        DownloadInfo info = notificationInfo.downloadInfo;
        if (notificationInfo.type == DownloadNotificationType.PROGRESS) {
            mActiveDownloads.add(info.getDownloadGuid());
        } else if (notificationInfo.type != DownloadNotificationType.RESUME_ALL) {
            mActiveDownloads.remove(info.getDownloadGuid());
        }

        switch (notificationInfo.type) {
            case DownloadNotificationType.PROGRESS:
                mBoundService.notifyDownloadProgress(info.getContentId(), info.getFileName(),
                        info.getProgress(), info.getBytesReceived(),
                        info.getTimeRemainingInMillis(), notificationInfo.startTime,
                        info.isOffTheRecord(), notificationInfo.canDownloadWhileMetered,
                        info.getIsTransient(), info.getIcon());
                break;
            case DownloadNotificationType.PAUSE:
                mBoundService.notifyDownloadPaused(info.getContentId(), info.getFileName(), true,
                        false, info.isOffTheRecord(), info.getIsTransient(), info.getIcon());
                break;
            case DownloadNotificationType.INTERRUPT:
                mBoundService.notifyDownloadPaused(info.getContentId(), info.getFileName(),
                        info.isResumable(), notificationInfo.isAutoResumable, info.isOffTheRecord(),
                        info.getIsTransient(), info.getIcon());
                break;
            case DownloadNotificationType.SUCCESS:
                final int notificationId = mBoundService.notifyDownloadSuccessful(
                        info.getContentId(), info.getFilePath(), info.getFileName(),
                        notificationInfo.systemDownloadId, info.isOffTheRecord(),
                        notificationInfo.isSupportedMimeType, info.getIsOpenable(), info.getIcon(),
                        info.getOriginalUrl(), info.getReferrer());
                onSuccessNotificationShown(notificationInfo, notificationId);
                break;
            case DownloadNotificationType.FAILURE:
                mBoundService.notifyDownloadFailed(info.getContentId(), info.getFileName(),
                        info.getIcon(), info.getFailState());
                break;
            case DownloadNotificationType.CANCEL:
                mBoundService.notifyDownloadCanceled(info.getContentId());
                break;
            case DownloadNotificationType.RESUME_ALL:
                mBoundService.resumeAllPendingDownloads();
                break;
            case DownloadNotificationType.REMOVE_NOTIFICATION:
                mBoundService.cancelNotification(
                        notificationInfo.notificationId, info.getContentId());
                break;
            default:
                assert false;
        }

        // Don't need to expose the notification id to ignore.  Cancel will automatically call this
        // method as well and pass it in.
        if (mBoundService != null) mBoundService.hideSummaryNotificationIfNecessary(-1);
        if (autoRelease) unbindServiceIfNeeded();
    }
}
