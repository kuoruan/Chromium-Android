// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.support.annotation.Nullable;

import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationHistory;

import javax.inject.Inject;

/**
 * Allows navigation to the most recent page that matches a criteria when the Custom Tabs close
 * button is pressed. We call this page the landing page.
 *
 * For example, in Trusted Web Activities we only show the close button when the user has left the
 * verified origin. If the user then pressed the close button, we want to navigate back to the
 * verified origin instead of closing the Activity.
 *
 * Thread safety: Should only be called on UI thread.
 * Native: Requires native.
 */
@ActivityScope
public class CloseButtonNavigator {
    @Nullable private PageCriteria mLandingPageCriteria;

    @Inject
    public CloseButtonNavigator() {}

    // TODO(peconn): Replace with Predicate<T> when we can use Java 8 libraries.
    /** An interface that allows specifying if a URL matches some criteria. */
    public interface PageCriteria {
        /** Whether the given |url| matches the criteria. */
        boolean matches(String url);
    }

    /** Sets the criteria for the page to go back to. */
    public void setLandingPageCriteria(PageCriteria criteria) {
        assert mLandingPageCriteria == null : "Conflicting criteria for close button navigation.";

        mLandingPageCriteria = criteria;
    }

    /**
     * Navigates to the most recent landing page. Returns {@code false} if no criteria for what is
     * a landing page has been given or no such page can be found.
     */
    public boolean navigateOnClose(@Nullable NavigationController controller) {
        if (mLandingPageCriteria == null || controller == null) return false;

        NavigationHistory history = controller.getNavigationHistory();
        for (int i = history.getCurrentEntryIndex() - 1; i >= 0; i--) {
            String url = history.getEntryAtIndex(i).getUrl();
            if (!mLandingPageCriteria.matches(url)) continue;

            controller.goToNavigationIndex(i);
            return true;
        }

        return false;
    }
}
