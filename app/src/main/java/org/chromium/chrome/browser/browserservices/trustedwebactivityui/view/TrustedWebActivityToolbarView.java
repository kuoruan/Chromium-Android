// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui.view;

import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.TOOLBAR_HIDDEN;

import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.customtabs.CustomTabBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyObservable;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * Hides and shows the toolbar according to the state of the Trusted Web Activity.
 */
@ActivityScope
public class TrustedWebActivityToolbarView implements
        PropertyObservable.PropertyObserver<PropertyKey> {
    private final Lazy<ChromeFullscreenManager> mFullscreenManager;
    private final CustomTabBrowserControlsVisibilityDelegate mControlsVisibilityDelegate;
    private final TrustedWebActivityModel mModel;

    private int mControlsHidingToken = FullscreenManager.INVALID_TOKEN;

    @Inject
    public TrustedWebActivityToolbarView(Lazy<ChromeFullscreenManager> fullscreenManager,
            CustomTabBrowserControlsVisibilityDelegate controlsVisibilityDelegate,
            TrustedWebActivityModel model) {
        mFullscreenManager = fullscreenManager;
        mControlsVisibilityDelegate = controlsVisibilityDelegate;
        mModel = model;
        mModel.addObserver(this);
    }

    @Override
    public void onPropertyChanged(PropertyObservable<PropertyKey> observable, PropertyKey key) {
        if (key != TOOLBAR_HIDDEN) return;

        boolean hide = mModel.get(TOOLBAR_HIDDEN);

        mControlsVisibilityDelegate.setTrustedWebActivityMode(hide);

        ChromeFullscreenManager fullscreenManager = mFullscreenManager.get();
        if (hide) {
            mControlsHidingToken =
                    fullscreenManager.hideAndroidControlsAndClearOldToken(mControlsHidingToken);
        } else {
            fullscreenManager.releaseAndroidControlsHidingToken(mControlsHidingToken);
            // Force showing the controls for a bit when leaving Trusted Web Activity mode.
            fullscreenManager.getBrowserVisibilityDelegate().showControlsTransient();
        }
    }
}
