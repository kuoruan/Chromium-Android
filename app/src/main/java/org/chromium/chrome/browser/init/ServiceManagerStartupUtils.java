// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeFeatureList;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class for features to query the SharedPreferences to learn whether they can start
 * ServiceManager instead of full browser on startup. Because Java code can only access the feature
 * list after native library is loaded, this class stores the feature values in the
 * SharedPreferences so that Java classes can access them on next startup.
 */
public class ServiceManagerStartupUtils {
    public static final String TASK_TAG = "Servicification Startup Task";

    // Master flag that gates all features that depend on starting the service manager early.
    private static final String EARLY_START_FLAG =
            ChromeFeatureList.ALLOW_STARTING_SERVICE_MANAGER_ONLY;

    // List of features that supports starting ServiceManager on startup.
    private static final String[] SERVICE_MANAGER_FEATURES = {EARLY_START_FLAG,
            ChromeFeatureList.NETWORK_SERVICE, ChromeFeatureList.SERVICE_MANAGER_FOR_DOWNLOAD};
    // Key in the SharedPreferences for storing all features that will start ServiceManager.
    private static final String SERVICE_MANAGER_FEATURES_KEY = "ServiceManagerFeatures";

    /**
     *  Check whether ServiceManager can be started for a set of |featureNames|.
     *  @param |featureNames| Feature names to query.
     *  @return Whether the features can start service manager.
     */
    public static boolean canStartServiceManager(Set<String> featureNames) {
        Set<String> features = ContextUtils.getAppSharedPreferences().getStringSet(
                SERVICE_MANAGER_FEATURES_KEY, null);
        if (features == null) return false;
        for (String featureName : featureNames) {
            if (!features.contains(featureName)) {
                return false;
            }
        }
        return features.contains(EARLY_START_FLAG);
    }

    /**
     *  Register all the service manager startup features with SharedPreferences.
     */
    public static void registerEnabledFeatures() {
        SharedPreferences.Editor editor = ContextUtils.getAppSharedPreferences().edit();
        Set<String> features = new HashSet<String>();
        for (String feature : SERVICE_MANAGER_FEATURES) {
            if (ChromeFeatureList.isEnabled(feature)) {
                features.add(feature);
            }
        }
        if (features.isEmpty()) {
            editor.remove(SERVICE_MANAGER_FEATURES_KEY);
        } else {
            editor.putStringSet(SERVICE_MANAGER_FEATURES_KEY, features);
        }
        editor.apply();
    }
}
