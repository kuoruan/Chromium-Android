// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.os.Build;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetModel.PropertyKey;
import org.chromium.chrome.browser.modelutil.LazyViewBinderAdapter;

/**
 * Observes {@link AccessorySheetModel} changes (like a newly available tab) and triggers the
 * {@link AccessorySheetViewBinder} which will modify the view accordingly.
 */
class AccessorySheetViewBinder
        implements LazyViewBinderAdapter
                           .SimpleViewBinder<AccessorySheetModel, ViewPager, PropertyKey> {
    @Override
    public PropertyKey getVisibilityProperty() {
        return PropertyKey.VISIBLE;
    }

    @Override
    public boolean isVisible(AccessorySheetModel model) {
        return model.isVisible();
    }

    @Override
    public void onInitialInflation(AccessorySheetModel model, ViewPager inflatedView) {
        inflatedView.setAdapter(
                AccessorySheetCoordinator.createTabViewAdapter(model, inflatedView));
        bind(model, inflatedView, PropertyKey.HEIGHT);
        bind(model, inflatedView, PropertyKey.ACTIVE_TAB_INDEX);
    }

    @Override
    public void bind(AccessorySheetModel model, ViewPager inflatedView, PropertyKey propertyKey) {
        if (propertyKey == PropertyKey.VISIBLE) {
            inflatedView.bringToFront(); // Ensure toolbars and other containers are overlaid.
            inflatedView.setVisibility(model.isVisible() ? View.VISIBLE : View.GONE);
            if (model.isVisible()
                    && model.getActiveTabIndex() != AccessorySheetModel.NO_ACTIVE_TAB) {
                announceOpenedTab(inflatedView, model.getTabList().get(model.getActiveTabIndex()));
            }
            requestLayout(inflatedView);
            return;
        }
        if (propertyKey == PropertyKey.HEIGHT) {
            ViewGroup.LayoutParams p = inflatedView.getLayoutParams();
            p.height = model.getHeight();
            inflatedView.setLayoutParams(p);
            requestLayout(inflatedView);
            return;
        }
        if (propertyKey == PropertyKey.ACTIVE_TAB_INDEX) {
            if (model.getActiveTabIndex() != AccessorySheetModel.NO_ACTIVE_TAB) {
                inflatedView.setCurrentItem(model.getActiveTabIndex());
            }
            requestLayout(inflatedView);
            return;
        }
        assert false : "Every possible property update needs to be handled!";
    }

    private static void requestLayout(ViewPager viewPager) {
         // Layout requests happen automatically since Kitkat and redundant requests cause warnings.
        if (viewPager == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) return;
        viewPager.post(() -> {
            ViewParent parent = viewPager.getParent();
            if (parent != null) {
                parent.requestLayout();
            }
        });
    }

    static void announceOpenedTab(View announcer, KeyboardAccessoryData.Tab tab) {
        if (tab.getOpeningAnnouncement() == null) return;
        announcer.announceForAccessibility(tab.getOpeningAnnouncement());
    }
}
