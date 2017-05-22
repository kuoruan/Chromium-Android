// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;

import java.util.Set;

/**
 * Java accessor for base/feature_list.h state.
 */
@JNINamespace("chrome::android")
@MainDex
public abstract class ChromeFeatureList {
    /** Map that stores substitution feature flags for tests. */
    private static Set<String> sTestEnabledFeatures;

    // Prevent instantiation.
    private ChromeFeatureList() {}

    /**
     * Sets the feature flags to use in JUnit tests, since native calls are not available there.
     * Do not use directly, prefer using the {@link EnableFeatures} annotation.
     *
     * @see EnableFeatures
     * @see EnableFeatures.Processor
     */
    @VisibleForTesting
    public static void setTestEnabledFeatures(Set<String> featureList) {
        sTestEnabledFeatures = featureList;
    }

    /**
     * Returns whether the specified feature is enabled or not.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to query.
     * @return Whether the feature is enabled or not.
     */
    public static boolean isEnabled(String featureName) {
        if (sTestEnabledFeatures == null) return nativeIsEnabled(featureName);
        return sTestEnabledFeatures.contains(featureName);
    }

    /**
     * Returns a field trial param for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @return The parameter value as a String. The string is empty if the feature does not exist or
     *   the specified parameter does not exist.
     */
    public static String getFieldTrialParamByFeature(String featureName, String paramName) {
        return nativeGetFieldTrialParamByFeature(featureName, paramName);
    }

    /**
     * Returns a field trial param as an int for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The integer value to use if the param is not available.
     * @return The parameter value as an int. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value does not represent an int.
     */
    public static int getFieldTrialParamByFeatureAsInt(
            String featureName, String paramName, int defaultValue) {
        return nativeGetFieldTrialParamByFeatureAsInt(featureName, paramName, defaultValue);
    }

    /**
     * Returns a field trial param as a double for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The double value to use if the param is not available.
     * @return The parameter value as a double. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value does not represent a double.
     */
    public static double getFieldTrialParamByFeatureAsDouble(
            String featureName, String paramName, double defaultValue) {
        return nativeGetFieldTrialParamByFeatureAsDouble(featureName, paramName, defaultValue);
    }

    /**
     * Returns a field trial param as a boolean for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The boolean value to use if the param is not available.
     * @return The parameter value as a boolean. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value is neither "true" nor "false".
     */
    public static boolean getFieldTrialParamByFeatureAsBoolean(
            String featureName, String paramName, boolean defaultValue) {
        return nativeGetFieldTrialParamByFeatureAsBoolean(featureName, paramName, defaultValue);
    }

    // Alphabetical:
    public static final String ANDROID_PAY_INTEGRATION_V1 = "AndroidPayIntegrationV1";
    public static final String ANDROID_PAY_INTEGRATION_V2 = "AndroidPayIntegrationV2";
    public static final String ANDROID_PAYMENT_APPS = "AndroidPaymentApps";
    public static final String AUTOFILL_SCAN_CARDHOLDER_NAME = "AutofillScanCardholderName";
    public static final String CCT_EXTERNAL_LINK_HANDLING = "CCTExternalLinkHandling";
    public static final String CCT_POST_MESSAGE_API = "CCTPostMessageAPI";
    public static final String CHROME_HOME = "ChromeHome";
    public static final String CONSISTENT_OMNIBOX_GEOLOCATION = "ConsistentOmniboxGeolocation";
    public static final String CONTEXTUAL_SEARCH_SINGLE_ACTIONS = "ContextualSearchSingleActions";
    public static final String CONTEXTUAL_SEARCH_URL_ACTIONS = "ContextualSearchUrlActions";
    public static final String CUSTOM_FEEDBACK_UI = "CustomFeedbackUi";
    /** Whether we show an important sites dialog in the "Clear Browsing Data" flow. */
    public static final String IMPORTANT_SITES_IN_CBD = "ImportantSitesInCBD";
    public static final String TABS_IN_CBD = "TabsInCBD";
    public static final String IMPROVED_A2HS = "ImprovedA2HS";
    public static final String NO_CREDIT_CARD_ABORT = "NoCreditCardAbort";
    public static final String NTP_CONDENSED_LAYOUT = "NTPCondensedLayout";
    public static final String NTP_CONDENSED_TILE_LAYOUT = "NTPCondensedTileLayout";
    public static final String NTP_FAKE_OMNIBOX_TEXT = "NTPFakeOmniboxText";
    public static final String NTP_FOREIGN_SESSIONS_SUGGESTIONS = "NTPForeignSessionsSuggestions";
    public static final String NTP_LAUNCH_AFTER_INACTIVITY = "NTPLaunchAfterInactivity";
    public static final String NTP_OFFLINE_PAGES_FEATURE_NAME = "NTPOfflinePages";
    public static final String NTP_SHOW_GOOGLE_G_IN_OMNIBOX = "NTPShowGoogleGInOmnibox";
    public static final String NTP_SNIPPETS_INCREASED_VISIBILITY = "NTPSnippetsIncreasedVisibility";
    public static final String NTP_SNIPPETS_SAVE_TO_OFFLINE = "NTPSaveToOffline";
    public static final String NTP_SNIPPETS_OFFLINE_BADGE = "NTPOfflineBadge";
    public static final String SERVICE_WORKER_PAYMENT_APPS = "ServiceWorkerPaymentApps";
    public static final String TAB_REPARENTING = "TabReparenting";
    public static final String UPLOAD_CRASH_REPORTS_USING_JOB_SCHEDULER =
            "UploadCrashReportsUsingJobScheduler";
    public static final String VIDEO_PERSISTENCE = "VideoPersistence";
    public static final String VR_SHELL = "VrShell";
    public static final String WEB_PAYMENTS = "WebPayments";
    public static final String WEB_PAYMENTS_MODIFIERS = "WebPaymentsModifiers";
    public static final String WEB_PAYMENTS_SINGLE_APP_UI_SKIP = "WebPaymentsSingleAppUiSkip";
    public static final String WEBVR_CARDBOARD_SUPPORT = "WebVRCardboardSupport";

    private static native boolean nativeIsEnabled(String featureName);
    private static native String nativeGetFieldTrialParamByFeature(
            String featureName, String paramName);
    private static native int nativeGetFieldTrialParamByFeatureAsInt(
            String featureName, String paramName, int defaultValue);
    private static native double nativeGetFieldTrialParamByFeatureAsDouble(
            String featureName, String paramName, double defaultValue);
    private static native boolean nativeGetFieldTrialParamByFeatureAsBoolean(
            String featureName, String paramName, boolean defaultValue);
}
