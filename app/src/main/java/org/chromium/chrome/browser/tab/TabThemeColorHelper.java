// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.res.Resources;
import android.graphics.Color;
import android.support.annotation.Nullable;

import org.chromium.base.UserData;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.components.security_state.ConnectionSecurityLevel;

/**
 * Manages theme color used for {@link Tab}. Destroyed together with the tab.
 */
public class TabThemeColorHelper extends EmptyTabObserver implements UserData {
    private static final Class<TabThemeColorHelper> USER_DATA_KEY = TabThemeColorHelper.class;
    private final Tab mTab;

    private int mDefaultColor;
    private int mColor;

    public static void createForTab(Tab tab) {
        assert get(tab) == null;
        tab.getUserDataHost().setUserData(USER_DATA_KEY, new TabThemeColorHelper(tab));
    }

    @Nullable
    public static TabThemeColorHelper get(Tab tab) {
        return tab.getUserDataHost().getUserData(USER_DATA_KEY);
    }

    /** Convenience method that returns theme color of {@link Tab}. */
    public static int getColor(Tab tab) {
        return get(tab).getColor();
    }

    /** Convenience method that returns default theme color of {@link Tab}. */
    public static int getDefaultColor(Tab tab) {
        return get(tab).getDefaultColor();
    }

    private TabThemeColorHelper(Tab tab) {
        mTab = tab;
        mDefaultColor = calculateDefaultColor();
        mColor = calculateThemeColor(false);
        tab.addObserver(this);
    }

    private void updateDefaultColor() {
        mDefaultColor = calculateDefaultColor();
        updateIfNeeded(false);
    }

    private int calculateDefaultColor() {
        Resources resources = mTab.getThemedApplicationContext().getResources();
        return ColorUtils.getDefaultThemeColor(resources, mTab.isIncognito());
    }

    void updateFromTabState(TabState state) {
        mColor = state.hasThemeColor() ? state.getThemeColor() : getDefaultColor();
        updateIfNeeded(false);
    }

    /**
     * Calculate the theme color based on if the page is native, the theme color changed, etc.
     * @param didWebContentsThemeColorChange If the theme color of the web contents is known to have
     *                                       changed.
     * @return The theme color that should be used for this tab.
     */
    private int calculateThemeColor(boolean didWebContentsThemeColorChange) {
        // Start by assuming the current theme color is that one that should be used. This will
        // either be transparent, the last theme color, or the color restored from TabState.
        int themeColor =
                ColorUtils.isValidThemeColor(mColor) || mColor == 0 ? mColor : getDefaultColor();

        // Only use the web contents for the theme color if it is known to have changed, This
        // corresponds to the didChangeThemeColor in WebContentsObserver.
        if (mTab.getWebContents() != null && didWebContentsThemeColorChange) {
            themeColor = mTab.getWebContents().getThemeColor();
            if (themeColor != 0 && !ColorUtils.isValidThemeColor(themeColor)) themeColor = 0;
        }

        // Do not apply the theme color if there are any security issues on the page.
        int securityLevel = mTab.getSecurityLevel();
        if (securityLevel == ConnectionSecurityLevel.DANGEROUS
                || securityLevel == ConnectionSecurityLevel.SECURE_WITH_POLICY_INSTALLED_CERT) {
            themeColor = getDefaultColor();
        }

        if (mTab.getActivity() != null && mTab.getActivity().isTablet()) {
            themeColor = getDefaultColor();
        }

        if (mTab.isNativePage()) themeColor = getDefaultColor();
        if (mTab.isShowingInterstitialPage()) themeColor = getDefaultColor();

        if (themeColor == Color.TRANSPARENT) themeColor = getDefaultColor();
        if (mTab.isIncognito()) themeColor = getDefaultColor();
        if (mTab.isPreview()) themeColor = getDefaultColor();

        // Ensure there is no alpha component to the theme color as that is not supported in the
        // dependent UI.
        themeColor |= 0xFF000000;
        return themeColor;
    }

    /**
     * Determines if the theme color has changed and notifies the listeners if it has.
     * @param didWebContentsThemeColorChange If the theme color of the web contents is known to have
     *                                       changed.
     */
    public void updateIfNeeded(boolean didWebContentsThemeColorChange) {
        int themeColor = calculateThemeColor(didWebContentsThemeColorChange);
        if (themeColor == mColor) return;
        mColor = themeColor;
        mTab.notifyThemeColorChanged(themeColor);
    }

    /**
     * @return Whether the theme color for this tab is the default color.
     */
    public boolean isDefaultColor() {
        return mTab.isNativePage() || mDefaultColor == getColor();
    }

    /**
     * @return The default theme color for this tab.
     */
    @VisibleForTesting
    public int getDefaultColor() {
        return mDefaultColor;
    }

    /**
     * @return The current theme color based on the value passed from the web contents and the
     *         security state.
     */
    public int getColor() {
        return mColor;
    }

    // TabObserver

    @Override
    public void onSSLStateUpdated(Tab tab) {
        updateIfNeeded(false);
    }

    @Override
    public void onUrlUpdated(Tab tab) {
        updateIfNeeded(false);
    }

    @Override
    public void onDidFailLoad(
            Tab tab, boolean isMainFrame, int errorCode, String description, String failingUrl) {
        updateIfNeeded(true);
    }

    @Override
    public void onDidFinishNavigation(Tab tab, String url, boolean isInMainFrame,
            boolean isErrorPage, boolean hasCommitted, boolean isSameDocument,
            boolean isFragmentNavigation, @Nullable Integer pageTransition, int errorCode,
            int httpStatusCode) {
        if (errorCode != 0) updateIfNeeded(true);
    }

    @Override
    public void onDidAttachInterstitialPage(Tab tab) {
        updateIfNeeded(false);
    }

    @Override
    public void onDidDetachInterstitialPage(Tab tab) {
        updateIfNeeded(false);
    }

    @Override
    public void onActivityAttachmentChanged(Tab tab, boolean isAttached) {
        updateDefaultColor();
    }

    @Override
    public void onDestroyed(Tab tab) {
        tab.removeObserver(this);
    }
}
