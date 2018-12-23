// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.ToolbarSwipeLayout;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.resources.ResourceManager;

/**
 * The root coordinator for the bottom toolbar. It has two subcomponents. The browing mode bottom
 * toolbar and the tab switcher mode bottom toolbar.
 */
public class BottomToolbarCoordinator {
    /** The browsing mode bottom toolbar component */
    private final BrowsingModeBottomToolbarCoordinator mBrowsingModeCoordinator;

    /** The tab switcher mode bottom toolbar component */
    private TabSwitcherBottomToolbarCoordinator mTabSwitcherModeCoordinator;

    /** The tab switcher mode bottom toolbar stub that will be inflated when native is ready. */
    private final ViewStub mTabSwitcherModeStub;

    /**
     * Build the coordinator that manages the bottom toolbar.
     * @param fullscreenManager A {@link ChromeFullscreenManager} to update the bottom controls
     *                          height for the renderer.
     * @param stub The bottom toolbar {@link ViewStub} to inflate.
     * @param tabProvider The {@link ActivityTabProvider} used for making the IPH.
     * @param homeButtonListener The {@link OnClickListener} for the home button.
     * @param searchAcceleratorListener The {@link OnClickListener} for the search accelerator.
     * @param shareButtonListener The {@link OnClickListener} for the share button.
     */
    public BottomToolbarCoordinator(ChromeFullscreenManager fullscreenManager, ViewStub stub,
            ActivityTabProvider tabProvider, OnClickListener homeButtonListener,
            OnClickListener searchAcceleratorListener, OnClickListener shareButtonListener) {
        final View root = stub.inflate();

        mBrowsingModeCoordinator = new BrowsingModeBottomToolbarCoordinator(root, fullscreenManager,
                tabProvider, homeButtonListener, searchAcceleratorListener, shareButtonListener);

        mTabSwitcherModeStub = root.findViewById(R.id.bottom_toolbar_tab_switcher_mode_stub);
    }

    /**
     * Initialize the bottom toolbar with the components that had native initialization
     * dependencies.
     * <p>
     * Calling this must occur after the native library have completely loaded.
     * @param resourceManager A {@link ResourceManager} for loading textures into the compositor.
     * @param layoutManager A {@link LayoutManager} to attach overlays to.
     * @param tabSwitcherListener An {@link OnClickListener} that is triggered when the
     *                            tab switcher button is clicked.
     * @param newTabClickListener An {@link OnClickListener} that is triggered when the
     *                            new tab button is clicked.
     * @param menuButtonHelper An {@link AppMenuButtonHelper} that is triggered when the
     *                         menu button is clicked.
     * @param tabModelSelector A {@link TabModelSelector} that incognito toggle tab layout uses to
                               switch between normal and incognito tabs.
     * @param overviewModeBehavior The overview mode manager.
     * @param windowAndroid A {@link WindowAndroid} for watching keyboard visibility events.
     * @param tabCountProvider Updates the tab count number in the tab switcher button and in the
     *                         incognito toggle tab layout.
     * @param incognitoStateProvider Notifies components when incognito mode is entered or exited.
     */
    public void initializeWithNative(ResourceManager resourceManager, LayoutManager layoutManager,
            OnClickListener tabSwitcherListener, OnClickListener newTabClickListener,
            AppMenuButtonHelper menuButtonHelper, TabModelSelector tabModelSelector,
            OverviewModeBehavior overviewModeBehavior, WindowAndroid windowAndroid,
            TabCountProvider tabCountProvider, IncognitoStateProvider incognitoStateProvider) {
        mBrowsingModeCoordinator.initializeWithNative(resourceManager, layoutManager,
                tabSwitcherListener, menuButtonHelper, overviewModeBehavior, windowAndroid,
                tabCountProvider, incognitoStateProvider, tabModelSelector);
        mTabSwitcherModeCoordinator = new TabSwitcherBottomToolbarCoordinator(mTabSwitcherModeStub,
                incognitoStateProvider, newTabClickListener, menuButtonHelper, tabModelSelector,
                overviewModeBehavior, tabCountProvider);
    }

    /**
     * Show the update badge over the bottom toolbar's app menu.
     */
    public void showAppMenuUpdateBadge() {
        mBrowsingModeCoordinator.showAppMenuUpdateBadge();
        if (mTabSwitcherModeCoordinator != null) {
            mTabSwitcherModeCoordinator.showAppMenuUpdateBadge();
        }
    }

    /**
     * Remove the update badge.
     */
    public void removeAppMenuUpdateBadge() {
        mBrowsingModeCoordinator.removeAppMenuUpdateBadge();
        if (mTabSwitcherModeCoordinator != null) {
            mTabSwitcherModeCoordinator.removeAppMenuUpdateBadge();
        }
    }

    /**
     * @return Whether the update badge is showing.
     */
    public boolean isShowingAppMenuUpdateBadge() {
        return mBrowsingModeCoordinator.isShowingAppMenuUpdateBadge();
    }

    /**
     * @param layout The {@link ToolbarSwipeLayout} that the bottom toolbar will hook into. This
     *               allows the bottom toolbar to provide the layout with scene layers with the
     *               bottom toolbar's texture.
     */
    public void setToolbarSwipeLayout(ToolbarSwipeLayout layout) {
        mBrowsingModeCoordinator.setToolbarSwipeLayout(layout);
    }

    /**
     * @return The wrapper for the app menu button.
     */
    public MenuButton getMenuButtonWrapper() {
        if (mBrowsingModeCoordinator.isVisible()) {
            return mBrowsingModeCoordinator.getMenuButton();
        }
        if (mTabSwitcherModeCoordinator != null) {
            return mTabSwitcherModeCoordinator.getMenuButton();
        }
        return null;
    }

    /**
     * Clean up any state when the bottom toolbar is destroyed.
     */
    public void destroy() {
        mBrowsingModeCoordinator.destroy();
        if (mTabSwitcherModeCoordinator != null) {
            mTabSwitcherModeCoordinator.destroy();
            mTabSwitcherModeCoordinator = null;
        }
    }
}
