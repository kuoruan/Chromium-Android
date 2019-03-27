// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.view.ViewGroup;

import org.chromium.chrome.browser.tab.Tab;

/** Delegate for {@link SplashScreenController}. */
interface WebappSplashDelegate {
    /** Shows the splash screen. */
    void showSplash(ViewGroup parentView, WebappInfo webappInfo);

    /**
     * Called once native has been loaded. This may be called either before or after
     * {@link #showSplash()}.
     */
    void onNativeLoaded();

    /** Hides the splash screen. Runs the callback once the splash screen is hidden. */
    void hideSplash(Runnable finishedHidingCallback);

    /** Returns whether the splash screen is visible and not in the process of hiding. */
    boolean isSplashVisible();

    /**
     * Returns the {@link ViewGroup} containing the splash screen if it is a direct child of
     * the passed-in view.
     */
    ViewGroup getSplashViewIfChildOf(ViewGroup parent);

    /** Returns whether the network error dialog is visible. */
    boolean isNetworkErrorDialogVisible();

    /** Shows the network error dialog. */
    void showNetworkErrorDialog(Tab tab, String errorMsg);

    /** Hides the network error dialog. */
    void hideNetworkErrorDialog();
}
