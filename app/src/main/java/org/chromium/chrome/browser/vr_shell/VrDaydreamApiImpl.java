// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;

import com.google.vr.ndk.base.DaydreamApi;
import com.google.vr.ndk.base.GvrApi;

import org.chromium.base.Log;
import org.chromium.ui.base.WindowAndroid;

import java.lang.reflect.Method;

/**
 * A wrapper for DaydreamApi.
 */
public class VrDaydreamApiImpl implements VrDaydreamApi {
    public static final String VR_BOOT_SYSTEM_PROPERTY = "ro.boot.vr";

    private final Context mContext;

    private DaydreamApi mDaydreamApi;
    private Boolean mBootsToVr = null;

    public VrDaydreamApiImpl(Context context) {
        mContext = context;
    }

    private DaydreamApi getDaydreamApi() {
        if (mDaydreamApi == null) mDaydreamApi = DaydreamApi.create(mContext);
        return mDaydreamApi;
    }

    @Override
    public boolean isDaydreamReadyDevice() {
        return DaydreamApi.isDaydreamReadyPlatform(mContext);
    }

    @Override
    public boolean registerDaydreamIntent(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.registerDaydreamIntent(pendingIntent);
        return true;
    }

    @Override
    public boolean unregisterDaydreamIntent() {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.unregisterDaydreamIntent();
        return true;
    }

    @Override
    public Intent createVrIntent(final ComponentName componentName) {
        return DaydreamApi.createVrIntent(componentName);
    }

    @Override
    public boolean launchInVr(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.launchInVr(pendingIntent);
        return true;
    }

    @Override
    public boolean launchInVr(final Intent intent) {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.launchInVr(intent);
        return true;
    }

    @Override
    public boolean exitFromVr(int requestCode, final Intent intent) {
        Activity activity = WindowAndroid.activityFromContext(mContext);
        if (activity == null) {
            throw new IllegalStateException("Activity is null");
        }
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.exitFromVr(activity, requestCode, intent);
        return true;
    }

    @Override
    public Boolean isDaydreamCurrentViewer() {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        // If this is the first time any app reads the daydream config file, daydream may create its
        // config directory... crbug.com/686104
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        int type = GvrApi.ViewerType.CARDBOARD;
        try {
            type = daydreamApi.getCurrentViewerType();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return type == GvrApi.ViewerType.DAYDREAM;
    }

    @Override
    public boolean launchVrHomescreen() {
        DaydreamApi daydreamApi = getDaydreamApi();
        if (daydreamApi == null) return false;
        daydreamApi.launchVrHomescreen();
        return true;
    }

    @Override
    public boolean bootsToVr() {
        if (mBootsToVr == null) {
            // TODO(mthiesse): Replace this with a Daydream API call when supported.
            // Note that System.GetProperty is unable to read system ro properties, so we have to
            // resort to reflection as seen below. This method of reading system properties has been
            // available since API level 1.
            mBootsToVr = getIntSystemProperty(VR_BOOT_SYSTEM_PROPERTY, 0) == 1;
        }
        return mBootsToVr;
    }

    @Override
    public void close() {
        if (mDaydreamApi == null) return;
        mDaydreamApi.close();
        mDaydreamApi = null;
    }

    private int getIntSystemProperty(String key, int defaultValue) {
        try {
            final Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            final Method getInt = systemProperties.getMethod("getInt", String.class, int.class);
            return (Integer) getInt.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e("Exception while getting system property %s. Using default %s.", key,
                    defaultValue, e);
            return defaultValue;
        }
    }
}
