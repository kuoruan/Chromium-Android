// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.res.ColorStateList;

import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.toolbar.ThemeColorProvider.ThemeColorObserver;

/**
 * This class is responsible for reacting to events from the outside world, interacting with other
 * coordinators, running most of the business logic associated with the tab switcher bottom toolbar,
 * and updating the model accordingly.
 */
class TabSwitcherBottomToolbarMediator implements OverviewModeObserver, ThemeColorObserver {
    /** The model for the tab switcher bottom toolbar that holds all of its state. */
    private final TabSwitcherBottomToolbarModel mModel;

    /** A provider that notifies components when the theme color changes.*/
    private final ThemeColorProvider mThemeColorProvider;

    /** The overview mode manager. */
    private final OverviewModeBehavior mOverviewModeBehavior;

    /**
     * Build a new mediator that handles events from outside the tab switcher bottom toolbar.
     * @param model The {@link TabSwitcherBottomToolbarModel} that holds all the state for the
     *              tab switcher bottom toolbar.
     * @param themeColorProvider Notifies components when the theme color changes.
     * @param overviewModeBehavior The overview mode manager.
     */
    TabSwitcherBottomToolbarMediator(TabSwitcherBottomToolbarModel model,
            ThemeColorProvider themeColorProvider, OverviewModeBehavior overviewModeBehavior) {
        mModel = model;

        mThemeColorProvider = themeColorProvider;
        mThemeColorProvider.addObserver(this);

        mOverviewModeBehavior = overviewModeBehavior;
        mOverviewModeBehavior.addOverviewModeObserver(this);
    }

    /**
     * Clean up anything that needs to be when the tab switcher bottom toolbar is destroyed.
     */
    void destroy() {
        if (mOverviewModeBehavior != null) mOverviewModeBehavior.removeOverviewModeObserver(this);
        if (mThemeColorProvider != null) mThemeColorProvider.removeObserver(this);
    }

    @Override
    public void onOverviewModeStartedShowing(boolean showToolbar) {
        mModel.set(TabSwitcherBottomToolbarModel.IS_VISIBLE, true);
    }

    @Override
    public void onOverviewModeFinishedShowing() {}

    @Override
    public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
        mModel.set(TabSwitcherBottomToolbarModel.IS_VISIBLE, false);
    }

    @Override
    public void onOverviewModeFinishedHiding() {}

    @Override
    public void onThemeColorChanged(ColorStateList tint, int primaryColor) {
        mModel.set(TabSwitcherBottomToolbarModel.PRIMARY_COLOR, primaryColor);
    }
}
