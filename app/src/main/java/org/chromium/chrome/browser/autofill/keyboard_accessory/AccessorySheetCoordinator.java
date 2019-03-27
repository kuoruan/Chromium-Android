// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.ACTIVE_TAB_INDEX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.HEIGHT;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.NO_ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.PAGE_CHANGE_LISTENER;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.TOP_SHADOW_VISIBLE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.VISIBLE;

import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;

import org.chromium.base.VisibleForTesting;
import org.chromium.ui.ViewProvider;
import org.chromium.ui.modelutil.LazyConstructionPropertyMcp;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.ListModelChangeProcessor;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * Creates and owns all elements which are part of the accessory sheet component.
 * It's part of the controller but will mainly forward events (like showing the sheet) and handle
 * communication with the {@link ManualFillingCoordinator} (e.g. add a tab to trigger the sheet).
 * to the {@link AccessorySheetMediator}.
 */
public class AccessorySheetCoordinator {
    private final AccessorySheetMediator mMediator;

    /**
     * Creates the sheet component by instantiating Model, View and Controller before wiring these
     * parts up.
     * @param viewProvider A provider for the accessory layout.
     */
    public AccessorySheetCoordinator(ViewProvider<AccessorySheetView> viewProvider) {
        PropertyModel model = new PropertyModel
                                      .Builder(TABS, ACTIVE_TAB_INDEX, VISIBLE, HEIGHT,
                                              TOP_SHADOW_VISIBLE, PAGE_CHANGE_LISTENER)
                                      .with(TABS, new ListModel<>())
                                      .with(ACTIVE_TAB_INDEX, NO_ACTIVE_TAB)
                                      .with(VISIBLE, false)
                                      .with(TOP_SHADOW_VISIBLE, false)
                                      .build();

        LazyConstructionPropertyMcp.create(
                model, VISIBLE, viewProvider, AccessorySheetViewBinder::bind);

        KeyboardAccessoryMetricsRecorder.registerAccessorySheetModelMetricsObserver(model);
        mMediator = new AccessorySheetMediator(model);
    }

    /**
     * Creates the {@link PagerAdapter} for the newly inflated {@link ViewPager}.
     * The created adapter observes the given model for item changes and updates the view pager.
     * @param tabList The list of tabs to be displayed.
     * @param viewPager The newly inflated {@link ViewPager}.
     * @return A fully initialized {@link PagerAdapter}.
     */
    static PagerAdapter createTabViewAdapter(
            ListModel<KeyboardAccessoryData.Tab> tabList, ViewPager viewPager) {
        AccessoryPagerAdapter adapter = new AccessoryPagerAdapter(tabList);
        tabList.addObserver(new ListModelChangeProcessor<>(tabList, viewPager, adapter));
        return adapter;
    }

    /**
     * Adds the contents of a given {@link KeyboardAccessoryData.Tab} to the accessory sheet. If it
     * is the first Tab, it automatically becomes the active Tab.
     * @param tab The tab which should be added to the AccessorySheet.
     */
    void addTab(KeyboardAccessoryData.Tab tab) {
        mMediator.addTab(tab);
    }

    void removeTab(KeyboardAccessoryData.Tab tab) {
        mMediator.removeTab(tab);
    }

    void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mMediator.setTabs(tabs);
    }

    RecyclerView.OnScrollListener getScrollListener() {
        return mMediator.getScrollListener();
    }

    /**
     * Returns a {@link KeyboardAccessoryData.Tab} object that is used to display this bottom sheet.
     * @return Returns a {@link KeyboardAccessoryData.Tab}.
     */
    @Nullable
    public KeyboardAccessoryData.Tab getTab() {
        return mMediator.getTab();
    }

    /**
     * Sets the height of the accessory sheet (i.e. adapts to keyboard heights).
     * @param height The height of the sheet in pixels.
     */
    public void setHeight(@Px int height) {
        mMediator.setHeight(height);
    }

    /**
     * Gets the height of the accessory sheet (even if not visible).
     * @return The height of the sheet in pixels.
     */
    public @Px int getHeight() {
        return mMediator.getHeight();
    }

    /**
     * Shows the Accessory Sheet.
     */
    public void show() {
        mMediator.show();
    }

    /**
     * Hides the Accessory Sheet.
     */
    public void hide() {
        mMediator.hide();
    }

    /**
     * Returns whether the accessory sheet is currently visible.
     * @return True, if the accessory sheet is visible.
     */
    public boolean isShown() {
        return mMediator.isShown();
    }

    @VisibleForTesting
    AccessorySheetMediator getMediatorForTesting() {
        return mMediator;
    }

    /**
     * Calling this function changes the active tab to the tab at the given |position|.
     * @param position The index of the tab (starting with 0) that should be set active.
     */
    public void setActiveTab(int position) {
        mMediator.setActiveTab(position);
    }

    void setOnPageChangeListener(ViewPager.OnPageChangeListener onPageChangeListener) {
        mMediator.setOnPageChangeListener(onPageChangeListener);
    }
}