// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.TOOLBAR_HIDDEN;
import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier.VERIFICATION_FAILURE;

import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier.VerificationState;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.InflationObserver;

import javax.inject.Inject;

/**
 * Controls the visibility of the toolbar in Trusted Web Activity.
 * Toolbar is hidden when user browses the pages belonging to the verified origin and appears when
 * user leaves the verified origin.
 */
@ActivityScope
public class TrustedWebActivityToolbarController implements InflationObserver {

    private final TrustedWebActivityModel mModel;
    private final TrustedWebActivityVerifier mVerifier;

    @Inject
    public TrustedWebActivityToolbarController(
            TrustedWebActivityModel model,
            TrustedWebActivityVerifier verifier,
            ActivityLifecycleDispatcher lifecycleDispatcher) {
        mModel = model;
        mVerifier = verifier;

        mVerifier.addVerificationObserver(this::handleVerificationUpdate);
        lifecycleDispatcher.register(this);
    }

    @Override
    public void onPreInflationStartup() {}

    @Override
    public void onPostInflationStartup() {
        // Before the verification completes, we optimistically expect it to be successful and apply
        // the trusted web activity mode to UI. So hide the toolbar as soon as possible.
        if (mVerifier.getState() == null) {
            mModel.set(TOOLBAR_HIDDEN, true);
        }
    }

    private void handleVerificationUpdate() {
        VerificationState state = mVerifier.getState();
        boolean shouldHide = state == null || state.status != VERIFICATION_FAILURE;
        mModel.set(TOOLBAR_HIDDEN, shouldHide);
    }
}
