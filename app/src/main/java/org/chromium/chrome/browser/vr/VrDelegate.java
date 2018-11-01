// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.chrome.browser.ChromeActivity;

/** Interface to call into VR. */
public interface VrDelegate {
    void forceExitVrImmediately();
    boolean onActivityResultWithNative(int requestCode, int resultCode);
    void onNativeLibraryAvailable();
    boolean isInVr();
    boolean canLaunch2DIntents();
    boolean onBackPressed();
    boolean enterVrIfNecessary();
    void maybeRegisterVrEntryHook(final ChromeActivity activity);
    void maybeUnregisterVrEntryHook();
    void onMultiWindowModeChanged(boolean isInMultiWindowMode);
    void requestToExitVrForSearchEnginePromoDialog(
            OnExitVrRequestListener listener, Activity activity);
    void requestToExitVr(OnExitVrRequestListener listener);
    void requestToExitVr(OnExitVrRequestListener listener, @UiUnsupportedMode int reason);
    void requestToExitVrAndRunOnSuccess(Runnable onSuccess);
    void requestToExitVrAndRunOnSuccess(Runnable onSuccess, @UiUnsupportedMode int reason);
    void onActivityShown(ChromeActivity activity);
    void onActivityHidden(ChromeActivity activity);
    boolean onDensityChanged(int oldDpi, int newDpi);
    void rawTopContentOffsetChanged(float topContentOffset);
    void onNewIntentWithNative(ChromeActivity activity, Intent intent);
    void maybeHandleVrIntentPreNative(ChromeActivity activity, Intent intent);
    void setVrModeEnabled(Activity activity, boolean enabled);
    void doPreInflationStartup(ChromeActivity activity, Bundle savedInstanceState);
    boolean bootsToVr();
    boolean isDaydreamReadyDevice();
    boolean isDaydreamCurrentViewer();
    boolean willChangeDensityInVr(ChromeActivity activity);
    void onSaveInstanceState(Bundle outState);
}
