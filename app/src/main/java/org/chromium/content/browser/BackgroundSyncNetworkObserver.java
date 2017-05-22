// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeClassQualifiedName;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.net.NetworkChangeNotifierAutoDetect;
import org.chromium.net.RegistrationPolicyAlwaysRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the Java code used by the BackgroundSyncNetworkObserverAndroid C++ class.
 *
 * The purpose of this class is to listen for and forward network connectivity events to the
 * BackgroundSyncNetworkObserverAndroid objects even when the application is paused. The standard
 * NetworkChangeNotifier does not listen for connectivity events when the application is paused.
 *
 * This class maintains a NetworkChangeNotifierAutoDetect, which exists for as long as any
 * BackgroundSyncNetworkObserverAndroid objects are registered.
 *
 * This class lives on the main thread.
 */
@JNINamespace("content")
class BackgroundSyncNetworkObserver implements NetworkChangeNotifierAutoDetect.Observer {
    private static final String TAG = "cr_BgSyncNetObserver";

    private NetworkChangeNotifierAutoDetect mNotifier;
    private Context mContext;

    // The singleton instance.
    private static BackgroundSyncNetworkObserver sInstance;

    // List of native observers. These are each called when the network state changes.
    private List<Long> mNativePtrs;

    private BackgroundSyncNetworkObserver(Context ctx) {
        ThreadUtils.assertOnUiThread();
        mContext = ctx;
        mNativePtrs = new ArrayList<Long>();
    }

    private static boolean canCreateObserver(Context ctx) {
        return ApiCompatibilityUtils.checkPermission(ctx, Manifest.permission.ACCESS_NETWORK_STATE,
                Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    @CalledByNative
    private static BackgroundSyncNetworkObserver createObserver(Context ctx, long nativePtr) {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = new BackgroundSyncNetworkObserver(ctx);
        }
        sInstance.registerObserver(nativePtr);
        return sInstance;
    }

    private void registerObserver(final long nativePtr) {
        ThreadUtils.assertOnUiThread();
        if (!canCreateObserver(mContext)) {
            RecordHistogram.recordBooleanHistogram(
                    "BackgroundSync.NetworkObserver.HasPermission", false);
            return;
        }

        // Create the NetworkChangeNotifierAutoDetect if it does not exist already.
        if (mNotifier == null) {
            mNotifier = new NetworkChangeNotifierAutoDetect(this, mContext,
                                new RegistrationPolicyAlwaysRegister());
            RecordHistogram.recordBooleanHistogram(
                    "BackgroundSync.NetworkObserver.HasPermission", true);
        }
        mNativePtrs.add(nativePtr);

        nativeNotifyConnectionTypeChanged(
                nativePtr, NetworkChangeNotifierAutoDetect.convertToConnectionType(
                                   mNotifier.getCurrentNetworkState()));
    }

    @CalledByNative
    private void removeObserver(long nativePtr) {
        ThreadUtils.assertOnUiThread();
        mNativePtrs.remove(nativePtr);
        // Destroy the NetworkChangeNotifierAutoDetect if there are no more observers.
        if (mNativePtrs.size() == 0 && mNotifier != null) {
            mNotifier.destroy();
            mNotifier = null;
        }
    }

    @Override
    public void onConnectionTypeChanged(int newConnectionType) {
        ThreadUtils.assertOnUiThread();
        for (Long nativePtr : mNativePtrs) {
            nativeNotifyConnectionTypeChanged(nativePtr, newConnectionType);
        }
    }

    @Override
    public void onMaxBandwidthChanged(double maxBandwidthMbps) {}
    @Override
    public void onNetworkConnect(long netId, int connectionType) {}
    @Override
    public void onNetworkSoonToDisconnect(long netId) {}
    @Override
    public void onNetworkDisconnect(long netId) {}
    @Override
    public void purgeActiveNetworkList(long[] activeNetIds) {}

    @NativeClassQualifiedName("BackgroundSyncNetworkObserverAndroid::Observer")
    private native void nativeNotifyConnectionTypeChanged(long nativePtr, int newConnectionType);
}
