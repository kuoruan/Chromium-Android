// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Tab;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryModel.PropertyKey;
import org.chromium.chrome.browser.modelutil.LazyViewBinderAdapter;
import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.ListModelChangeProcessor;

/**
 * Observes {@link KeyboardAccessoryModel} changes (like a newly available tab) and triggers the
 * {@link KeyboardAccessoryViewBinder} which will modify the view accordingly.
 */
class KeyboardAccessoryViewBinder
        implements LazyViewBinderAdapter.SimpleViewBinder<KeyboardAccessoryModel,
                KeyboardAccessoryView, PropertyKey> {
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
            while (count-- > 0) {
                Tab tab = model.get(index);
                view.addTabAt(index, tab.getIcon(), tab.getContentDescription());
                ++index;
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
            for (int i = 0; i < model.size(); ++i) {
                Tab tab = model.get(i);
                // Mutate tab icons so we can apply color filters.
                view.addTabAt(i, tab.getIcon().mutate(), tab.getContentDescription());
            }
        }
    }

    @Override
    public PropertyKey getVisibilityProperty() {
        return PropertyKey.VISIBLE;
    }

    @Override
    public boolean isVisible(KeyboardAccessoryModel model) {
        return model.isVisible();
    }

    @Override
    public void onInitialInflation(
            KeyboardAccessoryModel model, KeyboardAccessoryView inflatedView) {
        for (PropertyKey key : PropertyKey.ALL_PROPERTIES) {
            bind(model, inflatedView, key);
        }

        inflatedView.setActionsAdapter(KeyboardAccessoryCoordinator.createActionsAdapter(model));
        KeyboardAccessoryCoordinator.createTabViewBinder(model, inflatedView)
                .updateAllTabs(inflatedView, model.getTabList());
    }

    @Override
    public void bind(
            KeyboardAccessoryModel model, KeyboardAccessoryView view, PropertyKey propertyKey) {
        if (propertyKey == PropertyKey.VISIBLE) {
            view.setActiveTabColor(model.activeTab());
            setActiveTabHint(model, view);
            view.setVisible(model.isVisible());
            requestLayout(view);
            return;
        }
        if (propertyKey == PropertyKey.ACTIVE_TAB) {
            view.setActiveTabColor(model.activeTab());
            setActiveTabHint(model, view);
            requestLayout(view);
            return;
        }
        if (propertyKey == PropertyKey.BOTTOM_OFFSET) {
            view.setBottomOffset(model.bottomOffset());
            requestLayout(view);
            return;
        }
        if (propertyKey == PropertyKey.TAB_SELECTION_CALLBACKS) {
            // Don't add null as listener. It's a valid state but an invalid argument.
            if (model.getTabSelectionCallbacks() == null) return;
            view.setTabSelectionAdapter(model.getTabSelectionCallbacks());
            requestLayout(view);
            return;
        }
        assert false : "Every possible property update needs to be handled!";
    }

    private static void requestLayout(KeyboardAccessoryView view) {
         // Layout requests happen automatically since Kitkat and redundant requests cause warnings.
        if (view == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) return;
        view.post(() -> {
            ViewParent parent = view.getParent();
            if (parent != null) {
                parent.requestLayout();
            }
        });
    }

    private static void setActiveTabHint(KeyboardAccessoryModel model, KeyboardAccessoryView view) {
        int activeTab = -1;
        if (model.activeTab() != null) {
            activeTab = model.activeTab();
        }
        for (int i = 0; i < model.getTabList().size(); ++i) {
            Tab tab = model.getTabList().get(i);
            if (activeTab == i) {
                view.setTabDescription(i, R.string.keyboard_accessory_sheet_hide);
            } else {
                view.setTabDescription(i, tab.getContentDescription());
            }
        }
    }
}
