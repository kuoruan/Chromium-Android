// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.view.View;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.StrictModeContext;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.PromoDialog;
import org.chromium.ui.base.DeviceFormFactor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * A promotion for Chrome Home (the bottom sheet). This dialog can optionally restart the current
 * activity to bring a user in or out of the feature.
 */
public class ChromeHomePromoDialog extends PromoDialog {
    /** Notified about dialog events. */
    public static interface ChromeHomePromoDialogTestObserver {
        void onDialogShown(ChromeHomePromoDialog shownDialog);
    }

    private static ChromeHomePromoDialogTestObserver sTestObserver;

    /** Reasons that the promo was shown. */
    @IntDef({ShowReason.NTP, ShowReason.MENU, ShowReason.STARTUP, ShowReason.BOUNDARY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShowReason {
        int NTP = 0;
        int MENU = 1;
        int STARTUP = 2;
        int BOUNDARY = 3;
    }

    /** States the promo was closed in. */
    @IntDef({PromoResult.ENABLED, PromoResult.DISABLED, PromoResult.REMAINED_ENABLED,
            PromoResult.REMAINED_DISABLED, PromoResult.BOUNDARY})
    @Retention(RetentionPolicy.SOURCE)
    private @interface PromoResult {
        int ENABLED = 0;
        int DISABLED = 1;
        int REMAINED_ENABLED = 2;
        int REMAINED_DISABLED = 3;
        int BOUNDARY = 4;
    }

    /** The reason the promo was shown. */
    @ShowReason
    private final int mShowReason;

    /** Whether Chrome Home should be enabled or disabled after the promo is dismissed. */
    private boolean mChromeHomeShouldBeEnabled;

    /** Whether Chrome Home is enabled when the promo is shown. */
    private boolean mChromeHomeEnabledOnShow;

    /**
     * Default constructor.
     * @param activity The {@link Activity} showing the promo.
     * @param showReason The reason that the promo was shown.
     */
    public ChromeHomePromoDialog(Activity activity, @ShowReason int showReason) {
        super(activity);
        setOnDismissListener(this);
        mShowReason = showReason;
        mChromeHomeShouldBeEnabled = FeatureUtilities.isChromeHomeEnabled();

        RecordHistogram.recordEnumeratedHistogram("Android.ChromeHome.Promo.ShowReason", showReason,
                ChromeHomePromoDialog.ShowReason.BOUNDARY);
    }

    @Override
    public void show() {
        if (DeviceFormFactor.isTablet()) {
            throw new RuntimeException("Promo should not be shown for tablet devices!");
        }
        super.show();
    }

    @Override
    protected DialogParams getDialogParams() {
        PromoDialog.DialogParams params = new PromoDialog.DialogParams();
        params.headerStringResource = R.string.chrome_home_promo_dialog_title;
        params.subheaderStringResource = AccessibilityUtil.isAccessibilityEnabled()
                ? R.string.chrome_home_promo_dialog_message_accessibility
                : R.string.chrome_home_promo_dialog_message;

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_PROMO_INFO_ONLY)) {
            params.primaryButtonStringResource = R.string.ok;
        } else if (FeatureUtilities.isChromeHomeEnabled()) {
            params.primaryButtonStringResource = R.string.ok;
            params.secondaryButtonStringResource = R.string.chrome_home_promo_dialog_turn_off;
        } else {
            params.primaryButtonStringResource = R.string.chrome_home_promo_dialog_try_it;
            params.secondaryButtonStringResource = R.string.chrome_home_promo_dialog_not_yet;
        }

        if (SysUtils.isLowEndDevice()) {
            params.drawableResource = R.drawable.chrome_home_promo_static;
        } else {
            params.drawableInstance = new ChromeHomePromoIllustration(getContext());
        }

        return params;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button_primary) {
            mChromeHomeShouldBeEnabled = true;
        } else if (view.getId() == R.id.button_secondary) {
            mChromeHomeShouldBeEnabled = false;
        }

        dismiss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mChromeHomeEnabledOnShow = FeatureUtilities.isChromeHomeEnabled();

        if (sTestObserver != null) sTestObserver.onDialogShown(this);
    }

    /**
     * Restart any open Chrome instances, including the activity this promo is running in.
     */
    private void restartChromeInstances() {
        // If there can be multiple activities, restart them before restarting this one.
        if (FeatureUtilities.isTabModelMergingEnabled()) {
            Class<?> otherWindowActivityClass =
                    MultiWindowUtils.getInstance().getOpenInOtherWindowActivity(getOwnerActivity());

            for (WeakReference<Activity> activityRef : ApplicationStatus.getRunningActivities()) {
                Activity activity = activityRef.get();
                if (activity == null) continue;
                if (activity.getClass().equals(otherWindowActivityClass)) {
                    activity.recreate();
                    break;
                }
            }
        }

        ChromeActivity activity = (ChromeActivity) getOwnerActivity();
        final Tab tab = activity.getActivityTab();

        boolean showOptOutSnackbar = false;
        if (tab != null && !mChromeHomeShouldBeEnabled
                && ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_OPT_OUT_SNACKBAR)) {
            try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
                showOptOutSnackbar =
                        !ChromePreferenceManager.getInstance().getChromeHomeOptOutSnackbarShown();
            }
        }

        Runnable finalizeCallback = null;
        if (showOptOutSnackbar) {
            finalizeCallback = new Runnable() {
                @Override
                public void run() {
                    ChromeHomeSnackbarController.initialize(tab);
                }
            };
        }

        // Detach the foreground tab and. It will be reattached when the activity is restarted.
        if (tab != null) tab.detachAndStartReparenting(null, null, finalizeCallback);

        getOwnerActivity().recreate();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        // If the dialog is info-only, do not record any metrics since there were no provided
        // options.
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_PROMO_INFO_ONLY)) return;

        // If the state of Chrome Home changed while this dialog was opened, do nothing. This can
        // happen in multi-window if this dialog is shown in both windows.
        if (mChromeHomeEnabledOnShow != FeatureUtilities.isChromeHomeEnabled()) return;

        String histogramName = null;
        switch (mShowReason) {
            case ShowReason.MENU:
                histogramName = "Android.ChromeHome.Promo.Result.Menu";
                break;
            case ShowReason.NTP:
                histogramName = "Android.ChromeHome.Promo.Result.NTP";
                break;
            case ShowReason.STARTUP:
                histogramName = "Android.ChromeHome.Promo.Result.Startup";
                break;
            default:
                assert false;
        }

        @PromoResult
        int state;
        if (FeatureUtilities.isChromeHomeEnabled()) {
            state = mChromeHomeShouldBeEnabled ? PromoResult.REMAINED_ENABLED
                                               : PromoResult.DISABLED;
        } else {
            state = mChromeHomeShouldBeEnabled ? PromoResult.ENABLED
                                               : PromoResult.REMAINED_DISABLED;
        }
        RecordHistogram.recordEnumeratedHistogram(histogramName, state, PromoResult.BOUNDARY);

        boolean restartRequired =
                mChromeHomeShouldBeEnabled != FeatureUtilities.isChromeHomeEnabled();
        FeatureUtilities.switchChromeHomeUserSetting(mChromeHomeShouldBeEnabled);

        if (restartRequired) restartChromeInstances();
    }

    /**
     * An observer to be notified about dialog events.  Used for testing. Must be called on the UI
     * thread.
     */
    @VisibleForTesting
    public static void setObserverForTests(ChromeHomePromoDialogTestObserver observer) {
        ThreadUtils.assertOnUiThread();
        sTestObserver = observer;
    }
}
