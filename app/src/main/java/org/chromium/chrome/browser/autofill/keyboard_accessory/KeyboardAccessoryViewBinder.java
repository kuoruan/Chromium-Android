// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIONS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TAB_SELECTION_CALLBACKS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.os.Build;
import android.support.design.widget.TabLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Tab;
import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.ListModelChangeProcessor;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel;

/**
 * Observes {@link KeyboardAccessoryProperties} changes (like a newly available tab) and triggers
 * the {@link KeyboardAccessoryViewBinder} which will modify the view accordingly.
 */
class KeyboardAccessoryViewBinder {
    static class ActionViewHolder extends RecyclerView.ViewHolder {
        public ActionViewHolder(View actionView) {
            super(actionView);
        }

        public static ActionViewHolder create(ViewGroup parent, @AccessoryAction int viewType) {
            switch (viewType) {
                case AccessoryAction.GENERATE_PASSWORD_AUTOMATIC:
                    return new ActionViewHolder(
                            LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.keyboard_accessory_action, parent, false));
                case AccessoryAction.AUTOFILL_SUGGESTION:
                    return new ActionViewHolder(
                            LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.keyboard_accessory_chip, parent, false));
                case AccessoryAction.MANAGE_PASSWORDS: // Intentional fallthrough.
                case AccessoryAction.COUNT:
                    assert false : "Type " + viewType + " is not a valid accessory bar action!";
            }
            assert false : "Action type " + viewType + " was not handled!";
            return null;
        }

        public void bind(Action action) {
            getView().setText(action.getCaption());
            getView().setOnClickListener(view -> action.getCallback().onResult(action));
        }

        private TextView getView() {
            return (TextView) super.itemView;
        }
    }

    static class TabViewBinder
            implements ListModelChangeProcessor.ViewBinder<ListModel<Tab>, KeyboardAccessoryView> {
        @Override
        public void onItemsInserted(
                ListModel<Tab> model, KeyboardAccessoryView view, int index, int count) {
            assert count > 0 : "Tried to insert invalid amount of tabs - must be at least one.";
            for (int i = index; i < index + count; i++) {
                Tab tab = model.get(i);
                view.addTabAt(i, tab.getIcon(), tab.getContentDescription());
            }
        }

        @Override
        public void onItemsRemoved(
                ListModel<Tab> model, KeyboardAccessoryView view, int index, int count) {
            assert count > 0 : "Tried to remove invalid amount of tabs - must be at least one.";
            while (count-- > 0) {
                view.removeTabAt(index++);
            }
        }

        @Override
        public void onItemsChanged(
                ListModel<Tab> model, KeyboardAccessoryView view, int index, int count) {
            // TODO(fhorschig): Implement fine-grained, ranged changes should the need arise.
            updateAllTabs(view, model);
        }

        void updateAllTabs(KeyboardAccessoryView view, ListModel<Tab> model) {
            view.clearTabs();
            if (model.size() > 0) onItemsInserted(model, view, 0, model.size());
        }
    }

    public static void bind(
            PropertyModel model, KeyboardAccessoryView view, PropertyKey propertyKey) {
        if (propertyKey == ACTIONS) {
            view.setActionsAdapter(
                    KeyboardAccessoryCoordinator.createActionsAdapter(model.get(ACTIONS)));
        } else if (propertyKey == TABS) {
            KeyboardAccessoryCoordinator.createTabViewBinder(model, view)
                    .updateAllTabs(view, model.get(TABS));
        } else if (propertyKey == VISIBLE) {
            view.setActiveTabColor(model.get(ACTIVE_TAB));
            setActiveTabHint(model, view);
            view.setVisible(model.get(VISIBLE));
        } else if (propertyKey == ACTIVE_TAB) {
            view.setActiveTabColor(model.get(ACTIVE_TAB));
            setActiveTabHint(model, view);
        } else if (propertyKey == BOTTOM_OFFSET_PX) {
            view.setBottomOffset(model.get(BOTTOM_OFFSET_PX));
        } else if (propertyKey == TAB_SELECTION_CALLBACKS) {
            // Don't add null as listener. It's a valid state but an invalid argument.
            TabLayout.OnTabSelectedListener listener = model.get(TAB_SELECTION_CALLBACKS);
            if (listener == null) return;
            view.setTabSelectionAdapter(listener);
        } else {
            assert false : "Every possible property update needs to be handled!";
        }
        // Layout requests happen automatically since Kitkat and redundant requests cause warnings.
        if (view != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            view.post(() -> {
                ViewParent parent = view.getParent();
                if (parent != null) {
                    parent.requestLayout();
                }
            });
        }
    }

    private static void setActiveTabHint(PropertyModel model, KeyboardAccessoryView view) {
        int activeTab = -1;
        if (model.get(ACTIVE_TAB) != null) {
            activeTab = model.get(ACTIVE_TAB);
        }
        for (int i = 0; i < model.get(TABS).size(); ++i) {
            Tab tab = model.get(TABS).get(i);
            if (activeTab == i) {
                view.setTabDescription(i, R.string.keyboard_accessory_sheet_hide);
            } else {
                view.setTabDescription(i, tab.getContentDescription());
            }
        }
    }
}
