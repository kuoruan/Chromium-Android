// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.content.res.Resources;

import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

/**
 * Provides Field Trial support for Omnibox Placeholder Experiment.
 */
public class OmniboxPlaceholderFieldTrial {
    private static final String FIELD_TRIAL_NAME = "OmniboxPlaceholderExperiment";
    private static final String GROUP_SEARCH_OR_TYPE_URL = "SearchOrTypeUrl";
    private static final String GROUP_SEARCH_OR_TYPE_WEB_ADDRESS = "SearchOrTypeWebAddress";
    private static final String GROUP_TYPE_WHAT_YOU_ARE_LOOKING_FOR = "TypeWhatYouAreLookingFor";
    private static final String GROUP_FIND_NEWS_RECIPES_WEATHER = "FindNewsRecipesWeather";
    private static final String GROUP_BLANK = "Blank";

    private static String sCachedHint;

    /** Prevent initialization of this class. */
    private OmniboxPlaceholderFieldTrial() {}

    /**
     * Caches omnibox placeholder experiment group to shared preferences.
     */
    public static void cacheOmniboxPlaceholderGroup() {
        String groupName = FieldTrialList.findFullName(FIELD_TRIAL_NAME);
        ChromePreferenceManager.getInstance().setOmniboxPlaceholderGroup(groupName);
    }

    /**
     * Initialize ominibox placeholder text to static variable sCachedHint
     */
    private static void initOmniboxPlaceholder() {
        // setUrlBarHint is only called once when hint is not cached to static variable sCachedHint.
        // This is to keep consistency on showing same hint to user in one session.
        String groupName = ChromePreferenceManager.getInstance().getOmniboxPlaceholderGroup();
        Resources resources = ContextUtils.getApplicationContext().getResources();
        switch (groupName) {
            case GROUP_SEARCH_OR_TYPE_URL:
                sCachedHint = resources.getString(R.string.search_or_type_url);
                break;
            case GROUP_SEARCH_OR_TYPE_WEB_ADDRESS:
                sCachedHint = resources.getString(R.string.search_or_type_web_address);
                break;
            case GROUP_TYPE_WHAT_YOU_ARE_LOOKING_FOR:
                sCachedHint = resources.getString(R.string.type_what_you_are_looking_for);
                break;
            case GROUP_FIND_NEWS_RECIPES_WEATHER:
                sCachedHint = resources.getString(R.string.find_news_recipes_weather);
                break;
            case GROUP_BLANK:
                sCachedHint = "";
                break;
            default:
                sCachedHint = resources.getString(R.string.search_or_type_url);
        }
    }

    /**
     * @return String of hint text to show in omnibox url bar.
     */
    public static String getOmniboxPlaceholder() {
        if (sCachedHint == null) initOmniboxPlaceholder();
        return sCachedHint;
    }
}
