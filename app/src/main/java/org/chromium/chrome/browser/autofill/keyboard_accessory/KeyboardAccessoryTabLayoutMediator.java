// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.TAB_SELECTION_CALLBACKS;

import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;

import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutCoordinator.AccessoryTabObserver;
import org.chromium.ui.modelutil.ListObservable;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyObservable;

/**
 * This mediator observes and changes a {@link PropertyModel} that contains the visual appearance of
 * a {@link TabLayout}. It manages {@link ViewPager.OnPageChangeListener}s.
 */
class KeyboardAccessoryTabLayoutMediator
        implements ListObservable.ListObserver<Void>, TabLayout.OnTabSelectedListener,
                   PropertyObservable.PropertyObserver<PropertyKey>,
                   KeyboardAccessoryCoordinator.TabSwitchingDelegate {
    private final PropertyModel mModel;
    private @Nullable AccessoryTabObserver mAccessoryTabObserver;
    private ViewPager.OnPageChangeListener mPageChangeListener;

    KeyboardAccessoryTabLayoutMediator(PropertyModel model) {
        mModel = model;
        mModel.addObserver(this);
        mModel.get(TABS).addObserver(this);
        mModel.set(TAB_SELECTION_CALLBACKS, this);
    }

    void setPageChangeListener(ViewPager.OnPageChangeListener onPageChangeListener) {
        mPageChangeListener = onPageChangeListener;
    }

    ViewPager.OnPageChangeListener getStableOnPageChangeListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int j) {
                if (mPageChangeListener != null) mPageChangeListener.onPageScrolled(i, v, j);
            }

            @Override
            public void onPageSelected(int i) {
                if (mPageChangeListener != null) mPageChangeListener.onPageSelected(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (mPageChangeListener != null) mPageChangeListener.onPageScrollStateChanged(i);
            }
        };
    }

    @Override
    public void onPropertyChanged(
            PropertyObservable<PropertyKey> source, @Nullable PropertyKey propertyKey) {
        if (propertyKey == ACTIVE_TAB) {
            if (mAccessoryTabObserver != null) {
                mAccessoryTabObserver.onActiveTabChanged(mModel.get(ACTIVE_TAB));
            }
            return;
        }
        if (propertyKey == TABS || propertyKey == TAB_SELECTION_CALLBACKS) {
            return;
        }
        assert false : "Every property update needs to be handled explicitly!";
    }

    @Override
    public void addTab(KeyboardAccessoryData.Tab tab) {
        mModel.get(TABS).add(tab);
    }

    @Override
    public void removeTab(KeyboardAccessoryData.Tab tab) {
        mModel.get(TABS).remove(tab);
    }

    @Override
    public void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mModel.get(TABS).set(tabs);
    }

    @Override
    public void closeActiveTab() {
        mModel.set(ACTIVE_TAB, null);
    }

    @Override
    public @Nullable KeyboardAccessoryData.Tab getActiveTab() {
        if (mModel.get(ACTIVE_TAB) == null) return null;
        return mModel.get(TABS).get(mModel.get(ACTIVE_TAB));
    }

    @Override
    public boolean hasTabs() {
        return mModel.get(TABS).size() > 0;
    }

    @Override
    public void onItemRangeInserted(ListObservable source, int index, int count) {
        assert source == mModel.get(TABS);
        if (mAccessoryTabObserver != null) mAccessoryTabObserver.onTabsChanged();
    }

    @Override
    public void onItemRangeRemoved(ListObservable source, int index, int count) {
        assert source == mModel.get(TABS);
        if (mAccessoryTabObserver != null) mAccessoryTabObserver.onTabsChanged();
    }

    @Override
    public void onItemRangeChanged(
            ListObservable<Void> source, int index, int count, @Nullable Void payload) {
        assert source == mModel.get(TABS);
        if (mAccessoryTabObserver != null) mAccessoryTabObserver.onTabsChanged();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mModel.set(ACTIVE_TAB, tab.getPosition());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        if (mModel.get(ACTIVE_TAB) == null) {
            mModel.set(ACTIVE_TAB, tab.getPosition());
        } else if (mAccessoryTabObserver != null) {
            mAccessoryTabObserver.onActiveTabReselected();
        }
    }

    public void setTabObserver(AccessoryTabObserver accessoryTabObserver) {
        mAccessoryTabObserver = accessoryTabObserver;
    }
}