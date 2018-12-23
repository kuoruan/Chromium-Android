// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.ACTIVE_TAB_INDEX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.HEIGHT;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.NO_ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetProperties.VISIBLE;

import android.os.Build;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel;

/**
 * Observes {@link AccessorySheetProperties} changes (like a newly available tab) and triggers the
 * {@link AccessorySheetViewBinder} which will modify the view accordingly.
 */
class AccessorySheetViewBinder {
    public static void bind(PropertyModel model, ViewPager viewPager, PropertyKey propertyKey) {
        if (propertyKey == TABS) {
            viewPager.setAdapter(
                    AccessorySheetCoordinator.createTabViewAdapter(model.get(TABS), viewPager));
        } else if (propertyKey == VISIBLE) {
            viewPager.bringToFront(); // Ensure toolbars and other containers are overlaid.
            viewPager.setVisibility(model.get(VISIBLE) ? View.VISIBLE : View.GONE);
            if (model.get(VISIBLE) && model.get(ACTIVE_TAB_INDEX) != NO_ACTIVE_TAB) {
                announceOpenedTab(viewPager, model.get(TABS).get(model.get(ACTIVE_TAB_INDEX)));
            }
        } else if (propertyKey == HEIGHT) {
            ViewGroup.LayoutParams p = viewPager.getLayoutParams();
            p.height = model.get(HEIGHT);
            viewPager.setLayoutParams(p);
        } else if (propertyKey == ACTIVE_TAB_INDEX) {
            if (model.get(ACTIVE_TAB_INDEX) != NO_ACTIVE_TAB) {
                viewPager.setCurrentItem(model.get(ACTIVE_TAB_INDEX));
            }
        } else {
            assert false : "Every possible property update needs to be handled!";
        }
        // Layout requests happen automatically since Kitkat and redundant requests cause warnings.
        if (viewPager != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            viewPager.post(() -> {
                ViewParent parent = viewPager.getParent();
                if (parent != null) {
                    parent.requestLayout();
                }
            });
        }
    }

    static void announceOpenedTab(View announcer, KeyboardAccessoryData.Tab tab) {
        if (tab.getOpeningAnnouncement() == null) return;
        announcer.announceForAccessibility(tab.getOpeningAnnouncement());
    }
}
