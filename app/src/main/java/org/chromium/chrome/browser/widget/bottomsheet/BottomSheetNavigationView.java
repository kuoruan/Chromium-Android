// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationView;

/**
 * An implementation of forked {@link BottomNavigationView} specifically for the Chrome Home
 * bottom navigation menu.
 */
public class BottomSheetNavigationView extends BottomNavigationView {
    public BottomSheetNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomSheetNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected BottomSheetNavigationMenuView getBottomNavigationMenuViewInstance(Context context) {
        return new BottomSheetNavigationMenuView(context);
    }

    @Override
    public BottomSheetNavigationMenuView getMenuView() {
        return (BottomSheetNavigationMenuView) super.getMenuView();
    }
}