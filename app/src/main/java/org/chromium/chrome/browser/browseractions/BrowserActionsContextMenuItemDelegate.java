// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Browser;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.notifications.ChromeNotificationBuilder;
import org.chromium.chrome.browser.notifications.NotificationBuilderFactory;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.notifications.NotificationUmaTracker;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.share.ShareParams;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.ui.widget.Toast;

/**
 * A delegate responsible for taking actions based on browser action context menu selections.
 */
public class BrowserActionsContextMenuItemDelegate {
    private static final String TAG = "BrowserActionsItem";
    /**
     * Action to request open ChromeTabbedActivity in tab switcher mode.
     */
    public static final String ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND =
            "org.chromium.chrome.browser.browseractions.browser_action_open_in_background";

    public static final String PREF_HAS_BROWSER_ACTIONS_NOTIFICATION =
            "org.chromium.chrome.browser.browseractions.HAS_BROWSER_ACTIONS_NOTIFICATION";

    /**
     * Extra that indicates whether to show a Tab for single url or the tab switcher for
     * multiple urls.
     */
    public static final String EXTRA_IS_SINGLE_URL =
            "org.chromium.chrome.browser.browseractions.is_single_url";

    private final Activity mActivity;
    private final NotificationManager mNotificationManager;
    private final SharedPreferences mSharedPreferences;
    private final String mSourcePackageName;

    /**
     * Builds a {@link BrowserActionsContextMenuItemDelegate} instance.
     * @param activity The activity displays the context menu.
     * @param sourcePackageName The package name of the app which requests the Browser Actions.
     */
    public BrowserActionsContextMenuItemDelegate(Activity activity, String sourcePackageName) {
        mActivity = activity;
        mNotificationManager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        mSharedPreferences = ContextUtils.getAppSharedPreferences();
        mSourcePackageName = sourcePackageName;
    }

    private void sendBrowserActionsNotification() {
        ChromeNotificationBuilder builder = createNotificationBuilder();
        mNotificationManager.notify(
                NotificationConstants.NOTIFICATION_ID_BROWSER_ACTIONS, builder.build());
        mSharedPreferences.edit().putBoolean(PREF_HAS_BROWSER_ACTIONS_NOTIFICATION, true).apply();
        NotificationUmaTracker.getInstance().onNotificationShown(
                NotificationUmaTracker.BROWSER_ACTIONS, ChannelDefinitions.CHANNEL_ID_BROWSER);
    }

    private ChromeNotificationBuilder createNotificationBuilder() {
        ChromeNotificationBuilder builder =
                NotificationBuilderFactory
                        .createChromeNotificationBuilder(
                                true /* preferCompat */, ChannelDefinitions.CHANNEL_ID_BROWSER)
                        .setSmallIcon(R.drawable.infobar_chrome)
                        .setLocalOnly(true)
                        .setAutoCancel(true)
                        .setContentText(
                                mActivity.getString(R.string.browser_actions_notification_text));
        int titleResId = hasBrowserActionsNotification()
                ? R.string.browser_actions_multi_links_open_notification_title
                : R.string.browser_actions_single_link_open_notification_title;
        builder.setContentTitle(mActivity.getString(titleResId));
        Intent intent = buildNotificationIntent();
        PendingIntent notifyPendingIntent =
                PendingIntent.getActivity(mActivity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(notifyPendingIntent);
        return builder;
    }

    private Intent buildNotificationIntent() {
        Intent intent = new Intent(mActivity, ChromeLauncherActivity.class);
        intent.setAction(ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND);
        intent.putExtra(EXTRA_IS_SINGLE_URL, !hasBrowserActionsNotification());
        return intent;
    }

    private boolean hasBrowserActionsNotification() {
        return mSharedPreferences.getBoolean(PREF_HAS_BROWSER_ACTIONS_NOTIFICATION, false);
    }

    /**
     * Called when the {@code text} should be saved to the clipboard.
     * @param text The text to save to the clipboard.
     */
    public void onSaveToClipboard(String text) {
        ClipboardManager clipboardManager =
                (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data = ClipData.newPlainText("url", text);
        clipboardManager.setPrimaryClip(data);
    }

    /**
     * Called when the {@code linkUrl} should be opened in Chrome incognito tab.
     * @param linkUrl The url to open.
     */
    public void onOpenInIncognitoTab(String linkUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(mActivity.getPackageName());
        intent.putExtra(ChromeLauncherActivity.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, false);
        intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mActivity.getPackageName());
        IntentHandler.addTrustedIntentExtras(intent);
        IntentHandler.setTabLaunchType(intent, TabLaunchType.FROM_EXTERNAL_APP);
        IntentUtils.safeStartActivity(mActivity, intent);
    }

    /**
     * Called when the {@code linkUrl} should be opened in Chrome in the background.
     * @param linkUrl The url to open.
     */
    public void onOpenInBackground(String linkUrl) {
        sendBrowserActionsNotification();
        Toast.makeText(mActivity, R.string.browser_actions_open_in_background_toast_message,
                     Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * Called when a custom item of Browser action menu is selected.
     * @param action The PendingIntent action to be launched.
     */
    public void onCustomItemSelected(PendingIntent action) {
        try {
            action.send();
        } catch (CanceledException e) {
            Log.e(TAG, "Browser Action in Chrome failed to send pending intent.");
        }
    }

    /**
     * Called when the page of the {@code linkUrl} should be downloaded.
     * @param linkUrl The url of the page to download.
     */
    public void startDownload(String linkUrl) {}

    /**
     * Called when the {@code linkUrl} should be shared.
     * @param shareDirectly Whether to share directly with the previous app shared with.
     * @param linkUrl The url to share.
     */
    public void share(Boolean shareDirectly, String linkUrl) {
        ShareParams params = new ShareParams.Builder(mActivity, linkUrl, linkUrl)
                                     .setShareDirectly(shareDirectly)
                                     .setSaveLastUsed(!shareDirectly)
                                     .setSourcePackageName(mSourcePackageName)
                                     .setIsExternalUrl(true)
                                     .build();
        ShareHelper.share(params);
    }

    /**
     * Cancel Browser Actions notification.
     */
    public static void cancelBrowserActionsNotification() {
        NotificationManager notificationManager =
                (NotificationManager) ContextUtils.getApplicationContext().getSystemService(
                        Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NotificationConstants.NOTIFICATION_ID_BROWSER_ACTIONS);
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(
                        BrowserActionsContextMenuItemDelegate.PREF_HAS_BROWSER_ACTIONS_NOTIFICATION,
                        false)
                .apply();
    }

    /**
     * Checks whether Chrome should display tab switcher via Browser Actions Intent.
     * @param intent The intent to open the Chrome.
     * @param isOverviewVisible Whether tab switcher is shown.
     */
    public static boolean toggleOverviewByBrowserActions(Intent intent, boolean isOverviewVisible) {
        boolean fromBrowserActions = isStartedByBrowserActions(intent);
        boolean isSingleUrl = IntentUtils.safeGetBooleanExtra(
                intent, BrowserActionsContextMenuItemDelegate.EXTRA_IS_SINGLE_URL, false);
        if (fromBrowserActions) {
            return isSingleUrl == isOverviewVisible;
        }
        return false;
    }

    private static boolean isStartedByBrowserActions(Intent intent) {
        if (BrowserActionsContextMenuItemDelegate.ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND.equals(
                    intent.getAction())) {
            return true;
        }
        return false;
    }
}
