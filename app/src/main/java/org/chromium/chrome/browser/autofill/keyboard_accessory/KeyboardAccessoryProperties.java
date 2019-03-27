// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.PropertyModel.ReadableObjectPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableBooleanPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableIntPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableObjectPropertyKey;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * As model of the keyboard accessory component, this class holds the data relevant to the visual
 * state of the accessory.
 * This includes the visibility of the accessory, its relative position and actions. Whenever the
 * state changes, it notifies its listeners - like the {@link KeyboardAccessoryMediator} or a
 * ModelChangeProcessor.
 */
class KeyboardAccessoryProperties {
    static final ReadableObjectPropertyKey<ListModel<BarItem>> BAR_ITEMS =
            new ReadableObjectPropertyKey<>("bar_items");
    static final WritableBooleanPropertyKey VISIBLE = new WritableBooleanPropertyKey("visible");
    static final WritableIntPropertyKey BOTTOM_OFFSET_PX = new WritableIntPropertyKey("offset");
    static final WritableBooleanPropertyKey KEYBOARD_TOGGLE_VISIBLE =
            new WritableBooleanPropertyKey("toggle_visible");
    static final WritableObjectPropertyKey<Runnable> SHOW_KEYBOARD_CALLBACK =
            new WritableObjectPropertyKey<>("keyboard_callback");

    /**
     * This class wraps data used in ViewHolders of the accessory bar's {@link RecyclerView}.
     * It can hold an {@link Action}s that defines a callback and a recording type.
     */
    public static class BarItem {
        /**
         * This type is used to infer which type of view will represent this item.
         */
        @IntDef({Type.ACTION_BUTTON, Type.SUGGESTION, Type.TAB_SWITCHER, Type.COUNT})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
            int ACTION_BUTTON = 0;
            int SUGGESTION = 1;
            int TAB_SWITCHER = 2;
            int COUNT = 3;
        }
        private @Type int mType;
        private final @Nullable Action mAction;

        /**
         * Creates a new item. An item must have a type and can have an action.
         * @param type A {@link Type}.
         * @param action An {@link Action}.
         */
        public BarItem(@Type int type, @Nullable Action action) {
            mType = type;
            mAction = action;
        }

        /**
         * Returns the which type of view represents this item.
         * @return A {@link Type}.
         */
        public @Type int getViewType() {
            return mType;
        }

        /**
         * If applicable, returns which action is held by this item.
         * @return An {@link Action}.
         */
        public @Nullable Action getAction() {
            return mAction;
        }

        @Override
        public String toString() {
            String typeName = "BarItem(" + mType + ")"; // Fallback. We shouldn't crash.
            switch (mType) {
                case Type.ACTION_BUTTON:
                    typeName = "ACTION_BUTTON";
                    break;
                case Type.SUGGESTION:
                    typeName = "SUGGESTION";
                    break;
                case Type.TAB_SWITCHER:
                    typeName = "TAB_SWITCHER";
                    break;
            }
            return typeName + ": " + mAction;
        }
    }

    private KeyboardAccessoryProperties() {}
}
