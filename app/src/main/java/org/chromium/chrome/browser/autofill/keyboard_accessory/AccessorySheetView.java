// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Displays the data provided by the {@link AccessorySheetViewBinder}.
 */
class AccessorySheetView extends FrameLayout {
    private ViewPager mViewPager;
    private ImageView mTopShadow;

    /**
     * Constructor for inflating from XML.
     */
    public AccessorySheetView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mViewPager = findViewById(org.chromium.chrome.R.id.keyboard_accessory_sheet);
        mTopShadow = findViewById(org.chromium.chrome.R.id.accessory_sheet_shadow);
    }

    void setAdapter(PagerAdapter adapter) {
        mViewPager.setAdapter(adapter);
    }

    void addOnPageChangeListener(ViewPager.OnPageChangeListener pageChangeListener) {
        mViewPager.addOnPageChangeListener(pageChangeListener);
    }

    void setCurrentItem(int index) {
        mViewPager.setCurrentItem(index);
    }

    ViewPager getViewPager() {
        return mViewPager;
    }

    void setTopShadowVisible(boolean isShadowVisible) {
        mTopShadow.setVisibility(isShadowVisible ? View.VISIBLE : View.INVISIBLE);
    }
}
