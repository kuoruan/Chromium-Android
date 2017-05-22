// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.os.StrictMode;
import android.provider.Settings;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.GooglePlayInstallState;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Contains functionality needed for Chrome to host WebAPKs.
 */
public class ChromeWebApkHost {
    private static final String TAG = "ChromeWebApkHost";

    /** Whether installing WebAPks from Google Play is possible. */
    private static Integer sGooglePlayInstallState;

    private static Boolean sEnabledForTesting;

    public static void init() {
        WebApkValidator.initWithBrowserHostSignature(ChromeWebApkHostSignature.EXPECTED_SIGNATURE);
    }

    public static void initForTesting(boolean enabled) {
        sEnabledForTesting = enabled;
    }

    public static boolean isEnabled() {
        if (sEnabledForTesting != null) return sEnabledForTesting;

        return isEnabledInPrefs();
    }

    // Returns whether updating the WebAPK is enabled.
    public static boolean areUpdatesEnabled() {
        if (!isEnabled()) return false;

        // Updating a WebAPK without going through Google Play requires "installation from unknown
        // sources" to be enabled. It is confusing for a user to see a dialog asking them to enable
        // "installation from unknown sources" when they are in the middle of using the WebAPK (as
        // opposed to after requesting to add a WebAPK to the homescreen).
        return installingFromUnknownSourcesAllowed() || canUseGooglePlayToInstallWebApk();
    }

    /** Computes the GooglePlayInstallState. */
    private static int computeGooglePlayInstallState() {
        if (!isGooglePlayInstallEnabledByChromeFeature()) {
            return GooglePlayInstallState.DISABLED_BY_VARIATIONS;
        }

        if (!ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                    ContextUtils.getApplicationContext(),
                    new UserRecoverableErrorHandler.Silent())) {
            return GooglePlayInstallState.NO_PLAY_SERVICES;
        }

        GooglePlayWebApkInstallDelegate delegate =
                AppHooks.get().getGooglePlayWebApkInstallDelegate();
        if (delegate == null) {
            return GooglePlayInstallState.DISABLED_OTHER;
        }

        return GooglePlayInstallState.SUPPORTED;
    }

    /**
     * Returns whether installing WebAPKs from Google Play is possible.
     * If {@link sCanUseGooglePlayInstall} hasn't been set yet, it returns false immediately and
     * calls the Google Play Install API to update {@link sCanUseGooglePlayInstall} asynchronously.
     */
    public static boolean canUseGooglePlayToInstallWebApk() {
        return getGooglePlayInstallState() == GooglePlayInstallState.SUPPORTED;
    }

    /**
     * Returns whether Google Play install is enabled by Chrome. Does not check whether installing
     * from Google Play is possible.
     */
    public static boolean isGooglePlayInstallEnabledByChromeFeature() {
        return isEnabled() && LibraryLoader.isInitialized()
                && nativeCanUseGooglePlayToInstallWebApk();
    }

    /**
     * Returns whether installing WebAPKs is possible either from "unknown resources" or Google
     * Play.
     */
    @CalledByNative
    private static boolean canInstallWebApk() {
        return isEnabled()
                && (canUseGooglePlayToInstallWebApk() || nativeCanInstallFromUnknownSources());
    }

    @CalledByNative
    private static int getGooglePlayInstallState() {
        if (sGooglePlayInstallState == null) {
            sGooglePlayInstallState = computeGooglePlayInstallState();
        }
        return sGooglePlayInstallState;
    }

    /* Returns whether launching renderer in WebAPK process is enabled by Chrome. */
    public static boolean canLaunchRendererInWebApkProcess() {
        return isEnabled() && LibraryLoader.isInitialized()
                && nativeCanLaunchRendererInWebApkProcess();
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have to use the cached
     * value because native library may not yet been loaded.
     * @return Whether the feature is enabled.
     */
    private static boolean isEnabledInPrefs() {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance().getCachedWebApkRuntimeEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Once native is loaded we can consult the command-line (set via about:flags) and also finch
     * state to see if we should enable WebAPKs.
     */
    public static void cacheEnabledStateForNextLaunch() {
        ChromePreferenceManager preferenceManager = ChromePreferenceManager.getInstance();

        boolean wasEnabled = isEnabledInPrefs();
        boolean isEnabled = ChromeFeatureList.isEnabled(ChromeFeatureList.IMPROVED_A2HS);
        if (isEnabled != wasEnabled) {
            Log.d(TAG, "WebApk setting changed (%s => %s)", wasEnabled, isEnabled);
            preferenceManager.setCachedWebApkRuntimeEnabled(isEnabled);
        }
    }

    /**
     * Returns whether the user has enabled installing apps from sources other than the Google Play
     * Store.
     */
    private static boolean installingFromUnknownSourcesAllowed() {
        Context applicationContext = ContextUtils.getApplicationContext();
        try {
            return Settings.Secure.getInt(applicationContext.getContentResolver(),
                           Settings.Secure.INSTALL_NON_MARKET_APPS)
                    == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private static native boolean nativeCanUseGooglePlayToInstallWebApk();
    private static native boolean nativeCanLaunchRendererInWebApkProcess();
    private static native boolean nativeCanInstallFromUnknownSources();
}
