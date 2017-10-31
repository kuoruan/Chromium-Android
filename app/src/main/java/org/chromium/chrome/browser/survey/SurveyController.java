// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.survey;

import android.app.Activity;
import android.content.Context;

import org.chromium.chrome.browser.AppHooks;

/**
 * Class that controls retrieving and displaying surveys. Clients should call #downloadSurvey() and
 * register a runnable to run when the survey is available. After downloading the survey, call
 * {@link showSurveyIfAvailable()} to display the survey.
 */
public class SurveyController {
    private static SurveyController sInstance;

    /**
     * @return The SurveyController to use during the lifetime of the browser process.
     */
    public static SurveyController getInstance() {
        if (sInstance == null) {
            sInstance = AppHooks.get().createSurveyController();
        }
        return sInstance;
    }

    /**
     * Asynchronously downloads the survey using the provided parameters.
     * @param context The context used to register a broadcast receiver.
     * @param siteId The id of the site from where the survey will be downloaded.
     * @param advertisingId Optional advertising id sent with the download request. Pass empty
     *                      string for no advertising id.
     * @param onSuccessRunnable The runnable to notify when the survey is ready.
     *                          If no survey is available, the runnable will not be run.
     */
    public void downloadSurvey(
            Context context, String siteId, String advertisingId, Runnable onSuccessRunnable) {}

    /**
     * Show the survey.
     * @param activity The client activity for the survey request.
     * @param siteId The id of the site from where the survey will be downloaded.
     * @param showAsBottomSheet Whether the survey should be presented as a bottom sheet or not.
     * @param displayLogoResId Optional resource id of the logo to be displayed on the survey.
     *                         Pass 0 for no logo.
     */
    public void showSurveyIfAvailable(
            Activity activity, String siteId, boolean showAsBottomSheet, int displayLogoResId) {}

    /**
     * Clears the survey cache containing responses and history.
     * @param context The context used to clear the cache.
     */
    public void clearCache(Context context) {}
}
