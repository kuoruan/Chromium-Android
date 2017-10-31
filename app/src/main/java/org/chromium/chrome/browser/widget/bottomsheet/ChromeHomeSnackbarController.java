// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * A {@link SnackbarController} that displays a snackbar prompting users to provide feedback after
 * opting out of Chrome Home.
 */
public class ChromeHomeSnackbarController implements SnackbarController {
    private static final String FIELD_TRIAL_NAME = "ChromeHome";
    private static final String SURVEY_URL_PARAM = "survey_url";
    private static final String DEFAULT_SURVEY_URL =
            "https://support.google.com/chrome/?p=home_optout";
    private static final Integer SNACKBAR_DURATION_MS = 5000;

    private TabCreator mTabCreator;

    /**
     * @param tab The {@link Tab} that was reparented after opting out of Chrome Home. The Tab is
     *            used to retrieve the new {@link ChromeActivity}.
     */
    public static void initialize(Tab tab) {
        ChromeActivity activity = tab.getActivity();
        new ChromeHomeSnackbarController(
                activity.getSnackbarManager(), activity.getTabCreator(false));
    }

    private ChromeHomeSnackbarController() {}

    private ChromeHomeSnackbarController(SnackbarManager manager, TabCreator defaultTabCreator) {
        mTabCreator = defaultTabCreator;

        ChromePreferenceManager.getInstance().setChromeHomeOptOutSnackbarShown();

        Snackbar snackbar = Snackbar.make(ContextUtils.getApplicationContext().getString(
                                                  R.string.chrome_home_opt_out_snackbar_text),
                this, Snackbar.TYPE_ACTION, Snackbar.UMA_CHROME_HOME_OPT_OUT_SURVEY);
        snackbar.setAction(ContextUtils.getApplicationContext().getString(
                                   R.string.chrome_home_opt_out_snackbar_action),
                null);
        snackbar.setSingleLine(false);
        snackbar.setDuration(SNACKBAR_DURATION_MS);
        manager.showSnackbar(snackbar);
    }

    @Override
    public void onAction(Object actionData) {
        String surveyUrl =
                VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, SURVEY_URL_PARAM);
        if (TextUtils.isEmpty(surveyUrl)) surveyUrl = DEFAULT_SURVEY_URL;

        mTabCreator.createNewTab(new LoadUrlParams(surveyUrl), TabLaunchType.FROM_CHROME_UI, null);
        RecordUserAction.record("Android.ChromeHome.OptOutSnackbarClicked");
    }

    @Override
    public void onDismissNoAction(Object actionData) {}
}
