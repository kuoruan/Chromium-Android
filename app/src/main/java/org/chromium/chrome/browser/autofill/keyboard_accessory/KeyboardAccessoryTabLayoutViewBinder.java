// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryTabLayoutProperties.TAB_SELECTION_CALLBACKS;

import android.support.design.widget.TabLayout;

import org.chromium.chrome.R;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.ListModelChangeProcessor;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * Stateless {@link ListModelChangeProcessor.ViewBinder} that binds a {@link ListModel}'s data to
 * a {@link KeyboardAccessoryTabLayoutView}.
 */
class KeyboardAccessoryTabLayoutViewBinder
        implements ListModelChangeProcessor.ViewBinder<ListModel<KeyboardAccessoryData.Tab>,
                KeyboardAccessoryTabLayoutView> {
    @Override
    public void onItemsInserted(ListModel<KeyboardAccessoryData.Tab> model,
            KeyboardAccessoryTabLayoutView view, int index, int count) {
        assert count > 0 : "Tried to insert invalid amount of tabs - must be at least one.";
        for (int i = index; i < index + count; i++) {
            KeyboardAccessoryData.Tab tab = model.get(i);
            view.addTabAt(i, tab.getIcon(), tab.getContentDescription());
        }
    }

    @Override
    public void onItemsRemoved(ListModel<KeyboardAccessoryData.Tab> model,
            KeyboardAccessoryTabLayoutView view, int index, int count) {
        assert count > 0 : "Tried to remove invalid amount of tabs - must be at least one.";
        while (count-- > 0) {
            view.tryToRemoveTabAt(index++);
        }
    }

    @Override
    public void onItemsChanged(ListModel<KeyboardAccessoryData.Tab> model,
            KeyboardAccessoryTabLayoutView view, int index, int count) {
        // TODO(fhorschig): Implement fine-grained, ranged changes should the need arise.
        updateAllTabs(view, model);
    }

    private void updateAllTabs(
            KeyboardAccessoryTabLayoutView view, ListModel<KeyboardAccessoryData.Tab> model) {
        view.removeAllTabs();
        if (model.size() > 0) onItemsInserted(model, view, 0, model.size());
    }

    protected static void bind(
            PropertyModel model, KeyboardAccessoryTabLayoutView view, PropertyKey propertyKey) {
        if (propertyKey == TABS) {
            KeyboardAccessoryTabLayoutCoordinator.createTabViewBinder(model, view)
                    .updateAllTabs(view, model.get(TABS));
        } else if (propertyKey == ACTIVE_TAB) {
            view.setActiveTabColor(model.get(ACTIVE_TAB));
            setActiveTabHint(model, view);
        } else if (propertyKey == TAB_SELECTION_CALLBACKS) {
            // Don't add null as listener. It's a valid state but an invalid argument.
            TabLayout.OnTabSelectedListener listener = model.get(TAB_SELECTION_CALLBACKS);
            if (listener != null) view.setTabSelectionAdapter(listener);
        } else {
            assert false : "Every possible property update needs to be handled!";
        }
    }

    private static void setActiveTabHint(PropertyModel model, KeyboardAccessoryTabLayoutView view) {
        int activeTab = -1;
        if (model.get(ACTIVE_TAB) != null) {
            activeTab = model.get(ACTIVE_TAB);
        }
        for (int i = 0; i < model.get(TABS).size(); ++i) {
            KeyboardAccessoryData.Tab tab = model.get(TABS).get(i);
            if (activeTab == i) {
                view.setTabDescription(i, R.string.keyboard_accessory_sheet_hide);
            } else {
                view.setTabDescription(i, tab.getContentDescription());
            }
        }
    }
}