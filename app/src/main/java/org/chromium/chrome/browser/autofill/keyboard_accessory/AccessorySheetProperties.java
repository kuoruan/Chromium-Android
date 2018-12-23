// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Tab;
import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.PropertyModel.ReadableObjectPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.WritableBooleanPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.WritableIntPropertyKey;

/**
 * This model holds all view state of the accessory sheet.
 * It is updated by the {@link AccessorySheetMediator} and emits notification on which observers
 * like the view binder react.
 */
class AccessorySheetProperties {
    public static final ReadableObjectPropertyKey<ListModel<Tab>> TABS =
            new ReadableObjectPropertyKey<>();
    public static final WritableIntPropertyKey ACTIVE_TAB_INDEX = new WritableIntPropertyKey();
    public static final WritableBooleanPropertyKey VISIBLE = new WritableBooleanPropertyKey();
    public static final WritableIntPropertyKey HEIGHT = new WritableIntPropertyKey();

    public static final int NO_ACTIVE_TAB = -1;

    private AccessorySheetProperties() {}
}