// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feedback;

import android.util.Pair;

import org.chromium.base.CollectionUtil;
import org.chromium.base.VisibleForTesting;
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

    private final boolean mIsOffTheRecord;

    ChromeHomeFeedbackSource(Profile profile) {
        mIsOffTheRecord = profile.isOffTheRecord();
    }

    @Override
    public Map<String, String> getFeedback() {
        if (mIsOffTheRecord) return null;

        String value = FeatureUtilities.isChromeHomeEnabled() ? CHROME_HOME_ENABLED_VALUE
                                                              : CHROME_HOME_DISABLED_VALUE;

        return CollectionUtil.newHashMap(Pair.create(CHROME_HOME_STATE_KEY, value));
    }
}