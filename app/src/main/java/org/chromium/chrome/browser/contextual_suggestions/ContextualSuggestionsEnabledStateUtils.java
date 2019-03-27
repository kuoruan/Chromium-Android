// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.components.signin.ChromeSigninController;

/** Utility functions related to enabled state of Contextual Suggestions. */
public class ContextualSuggestionsEnabledStateUtils {

    private ContextualSuggestionsEnabledStateUtils() {}

    /** @return Whether the user settings for contextual suggestions should be shown. */
    public static boolean shouldShowSettings() {
        return isDSEConditionMet()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_OPT_OUT);
    }

    /** @return Whether the settings state is currently enabled. */
    static boolean getSettingsEnabled() {
        return isDSEConditionMet() && !ContextualSuggestionsBridge.isDisabledByEnterprisePolicy()
                && ChromeSigninController.get().isSignedIn()
                && (ProfileSyncService.get().isUrlKeyedDataCollectionEnabled(false)
                           || ProfileSyncService.get().isUrlKeyedDataCollectionEnabled(true));
    }

    /** @return Whether the state is currently enabled. */
    public static boolean getEnabledState() {
        return getSettingsEnabled()
                && (PrefServiceBridge.getInstance().getBoolean(Pref.CONTEXTUAL_SUGGESTIONS_ENABLED)
                || !ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_OPT_OUT));
    }

    public static void recordEnabled(boolean enabled) {
        RecordHistogram.recordBooleanHistogram("ContextualSuggestions.EnabledState", enabled);
    }

    public static void recordPreferenceEnabled(boolean enabled) {
        RecordHistogram.recordBooleanHistogram("ContextualSuggestions.Preference.State", enabled);
    }

    private static boolean isDSEConditionMet() {
        boolean hasCompletedSearchEnginePromo =
                LocaleManager.getInstance().hasCompletedSearchEnginePromo();
        boolean isGoogleDSE = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
        return !hasCompletedSearchEnginePromo || isGoogleDSE;
    }
}
