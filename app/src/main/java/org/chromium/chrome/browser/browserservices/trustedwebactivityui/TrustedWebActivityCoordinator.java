// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui;

import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityDisclosureController;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityOpenTimeRecorder;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityToolbarController;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityDisclosureView;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityToolbarView;
import org.chromium.chrome.browser.customtabs.CloseButtonNavigator;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;

import javax.inject.Inject;

/**
 * Coordinator for the Trusted Web Activity component.
 * Add methods here if other components need to communicate with Trusted Web Activity component.
 */
@ActivityScope
public class TrustedWebActivityCoordinator {
    @Inject
    public TrustedWebActivityCoordinator(
            TrustedWebActivityDisclosureController disclosureController,
            TrustedWebActivityToolbarController toolbarController,
            TrustedWebActivityToolbarView toolbarView,
            TrustedWebActivityDisclosureView disclosureView,
            TrustedWebActivityOpenTimeRecorder openTimeRecorder,
            TrustedWebActivityVerifier verifier,
            CloseButtonNavigator closeButtonNavigator) {
        // We don't need to do anything with most of the classes above, we just need to resolve them
        // so they start working.

        closeButtonNavigator.setLandingPageCriteria(verifier::isPageOnVerifiedOrigin);
    }
}
