// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.incognito;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.notifications.ChromeNotificationBuilder;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.notifications.NotificationUmaTracker;

/**
 * Manages the notification indicating that there are incognito tabs opened in Document mode.
 */
public class IncognitoNotificationManager {
    private static final String INCOGNITO_TABS_OPEN_TAG = "incognito_tabs_open";
    private static final int INCOGNITO_TABS_OPEN_ID = 100;

    /**
     * Shows the close all incognito notification.
     */
    public static void showIncognitoNotification() {
        Context context = ContextUtils.getApplicationContext();
        String actionMessage =
                context.getResources().getString(R.string.close_all_incognito_notification);
        String title = context.getResources().getString(R.string.app_name);

        ChromeNotificationBuilder builder =
                AppHooks.get()
                        .createChromeNotificationBuilder(true /* preferCompat */,
                                NotificationConstants.CATEGORY_ID_BROWSER,
                                context.getString(R.string.notification_category_browser),
                                NotificationConstants.CATEGORY_GROUP_ID_GENERAL,
                                context.getString(R.string.notification_category_group_general))
                        .setContentTitle(title)
                        .setContentIntent(
                                IncognitoNotificationService.getRemoveAllIncognitoTabsIntent(
                                        context))
                        .setContentText(actionMessage)
                        .setOngoing(true)
                        .setVisibility(Notification.VISIBILITY_SECRET)
                        .setSmallIcon(R.drawable.incognito_statusbar)
                        .setShowWhen(false)
                        .setLocalOnly(true)
                        .setGroup(NotificationConstants.GROUP_INCOGNITO);
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(INCOGNITO_TABS_OPEN_TAG, INCOGNITO_TABS_OPEN_ID, builder.build());
        NotificationUmaTracker.getInstance().onNotificationShown(
                NotificationUmaTracker.CLOSE_INCOGNITO);
    }

    /**
     * Dismisses the incognito notification.
     */
    public static void dismissIncognitoNotification() {
        Context context = ContextUtils.getApplicationContext();
        NotificationManager nm =
                  (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(INCOGNITO_TABS_OPEN_TAG, INCOGNITO_TABS_OPEN_ID);
    }
}