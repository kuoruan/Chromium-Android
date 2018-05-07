// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.webapps;

import android.support.annotation.IntDef;

import org.chromium.chrome.browser.util.UrlUtilities;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines which URLs are inside a web app scope as well as what to do when user navigates to them.
 */
public enum WebappScopePolicy {
    LEGACY {
        @Override
        public boolean isUrlInScope(WebappInfo info, String url) {
            return UrlUtilities.sameDomainOrHost(info.uri().toString(), url, true);
        }

        @Override
        protected boolean openOffScopeNavsInCct() {
            // This is motivated by redirect based OAuth. Legacy web apps cannot capture in-scope
            // URLs to WebappActivity. Redirect based OAuth therefore would move the user to CCT
            // and keeps them there even after redirecting back to in-scope URL.
            // See crbug.com/771418
            return false;
        }
    },
    STRICT {
        @Override
        public boolean isUrlInScope(WebappInfo info, String url) {
            return UrlUtilities.isUrlWithinScope(url, info.scopeUri().toString());
        }

        @Override
        protected boolean openOffScopeNavsInCct() {
            return true;
        }
    };

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NavigationDirective.NORMAL_BEHAVIOR,
            NavigationDirective.IGNORE_EXTERNAL_INTENT_REQUESTS, NavigationDirective.LAUNCH_CCT})
    public @interface NavigationDirective {
        // No special handling.
        int NORMAL_BEHAVIOR = 0;
        // The navigation should stay in the webapp. External intent handlers should be ignored.
        int IGNORE_EXTERNAL_INTENT_REQUESTS = 1;
        // The navigation should launch a CCT.
        int LAUNCH_CCT = 2;
    }

    /**
     * @return {@code true} if given {@code url} is in scope of a web app as defined by its
     *         {@code WebappInfo}, {@code false} otherwise.
     */
    abstract boolean isUrlInScope(WebappInfo info, String url);

    /**
     * @return {@code true} if off-scope URLs should be handled by the Chrome Custom Tab,
     *         {@code false} otherwise.
     */
    protected abstract boolean openOffScopeNavsInCct();

    /** Applies the scope policy for navigation to {@link url}. */
    public @NavigationDirective int applyPolicyForNavigationToUrl(WebappInfo info, String url) {
        if (isUrlInScope(info, url)) return NavigationDirective.IGNORE_EXTERNAL_INTENT_REQUESTS;

        return openOffScopeNavsInCct() ? NavigationDirective.LAUNCH_CCT
                                       : NavigationDirective.NORMAL_BEHAVIOR;
    }
}
