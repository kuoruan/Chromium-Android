// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.Resources;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.chromium.base.BuildInfo;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.sync.GoogleServiceAuthError;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.components.sync.ProtocolErrorClientAction;
import org.chromium.components.sync.StopSource;

/**
 * Helper methods for sync preferences.
 */
public class SyncPreferenceUtils {
    /**
     * Checks if sync error icon should be shown. Show sync error icon if sync is off because
     * of error, passphrase required or disabled in Android.
     */
    public static boolean showSyncErrorIcon(Context context) {
        if (!AndroidSyncSettings.get().isMasterSyncEnabled()) {
            return true;
        }

        ProfileSyncService profileSyncService = ProfileSyncService.get();
        if (profileSyncService != null) {
            if (profileSyncService.hasUnrecoverableError()) {
                return true;
            }

            if (profileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE) {
                return true;
            }

            if (profileSyncService.isSyncActive()
                    && profileSyncService.isPassphraseRequiredForDecryption()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return a short summary of the current sync status.
     */
    public static String getSyncStatusSummary(Context context) {
        if (!ChromeSigninController.get().isSignedIn()) return "";

        ProfileSyncService profileSyncService = ProfileSyncService.get();
        Resources res = context.getResources();

        if (!AndroidSyncSettings.get().isMasterSyncEnabled()) {
            return res.getString(org.chromium.chrome.R.string.sync_android_master_sync_disabled);
        }

        if (profileSyncService == null) {
            return res.getString(org.chromium.chrome.R.string.sync_is_disabled);
        }

        if (profileSyncService.getAuthError() != GoogleServiceAuthError.State.NONE) {
            return res.getString(
                    GoogleServiceAuthError.getMessageID(profileSyncService.getAuthError()));
        }

        if (profileSyncService.getProtocolErrorClientAction()
                == ProtocolErrorClientAction.UPGRADE_CLIENT) {
            return res.getString(org.chromium.chrome.R.string.sync_error_upgrade_client,
                    BuildInfo.getInstance().hostPackageLabel);
        }

        if (profileSyncService.hasUnrecoverableError()) {
            return res.getString(org.chromium.chrome.R.string.sync_error_generic);
        }

        String accountName = ChromeSigninController.get().getSignedInAccountName();
        boolean syncEnabled = AndroidSyncSettings.get().isSyncEnabled();
        if (syncEnabled) {
            if (!profileSyncService.isSyncActive()) {
                return res.getString(org.chromium.chrome.R.string.sync_setup_progress);
            }

            if (profileSyncService.isPassphraseRequiredForDecryption()) {
                return res.getString(org.chromium.chrome.R.string.sync_need_passphrase);
            }
            return context.getString(
                    org.chromium.chrome.R.string.account_management_sync_summary, accountName);
        }

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.UNIFIED_CONSENT)) {
            return context.getString(
                    org.chromium.chrome.R.string.account_management_sync_off_summary, accountName);
        }
        return context.getString(org.chromium.chrome.R.string.sync_is_disabled);
    }

    /**
     * Enables or disables {@link ProfileSyncService} and optionally records metrics that the sync
     * was disabled from settings. Requires that {@link ProfileSyncService#get()} returns non-null
     * reference.
     */
    public static void enableSync(boolean enable) {
        ProfileSyncService profileSyncService = ProfileSyncService.get();
        if (enable == profileSyncService.isSyncRequested()) return;

        if (enable) {
            profileSyncService.requestStart();
        } else {
            RecordHistogram.recordEnumeratedHistogram("Sync.StopSource",
                    StopSource.CHROME_SYNC_SETTINGS, StopSource.STOP_SOURCE_LIMIT);
            profileSyncService.requestStop();
        }
    }

    /**
     * Creates a wrapper around {@link Runnable} that calls the runnable only if
     * {@link PreferenceFragment} is still in resumed state. Click events that arrive after the
     * fragment has been paused will be ignored. See http://b/5983282.
     * @param fragment The fragment that hosts the preference.
     * @param runnable The runnable to call from {@link Preference.OnPreferenceClickListener}.
     */
    static Preference.OnPreferenceClickListener toOnClickListener(
            PreferenceFragment fragment, Runnable runnable) {
        return preference -> {
            if (!fragment.isResumed()) {
                // This event could come in after onPause if the user clicks back and the preference
                // at roughly the same time. See http://b/5983282.
                return false;
            }
            runnable.run();
            return false;
        };
    }
}
