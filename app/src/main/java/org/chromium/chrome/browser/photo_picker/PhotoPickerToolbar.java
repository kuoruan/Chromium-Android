// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;

/**
 * Handles toolbar functionality for the Photo Picker class.
 */
public class PhotoPickerToolbar extends SelectableListToolbar<PickerBitmap> {
    public PhotoPickerToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflateMenu(R.menu.photo_picker_menu);
    }
}
