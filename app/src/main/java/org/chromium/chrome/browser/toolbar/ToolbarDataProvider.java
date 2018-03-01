// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.security_state.ConnectionSecurityLevel;

/**
 * Defines the data that is exposed to properly render the Toolbar.
 */
public interface ToolbarDataProvider {
    /**
     * @return The tab that contains the information currently displayed in the toolbar.
     */
    @Nullable
    Tab getTab();

    /**
     * @return Whether ToolbarDataProvider currently has a tab related to it.
     */
    boolean hasTab();

    /**
     * @return The current url for the current tab. Returns empty string when there is no tab.
     */
    @NonNull
    String getCurrentUrl();

    /**
     * @return The NewTabPage shown for the current Tab or null if one is not being shown.
     */
    NewTabPage getNewTabPageForCurrentTab();

    /**
     * @return Whether the toolbar is currently being displayed for incognito.
     */
    boolean isIncognito();

    /**
     * @return The current {@link Profile}.
     */
    Profile getProfile();

    /**
     * @return The formatted text (URL or search terms) for display.
     */
    String getText();

    /**
     * @return The primary color to use for the background drawable.
     */
    int getPrimaryColor();

    /**
     * @return Whether the current primary color is a brand color.
     */
    boolean isUsingBrandColor();

    /**
     * @return Whether the page currently shown is an offline page.
     */
    boolean isOfflinePage();

    /**
     * @param urlBarText The text currently displayed in the url bar.
     * @return Whether the Google 'G' should be shown in the location bar.
     */
    boolean shouldShowGoogleG(String urlBarText);

    /**
     * @return Whether the security icon should be displayed.
     */
    boolean shouldShowSecurityIcon();

    /**
     * @return Whether verbose status next to the security icon should be displayed.
     */
    boolean shouldShowVerboseStatus();

    /**
     * @return The current {@link ConnectionSecurityLevel}.
     */
    @ConnectionSecurityLevel
    int getSecurityLevel();

    /**
     * Determines the icon that should be displayed for the current security level.
     * @return The resource ID of the icon that should be displayed, 0 if no icon should show.
     */
    @DrawableRes
    int getSecurityIconResource();
}
