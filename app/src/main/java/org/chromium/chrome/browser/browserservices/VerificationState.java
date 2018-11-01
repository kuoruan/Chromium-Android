// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


package org.chromium.chrome.browser.browserservices;

import android.support.customtabs.CustomTabsService;

import org.chromium.base.Callback;
import org.chromium.base.Promise;

/**
 * Kicks off Digital Asset Link verification and remembers the result.
 */
public class VerificationState {
    private final Promise<Boolean> mVerification = new Promise<>();

    /**
     * Verify the Digital Asset Links declared by the Android native client with the currently
     * loading origin. See {@link #didVerificationFail()} for the result.
     */
    public VerificationState(String packageName, Origin origin, Callback<Boolean> onResult) {
        mVerification.then(onResult);

        new OriginVerifier(
                (packageName2, origin2, verified, online) -> mVerification.fulfill(verified),
                packageName, CustomTabsService.RELATION_HANDLE_ALL_URLS).start(origin);
    }

    /**
     * @return Whether origin verification for the corresponding client failed. If verification is
     * not complete, this will return false.
     */
    public boolean didVerificationFail() {
        return mVerification.isFulfilled() && !mVerification.getResult();
    }
}
