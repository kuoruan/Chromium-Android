// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dependency_injection;

import android.content.res.Resources;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;

import dagger.Module;
import dagger.Provides;

/**
 * Module for common dependencies in {@link ChromeActivity}.
 */
@Module
public class ChromeActivityCommonsModule {
    private final ChromeActivity<?> mActivity;

    /** See {@link ModuleFactoryOverrides} */
    public interface Factory { ChromeActivityCommonsModule create(ChromeActivity<?> activity); }

    public ChromeActivityCommonsModule(ChromeActivity<?> activity) {
        mActivity = activity;
    }

    @Provides
    public BottomSheetController provideBottomSheetController() {
        // Once the BottomSheetController is in the dependency graph, this method would no longer
        // be necessary, as well as the getter in ChromeActivity. Same is true for a few other
        // methods below.
        return mActivity.getBottomSheetController();
    }

    @Provides
    public TabModelSelector provideTabModelSelector() {
        return mActivity.getTabModelSelector();
    }

    @Provides
    public ChromeFullscreenManager provideChromeFullscreenManager() {
        return mActivity.getFullscreenManager();
    }

    @Provides
    public ToolbarManager provideToolbarManager() {
        return mActivity.getToolbarManager();
    }

    @Provides
    public LayoutManager provideLayoutManager() {
        return mActivity.getCompositorViewHolder().getLayoutManager();
    }

    @Provides
    public ChromeActivity provideChromeActivity() {
        // Ideally this should provide only the Context instead of specific activity, but currently
        // a lot of code is coupled specifically to ChromeActivity.
        return mActivity;
    }

    @Provides
    public Resources provideResources() {
        return mActivity.getResources();
    }
}
