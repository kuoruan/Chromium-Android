// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import static android.app.DownloadManager.ACTION_NOTIFICATION_CLICKED;
import static android.app.DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS;

import static org.chromium.chrome.browser.download.DownloadNotificationService2.ACTION_DOWNLOAD_CANCEL;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.ACTION_DOWNLOAD_OPEN;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.ACTION_DOWNLOAD_PAUSE;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.ACTION_DOWNLOAD_RESUME;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_DOWNLOAD_CONTENTID_ID;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_DOWNLOAD_CONTENTID_NAMESPACE;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_DOWNLOAD_FILE_PATH;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_IS_OFF_THE_RECORD;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_IS_SUPPORTED_MIME_TYPE;
import static org.chromium.chrome.browser.download.DownloadNotificationService2.EXTRA_NOTIFICATION_BUNDLE_ICON_ID;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.google.ipc.invalidation.util.Preconditions;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.notifications.ChromeNotificationBuilder;
import org.chromium.chrome.browser.notifications.NotificationBuilderFactory;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;
import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.LegacyHelpers;

/**
 * Creates and updates notifications related to downloads.
 */
public final class DownloadNotificationFactory {
    // Limit file name to 25 characters. TODO(qinmin): use different limit for different devices?
    public static final int MAX_FILE_NAME_LENGTH = 25;

    // TODO(jming): Eventually move this to DownloadNotificationStore.
    enum DownloadStatus {
        IN_PROGRESS,
        PAUSED,
        SUCCESSFUL,
        FAILED,
        DELETED,
        SUMMARY // TODO(jming): Remove when summary notification is no longer in-use.
    }

    /**
     * Builds a downloads notification based on the status of the download and its information.
     * @param context of the download.
     * @param downloadStatus (in progress, paused, successful, failed, deleted, or summary).
     * @param downloadUpdate information about the download (ie. contentId, fileName, icon, etc).
     * @return Notification that is built based on these parameters.
     */
    public static Notification buildNotification(
            Context context, DownloadStatus downloadStatus, DownloadUpdate downloadUpdate) {
        ChromeNotificationBuilder builder =
                NotificationBuilderFactory
                        .createChromeNotificationBuilder(
                                true /* preferCompat */, ChannelDefinitions.CHANNEL_ID_DOWNLOADS)
                        .setLocalOnly(true)
                        .setGroup(NotificationConstants.GROUP_DOWNLOADS)
                        .setAutoCancel(true);

        String contentText;
        int iconId;

        switch (downloadStatus) {
            case IN_PROGRESS:
                Preconditions.checkNotNull(downloadUpdate.getProgress());
                Preconditions.checkNotNull(downloadUpdate.getContentId());
                Preconditions.checkArgument(downloadUpdate.getNotificationId() != -1);

                boolean indeterminate = downloadUpdate.getProgress().isIndeterminate()
                        || downloadUpdate.getIsDownloadPending();
                if (downloadUpdate.getIsDownloadPending()) {
                    contentText = context.getResources().getString(
                            R.string.download_notification_pending);
                } else if (indeterminate || downloadUpdate.getTimeRemainingInMillis() < 0) {
                    // TODO(dimich): Enable the byte count back in M59. See bug 704049 for more info
                    // and details of what was temporarily reverted (for M58).
                    contentText = context.getResources().getString(R.string.download_started);
                } else {
                    contentText = DownloadUtils.getTimeOrFilesLeftString(context,
                            downloadUpdate.getProgress(),
                            downloadUpdate.getTimeRemainingInMillis());
                }
                iconId = downloadUpdate.getIsDownloadPending()
                        ? R.drawable.ic_download_pending
                        : android.R.drawable.stat_sys_download;

                Intent pauseIntent = buildActionIntent(context, ACTION_DOWNLOAD_PAUSE,
                        downloadUpdate.getContentId(), downloadUpdate.getIsOffTheRecord());
                Intent cancelIntent = buildActionIntent(context, ACTION_DOWNLOAD_CANCEL,
                        downloadUpdate.getContentId(), downloadUpdate.getIsOffTheRecord());

                builder.setOngoing(true)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setAutoCancel(false)
                        .setLargeIcon(downloadUpdate.getIcon())
                        .addAction(R.drawable.ic_pause_white_24dp,
                                context.getResources().getString(
                                        R.string.download_notification_pause_button),
                                buildPendingIntent(
                                        context, pauseIntent, downloadUpdate.getNotificationId()))
                        .addAction(R.drawable.btn_close_white,
                                context.getResources().getString(
                                        R.string.download_notification_cancel_button),
                                buildPendingIntent(
                                        context, cancelIntent, downloadUpdate.getNotificationId()));

                if (!downloadUpdate.getIsDownloadPending()) {
                    builder.setProgress(100,
                            indeterminate ? -1 : downloadUpdate.getProgress().getPercentage(),
                            indeterminate);
                }

                if (!indeterminate
                        && !LegacyHelpers.isLegacyOfflinePage(downloadUpdate.getContentId())) {
                    String percentText = DownloadUtils.getPercentageString(
                            downloadUpdate.getProgress().getPercentage());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        builder.setSubText(percentText);
                    } else {
                        builder.setContentInfo(percentText);
                    }
                }

                if (downloadUpdate.getStartTime() > 0) {
                    builder.setWhen(downloadUpdate.getStartTime());
                }

                break;
            case PAUSED:
                Preconditions.checkNotNull(downloadUpdate.getContentId());
                Preconditions.checkArgument(downloadUpdate.getNotificationId() != -1);

                contentText =
                        context.getResources().getString(R.string.download_notification_paused);
                iconId = R.drawable.ic_download_pause;

                Intent resumeIntent = buildActionIntent(context, ACTION_DOWNLOAD_RESUME,
                        downloadUpdate.getContentId(), downloadUpdate.getIsOffTheRecord());
                cancelIntent = buildActionIntent(context, ACTION_DOWNLOAD_CANCEL,
                        downloadUpdate.getContentId(), downloadUpdate.getIsOffTheRecord());
                PendingIntent deleteIntent = buildPendingIntent(
                        context, cancelIntent, downloadUpdate.getNotificationId());

                builder.setAutoCancel(false)
                        .setLargeIcon(downloadUpdate.getIcon())
                        .addAction(R.drawable.ic_file_download_white_24dp,
                                context.getResources().getString(
                                        R.string.download_notification_resume_button),
                                buildPendingIntent(
                                        context, resumeIntent, downloadUpdate.getNotificationId()))
                        .addAction(R.drawable.btn_close_white,
                                context.getResources().getString(
                                        R.string.download_notification_cancel_button),
                                buildPendingIntent(
                                        context, cancelIntent, downloadUpdate.getNotificationId()))
                        .setDeleteIntent(deleteIntent);

                break;

            case SUCCESSFUL:
                Preconditions.checkArgument(downloadUpdate.getNotificationId() != -1);

                contentText =
                        context.getResources().getString(R.string.download_notification_completed);
                iconId = R.drawable.offline_pin;

                if (downloadUpdate.getIsOpenable()) {
                    Intent intent;
                    if (LegacyHelpers.isLegacyDownload(downloadUpdate.getContentId())) {
                        Preconditions.checkNotNull(downloadUpdate.getContentId());
                        Preconditions.checkArgument(downloadUpdate.getSystemDownloadId() != -1);

                        intent = new Intent(ACTION_NOTIFICATION_CLICKED);
                        long[] idArray = {downloadUpdate.getSystemDownloadId()};
                        intent.putExtra(EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS, idArray);
                        intent.putExtra(EXTRA_DOWNLOAD_FILE_PATH, downloadUpdate.getFilePath());
                        intent.putExtra(EXTRA_IS_SUPPORTED_MIME_TYPE,
                                downloadUpdate.getIsSupportedMimeType());
                        intent.putExtra(
                                EXTRA_IS_OFF_THE_RECORD, downloadUpdate.getIsOffTheRecord());
                        intent.putExtra(
                                EXTRA_DOWNLOAD_CONTENTID_ID, downloadUpdate.getContentId().id);
                        intent.putExtra(EXTRA_DOWNLOAD_CONTENTID_NAMESPACE,
                                downloadUpdate.getContentId().namespace);
                        intent.putExtra(NotificationConstants.EXTRA_NOTIFICATION_ID,
                                downloadUpdate.getNotificationId());
                        DownloadUtils.setOriginalUrlAndReferralExtraToIntent(intent,
                                downloadUpdate.getOriginalUrl(), downloadUpdate.getReferrer());
                    } else {
                        intent = buildActionIntent(context, ACTION_DOWNLOAD_OPEN,
                                downloadUpdate.getContentId(), false);
                    }

                    ComponentName component = new ComponentName(
                            context.getPackageName(), DownloadBroadcastManager.class.getName());
                    intent.setComponent(component);
                    builder.setContentIntent(
                            PendingIntent.getService(context, downloadUpdate.getNotificationId(),
                                    intent, PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;

            case FAILED:
                iconId = android.R.drawable.stat_sys_download_done;
                contentText =
                        context.getResources().getString(R.string.download_notification_failed);
                break;

            case SUMMARY:
                Preconditions.checkArgument(downloadUpdate.getIconId() != -1);

                iconId = downloadUpdate.getIconId();
                contentText = "";
                builder.setContentTitle(
                               context.getString(R.string.download_notification_summary_title))
                        .setSubText(context.getString(R.string.menu_downloads))
                        .setSmallIcon(iconId)
                        .setGroupSummary(true);
                break;

            default:
                iconId = -1;
                contentText = "";
                break;
        }

        Bundle extras = new Bundle();
        extras.putInt(EXTRA_NOTIFICATION_BUNDLE_ICON_ID, iconId);

        builder.setContentText(contentText).setSmallIcon(iconId).addExtras(extras);

        if (downloadUpdate.getFileName() != null) {
            builder.setContentTitle(DownloadUtils.getAbbreviatedFileName(
                    downloadUpdate.getFileName(), MAX_FILE_NAME_LENGTH));
        }
        if (downloadUpdate.getIcon() != null) builder.setLargeIcon(downloadUpdate.getIcon());
        if (!downloadUpdate.getIsTransient() && downloadUpdate.getNotificationId() != -1
                && downloadStatus != DownloadStatus.SUCCESSFUL
                && downloadStatus != DownloadStatus.FAILED) {
            Intent downloadHomeIntent = buildActionIntent(
                    context, ACTION_NOTIFICATION_CLICKED, null, downloadUpdate.getIsOffTheRecord());
            builder.setContentIntent(
                    PendingIntent.getService(context, downloadUpdate.getNotificationId(),
                            downloadHomeIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        return builder.build();
    }

    /**
     * Helper method to build a PendingIntent from the provided intent.
     * @param intent Intent to broadcast.
     * @param notificationId ID of the notification.
     */
    private static PendingIntent buildPendingIntent(
            Context context, Intent intent, int notificationId) {
        return PendingIntent.getService(
                context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Helper method to build an download action Intent from the provided information.
     * @param context {@link Context} to pull resources from.
     * @param action Download action to perform.
     * @param id The {@link ContentId} of the download.
     * @param isOffTheRecord Whether the download is incognito.
     */
    public static Intent buildActionIntent(
            Context context, String action, ContentId id, boolean isOffTheRecord) {
        ComponentName component = new ComponentName(
                context.getPackageName(), DownloadBroadcastManager.class.getName());
        Intent intent = new Intent(action);
        intent.setComponent(component);
        intent.putExtra(EXTRA_DOWNLOAD_CONTENTID_ID, id != null ? id.id : "");
        intent.putExtra(EXTRA_DOWNLOAD_CONTENTID_NAMESPACE, id != null ? id.namespace : "");
        intent.putExtra(EXTRA_IS_OFF_THE_RECORD, isOffTheRecord);
        return intent;
    }
}
