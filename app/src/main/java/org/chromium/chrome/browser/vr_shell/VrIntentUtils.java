// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Utilities dealing with extracting information about VR intents.
 */
public class VrIntentUtils {
    private static final String DAYDREAM_HOME_PACKAGE = "com.google.android.vr.home";
    // The Daydream Home app adds this extra to auto-present intents.
    private static final String AUTOPRESENT_WEVBVR_EXTRA = "browser.vr.AUTOPRESENT_WEBVR";
    public static final String DAYDREAM_VR_EXTRA = "android.intent.extra.VR_LAUNCH";

    static final String VR_FRE_INTENT_EXTRA = "org.chromium.chrome.browser.vr_shell.VR_FRE";
    static final String VR_FRE_CALLER_INTENT_EXTRA =
            "org.chromium.chrome.browser.vr_shell.VR_FRE_CALLER";

    private static VrIntentHandler sHandlerInstance;

    /**
     * Handles VR intent checking for VrShellDelegate.
     */
    public interface VrIntentHandler {
        /**
         * Determines whether the given intent is a VR intent from Daydream Home.
         * @param intent The intent to check
         * @return Whether the intent is a VR intent and originated from Daydream Home
         */
        boolean isTrustedDaydreamIntent(Intent intent);

        /**
         * Determines whether the given intent is a VR intent that is allowed to auto-present WebVR
         * content.
         * @param intent The intent to check
         * @return Whether the intent should be allowed to auto-present.
         */
        boolean isTrustedAutopresentIntent(Intent intent);
    }

    private static VrIntentHandler createInternalVrIntentHandler() {
        return new VrIntentHandler() {
            @Override
            public boolean isTrustedDaydreamIntent(Intent intent) {
                return isVrIntent(intent)
                        && IntentHandler.isIntentFromTrustedApp(intent, DAYDREAM_HOME_PACKAGE);
            }

            @Override
            public boolean isTrustedAutopresentIntent(Intent intent) {
                // Note that all auto-present intents may not have the intent extra because the user
                // may have an older version of the Daydream app which doesn't add this extra.
                // This is probably fine because we mostly use isTrustedDaydreamIntent above to
                // start auto-presentation. We should switch those calls to use this method when
                // we're sure that most clients have the change.
                return isTrustedDaydreamIntent(intent)
                        && IntentUtils.safeGetBooleanExtra(intent, AUTOPRESENT_WEVBVR_EXTRA, false);
            }
        };
    }

    /**
     * Gets the static VrIntentHandler instance.
     * @return The VrIntentHandler instance
     */
    public static VrIntentHandler getHandlerInstance() {
        if (sHandlerInstance == null) {
            sHandlerInstance = createInternalVrIntentHandler();
        }
        return sHandlerInstance;
    }

    @VisibleForTesting
    public static void setHandlerInstanceForTesting(VrIntentHandler handler) {
        sHandlerInstance = handler;
    }

    /**
     * @return Whether or not the given intent is a VR-specific intent.
     */
    public static boolean isVrIntent(Intent intent) {
        // For simplicity, we only return true here if VR is enabled on the platform and this intent
        // is not fired from a recent apps page. The latter is there so that we don't enter VR mode
        // when we're being resumed from the recent apps in 2D mode.
        boolean canHandleIntent = VrShellDelegate.isVrEnabled() && !launchedFromRecentApps(intent);
        return IntentUtils.safeGetBooleanExtra(intent, DAYDREAM_VR_EXTRA, false) && canHandleIntent;
    }

    /**
     * @return whether the given intent is should open in a Custom Tab.
     */
    public static boolean isCustomTabVrIntent(Intent intent) {
        // TODO(crbug.com/719661): Currently, only Daydream intents open in a CustomTab. We should
        // probably change this once we figure out core CCT flows in VR.
        return getHandlerInstance().isTrustedDaydreamIntent(intent);
    }

    /**
     * This function returns an intent that will launch a VR activity that will prompt the
     * user to take off their headset and foward the freIntent to the standard
     * 2D FRE activity.
     *
     * @param caller          Activity instance that is checking if first run is necessary.
     * @param freCallerIntent The intent that is used to launch the caller.
     * @param freIntent       The intent that will be used to start the first run in 2D mode.
     * @return The intermediate VR activity intent.
     */
    public static Intent setupVrFreIntent(
            Context context, Intent freCallerIntent, Intent freIntent) {
        if (!VrShellDelegate.isVrEnabled()) return freIntent;
        Intent intent = new Intent();
        intent.setClassName(context, VrFirstRunActivity.class.getName());
        intent.putExtra(VR_FRE_CALLER_INTENT_EXTRA, new Intent(freCallerIntent));
        intent.putExtra(VR_FRE_INTENT_EXTRA, new Intent(freIntent));
        intent.putExtra(DAYDREAM_VR_EXTRA, true);
        return intent;
    }

    /*
     * Remove VR-specific extras from the given intent so that we don't auto-present
     * WebVR content after FRE completion.
     */
    public static void updateFreCallerIntent(Context context, Intent intent) {
        // Let the caller intent be handeled by the standard laucher.
        intent.setClassName(context, ChromeLauncherActivity.class.getName());
        intent.removeExtra(DAYDREAM_VR_EXTRA);
    }

    /**
     * @return Options that a VR-specific Chrome activity should be launched with.
     */
    public static Bundle getVrIntentOptions(Context context) {
        // These options are used to start the Activity with a custom animation to keep it hidden
        // for a few hundread milliseconds - enough time for us to draw the first black view.
        // The animation is sufficient to hide the 2D screenshot but not to the 2D UI while the
        // WebVR page is being loaded because the animation is somehow cancelled when we try to
        // enter VR (I don't know what's cancelling it). To hide the 2D UI, we resort to the black
        // overlay view added in {@link startWithVrIntentPreNative}.
        return ActivityOptions.makeCustomAnimation(context, R.anim.stay_hidden, 0).toBundle();
    }

    /**
     * @return Whether the intent is fired from the recent apps overview.
     */
    /* package */ static boolean launchedFromRecentApps(Intent intent) {
        return ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0);
    }
}
