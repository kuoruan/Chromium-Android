// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.bottom;

import android.content.Context;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider.IncognitoStateObserver;

/** A ThemeColorProvider for the bottom toolbar. */
public class BottomToolbarThemeColorProvider
        extends ThemeColorProvider implements IncognitoStateObserver {
    /** Primary color for light mode. */
    private final int mLightPrimaryColor;

    /** Primary color for dark mode. */
    private final int mDarkPrimaryColor;

    /** Used to know when incognito mode is entered or exited. */
    private IncognitoStateProvider mIncognitoStateProvider;

    /** The overview mode manager. */
    private OverviewModeBehavior mOverviewModeBehavior;

    /** Observer to know when overview mode is entered/exited. */
    private final OverviewModeObserver mOverviewModeObserver;

    /** Whether theme is dark mode. */
    private boolean mIsUsingDarkBackground;

    /** Whether app is in incognito mode. */
    private boolean mIsIncognito;

    /** Whether app is in overview mode. */
    private boolean mIsOverviewVisible;

    public BottomToolbarThemeColorProvider() {
        final Context context = ContextUtils.getApplicationContext();
        mLightPrimaryColor = ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.modern_primary_color);
        mDarkPrimaryColor = ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.incognito_modern_primary_color);

        mOverviewModeObserver = new EmptyOverviewModeObserver() {
            @Override
            public void onOverviewModeStartedShowing(boolean showToolbar) {
                mIsOverviewVisible = true;
                updateTheme();
            }

            @Override
            public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
                mIsOverviewVisible = false;
                updateTheme();
            }
        };
    }

    void setIncognitoStateProvider(IncognitoStateProvider provider) {
        mIncognitoStateProvider = provider;
        mIncognitoStateProvider.addIncognitoStateObserverAndTrigger(this);
    }

    @Override
    public void onIncognitoStateChanged(boolean isIncognito) {
        mIsIncognito = isIncognito;
        updateTheme();
    }

    void setOverviewModeBehavior(OverviewModeBehavior overviewModeBehavior) {
        mOverviewModeBehavior = overviewModeBehavior;
        mOverviewModeBehavior.addOverviewModeObserver(mOverviewModeObserver);
    }

    private void updateTheme() {
        final boolean isAccessibilityEnabled = DeviceClassManager.enableAccessibilityLayout();
        final boolean isHorizontalTabSwitcherEnabled =
                ChromeFeatureList.isEnabled(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID);
        final boolean shouldUseDarkBackground = mIsIncognito
                && (isAccessibilityEnabled || isHorizontalTabSwitcherEnabled
                        || !mIsOverviewVisible);

        updatePrimaryColor(shouldUseDarkBackground ? mDarkPrimaryColor : mLightPrimaryColor, false);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (mIncognitoStateProvider != null) {
            mIncognitoStateProvider.removeObserver(this);
            mIncognitoStateProvider = null;
        }
        if (mOverviewModeBehavior != null) {
            mOverviewModeBehavior.removeOverviewModeObserver(mOverviewModeObserver);
            mOverviewModeBehavior = null;
        }
    }
}
