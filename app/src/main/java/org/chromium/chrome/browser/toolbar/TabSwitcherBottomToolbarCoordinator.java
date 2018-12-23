// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.newtab.NewTabButton;

/**
 * The coordinator for the tab switcher mode bottom toolbar. This class handles all interactions
 * that the tab switcher bottom toolbar has with the outside world.
 */
public class TabSwitcherBottomToolbarCoordinator {
    /** The mediator that handles events from outside the tab switcher bottom toolbar. */
    private final TabSwitcherBottomToolbarMediator mMediator;

    /** The new tab button that lives in the bottom toolbar. */
    private final NewTabButton mNewTabButton;

    /** The incognito toggle tab layout that lives in bottom toolbar. */
    private final IncognitoToggleTabLayout mIncognitoToggleTabLayout;

    /** The menu button that lives in the tab switcher bottom toolbar. */
    private final MenuButton mMenuButton;

    /**
     * Build the coordinator that manages the tab switcher bottom toolbar.
     * @param stub The tab switcher bottom toolbar {@link ViewStub} to inflate.
     * @param incognitoStateProvider Notifies components when incognito mode is entered or exited.
     * @param newTabClickListener An {@link OnClickListener} that is triggered when the
     *                            new tab button is clicked.
     * @param menuButtonHelper An {@link AppMenuButtonHelper} that is triggered when the
     *                         menu button is clicked.
     * @param tabModelSelector A {@link TabModelSelector} that incognito toggle tab layout uses to
     *                         switch between normal and incognito tabs.
     * @param overviewModeBehavior The overview mode manager.
     * @param tabCountProvider Updates the tab count number in the tab switcher button and in the
     *                         incognito toggle tab layout.
     */
    public TabSwitcherBottomToolbarCoordinator(ViewStub stub,
            IncognitoStateProvider incognitoStateProvider, OnClickListener newTabClickListener,
            AppMenuButtonHelper menuButtonHelper, TabModelSelector tabModelSelector,
            OverviewModeBehavior overviewModeBehavior, TabCountProvider tabCountProvider) {
        final View root = stub.inflate();

        TabSwitcherBottomToolbarModel model = new TabSwitcherBottomToolbarModel();

        PropertyModelChangeProcessor.create(model, root, new TabSwitcherBottomToolbarViewBinder());

        mMediator = new TabSwitcherBottomToolbarMediator(
                model, incognitoStateProvider, overviewModeBehavior);

        mNewTabButton = root.findViewById(R.id.new_tab_button);
        mNewTabButton.setIncognitoStateProvider(incognitoStateProvider);
        mNewTabButton.setOnClickListener(newTabClickListener);
        mNewTabButton.postNativeInitialization();

        mMenuButton = root.findViewById(R.id.menu_button_wrapper);
        mMenuButton.setThemeColorProvider(incognitoStateProvider);
        mMenuButton.setTouchListener(menuButtonHelper);
        mMenuButton.setAccessibilityDelegate(menuButtonHelper);

        if (!DeviceClassManager.enableAccessibilityLayout()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID)) {
            ViewStub incognitoToggleTabsStub = root.findViewById(R.id.incognito_tabs_stub);
            mIncognitoToggleTabLayout =
                    (IncognitoToggleTabLayout) incognitoToggleTabsStub.inflate();
            mIncognitoToggleTabLayout.setTabModelSelector(tabModelSelector);
            mIncognitoToggleTabLayout.setTabCountProvider(tabCountProvider);
        } else {
            mIncognitoToggleTabLayout = null;
        }
    }

    /**
     * Show the update badge over the bottom toolbar's app menu.
     */
    public void showAppMenuUpdateBadge() {
        mMenuButton.setUpdateBadgeVisibilityIfValidState(true);
    }

    /**
     * Remove the update badge.
     */
    public void removeAppMenuUpdateBadge() {
        mMenuButton.setUpdateBadgeVisibilityIfValidState(false);
    }

    /**
     * @return Whether the update badge is showing.
     */
    public boolean isShowingAppMenuUpdateBadge() {
        return mMenuButton.isShowingAppMenuUpdateBadge();
    }

    /**
     * @return The tab switcher mode bottom toolbar's menu button.
     */
    public MenuButton getMenuButton() {
        return mMenuButton;
    }

    /**
     * Clean up any state when the bottom toolbar is destroyed.
     */
    public void destroy() {
        mMediator.destroy();
        mNewTabButton.destroy();
        if (mIncognitoToggleTabLayout != null) mIncognitoToggleTabLayout.destroy();
        mMenuButton.destroy();
    }
}
