// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.os.Build;

import org.chromium.base.BuildInfo;

import java.util.List;

/**
 * Default implementation of the NotificationManagerProxy, which passes through all calls to the
 * normal Android Notification Manager.
 */
public class NotificationManagerProxyImpl implements NotificationManagerProxy {
    private final NotificationManager mNotificationManager;

    public NotificationManagerProxyImpl(NotificationManager notificationManager) {
        mNotificationManager = notificationManager;
    }

    @Override
    public void cancel(int id) {
        mNotificationManager.cancel(id);
    }

    @Override
    public void cancel(String tag, int id) {
        mNotificationManager.cancel(tag, id);
    }

    @Override
    public void cancelAll() {
        mNotificationManager.cancelAll();
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void createNotificationChannel(NotificationChannel channel) {
        assert BuildInfo.isAtLeastO();
        // Suppress the notification dot/number that may appear on the browser app launcher. We
        // suppress this because showing it may imply that tapping the launch icon will lead
        // to some way of dismissing the dot, which is generally not the case. We don't want to
        // show a number either because users may have notifications from various websites, so an
        // aggregate figure is probably not useful.
        channel.setShowBadge(false);
        mNotificationManager.createNotificationChannel(channel);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void createNotificationChannelGroup(NotificationChannelGroup channelGroup) {
        assert BuildInfo.isAtLeastO();
        mNotificationManager.createNotificationChannelGroup(channelGroup);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public List<NotificationChannel> getNotificationChannels() {
        assert BuildInfo.isAtLeastO();
        return mNotificationManager.getNotificationChannels();
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public List<NotificationChannelGroup> getNotificationChannelGroups() {
        assert BuildInfo.isAtLeastO();
        return mNotificationManager.getNotificationChannelGroups();
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void deleteNotificationChannel(String id) {
        assert BuildInfo.isAtLeastO();
        mNotificationManager.deleteNotificationChannel(id);
    }

    @Override
    public void notify(int id, Notification notification) {
        mNotificationManager.notify(id, notification);
    }

    @Override
    public void notify(String tag, int id, Notification notification) {
        mNotificationManager.notify(tag, id, notification);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public NotificationChannel getNotificationChannel(String channelId) {
        assert BuildInfo.isAtLeastO();
        return mNotificationManager.getNotificationChannel(channelId);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void deleteNotificationChannelGroup(String groupId) {
        assert BuildInfo.isAtLeastO();
        mNotificationManager.deleteNotificationChannelGroup(groupId);
    }
}
