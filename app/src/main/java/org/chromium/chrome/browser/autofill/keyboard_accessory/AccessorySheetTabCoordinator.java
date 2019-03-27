// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.graphics.drawable.Drawable;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

/**
 * This coordinator aims to be the base class for sheets to be added to the
 * {@link ManualFillingCoordinator}. It mainly enforces a consistent use of scroll listeners in
 * {@link RecyclerView}s.
 */
public abstract class AccessorySheetTabCoordinator implements KeyboardAccessoryData.Tab.Listener {
    private final KeyboardAccessoryData.Tab mTab;
    private final RecyclerView.OnScrollListener mScrollListener;

    /**
     * Creates a keyboard accessory sheet tab coordinator.
     * @param icon The icon that represents this sheet in the keyboard accessory tab switcher.
     * @param contentDescription A description for this sheet used in the tab switcher.
     * @param openingAnnouncement The announced string when opening this sheet.
     * @param layout The layout containing all views that are used by this sheet.
     * @param tabType The type of this tab as used in histograms.
     * @param scrollListener An optional listener that will be bound to an inflated recycler view.
     */
    public AccessorySheetTabCoordinator(Drawable icon, String contentDescription,
            String openingAnnouncement, @LayoutRes int layout, @AccessoryTabType int tabType,
            @Nullable RecyclerView.OnScrollListener scrollListener) {
        mTab = new KeyboardAccessoryData.Tab(
                icon, contentDescription, openingAnnouncement, layout, tabType, this);
        mScrollListener = scrollListener;
    }

    @CallSuper
    @Override
    public void onTabCreated(ViewGroup view) {
        AccessorySheetTabViewBinder.initializeView((RecyclerView) view, mScrollListener);
    }

    /**
     * Returns the Tab object that describes the appearance of this class in the keyboard accessory
     * or its accessory sheet. The returned object doesn't change for this instance.
     * @return Returns a stable {@link KeyboardAccessoryData.Tab} that is connected to this sheet.
     */
    public KeyboardAccessoryData.Tab getTab() {
        return mTab;
    }
}
