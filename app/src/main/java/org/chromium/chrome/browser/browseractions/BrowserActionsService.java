// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.notifications.ChromeNotificationBuilder;
import org.chromium.chrome.browser.notifications.NotificationBuilderFactory;
import org.chromium.chrome.browser.notifications.NotificationConstants;
import org.chromium.chrome.browser.notifications.NotificationUmaTracker;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * The foreground service responsible for creating notifications for Browser Actions and keep the
 * process alive during tab creation.
 */
public class BrowserActionsService extends Service {
    public static final String ACTION_TAB_CREATION_START =
            "org.chromium.chrome.browser.browseractions.ACTION_TAB_CREATION_START";
    public static final String ACTION_TAB_CREATION_CHROME_DISPLAYED =
            "org.chromium.chrome.browser.browseractions.ACTION_TAB_CREATION_CHROME_DISPLAYED";
    public static final String EXTRA_TAB_ID = "org.chromium.chrome.browser.browseractions.TAB_ID";
    public static final String EXTRA_LINK_URL =
            "org.chromium.chrome.browser.browseractions.LINK_URL";
    public static final String EXTRA_SOURCE_PACKAGE_NAME =
            "org.chromium.chrome.browser.browseractions.SOURCE_PACKAGE_NAME";

    /**
     * Action to request open ChromeTabbedActivity in tab switcher mode.
     */
    public static final String ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND =
            "org.chromium.chrome.browser.browseractions.browser_action_open_in_background";

    public static final String PREF_HAS_BROWSER_ACTIONS_NOTIFICATION =
            "org.chromium.chrome.browser.browseractions.HAS_BROWSER_ACTIONS_NOTIFICATION";

    public static final String PREF_IS_BROWSER_ACTIONS_SERVICE_ALIVE =
            "org.chromium.chrome.browser.browseractions.PREF_IS_BROWSER_ACTIONS_SERVICE_ALIVE";

    /**
     * Extra that indicates whether to show a Tab for single url or the tab switcher for
     * multiple urls.
     */
    public static final String EXTRA_IS_SINGLE_URL =
            "org.chromium.chrome.browser.browseractions.is_single_url";

    private static Intent sNotificationIntent;

    private static int sTitleResId;

    private static int sLoadingUrlNum;

    private BrowserActionsTabModelSelector mSelector;

    private TabModelObserver mObserver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @VisibleForTesting
    static Intent getNotificationIntent() {
        return sNotificationIntent;
    }

    @VisibleForTesting
    static int getTitleResId() {
        return sTitleResId;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (TextUtils.equals(intent.getAction(), ACTION_TAB_CREATION_START)) {
            sendBrowserActionsNotification(Tab.INVALID_TAB_ID);

            String linkUrl = IntentUtils.safeGetStringExtra(intent, EXTRA_LINK_URL);
            String sourcePackageName =
                    IntentUtils.safeGetStringExtra(intent, EXTRA_SOURCE_PACKAGE_NAME);
            Context context = ContextUtils.getApplicationContext();
            int tabId = openTabInBackground(linkUrl, sourcePackageName);
            updateTabIdForNotification(tabId);
            if (tabId != Tab.INVALID_TAB_ID) {
                finishTabCreation();
            }
            Toast.makeText(context, R.string.browser_actions_open_in_background_toast_message,
                         Toast.LENGTH_SHORT)
                    .show();
            NotificationUmaTracker.getInstance().onNotificationShown(
                    NotificationUmaTracker.BROWSER_ACTIONS, ChannelDefinitions.CHANNEL_ID_BROWSER);
        } else if (TextUtils.equals(intent.getAction(), ACTION_TAB_CREATION_CHROME_DISPLAYED)) {
            setLoadingUrlNum(0);
            if (mSelector != null) {
                mSelector.getModel(false).removeObserver(mObserver);
            }
            stopForeground(true);
        }
        // The service will not be restarted if Chrome get killed.
        return START_NOT_STICKY;
    }

    private static void setLoadingUrlNum(int num) {
        sLoadingUrlNum = num;
    }

    private void updateTabIdForNotification(int tabId) {
        sendBrowserActionsNotification(tabId);
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PREF_HAS_BROWSER_ACTIONS_NOTIFICATION, true)
                .apply();
    }

    private void finishTabCreation() {
        if (sLoadingUrlNum > 0) {
            sLoadingUrlNum--;
            if (sLoadingUrlNum == 0) {
                stopForeground(false);
                if (mSelector != null) {
                    mSelector.getModel(false).removeObserver(mObserver);
                }
            }
        }
    }

    @VisibleForTesting
    static boolean isBackgroundService() {
        return sLoadingUrlNum == 0;
    }

    private int openTabInBackground(String linkUrl, String sourcePackageName) {
        Referrer referrer = IntentHandler.constructValidReferrerForAuthority(sourcePackageName);
        LoadUrlParams loadUrlParams = new LoadUrlParams(linkUrl);
        loadUrlParams.setReferrer(referrer);
        Tab tab = launchTabInRunningTabbedActivity(loadUrlParams);
        if (tab != null) {
            sLoadingUrlNum++;
            return tab.getId();
        }
        launchTabInBrowserActionsModel(loadUrlParams);
        return Tab.INVALID_TAB_ID;
    }

    private Tab launchTabInRunningTabbedActivity(LoadUrlParams loadUrlParams) {
        for (WeakReference<Activity> ref : ApplicationStatus.getRunningActivities()) {
            if (!(ref.get() instanceof ChromeTabbedActivity)) continue;

            ChromeTabbedActivity activity = (ChromeTabbedActivity) ref.get();
            if (activity == null) continue;
            if (activity.getTabModelSelector() != null) {
                Tab tab = activity.getTabModelSelector().openNewTab(
                        loadUrlParams, TabLaunchType.FROM_BROWSER_ACTIONS, null, false);
                assert tab != null;
                // TODO(ltian): need to listen to the observer from PersistenceStore when TabState
                // is saved to disk. See crbug.com/766349.
                tab.addObserver(new EmptyTabObserver() {
                    @Override
                    public void onPageLoadFinished(Tab tab) {
                        finishTabCreation();
                        tab.removeObserver(this);
                    }
                });
                return tab;
            }
        }
        return null;
    }

    private void launchTabInBrowserActionsModel(LoadUrlParams loadUrlParams) {
        mSelector = BrowserActionsTabModelSelector.getInstance();
        sLoadingUrlNum++;
        if (mObserver == null) {
            mObserver = new EmptyTabModelObserver() {
                @Override
                public void didAddTab(Tab tab, TabLaunchType type) {
                    assert mSelector != null;
                    if (!mSelector.isTabStateInitialized()) return;
                    assert tab != null;
                    finishTabCreation();
                }
            };
        }
        mSelector.getModel(false).addObserver(mObserver);
        mSelector.openNewTab(loadUrlParams);
    }

    private void sendBrowserActionsNotification(int tabId) {
        ChromeNotificationBuilder builder = createNotificationBuilder(tabId);
        startForeground(NotificationConstants.NOTIFICATION_ID_BROWSER_ACTIONS, builder.build());
    }

    private ChromeNotificationBuilder createNotificationBuilder(int tabId) {
        ChromeNotificationBuilder builder =
                NotificationBuilderFactory
                        .createChromeNotificationBuilder(
                                true /* preferCompat */, ChannelDefinitions.CHANNEL_ID_BROWSER)
                        .setSmallIcon(R.drawable.infobar_chrome)
                        .setLocalOnly(true)
                        .setAutoCancel(true)
                        .setContentText(this.getString(R.string.browser_actions_notification_text));
        sTitleResId = hasBrowserActionsNotification()
                ? R.string.browser_actions_multi_links_open_notification_title
                : R.string.browser_actions_single_link_open_notification_title;
        builder.setContentTitle(this.getString(sTitleResId));
        sNotificationIntent = buildNotificationIntent(tabId);
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                this, 0, sNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(notifyPendingIntent);
        return builder;
    }

    private Intent buildNotificationIntent(int tabId) {
        boolean multipleUrls = hasBrowserActionsNotification();
        if (!multipleUrls && tabId != Tab.INVALID_TAB_ID) {
            return Tab.createBringTabToFrontIntent(tabId);
        }
        Intent intent = new Intent(this, ChromeLauncherActivity.class);
        intent.setAction(ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND);
        intent.putExtra(EXTRA_IS_SINGLE_URL, !multipleUrls);
        IntentHandler.addTrustedIntentExtras(intent);
        return intent;
    }

    @VisibleForTesting
    static boolean hasBrowserActionsNotification() {
        return ContextUtils.getAppSharedPreferences().getBoolean(
                PREF_HAS_BROWSER_ACTIONS_NOTIFICATION, false);
    }

    /**
     * Cancel Browser Actions notification.
     */
    public static void cancelBrowserActionsNotification() {
        // If Chrome is shown, force the foreground service to be killed so notification bound to it
        // will be dismissed.
        if (sLoadingUrlNum != 0) {
            Context context = ContextUtils.getApplicationContext();
            Intent intent = new Intent(context, BrowserActionsService.class);
            intent.setAction(ACTION_TAB_CREATION_CHROME_DISPLAYED);
            context.startService(intent);
        } else {
            NotificationManager notificationManager =
                    (NotificationManager) ContextUtils.getApplicationContext().getSystemService(
                            Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NotificationConstants.NOTIFICATION_ID_BROWSER_ACTIONS);
        }
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PREF_HAS_BROWSER_ACTIONS_NOTIFICATION, false)
                .apply();
    }

    /**
     * Checks whether Chrome should display tab switcher via Browser Actions Intent.
     * @param intent The intent to open the Chrome.
     * @param isOverviewVisible Whether tab switcher is shown.
     */
    public static boolean shouldToggleOverview(Intent intent, boolean isOverviewVisible) {
        if (!IntentHandler.wasIntentSenderChrome(intent)) return false;
        if (!ACTION_BROWSER_ACTIONS_OPEN_IN_BACKGROUND.equals(intent.getAction())) return false;
        boolean isSingleUrl = IntentUtils.safeGetBooleanExtra(intent, EXTRA_IS_SINGLE_URL, false);
        return isSingleUrl == isOverviewVisible;
    }

    /**
     * Sends a {@link Intent} to open a tab in {@link BrowserActionsService}.
     * @param linkUrl The url to be opened.
     * @param packageName The source package name which requests the tab creation.
     */
    public static void openLinkInBackground(String linkUrl, String packageName) {
        Context context = ContextUtils.getApplicationContext();
        Intent intent = new Intent(context, BrowserActionsService.class);
        intent.setAction(ACTION_TAB_CREATION_START);
        intent.putExtra(EXTRA_LINK_URL, linkUrl);
        intent.putExtra(EXTRA_SOURCE_PACKAGE_NAME, packageName);
        context.startService(intent);
    }
}
