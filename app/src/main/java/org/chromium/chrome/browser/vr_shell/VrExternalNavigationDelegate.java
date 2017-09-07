// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.content.Intent;

import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;

/**
 * A custom external navigation delegate that show DOFF instead of sending intent to external app.
 */
public class VrExternalNavigationDelegate extends ExternalNavigationDelegateImpl {
    public VrExternalNavigationDelegate(Tab tab) {
        super(tab);
    }

    @Override
    public void startActivity(Intent intent, boolean proxy) {
        VrShellDelegate.showDoffAndExitVr(false);
    }

    @Override
    public boolean startActivityIfNeeded(Intent intent, boolean proxy) {
        return false;
    }

    @Override
    public void startIncognitoIntent(Intent intent, String referrerUrl, String fallbackUrl, Tab tab,
            boolean needsToCloseTab, boolean proxy) {
        VrShellDelegate.showDoffAndExitVr(false);
    }

    @Override
    public void startFileIntent(
            Intent intent, String referrerUrl, Tab tab, boolean needsToCloseTab) {
        VrShellDelegate.showDoffAndExitVr(false);
    }
}
