// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;

import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.navigation_interception.NavigationParams;

/**
 * Intercepts navigations made by the Web App and sends off-origin http(s) ones to a Custom Tab.
 */
public class WebappInterceptNavigationDelegate extends InterceptNavigationDelegateImpl {
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

        if (UrlUtilities.isValidForIntentFallbackNavigation(navigationParams.url)
                && !navigationParams.isPost
                && !mActivity.scopePolicy().isUrlInScope(
                           mActivity.mWebappInfo, navigationParams.url)
                && mActivity.scopePolicy().openOffScopeNavsInCct()) {
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
            customTabIntent.launchUrl(mActivity, Uri.parse(navigationParams.url));
            return true;
        }

        return false;
    }
}
