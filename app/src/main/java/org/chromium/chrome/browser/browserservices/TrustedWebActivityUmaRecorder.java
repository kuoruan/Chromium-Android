// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.tab.Tab;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Encapsulates Uma recording actions related to Trusted Web Activities.
 */
public class TrustedWebActivityUmaRecorder {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DelegatedNotificationSmallIconFallback.NO_FALLBACK,
            DelegatedNotificationSmallIconFallback.FALLBACK_ICON_NOT_PROVIDED,
            DelegatedNotificationSmallIconFallback.FALLBACK_FOR_STATUS_BAR,
            DelegatedNotificationSmallIconFallback.FALLBACK_FOR_STATUS_BAR_AND_CONTENT})
    public @interface DelegatedNotificationSmallIconFallback {
        int NO_FALLBACK = 0;
        int FALLBACK_ICON_NOT_PROVIDED = 1;
        int FALLBACK_FOR_STATUS_BAR = 2;
        int FALLBACK_FOR_STATUS_BAR_AND_CONTENT = 3;
        int NUM_ENTRIES = 4;
    }

    @Inject
    public TrustedWebActivityUmaRecorder() {}

    /**
     * Records that a Trusted Web Activity has been opened.
     */
    public void recordTwaOpened(@Nullable Tab tab) {
        RecordUserAction.record("BrowserServices.TwaOpened");
        if (tab != null) {
            new UkmRecorder.Bridge().recordTwaOpened(tab);
        }
    }

    /**
     * Records the time that a Trusted Web Activity has been in resumed state.
     */
    public void recordTwaOpenTime(long durationMs) {
        recordDuration(durationMs, "BrowserServices.TwaOpenTime");
    }

    /**
     * Records the time spent in verified origin until navigating to unverified one or pausing
     * the Trusted Web Activity.
     */
    public void recordTimeInVerifiedOrigin(long durationMs) {
        recordDuration(durationMs, "TrustedWebActivity.TimeInVerifiedOrigin");
    }

    /**
     * Records the time spent in verified origin until navigating to unverified one or pausing
     * the Trusted Web Activity.
     */
    public void recordTimeOutOfVerifiedOrigin(long durationMs) {
        recordDuration(durationMs, "TrustedWebActivity.TimeOutOfVerifiedOrigin");
    }

    private void recordDuration(long durationMs, String histogramName) {
        RecordHistogram.recordTimesHistogram(histogramName, durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records the fact that disclosure was shown.
     */
    public void recordDisclosureShown() {
        RecordUserAction.record("TrustedWebActivity.DisclosureShown");
    }

    /**
     * Records the fact that disclosure was accepted by user.
     */
    public void recordDisclosureAccepted() {
        RecordUserAction.record("TrustedWebActivity.DisclosureAccepted");
    }

    /**
     * Records which action the user took upon seeing a clear data dialog.
     * @param accepted Whether user proceeded to the settings from the dialog.
     * @param triggeredByUninstall Whether the dialog was triggered by app uninstall as opposed to
     * app data getting cleared.
     */
    public void recordClearDataDialogAction(boolean accepted, boolean triggeredByUninstall) {
        String histogramName = triggeredByUninstall
                ? "TrustedWebActivity.ClearDataDialogOnUninstallAccepted"
                : "TrustedWebActivity.ClearDataDialogOnClearAppDataAccepted";
        RecordHistogram.recordBooleanHistogram(histogramName, accepted);
    }

    /**
     * Records the fact that site settings were opened via "Manage Space" button in TWA client app's
     * settings.
     */
    public void recordOpenedSettingsViaManageSpace() {
        RecordUserAction.record("TrustedWebActivity.OpenedSettingsViaManageSpace");
    }

    /**
     * Records which fallback (if any) was used for the small icon of a delegated notification.
     */
    public void recordDelegatedNotificationSmallIconFallback(
            @DelegatedNotificationSmallIconFallback int fallback) {
        RecordHistogram.recordEnumeratedHistogram(
                "TrustedWebActivity.DelegatedNotificationSmallIconFallback", fallback,
                DelegatedNotificationSmallIconFallback.NUM_ENTRIES);
    }
}
