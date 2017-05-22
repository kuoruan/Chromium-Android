// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.components.location.LocationUtils;

/**
 * This class provides the basic interface to the Physical Web feature.
 */
public class PhysicalWeb {
    public static final int OPTIN_NOTIFY_MAX_TRIES = 1;
    private static final String PREF_PHYSICAL_WEB_NOTIFY_COUNT = "physical_web_notify_count";
    private static final String FEATURE_NAME = "PhysicalWeb";
    private static final String PHYSICAL_WEB_SHARING_FEATURE_NAME = "PhysicalWebSharing";
    private static final int MIN_ANDROID_VERSION = 18;

    /**
     * Evaluates whether the environment is one in which the Physical Web should
     * be enabled.
     * @return true if the PhysicalWeb should be enabled
     */
    public static boolean featureIsEnabled() {
        return ChromeFeatureList.isEnabled(FEATURE_NAME)
                && Build.VERSION.SDK_INT >= MIN_ANDROID_VERSION;
    }

    /**
     * Checks whether the Physical Web preference is switched to On.
     *
     * @return boolean {@code true} if the preference is On.
     */
    public static boolean isPhysicalWebPreferenceEnabled() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebEnabled();
    }

    /**
     * Checks whether the Physical Web Sharing feature is enabled.
     *
     * @return boolean {@code true} if the feature is enabled
     */
    public static boolean sharingIsEnabled() {
        return ChromeFeatureList.isEnabled(PHYSICAL_WEB_SHARING_FEATURE_NAME);
    }

    /**
     * Checks whether the Physical Web onboard flow is active and the user has
     * not yet elected to either enable or decline the feature.
     *
     * @return boolean {@code true} if onboarding is complete.
     */
    public static boolean isOnboarding() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebOnboarding();
    }

    /**
     * Starts the Physical Web feature.
     * At the moment, this only enables URL discovery over BLE.
     */
    public static void startPhysicalWeb() {
        // Only subscribe to Nearby if we have the location permission.
        LocationUtils locationUtils = LocationUtils.getInstance();
        if (locationUtils.hasAndroidLocationPermission()
                && locationUtils.isSystemLocationSettingEnabled()) {
            new NearbyBackgroundSubscription(NearbySubscription.SUBSCRIBE).run();
        }
    }

    /**
     * Stops the Physical Web feature.
     */
    public static void stopPhysicalWeb() {
        new NearbyBackgroundSubscription(NearbySubscription.UNSUBSCRIBE, new Runnable() {
            @Override
            public void run() {
                // This isn't absolutely necessary, but it's nice to clean up all our shared prefs.
                UrlManager.getInstance().clearAllUrls();
            }
        }).run();
    }

    /**
     * Increments a value tracking how many times we've shown the Physical Web
     * opt-in notification.
     */
    public static void recordOptInNotification() {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        int value = sharedPreferences.getInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, 0);
        sharedPreferences.edit().putInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, value + 1).apply();
    }

    /**
     * Gets the current count of how many times a high-priority opt-in notification
     * has been shown.
     * @return an integer representing the high-priority notifification display count.
     */
    public static int getOptInNotifyCount() {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        return sharedPreferences.getInt(PREF_PHYSICAL_WEB_NOTIFY_COUNT, 0);
    }

    /**
     * Performs various Physical Web operations that should happen on startup.
     */
    public static void onChromeStart() {
        if (!featureIsEnabled()) {
            stopPhysicalWeb();
            return;
        }

        // If this user is in the default state, we need to check if we should enable Physical Web.
        if (isOnboarding() && shouldAutoEnablePhysicalWeb()) {
            PrivacyPreferencesManager.getInstance().setPhysicalWebEnabled(true);
        }

        if (isPhysicalWebPreferenceEnabled()) {
            startPhysicalWeb();
            // The PhysicalWebUma call in this method should be called only when the native library
            // is loaded.  This is always the case on chrome startup.
            PhysicalWebUma.uploadDeferredMetrics();
        }
    }

    /**
     * Checks if this device should have Physical Web automatically enabled.
     */
    private static boolean shouldAutoEnablePhysicalWeb() {
        LocationUtils locationUtils = LocationUtils.getInstance();
        return locationUtils.isSystemLocationSettingEnabled()
                && locationUtils.hasAndroidLocationPermission()
                && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()
                && !Profile.getLastUsedProfile().isOffTheRecord();
    }

    /**
     * Starts the Activity that shows the list of Physical Web URLs.
     */
    public static void showUrlList() {
        IntentHandler.startChromeLauncherActivityForTrustedIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse(UrlConstants.PHYSICAL_WEB_URL))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
