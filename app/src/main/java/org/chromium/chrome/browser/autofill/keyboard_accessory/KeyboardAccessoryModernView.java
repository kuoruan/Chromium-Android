// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.content.Context;
import android.support.v7.content.res.AppCompatResources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.chromium.chrome.R;

/**
 * The Accessory sitting above the keyboard and below the content area. It is used for autofill
 * suggestions and manual entry points assisting the user in filling forms.
 */
class KeyboardAccessoryModernView extends KeyboardAccessoryView {
    private View mTabsItemsDivider;
    private View mTabsItemsSpacer;
    private ImageView mKeyboardToggle;

    /**
     * Constructor for inflating from XML.
     */
    public KeyboardAccessoryModernView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabsItemsDivider = findViewById(R.id.tabs_items_divider);
        mTabsItemsSpacer = findViewById(R.id.tabs_items_spacer);
        mKeyboardToggle = findViewById(R.id.show_keyboard);
        mKeyboardToggle.setImageDrawable(
                AppCompatResources.getDrawable(getContext(), R.drawable.ic_keyboard));
    }

    void setKeyboardToggleVisibility(boolean hasActiveTab) {
        mKeyboardToggle.setVisibility(hasActiveTab ? VISIBLE : GONE);
        mTabsItemsSpacer.setVisibility(hasActiveTab ? VISIBLE : GONE);
        mTabsItemsDivider.setVisibility(hasActiveTab ? GONE : VISIBLE);
        mBarItemsView.setVisibility(hasActiveTab ? GONE : VISIBLE);
    }

    void setShowKeyboardCallback(Runnable showKeyboardCallback) {
        mKeyboardToggle.setOnClickListener(
                showKeyboardCallback == null ? null : view -> showKeyboardCallback.run());
    }
}
