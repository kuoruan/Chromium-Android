// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.chrome.browser.ChromeActivity;

/**
 * Fallback {@link VrDelegate} and {@link VrIntentDelegate} implementation if the VR module is not
 * available.
 */
/* package */ class VrDelegateFallback implements VrDelegate, VrIntentDelegate {
    @Override
    public void forceExitVrImmediately() {}

    @Override
    public boolean onActivityResultWithNative(int requestCode, int resultCode) {
        return false;
    }

    @Override
    public void onNativeLibraryAvailable() {}

    @Override
    public boolean isInVr() {
        return false;
    }

    @Override
    public boolean canLaunch2DIntents() {
        return true;
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public boolean enterVrIfNecessary() {
        return false;
    }

    @Override
    public void maybeRegisterVrEntryHook(final ChromeActivity activity) {}

    @Override
    public void maybeUnregisterVrEntryHook() {}

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {}

    @Override
    public void requestToExitVrForSearchEnginePromoDialog(
            OnExitVrRequestListener listener, Activity activity) {
        listener.onSucceeded();
    }

    @Override
    public void requestToExitVr(OnExitVrRequestListener listener) {
        listener.onSucceeded();
    }

    @Override
    public void requestToExitVr(OnExitVrRequestListener listener, @UiUnsupportedMode int reason) {
        listener.onSucceeded();
    }

    @Override
    public void requestToExitVrAndRunOnSuccess(Runnable onSuccess) {
        onSuccess.run();
    }

    @Override
    public void requestToExitVrAndRunOnSuccess(Runnable onSuccess, @UiUnsupportedMode int reason) {
        onSuccess.run();
    }

    @Override
    public void onActivityShown(ChromeActivity activity) {}

    @Override
    public void onActivityHidden(ChromeActivity activity) {}

    @Override
    public boolean onDensityChanged(int oldDpi, int newDpi) {
        return false;
    }

    @Override
    public void rawTopContentOffsetChanged(float topContentOffset) {}

    @Override
    public void onNewIntentWithNative(ChromeActivity activity, Intent intent) {}

    @Override
    public void maybeHandleVrIntentPreNative(ChromeActivity activity, Intent intent) {}

    @Override
    public void setVrModeEnabled(Activity activity, boolean enabled) {
        assert false;
    }

    @Override
    public void doPreInflationStartup(ChromeActivity activity, Bundle savedInstanceState) {}

    @Override
    public boolean bootsToVr() {
        return false;
    }

    @Override
    public boolean isDaydreamReadyDevice() {
        return false;
    }

    @Override
    public boolean isDaydreamCurrentViewer() {
        return false;
    }

    @Override
    public boolean isVrIntent(Intent intent) {
        return false;
    }

    @Override
    public boolean isLaunchingIntoVr(Activity activity, Intent intent) {
        return false;
    }

    @Override
    public Intent setupVrFreIntent(Context context, Intent freIntent) {
        assert false;
        return freIntent;
    }

    @Override
    public Bundle getVrIntentOptions(Context context) {
        assert false;
        return null;
    }

    @Override
    public boolean willChangeDensityInVr(ChromeActivity activity) {
        assert false;
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {}
}
