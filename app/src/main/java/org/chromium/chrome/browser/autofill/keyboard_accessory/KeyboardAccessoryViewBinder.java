// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BAR_ITEMS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.KEYBOARD_TOGGLE_VISIBLE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.SHOW_KEYBOARD_CALLBACK;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BarItem;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * Observes {@link KeyboardAccessoryProperties} changes (like a newly available tab) and triggers
 * the {@link KeyboardAccessoryViewBinder} which will modify the view accordingly.
 */
class KeyboardAccessoryViewBinder {
    public static BarItemViewHolder create(ViewGroup parent, @BarItem.Type int viewType) {
        switch (viewType) {
            case BarItem.Type.ACTION_BUTTON:
                return new BarItemTextViewHolder(parent, R.layout.keyboard_accessory_action);
            case BarItem.Type.SUGGESTION:
                return new BarItemTextViewHolder(parent, R.layout.keyboard_accessory_chip);
            case BarItem.Type.TAB_SWITCHER: // Intentional fallthrough. Not supported.
            case BarItem.Type.COUNT:
                assert false : "Type " + viewType + " is not a valid accessory bar action!";
        }
        assert false : "Action type " + viewType + " was not handled!";
        return null;
    }

    static abstract class BarItemViewHolder<T extends BarItem, V extends View>
            extends RecyclerView.ViewHolder {
        BarItemViewHolder(ViewGroup parent, @LayoutRes int layout) {
            super(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @SuppressWarnings("unchecked")
        void bind(BarItem barItem) {
            bind((T) barItem, (V) itemView);
        }

        /**
         * Called when the ViewHolder is bound.
         * @param item The {@link BarItem} that this ViewHolder represents.
         * @param item The {@link View} that this ViewHolder binds the bar item to.
         */
        protected abstract void bind(T item, V view);

        /**
         * The opposite of {@link #bind}. Use this to free expensive resources or reset observers.
         */
        protected void recycle() {}
    }

    static class BarItemTextViewHolder extends BarItemViewHolder<BarItem, TextView> {
        BarItemTextViewHolder(ViewGroup parent, @LayoutRes int layout) {
            super(parent, layout);
        }

        @Override
        public void bind(BarItem barItem, TextView textView) {
            KeyboardAccessoryData.Action action = barItem.getAction();
            assert action != null : "";
            textView.setText(action.getCaption());
            textView.setOnClickListener(view -> action.getCallback().onResult(action));
        }
    }

    public static void bind(
            PropertyModel model, KeyboardAccessoryView view, PropertyKey propertyKey) {
        boolean wasBound = bindInternal(model, view, propertyKey);
        assert wasBound : "Every possible property update needs to be handled!";
        requestLayoutPreKitkat(view);
    }

    /**
     * Tries to bind the given property to the given view by using the value in the given model.
     * @param model       A {@link PropertyModel}.
     * @param view        A {@link KeyboardAccessoryView}.
     * @param propertyKey A {@link PropertyKey}.
     * @return True if the given propertyKey was bound to the given view.
     */
    protected static boolean bindInternal(
            PropertyModel model, KeyboardAccessoryView view, PropertyKey propertyKey) {
        if (propertyKey == BAR_ITEMS) {
            view.setBarItemsAdapter(
                    KeyboardAccessoryCoordinator.createBarItemsAdapter(model.get(BAR_ITEMS)));
        } else if (propertyKey == VISIBLE) {
            view.setVisible(model.get(VISIBLE));
        } else if (propertyKey == BOTTOM_OFFSET_PX) {
            view.setBottomOffset(model.get(BOTTOM_OFFSET_PX));
        } else if (propertyKey == SHOW_KEYBOARD_CALLBACK) {
            // No binding required.
        } else if (propertyKey == KEYBOARD_TOGGLE_VISIBLE) {
            // No binding required.
        } else {
            return false;
        }
        return true;
    }

    protected static void requestLayoutPreKitkat(View view) {
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
}
