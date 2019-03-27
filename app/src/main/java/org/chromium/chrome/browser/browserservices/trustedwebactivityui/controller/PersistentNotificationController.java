// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PERSISTENT_NOTIFICATION;
import static org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PERSISTENT_NOTIFICATION_TAG;

import android.os.Handler;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.browserservices.Origin;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel.PersistentNotificationData;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier.VerificationState;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.lifecycle.StartStopWithNativeObserver;

import javax.inject.Inject;

/**
 * Controls when the TWA persistent notification should be shown or hidden.
 * The notification is shown while the activity is in started state. It is not removed when the user
 * leaves the origin associated with the app by following links.
 */
@ActivityScope
public class PersistentNotificationController implements StartStopWithNativeObserver {
    private final CustomTabIntentDataProvider mIntentDataProvider;
    private final TrustedWebActivityModel mModel;
    private final TrustedWebActivityVerifier mVerifier;

    private boolean mStarted;

    // Origin for which the notification is currently shown. Null when notification not shown.
    @Nullable
    private Origin mLastVerifiedOrigin;

    @Nullable
    private Handler mHandler;

    @Inject
    public PersistentNotificationController(ActivityLifecycleDispatcher lifecycleDispatcher,
            CustomTabIntentDataProvider intentDataProvider,
            TrustedWebActivityModel model,
            TrustedWebActivityVerifier verifier) {
        mIntentDataProvider = intentDataProvider;
        mModel = model;
        mVerifier = verifier;
        lifecycleDispatcher.register(this);
        mVerifier.addVerificationObserver(this::onVerificationStateChanged);
        mModel.set(PERSISTENT_NOTIFICATION_TAG, mVerifier.getClientPackageName());
    }

    private void onVerificationStateChanged() {
        VerificationState state = mVerifier.getState();
        if (state == null) {
            return;
        }
        if (state.status == TrustedWebActivityVerifier.VERIFICATION_FAILURE) {
            return; // Keep showing the notification despite we've left the verified origin
        }
        if (state.origin.equals(mLastVerifiedOrigin)) {
            return;
        }
        mLastVerifiedOrigin = state.origin;
        if (mStarted) {
            show();
        }
    }

    @Override
    public void onStartWithNative() {
        mStarted = true;
        if (mLastVerifiedOrigin != null) {
            show();
        }
    }

    @Override
    public void onStopWithNative() {
        mStarted = false;
        hide();
    }

    private void show() {
        assert mLastVerifiedOrigin != null;
        mModel.set(PERSISTENT_NOTIFICATION, new PersistentNotificationData(
                mIntentDataProvider.getIntent(), mLastVerifiedOrigin));
    }

    private void hide() {
        mModel.set(PERSISTENT_NOTIFICATION, null);
    }
}
