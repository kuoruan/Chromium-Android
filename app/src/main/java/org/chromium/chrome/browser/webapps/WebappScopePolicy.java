// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.webapps;

import org.chromium.chrome.browser.util.UrlUtilities;

/**
 * Defines which URLs are inside a web app scope as well as what to do when user navigates to them.
 */
enum WebappScopePolicy {
    WEBAPP {
        @Override
        public boolean isUrlInScope(WebappInfo info, String url) {
            return UrlUtilities.sameDomainOrHost(info.uri().toString(), url, true);
        }

        @Override
        public boolean openOffScopeNavsInCct() {
            // This is motivated by redirect based OAuth. Legacy web apps cannot capture in-scope
            // URLs to WebappActivity. Redirect based OAuth therefore would move the user to CCT
            // and keeps them there even after redirecting back to in-scope URL.
            // See crbug.com/771418
            return false;
        }
    },
    WEBAPK {
        @Override
        public boolean isUrlInScope(WebappInfo info, String url) {
            return UrlUtilities.isUrlWithinScope(url, info.scopeUri().toString());
        }

        @Override
        public boolean openOffScopeNavsInCct() {
            return true;
        }
    };

    /**
     * @return {@code true} if given {@code url} is in scope of a web app as defined by its
     *         {@code WebappInfo}, {@code false} otherwise.
     */
    abstract boolean isUrlInScope(WebappInfo info, String url);

    /**
     * @return {@code true} if off-scope URLs should be handled by the Chrome Custom Tab,
     *         {@code false} otherwise.
     */
    abstract boolean openOffScopeNavsInCct();
}
