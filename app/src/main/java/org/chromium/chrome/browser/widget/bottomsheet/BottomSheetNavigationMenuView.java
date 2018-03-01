// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationItemView;
import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationMenuView;

/**
 * An implementation of {@link BottomNavigationMenuView} specifically for the Chrome Home bottom
 * navigation menu.
 *
 * This just overrides #getNewItemViewInstance so we add {@link BottomSheetNavigationItemView}s to
 * the menu instead of the defaults.
 */
public class BottomSheetNavigationMenuView extends BottomNavigationMenuView {
    public BottomSheetNavigationMenuView(Context context) {
        super(context);
    }

    public BottomSheetNavigationMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected BottomNavigationItemView getNewItemViewInstance() {
        return new BottomSheetNavigationItemView(getContext());
    }
}