// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;
import android.app.NotificationManager;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the NotificationManagerProxy, which passes through all calls to the
 * normal Android Notification Manager.
 */
public class NotificationManagerProxyImpl implements NotificationManagerProxy {
    private static final String TAG = "NotifManagerProxy";
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

    @Override
    public void createNotificationChannel(ChannelDefinitions.Channel channel) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            channel.setGroup(channelGroupId);
            channel.setShowBadge(false);
            mNotificationManager.createNotificationChannel(channel);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            // Create channel
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Constructor<?> channelConstructor = channelClass.getDeclaredConstructor(
                    String.class, CharSequence.class, int.class);
            Object channelObject = channelConstructor.newInstance(channel.mId,
                    ContextUtils.getApplicationContext().getString(channel.mNameResId),
                    channel.mImportance);

            // Set group on channel
            Method setGroupMethod = channelClass.getMethod("setGroup", String.class);
            setGroupMethod.invoke(channelObject, channel.mGroupId);

            // Set channel to not badge on app icon
            Method setShowBadgeMethod = channelClass.getMethod("setShowBadge", boolean.class);
            setShowBadgeMethod.invoke(channelObject, false);

            // Register channel
            Method createNotificationChannelMethod = mNotificationManager.getClass().getMethod(
                    "createNotificationChannel", channelClass);
            createNotificationChannelMethod.invoke(mNotificationManager, channelObject);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            Log.e(TAG, "Error initializing notification channel:", e);
        }
    }

    @Override
    public void createNotificationChannelGroup(ChannelDefinitions.ChannelGroup channelGroup) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            mNotificationManager.createNotificationChannelGroup(channelGroup);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            // Create channel group
            Class<?> channelGroupClass = Class.forName("android.app.NotificationChannelGroup");
            Constructor<?> channelGroupConstructor =
                    channelGroupClass.getDeclaredConstructor(String.class, CharSequence.class);
            Object channelGroupObject = channelGroupConstructor.newInstance(channelGroup.mId,
                    ContextUtils.getApplicationContext().getString(channelGroup.mNameResId));

            // Register channel group
            Method createNotificationChannelGroupMethod = mNotificationManager.getClass().getMethod(
                    "createNotificationChannelGroup", channelGroupClass);
            createNotificationChannelGroupMethod.invoke(mNotificationManager, channelGroupObject);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            Log.e(TAG, "Error initializing notification channel group:", e);
        }
    }

    @Override
    public List<String> getNotificationChannelIds() {
        assert BuildInfo.isAtLeastO();
        List<String> channelIds = new ArrayList<>();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            List<NotificationChannel> list = mNotificationManager.getNotificationChannels();
            for (NotificationChannel channel : list) {
                channelIds.add(channel.getId());
            }
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            Method method = mNotificationManager.getClass().getMethod("getNotificationChannels");
            List channelsList = (List) method.invoke(mNotificationManager);
            for (Object o : channelsList) {
                Method getId = o.getClass().getMethod("getId");
                channelIds.add((String) getId.invoke(o));
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error getting notification channels:", e);
        }
        return channelIds;
    }

    @Override
    public void deleteNotificationChannel(@ChannelDefinitions.ChannelId String id) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            mNotificationManager.deleteNotificationChannel(id);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            Method method = mNotificationManager.getClass().getMethod(
                    "deleteNotificationChannel", String.class);
            method.invoke(mNotificationManager, id);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error deleting notification channel:", e);
        }
    }

    @Override
    public void notify(int id, Notification notification) {
        mNotificationManager.notify(id, notification);
    }

    @Override
    public void notify(String tag, int id, Notification notification) {
        mNotificationManager.notify(tag, id, notification);
    }
}
