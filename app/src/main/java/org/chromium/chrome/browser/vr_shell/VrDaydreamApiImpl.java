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
 * A wrapper for DaydreamApi. Note that we have to recreate the DaydreamApi instance each time we
 * use it, or API calls begin to silently fail.
 */
public class VrDaydreamApiImpl implements VrDaydreamApi {
    private final Context mContext;

    private Boolean mBootsToVr = null;

    public static final String VR_BOOT_SYSTEM_PROPERTY = "ro.boot.vr";

    public VrDaydreamApiImpl(Context context) {
        mContext = context;
    }

    @Override
    public boolean isDaydreamReadyDevice() {
        return DaydreamApi.isDaydreamReadyPlatform(mContext);
    }

    @Override
    public boolean registerDaydreamIntent(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
        if (daydreamApi == null) return false;
        daydreamApi.registerDaydreamIntent(pendingIntent);
        daydreamApi.close();
        return true;
    }

    @Override
    public boolean unregisterDaydreamIntent() {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
        if (daydreamApi == null) return false;
        daydreamApi.unregisterDaydreamIntent();
        daydreamApi.close();
        return true;
    }

    @Override
    public Intent createVrIntent(final ComponentName componentName) {
        return DaydreamApi.createVrIntent(componentName);
    }

    @Override
    public boolean launchInVr(final PendingIntent pendingIntent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
        if (daydreamApi == null) return false;
        daydreamApi.launchInVr(pendingIntent);
        daydreamApi.close();
        return true;
    }

    @Override
    public boolean launchInVr(final Intent intent) {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
        if (daydreamApi == null) return false;
        daydreamApi.launchInVr(intent);
        daydreamApi.close();
        return true;
    }

    @Override
    public boolean exitFromVr(int requestCode, final Intent intent) {
        Activity activity = WindowAndroid.activityFromContext(mContext);
        if (activity == null) {
            throw new IllegalStateException("Activity is null");
        }
        DaydreamApi daydreamApi = DaydreamApi.create(activity);
        if (daydreamApi == null) return false;
        daydreamApi.exitFromVr(activity, requestCode, intent);
        daydreamApi.close();
        return true;
    }

    @Override
    public Boolean isDaydreamCurrentViewer() {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
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
        daydreamApi.close();
        return type == GvrApi.ViewerType.DAYDREAM;
    }

    @Override
    public boolean launchVrHomescreen() {
        DaydreamApi daydreamApi = DaydreamApi.create(mContext);
        if (daydreamApi == null) return false;
        daydreamApi.launchVrHomescreen();
        daydreamApi.close();
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
