// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.device.vr;

import android.content.Context;
import android.os.StrictMode;
import android.view.Display;
import android.view.WindowManager;

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.ndk.base.GvrApi;

import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.display.DisplayAndroid;

/**
 * Creates an active GvrContext from a GvrApi created from the Application Context. This GvrContext
 * cannot be used for VR rendering, and should only be used to query pose information and device
 * parameters.
 */
@JNINamespace("device")
public class NonPresentingGvrContext implements DisplayAndroid.DisplayAndroidObserver {
    private GvrApi mGvrApi;
    private DisplayAndroid mDisplayAndroid;

    private long mNativeGvrDevice;

    private NonPresentingGvrContext(long nativeGvrDevice) {
        mNativeGvrDevice = nativeGvrDevice;
        Context context = ContextUtils.getApplicationContext();
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplaySynchronizer synchronizer = new DisplaySynchronizer(context, display);

        // Creating the GvrApi can sometimes create the Daydream config file.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            mGvrApi = new GvrApi(context, synchronizer);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        mDisplayAndroid = DisplayAndroid.getNonMultiDisplay(context);
        mDisplayAndroid.addObserver(this);
    }

    @CalledByNative
    private static NonPresentingGvrContext create(long nativeNonPresentingGvrContext) {
        try {
            return new NonPresentingGvrContext(nativeNonPresentingGvrContext);
        } catch (IllegalStateException | UnsatisfiedLinkError e) {
            return null;
        }
    }

    @CalledByNative
    private long getNativeGvrContext() {
        return mGvrApi.getNativeGvrContext();
    }

    @CalledByNative
    private void shutdown() {
        mGvrApi.shutdown();
        mDisplayAndroid.removeObserver(this);
        mNativeGvrDevice = 0;
    }

    public void onRotationChanged(int rotation) {}

    public void onDIPScaleChanged(float dipScale) {
        mGvrApi.refreshDisplayMetrics();
        if (mNativeGvrDevice != 0) {
            nativeOnDIPScaleChanged(mNativeGvrDevice);
        }
    }

    private native void nativeOnDIPScaleChanged(long nativeGvrDevice);
}
