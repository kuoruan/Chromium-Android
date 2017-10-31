// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement.internal;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.components.feature_engagement.TriggerState;

/**
 * Java side of the JNI bridge between TrackerImpl in Java
 * and C++. All method calls are delegated to the native C++ class.
 */
@JNINamespace("feature_engagement")
public class TrackerImpl implements Tracker {
    /**
     * The pointer to the feature_engagement::TrackerImplAndroid JNI bridge.
     */
    private long mNativePtr;

    @CalledByNative
    private static TrackerImpl create(long nativePtr) {
        return new TrackerImpl(nativePtr);
    }

    private TrackerImpl(long nativePtr) {
        mNativePtr = nativePtr;
    }

    @Override
    public void notifyEvent(String event) {
        assert mNativePtr != 0;
        nativeNotifyEvent(mNativePtr, event);
    }

    @Override
    public boolean shouldTriggerHelpUI(String feature) {
        assert mNativePtr != 0;
        return nativeShouldTriggerHelpUI(mNativePtr, feature);
    }

    @Override
    @TriggerState
    public int getTriggerState(String feature) {
        assert mNativePtr != 0;
        return nativeGetTriggerState(mNativePtr, feature);
    }

    @Override
    public void dismissed(String feature) {
        assert mNativePtr != 0;
        nativeDismissed(mNativePtr, feature);
    }

    @Override
    public boolean isInitialized() {
        assert mNativePtr != 0;
        return nativeIsInitialized(mNativePtr);
    }

    @Override
    public void addOnInitializedCallback(Callback<Boolean> callback) {
        assert mNativePtr != 0;
        nativeAddOnInitializedCallback(mNativePtr, callback);
    }

    @CalledByNative
    private void clearNativePtr() {
        mNativePtr = 0;
    }

    @CalledByNative
    private long getNativePtr() {
        assert mNativePtr != 0;
        return mNativePtr;
    }

    private native void nativeNotifyEvent(long nativeTrackerImplAndroid, String event);
    private native boolean nativeShouldTriggerHelpUI(long nativeTrackerImplAndroid, String feature);
    @TriggerState
    private native int nativeGetTriggerState(long nativeTrackerImplAndroid, String feature);
    private native void nativeDismissed(long nativeTrackerImplAndroid, String feature);
    private native boolean nativeIsInitialized(long nativeTrackerImplAndroid);
    private native void nativeAddOnInitializedCallback(
            long nativeTrackerImplAndroid, Callback<Boolean> callback);
}
