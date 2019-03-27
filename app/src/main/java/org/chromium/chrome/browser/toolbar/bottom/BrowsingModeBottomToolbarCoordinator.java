// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.bottom;

import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ActivityTabProvider.HintlessActivityTabObserver;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.ToolbarSwipeLayout;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.HomeButton;
import org.chromium.chrome.browser.toolbar.MenuButton;
import org.chromium.chrome.browser.toolbar.TabCountProvider;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonCoordinator;
import org.chromium.chrome.browser.toolbar.bottom.BrowsingModeBottomToolbarViewBinder.ViewHolder;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;
import org.chromium.ui.resources.ResourceManager;

/**
 * The coordinator for the browsing mode bottom toolbar. This class has two primary components,
 * an Android view that handles user actions and a composited texture that draws when the controls
 * are being scrolled off-screen. The Android version does not draw unless the controls offset is 0.
 */
public class BrowsingModeBottomToolbarCoordinator {
    /** The mediator that handles events from outside the browsing mode bottom toolbar. */
    private final BrowsingModeBottomToolbarMediator mMediator;

    /** The home button that lives in the bottom toolbar. */
    private final HomeButton mHomeButton;

    /** The share button that lives in the bottom toolbar. */
    private final ShareButton mShareButton;

    /** The search acceleartor that lives in the bottom toolbar. */
    private final SearchAccelerator mSearchAccelerator;

    /** The tab switcher button component that lives in the bottom toolbar. */
    private final TabSwitcherButtonCoordinator mTabSwitcherButtonCoordinator;

    /** The menu button that lives in the browsing mode bottom toolbar. */
    private final MenuButton mMenuButton;

    /**
     * Build the coordinator that manages the browsing mode bottom toolbar.
     * @param root The root {@link View} for locating the views to inflate.
     * @param fullscreenManager A {@link ChromeFullscreenManager} to update the bottom controls
     *                          height for the renderer.
     * @param tabProvider The {@link ActivityTabProvider} used for making the IPH.
     * @param homeButtonListener The {@link OnClickListener} for the home button.
     * @param searchAcceleratorListener The {@link OnClickListener} for the search accelerator.
     * @param shareButtonListener The {@link OnClickListener} for the share button.
     */
    public BrowsingModeBottomToolbarCoordinator(View root,
            ChromeFullscreenManager fullscreenManager, ActivityTabProvider tabProvider,
            OnClickListener homeButtonListener, OnClickListener searchAcceleratorListener,
            OnClickListener shareButtonListener) {
        BrowsingModeBottomToolbarModel model = new BrowsingModeBottomToolbarModel();

        final ScrollingBottomViewResourceFrameLayout toolbarRoot =
                (ScrollingBottomViewResourceFrameLayout) root.findViewById(
                        R.id.bottom_toolbar_control_container);

        final int shadowHeight =
                toolbarRoot.getResources().getDimensionPixelOffset(R.dimen.toolbar_shadow_height);
        toolbarRoot.setTopShadowHeight(shadowHeight);

        PropertyModelChangeProcessor.create(
                model, new ViewHolder(toolbarRoot), new BrowsingModeBottomToolbarViewBinder());

        mMediator = new BrowsingModeBottomToolbarMediator(
                model, fullscreenManager, toolbarRoot.getResources());

        mHomeButton = toolbarRoot.findViewById(R.id.home_button);
        mHomeButton.setOnClickListener(homeButtonListener);
        mHomeButton.setActivityTabProvider(tabProvider);

        mShareButton = toolbarRoot.findViewById(R.id.share_button);
        mShareButton.setOnClickListener(shareButtonListener);
        mShareButton.setActivityTabProvider(tabProvider);

        mSearchAccelerator = toolbarRoot.findViewById(R.id.search_accelerator);
        mSearchAccelerator.setOnClickListener(searchAcceleratorListener);

        mTabSwitcherButtonCoordinator = new TabSwitcherButtonCoordinator(toolbarRoot);

        mMenuButton = toolbarRoot.findViewById(R.id.menu_button_wrapper);

        final View iphAnchor = toolbarRoot.findViewById(R.id.search_accelerator);
        tabProvider.addObserverAndTrigger(new HintlessActivityTabObserver() {
            @Override
            public void onActivityTabChanged(Tab tab) {
                if (tab == null) return;
                final Tracker tracker = TrackerFactory.getTrackerForProfile(tab.getProfile());
                tracker.addOnInitializedCallback(
                        (ready) -> mMediator.showIPH(tab.getActivity(), iphAnchor, tracker));
                tabProvider.removeObserver(this);
            }
        });
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
     * @param menuButtonHelper An {@link AppMenuButtonHelper} that is triggered when the
     *                         menu button is clicked.
     * @param overviewModeBehavior The overview mode manager.
     * @param windowAndroid A {@link WindowAndroid} for watching keyboard visibility events.
     * @param tabCountProvider Updates the tab count number in the tab switcher button.
     * @param themeColorProvider Notifies components when theme color changes.
     * @param tabModelSelector A {@link TabModelSelector} that the share button uses to know whether
     *                         or not to be enabled.
     */
    public void initializeWithNative(ResourceManager resourceManager, LayoutManager layoutManager,
            OnClickListener tabSwitcherListener, AppMenuButtonHelper menuButtonHelper,
            OverviewModeBehavior overviewModeBehavior, WindowAndroid windowAndroid,
            TabCountProvider tabCountProvider, ThemeColorProvider themeColorProvider,
            TabModelSelector tabModelSelector) {
        mMediator.setLayoutManager(layoutManager);
        mMediator.setResourceManager(resourceManager);
        mMediator.setToolbarSwipeHandler(layoutManager.getToolbarSwipeHandler());
        mMediator.setWindowAndroid(windowAndroid);
        mMediator.setOverviewModeBehavior(overviewModeBehavior);
        mMediator.setThemeColorProvider(themeColorProvider);

        mHomeButton.setThemeColorProvider(themeColorProvider);
        mShareButton.setThemeColorProvider(themeColorProvider);
        mSearchAccelerator.setThemeColorProvider(themeColorProvider);

        mTabSwitcherButtonCoordinator.setTabSwitcherListener(tabSwitcherListener);
        mTabSwitcherButtonCoordinator.setThemeColorProvider(themeColorProvider);
        mTabSwitcherButtonCoordinator.setTabCountProvider(tabCountProvider);

        mMenuButton.setAppMenuButtonHelper(menuButtonHelper);
        mMenuButton.setThemeColorProvider(themeColorProvider);
    }

    /**
     * Show the update badge over the bottom toolbar's app menu.
     */
    public void showAppMenuUpdateBadge() {
        mMenuButton.showAppMenuUpdateBadge(true);
    }

    /**
     * Remove the update badge.
     */
    public void removeAppMenuUpdateBadge() {
        mMenuButton.removeAppMenuUpdateBadge(true);
    }

    /**
     * @return Whether the update badge is showing.
     */
    public boolean isShowingAppMenuUpdateBadge() {
        return mMenuButton.isShowingAppMenuUpdateBadge();
    }

    /**
     * @param layout The {@link ToolbarSwipeLayout} that the bottom toolbar will hook into. This
     *               allows the bottom toolbar to provide the layout with scene layers with the
     *               bottom toolbar's texture.
     */
    public void setToolbarSwipeLayout(ToolbarSwipeLayout layout) {
        mMediator.setToolbarSwipeLayout(layout);
    }

    /**
     * @return The browsing mode bottom toolbar's menu button.
     */
    public MenuButton getMenuButton() {
        return mMenuButton;
    }

    /**
     * @return Whether the browsing mode toolbar is visible.
     */
    public boolean isVisible() {
        return mMediator.isVisible();
    }

    /**
     * Clean up any state when the browsing mode bottom toolbar is destroyed.
     */
    public void destroy() {
        mMediator.destroy();
        mHomeButton.destroy();
        mShareButton.destroy();
        mSearchAccelerator.destroy();
        mTabSwitcherButtonCoordinator.destroy();
        mMenuButton.destroy();
    }
}
