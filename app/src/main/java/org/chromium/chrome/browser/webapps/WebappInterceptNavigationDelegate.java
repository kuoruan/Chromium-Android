// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.net.Uri;
import android.os.SystemClock;
import android.provider.Browser;
import android.support.customtabs.CustomTabsIntent;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.externalnav.ExternalNavigationParams;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.navigation_interception.NavigationParams;

import java.util.concurrent.TimeUnit;

/**
 * Intercepts navigations made by the Web App and sends off-origin http(s) ones to a Custom Tab.
 */
public class WebappInterceptNavigationDelegate extends InterceptNavigationDelegateImpl {
    /**
     * Log the time spent on CCT opened by WebappActivity.
     */
    public static class CustomTabTimeSpentLogger {
        private long mStartTime;
        private @WebappActivity.ActivityType int mActivityType;

        private CustomTabTimeSpentLogger(@WebappActivity.ActivityType int activityType) {
            mActivityType = activityType;
            mStartTime = SystemClock.elapsedRealtime();
        }

        /**
         * Create {@link CustomTabTimeSpentLogger} instance and start timer if activity type is one
         * of WebAPP, WebApk and Trusted Web Activity,
         * @param type of the activity that opens the CCT.
         * @return {@link CustomTabTimeSpentLogger} instance if activity is WebAPP/WebApk/TWA,
         * otherwise null.
         */
        public static CustomTabTimeSpentLogger createInstanceAndStartTimer(
                @WebappActivity.ActivityType int activityType) {
            return new CustomTabTimeSpentLogger(activityType);
        }

        /**
         * Stop timer and log uma.
         */
        public void onPause() {
            long timeSpent = SystemClock.elapsedRealtime() - mStartTime;
            String umaSuffix;
            switch (mActivityType) {
                case WebappActivity.ACTIVITY_TYPE_WEBAPP:
                    umaSuffix = ".Webapp";
                    break;
                case WebappActivity.ACTIVITY_TYPE_WEBAPK:
                    umaSuffix = ".WebApk";
                    break;
                case WebappActivity.ACTIVITY_TYPE_TWA:
                    umaSuffix = ".TWA";
                    break;
                default:
                    umaSuffix = ".Other";
                    break;
            }
            RecordHistogram.recordLongTimesHistogram(
                    "CustomTab.SessionDuration" + umaSuffix, timeSpent, TimeUnit.MILLISECONDS);
        }
    }

    private final WebappActivity mActivity;

    public WebappInterceptNavigationDelegate(WebappActivity activity, Tab tab) {
        super(tab);
        this.mActivity = activity;
    }

    @Override
    public boolean shouldIgnoreNavigation(NavigationParams navigationParams) {
        if (super.shouldIgnoreNavigation(navigationParams)) {
            return true;
        }

        if (shouldOpenInCustomTab(
                    navigationParams, mActivity.getWebappInfo(), mActivity.scopePolicy())) {
            CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
            intentBuilder.setShowTitle(true);
            if (mActivity.mWebappInfo.hasValidThemeColor()) {
                // Need to cast as themeColor is a long to contain possible error results.
                intentBuilder.setToolbarColor((int) mActivity.mWebappInfo.themeColor());
            }
            CustomTabsIntent customTabIntent = intentBuilder.build();
            customTabIntent.intent.setPackage(mActivity.getPackageName());
            customTabIntent.intent.putExtra(
                    CustomTabIntentDataProvider.EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER, true);
            customTabIntent.intent.putExtra(CustomTabIntentDataProvider.EXTRA_BROWSER_LAUNCH_SOURCE,
                    mActivity.getActivityType());
            customTabIntent.intent.putExtra(
                    Browser.EXTRA_APPLICATION_ID, mActivity.mWebappInfo.apkPackageName());

            if (shouldCloseContentsOnOverrideUrlLoadingAndLaunchIntent()) {
                customTabIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            customTabIntent.launchUrl(mActivity, Uri.parse(navigationParams.url));

            onOverrideUrlLoadingAndLaunchIntent();
            return true;
        }

        return false;
    }

    @Override
    public ExternalNavigationParams.Builder buildExternalNavigationParams(
            NavigationParams navigationParams, TabRedirectHandler tabRedirectHandler,
            boolean shouldCloseTab) {
        ExternalNavigationParams.Builder builder = super.buildExternalNavigationParams(
                navigationParams, tabRedirectHandler, shouldCloseTab);
        builder.setNativeClientPackageName(mActivity.getNativeClientPackageName());
        return builder;
    }

    static boolean shouldOpenInCustomTab(
            NavigationParams navigationParams, WebappInfo info, WebappScopePolicy scopePolicy) {
        return UrlUtilities.isValidForIntentFallbackNavigation(navigationParams.url)
                && !navigationParams.isPost && !scopePolicy.isUrlInScope(info, navigationParams.url)
                && scopePolicy.openOffScopeNavsInCct();
    }
}
