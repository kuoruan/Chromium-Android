// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import static org.chromium.webapk.lib.common.WebApkConstants.WEBAPK_PACKAGE_PREFIX;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.notifications.ChromeNotificationBuilder;
import org.chromium.chrome.browser.notifications.NotificationBuilderFactory;
import org.chromium.chrome.browser.notifications.NotificationUmaTracker;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the notification indicating that a WebApk is backed by chrome code and may share data.
 * It's shown while an Unbound WebApk is displayed in the foreground until the user dismisses it.
 */
public class WebApkDisclosureNotificationManager {
    // We always use the same integer id when showing and closing notifications. The notification
    // tag is always set, which is a safe and sufficient way of identifying a notification, so the
    // integer id is not needed anymore except it must not vary in an uncontrolled way.
    private static final int PLATFORM_ID = 100;

    // Prefix used for generating a unique notification tag.
    private static final String DISMISSAL_NOTIFICATION_TAG_PREFIX =
            "dismissal_notification_tag_prefix.";

    /** Records whether we're currently showing a disclosure notification. */
    private static Set<String> sVisibleNotifications = new HashSet<>();

    /**
     * If we're showing a WebApk that's not with an expected package, it must be an
     * "Unbound WebApk" (crbug.com/714735). If we are not in a WebAPK but we have a BrowserSession
     * then it must be a Trusted Web Activity.
     *
     * For the above show a notification that it's running in Chrome.
     */
    static void maybeShowDisclosure(WebappActivity activity, WebappDataStorage storage) {
        String packageName = activity.getNativeClientPackageName();
        boolean isUnboundAPK = activity.isVerified() && !TextUtils.isEmpty(packageName)
                && !packageName.startsWith(WEBAPK_PACKAGE_PREFIX);
        boolean isNotificationAllowed = !storage.hasDismissedDisclosure()
                && !sVisibleNotifications.contains(packageName)
                && !WebappActionsNotificationManager.isEnabled();
        if (!isUnboundAPK || !isNotificationAllowed) return;

        int activityState = ApplicationStatus.getStateForActivity(activity);
        if (activityState == ActivityState.STARTED || activityState == ActivityState.RESUMED
                || activityState == ActivityState.PAUSED) {
            sVisibleNotifications.add(packageName);
            WebApkDisclosureNotificationManager.showDisclosure(activity.mWebappInfo);
        }
    }

    /**
     * Shows the privacy disclosure informing the user that Chrome is being used.
     * @param webappInfo Web App this is currently displayed fullscreen.
     */
    private static void showDisclosure(WebappInfo webappInfo) {
        Context context = ContextUtils.getApplicationContext();

        ChromeNotificationBuilder builder =
                NotificationBuilderFactory.createChromeNotificationBuilder(
                        false /* preferCompat */, ChannelDefinitions.CHANNEL_ID_BROWSER);
        builder.setContentTitle(webappInfo.name())
                .setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_chrome)
                .setLargeIcon(webappInfo.icon())
                .setDeleteIntent(WebApkDisclosureNotificationService.getDeleteIntent(
                        context, webappInfo.id()))
                .setContentText(context.getResources().getString(
                        R.string.webapk_running_in_chrome_disclosure));

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(DISMISSAL_NOTIFICATION_TAG_PREFIX + webappInfo.apkPackageName(), PLATFORM_ID,
                builder.build());
        NotificationUmaTracker.getInstance().onNotificationShown(
                NotificationUmaTracker.WEBAPK, ChannelDefinitions.CHANNEL_ID_BROWSER);
    }

    /**
     * Dismisses the notification.
     * @param activity Web App this is currently displayed fullscreen.
     */
    public static void dismissNotification(WebappActivity activity) {
        String packageName = activity.getNativeClientPackageName();
        if (!sVisibleNotifications.contains(packageName)) return;

        Context context = ContextUtils.getApplicationContext();
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(DISMISSAL_NOTIFICATION_TAG_PREFIX + packageName, PLATFORM_ID);
        sVisibleNotifications.remove(packageName);
    }
}
