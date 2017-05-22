// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.Manifest;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.components.location.LocationSettingsDialogContext.LocationSettingsDialogContextEnum;
import org.chromium.components.location.LocationSettingsDialogOutcome;
import org.chromium.components.location.LocationUtils;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Provides methods for querying Chrome's internal location setting and
 * combining that with the system-wide setting and permissions.
 *
 * This class should be used only on the UI thread.
 */
public class LocationSettings {

    private static LocationSettings sInstance;

    /**
     * Don't use this; use getInstance() instead. This should be used only by the Application inside
     * of createLocationSettings().
     */
    protected LocationSettings() {
    }

    /**
     * Returns the singleton instance of LocationSettings, creating it if needed.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static LocationSettings getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = AppHooks.get().createLocationSettings();
        }
        return sInstance;
    }

    @CalledByNative
    private static boolean canSitesRequestLocationPermission(WebContents webContents) {
        WindowAndroid windowAndroid = windowFromWebContents(webContents);
        if (windowAndroid == null) return false;

        LocationUtils locationUtils = LocationUtils.getInstance();
        if (!locationUtils.isSystemLocationSettingEnabled()) return false;

        return locationUtils.hasAndroidLocationPermission()
                || windowAndroid.canRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @CalledByNative
    private static boolean canPromptToEnableSystemLocationSetting() {
        return LocationUtils.getInstance().canPromptToEnableSystemLocationSetting();
    }

    @CalledByNative
    private static void promptToEnableSystemLocationSetting(
            @LocationSettingsDialogContextEnum int promptContext, WebContents webContents,
            final long nativeCallback) {
        WindowAndroid window = windowFromWebContents(webContents);
        if (window == null) {
            nativeOnLocationSettingsDialogOutcome(
                    nativeCallback, LocationSettingsDialogOutcome.NO_PROMPT);
            return;
        }
        LocationUtils.getInstance().promptToEnableSystemLocationSetting(
                promptContext, window, new Callback<Integer>() {
                    @Override
                    public void onResult(Integer result) {
                        nativeOnLocationSettingsDialogOutcome(nativeCallback, result);
                    }
                });
    }

    /**
     * Returns true if location is enabled system-wide and the Chrome location setting is enabled.
     */
    public boolean areAllLocationSettingsEnabled() {
        return isChromeLocationSettingEnabled()
                && LocationUtils.getInstance().isSystemLocationSettingEnabled();
    }

    /**
     * Returns whether Chrome's user-configurable location setting is enabled.
     */
    public boolean isChromeLocationSettingEnabled() {
        return PrefServiceBridge.getInstance().isAllowLocationEnabled();
    }

    @VisibleForTesting
    public static void setInstanceForTesting(LocationSettings instance) {
        sInstance = instance;
    }

    private static WindowAndroid windowFromWebContents(WebContents webContents) {
        ContentViewCore contentViewCore = ContentViewCore.fromWebContents(webContents);
        if (contentViewCore == null) return null;
        return contentViewCore.getWindowAndroid();
    }

    private static native void nativeOnLocationSettingsDialogOutcome(long callback, int result);
}
