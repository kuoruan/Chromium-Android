// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.util.SparseArray;
import android.widget.RemoteViews;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Utilies for managing the active {@link BrowserSessionContentHandler}. This is an interface owned
 * by the currently focused {@link ChromeActivity} has a linkage to a third party client app through
 * a session.
 */
public class BrowserSessionContentUtils {
    private static final String TAG = "BrowserSession_Utils";

    private static final SparseArray<CustomTabsSessionToken> sTaskIdToSession = new SparseArray<>();

    @Nullable
    private static BrowserSessionContentHandler sActiveContentHandler;


    @Nullable
    private static Callback<CustomTabsSessionToken> sSessionDisconnectCallback;

    /** Extra that is passed to intent to trigger a certain action within a running activity. */
    private static final String EXTRA_INTERNAL_ACTION =
            "org.chromium.chrome.extra.EXTRA_INTERNAL_ACTION";
    private static final String INTERNAL_ACTION_SHARE =
            "org.chromium.chrome.action.INTERNAL_ACTION_SHARE";

    /**
     * Sets the currently active {@link BrowserSessionContentHandler} in focus.
     * @param contentHandler {@link BrowserSessionContentHandler} to set.
     */
    public static void setActiveContentHandler(
            @NonNull BrowserSessionContentHandler contentHandler) {
        sActiveContentHandler = contentHandler;
        CustomTabsSessionToken session = sActiveContentHandler.getSession();
        if (session != null) {
            sTaskIdToSession.append(sActiveContentHandler.getTaskId(), session);
        }
        ensureSessionCleanUpOnDisconnects();
    }

    /**
     * Notifies that given {@link BrowserSessionContentHandler} no longer has focus.
     */
    public static void removeActiveContentHandler(BrowserSessionContentHandler contentHandler) {
        if (sActiveContentHandler == contentHandler) {
            sActiveContentHandler = null;
        } // else this contentHandler has already been replaced.

        // Intentionally not removing from sTaskIdToSession to handle cases when the task is
        // brought to foreground by a new intent - the CCT might not be able to call
        // setActiveContentHandler in time.
    }

    /**
     * @return whether there is an active content handler with a matching session running in the
     * same task as the given intent is being launched from.
     */
    public static boolean canHandleIntentInCurrentTask(Intent intent, Context context) {
        if (!(context instanceof Activity)) return false;
        int taskId = ((Activity) context).getTaskId();
        CustomTabsSessionToken sessionInCurrentTask = sTaskIdToSession.get(taskId);
        return sessionInCurrentTask != null
            && sessionInCurrentTask.equals(
                    CustomTabsSessionToken.getSessionTokenFromIntent(intent));
    }

    /**
     * Called when a Browser Services intent is handled.
     *
     * Used to check whether an incoming intent can be handled by the current
     * {@link BrowserSessionContentHandler}, and to perform action on new Intent.
     *
     * @return Whether the active {@link BrowserSessionContentHandler} has handled the intent.
     */
    public static boolean handleBrowserServicesIntent(Intent intent) {
        String url = IntentHandler.getUrlFromIntent(intent);
        if (TextUtils.isEmpty(url)) return false;

        CustomTabsSessionToken session = CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        return handleInternalIntent(intent, session) || handleExternalIntent(intent, url, session);

    }

    private static boolean handleInternalIntent(Intent intent,
            @Nullable CustomTabsSessionToken session) {
        if (!IntentHandler.wasIntentSenderChrome(intent)) return false;
        if (!sessionMatchesActiveContent(session)) return false;

        String internalAction = intent.getStringExtra(EXTRA_INTERNAL_ACTION);
        if (INTERNAL_ACTION_SHARE.equals(internalAction)) {
            sActiveContentHandler.triggerSharingFlow();
            return true;
        }
        return false;
    }

    private static boolean handleExternalIntent(Intent intent, String url,
           @Nullable CustomTabsSessionToken session) {
        if (!sessionMatchesActiveContent(session)) return false;
        if (sActiveContentHandler.shouldIgnoreIntent(intent)) {
            Log.w(TAG, "Incoming intent to Custom Tab was ignored.");
            return false;
        }
        sActiveContentHandler.loadUrlAndTrackFromTimestamp(
                new LoadUrlParams(url), IntentHandler.getTimestampFromIntent(intent));
        return true;
    }

    private static boolean sessionMatchesActiveContent(@Nullable CustomTabsSessionToken session) {
        return session != null && sActiveContentHandler != null &&
                session.equals(sActiveContentHandler.getSession());
    }

    /**
     * @return Whether the given session is the currently active session.
     */
    public static boolean isActiveSession(CustomTabsSessionToken session) {
        if (sActiveContentHandler == null) return false;
        if (session == null || sActiveContentHandler.getSession() == null) return false;
        return sActiveContentHandler.getSession().equals(session);
    }

    /**
     * Checks whether the given referrer can be used as valid within the Activity launched by the
     * given intent. For this to be true, the intent should be for a {@link CustomTabsSessionToken}
     * that is the currently in focus custom tab and also the related client should have a verified
     * relationship with the referrer origin. This can only be true for https:// origins.
     *
     * @param intent The intent that was used to launch the Activity in question.
     * @param referrer The referrer url that is to be used.
     * @return Whether the given referrer is a valid first party url to the client that launched
     *         the activity.
     */
    public static boolean canActiveContentHandlerUseReferrer(Intent intent, Uri referrer) {
        if (sActiveContentHandler == null) return false;
        CustomTabsSessionToken session = CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        if (session == null || !session.equals(sActiveContentHandler.getSession())) return false;
        String packageName =
                CustomTabsConnection.getInstance().getClientPackageNameForSession(session);
        if (TextUtils.isEmpty(packageName)) return false;
        boolean valid = OriginVerifier.wasPreviouslyVerified(
                packageName, new Origin(referrer), CustomTabsService.RELATION_USE_AS_ORIGIN);

        // OriginVerifier should only be allowing https schemes.
        assert valid == UrlConstants.HTTPS_SCHEME.equals(referrer.getScheme());

        return valid;
    }

    /**
     * @return The url for the page displayed using the current {@link
     * BrowserSessionContentHandler}.
     */
    public static String getCurrentUrlForActiveBrowserSession() {
        if (sActiveContentHandler == null) return null;
        return sActiveContentHandler.getCurrentUrl();
    }

    /**
     * @return The pending url for the page about to be displayed using the current {@link
     * BrowserSessionContentHandler}.
     */
    public static String getPendingUrlForActiveBrowserSession() {
        if (sActiveContentHandler == null) return null;
        return sActiveContentHandler.getPendingUrl();
    }

    /**
     * Checks whether the active {@link BrowserSessionContentHandler} belongs to the given session,
     * and if true, update toolbar's custom button.
     * @param session     The {@link IBinder} that the calling client represents.
     * @param bitmap      The new icon for action button.
     * @param description The new content description for the action button.
     * @return Whether the update is successful.
     */
    public static boolean updateCustomButton(
            CustomTabsSessionToken session, int id, Bitmap bitmap, String description) {
        ThreadUtils.assertOnUiThread();
        // Do nothing if there is no activity or the activity does not belong to this session.
        if (sActiveContentHandler == null || !sActiveContentHandler.getSession().equals(session)) {
            return false;
        }
        return sActiveContentHandler.updateCustomButton(id, bitmap, description);
    }

    /**
     * Checks whether the active {@link BrowserSessionContentHandler} belongs to the given session,
     * and if true, updates {@link RemoteViews} on the secondary toolbar.
     * @return Whether the update is successful.
     */
    public static boolean updateRemoteViews(CustomTabsSessionToken session, RemoteViews remoteViews,
            int[] clickableIDs, PendingIntent pendingIntent) {
        ThreadUtils.assertOnUiThread();
        // Do nothing if there is no activity or the activity does not belong to this session.
        if (sActiveContentHandler == null || !sActiveContentHandler.getSession().equals(session)) {
            return false;
        }
        return sActiveContentHandler.updateRemoteViews(remoteViews, clickableIDs, pendingIntent);
    }

    /**
     * Creates a share intent to be triggered in currently running activity.
     * @param originalIntent - intent with which the activity was launched.
     */
    public static Intent createShareIntent(Context context, Intent originalIntent) {
        Intent intent = new Intent(originalIntent)
                .putExtra(EXTRA_INTERNAL_ACTION, INTERNAL_ACTION_SHARE);
        IntentHandler.addTrustedIntentExtras(intent);
        return intent;
    }

    private static void ensureSessionCleanUpOnDisconnects() {
        if (sSessionDisconnectCallback != null) return;
        sSessionDisconnectCallback = (session) -> {
            if (session == null) {
                return;
            }
            for (int i = 0; i < sTaskIdToSession.size(); i++) {
                if (session.equals(sTaskIdToSession.valueAt(i))) {
                    sTaskIdToSession.removeAt(i);
                }
            }
        };
        ChromeApplication.getComponent().resolveCustomTabsConnection()
                .setDisconnectCallback(sSessionDisconnectCallback);
    }
}
