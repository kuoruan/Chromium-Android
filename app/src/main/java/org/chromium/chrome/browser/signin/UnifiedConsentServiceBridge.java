// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import org.chromium.chrome.browser.profiles.Profile;

/**
 * Bridge to UnifiedConsentService. Should only be used if
 * {@link org.chromium.chrome.browser.ChromeFeatureList.UNIFIED_CONSENT} feature is enabled.
 */
public class UnifiedConsentServiceBridge {
    private UnifiedConsentServiceBridge() {}

    /** Sets whether the user has given unified consent. */
    public static void setUnifiedConsentGiven(boolean unifiedConsentGiven) {
        nativeSetUnifiedConsentGiven(Profile.getLastUsedProfile(), unifiedConsentGiven);
    }

    /** Returns whether the user has given unified consent. */
    public static boolean isUnifiedConsentGiven() {
        return nativeIsUnifiedConsentGiven(Profile.getLastUsedProfile());
    }

    /** Enables Google services when the user is signing in. */
    public static void enableGoogleServices() {
        nativeEnableGoogleServices(Profile.getLastUsedProfile());
    }

    /** Returns whether collection of URL-keyed anonymized data is enabled. */
    public static boolean isUrlKeyedAnonymizedDataCollectionEnabled() {
        return nativeIsUrlKeyedAnonymizedDataCollectionEnabled(Profile.getLastUsedProfile());
    }

    /** Sets whether collection of URL-keyed anonymized data is enabled. */
    public static void setUrlKeyedAnonymizedDataCollectionEnabled(boolean enabled) {
        nativeSetUrlKeyedAnonymizedDataCollectionEnabled(Profile.getLastUsedProfile(), enabled);
    }

    /** Returns whether collection of URL-keyed anonymized data is configured by policy. */
    public static boolean isUrlKeyedAnonymizedDataCollectionManaged() {
        return nativeIsUrlKeyedAnonymizedDataCollectionManaged(Profile.getLastUsedProfile());
    }

    private static native void nativeSetUnifiedConsentGiven(Profile profile, boolean consentGiven);
    private static native boolean nativeIsUnifiedConsentGiven(Profile profile);
    private static native void nativeEnableGoogleServices(Profile profile);

    private static native boolean nativeIsUrlKeyedAnonymizedDataCollectionEnabled(Profile profile);
    private static native void nativeSetUrlKeyedAnonymizedDataCollectionEnabled(
            Profile profile, boolean enabled);
    private static native boolean nativeIsUrlKeyedAnonymizedDataCollectionManaged(Profile profile);
}
