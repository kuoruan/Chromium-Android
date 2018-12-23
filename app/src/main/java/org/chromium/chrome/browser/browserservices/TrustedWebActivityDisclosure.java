// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices;

import android.content.res.Resources;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;

import javax.inject.Inject;

/**
 * Shows the Trusted Web Activity disclosure when appropriate and records its acceptance.
 *
 * Lifecycle: There should be a 1-1 relationship between this class and
 * {@link TrustedWebActivityUi}.
 * Thread safety: All methods on this class should be called on the UI thread.
 */
@ActivityScope
public class TrustedWebActivityDisclosure {
    // TODO(peconn): Make this package private once TrustedWebActivityUi can be injected.
    private final Resources mResources;
    private final ChromePreferenceManager mPreferenceManager;

    private boolean mSnackbarShowing;

    /**
     * A {@link SnackbarManager.SnackbarController} that records the users acceptance of the
     * "Running in Chrome" disclosure.
     *
     * It is also used as a key to for our snackbar so we can dismiss it when the user navigates
     * to a page where they don't need to show the disclosure.
     */
    private final SnackbarManager.SnackbarController mSnackbarController =
            new SnackbarManager.SnackbarController() {
                /**
                 * To be called when the user accepts the Running in Chrome disclosure.
                 * @param actionData The package name of the client, as a String.
                 */
                @Override
                public void onAction(Object actionData) {
                    mPreferenceManager.setUserAcceptedTwaDisclosureForPackage((String) actionData);
                }
            };

    @Inject
    /* package */ TrustedWebActivityDisclosure(Resources resources,
            ChromePreferenceManager preferenceManager) {
        mResources = resources;
        mPreferenceManager = preferenceManager;
    }

    /** Dismisses the Snackbar if it is showing. */
    /* package */ void dismissSnackbarIfNeeded(SnackbarManager snackbarManager) {
        if (!mSnackbarShowing) return;

        snackbarManager.dismissSnackbars(mSnackbarController);
        mSnackbarShowing = false;
    }

    /** Shows the Snackbar if it is not already showing and hasn't been accepted. */
    /* package */ void showSnackbarIfNeeded(SnackbarManager snackbarManager, String packageName) {
        if (mSnackbarShowing) return;
        if (wasSnackbarDismissed(packageName)) return;

        snackbarManager.showSnackbar(makeRunningInChromeInfobar(packageName));
        mSnackbarShowing = true;
    }

    /** Has a Snackbar been dismissed for this client package before? */
    private boolean wasSnackbarDismissed(String packageName) {
        return mPreferenceManager.hasUserAcceptedTwaDisclosureForPackage(packageName);
    }

    private Snackbar makeRunningInChromeInfobar(String packageName) {
        String title = mResources.getString(R.string.twa_running_in_chrome);
        int type = Snackbar.TYPE_PERSISTENT;
        int code = Snackbar.UMA_TWA_PRIVACY_DISCLOSURE;

        String action = mResources.getString(R.string.ok_got_it);
        return Snackbar.make(title, mSnackbarController, type, code)
                .setAction(action, packageName)
                .setSingleLine(false);
    }
}
