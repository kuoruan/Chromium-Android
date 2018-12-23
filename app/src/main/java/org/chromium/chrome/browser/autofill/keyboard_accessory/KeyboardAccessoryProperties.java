// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.support.design.widget.TabLayout;

import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.PropertyModel.ReadableObjectPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.WritableBooleanPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.WritableIntPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.WritableObjectPropertyKey;

/**
 * As model of the keyboard accessory component, this class holds the data relevant to the visual
 * state of the accessory.
 * This includes the visibility of the accessory in general, any available tabs and actions.
 * Whenever the state changes, it notifies its listeners - like the
 * {@link KeyboardAccessoryMediator} or the ModelChangeProcessor.
 */
class KeyboardAccessoryProperties {
    static final ReadableObjectPropertyKey<ListModel<KeyboardAccessoryData.Action>> ACTIONS =
            new ReadableObjectPropertyKey<>();
    static final ReadableObjectPropertyKey<ListModel<KeyboardAccessoryData.Tab>> TABS =
            new ReadableObjectPropertyKey<>();
    static final WritableBooleanPropertyKey VISIBLE = new WritableBooleanPropertyKey();
    static final WritableIntPropertyKey BOTTOM_OFFSET_PX = new WritableIntPropertyKey();
    static final /* @Nullable */ WritableObjectPropertyKey<Integer> ACTIVE_TAB =
            new WritableObjectPropertyKey<>();
    static final WritableObjectPropertyKey<TabLayout.OnTabSelectedListener>
            TAB_SELECTION_CALLBACKS = new WritableObjectPropertyKey<>();

    private KeyboardAccessoryProperties() {}
}
