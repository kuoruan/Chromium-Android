// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.util.Pair;

import org.chromium.base.CollectionUtil;
import org.chromium.base.StrictModeContext;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.FeatureUtilities;

import java.util.Map;

/** Grabs feedback about the current opt-in/out state of Chrome Home. */
class ChromeHomeFeedbackSource implements FeedbackSource {
    /** A user visible string describing Chrome Home's current enabled state. */
    @VisibleForTesting
    static final String CHROME_HOME_STATE_KEY = "Chrome Home State";
    private static final String CHROME_HOME_ENABLED_VALUE = "Enabled";
    private static final String CHROME_HOME_DISABLED_VALUE = "Disabled";
    private static final String CHROME_HOME_OPT_IN_VALUE = "Opt-In";
    private static final String CHROME_HOME_OPT_OUT_VALUE = "Opt-Out";

    private final boolean mIsOffTheRecord;

    ChromeHomeFeedbackSource(Profile profile) {
        mIsOffTheRecord = profile.isOffTheRecord();
    }

    @Override
    public Map<String, String> getFeedback() {
        if (mIsOffTheRecord) return null;

        boolean userPreferenceSet = false;
        // Allow disk access for preferences while Chrome Home is in experimentation.
        try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
            userPreferenceSet =
                    ChromePreferenceManager.getInstance().isChromeHomeUserPreferenceSet();
        }

        String value;
        if (FeatureUtilities.isChromeHomeEnabled()) {
            value = userPreferenceSet ? CHROME_HOME_OPT_IN_VALUE : CHROME_HOME_ENABLED_VALUE;
        } else {
            value = userPreferenceSet ? CHROME_HOME_OPT_OUT_VALUE : CHROME_HOME_DISABLED_VALUE;
        }

        return CollectionUtil.newHashMap(Pair.create(CHROME_HOME_STATE_KEY, value));
    }
}