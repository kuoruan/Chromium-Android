// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.survey;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.StrictModeContext;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.infobar.SurveyInfoBar;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;

/**
 * Class that controls if and when to show surveys related to the Chrome Home experiment.
 */
public class ChromeHomeSurveyController {
    private static final String SURVEY_INFO_BAR_DISPLAYED = "chrome_home_survey_info_bar_displayed";
    private static final String SURVEY_FORCE_ENABLE_SWITCH = "force-enable-survey";
    private static final String TRIAL_NAME = "ChromeHome";
    private static final String PARAM_NAME = "survey_override_site_id";
    private static final long ONE_WEEK_IN_MILLIS = 604800000L;

    private TabModelSelector mTabModelSelector;

    private ChromeHomeSurveyController() {
        // Empty constructor.
    }

    /**
     * Checks if the conditions to show the survey are met and starts the process if they are.
     * @param context The current Android {@link Context}.
     * @param tabModelSelector The tab model selector to access the tab on which the survey will be
     *                         shown.
     */
    public static void initialize(Context context, TabModelSelector tabModelSelector) {
        new ChromeHomeSurveyController().startDownload(context, tabModelSelector);
    }

    private void startDownload(Context context, TabModelSelector tabModelSelector) {
        if (!doesUserQualifyForSurvey()) return;

        mTabModelSelector = tabModelSelector;

        SurveyController surveyController = SurveyController.getInstance();
        CommandLine commandLine = CommandLine.getInstance();
        String siteId;
        if (commandLine.hasSwitch(PARAM_NAME)) {
            siteId = commandLine.getSwitchValue(PARAM_NAME);
        } else {
            siteId = VariationsAssociatedData.getVariationParamValue(TRIAL_NAME, PARAM_NAME);
        }

        if (TextUtils.isEmpty(siteId)) return;

        Runnable onSuccessRunnable = new Runnable() {
            @Override
            public void run() {
                onSurveyAvailable(siteId);
            }
        };
        surveyController.downloadSurvey(context, siteId, "", onSuccessRunnable);
    }

    private boolean doesUserQualifyForSurvey() {
        if (CommandLine.getInstance().hasSwitch(SURVEY_FORCE_ENABLE_SWITCH)) return true;
        if (AccessibilityUtil.isAccessibilityEnabled()) return false;
        if (hasInfoBarBeenDisplayed()) return false;
        if (!FeatureUtilities.isChromeHomeEnabled()) return true;

        try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
            SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
            long earliestLoggedDate = sharedPreferences.getLong(
                    ChromePreferenceManager.CHROME_HOME_SHARED_PREFERENCES_KEY, Long.MAX_VALUE);
            if (System.currentTimeMillis() - earliestLoggedDate > ONE_WEEK_IN_MILLIS) return true;
        }
        return false;
    }

    private void onSurveyAvailable(String siteId) {
        Tab tab = mTabModelSelector.getCurrentTab();
        if (isValidTabForSurvey(tab)) {
            showSurveyInfoBar(tab, siteId);
            return;
        }

        TabModel normalModel = mTabModelSelector.getModel(false);
        normalModel.addObserver(new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                if (isValidTabForSurvey(tab)) {
                    showSurveyInfoBar(tab, siteId);
                    normalModel.removeObserver(this);
                }
            }
        });
    }

    private void showSurveyInfoBar(Tab tab, String siteId) {
        WebContents webContents = tab.getWebContents();
        if (webContents.isLoading()) {
            webContents.addObserver(new WebContentsObserver() {
                @Override
                public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
                    if (!isMainFrame) return;
                    showSurveyInfoBar(webContents, siteId);
                    webContents.removeObserver(this);
                }
            });
        } else {
            showSurveyInfoBar(webContents, siteId);
        }
    }

    private void showSurveyInfoBar(WebContents webContents, String siteId) {
        SurveyInfoBar.showSurveyInfoBar(webContents, siteId, true, R.drawable.chrome_sync_logo);
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        sharedPreferences.edit().putBoolean(SURVEY_INFO_BAR_DISPLAYED, true).apply();
    }

    private boolean hasInfoBarBeenDisplayed() {
        try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
            SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
            return sharedPreferences.getBoolean(SURVEY_INFO_BAR_DISPLAYED, false);
        }
    }

    private boolean isValidTabForSurvey(Tab tab) {
        return tab != null && tab.getWebContents() != null && !tab.isIncognito();
    }
}
