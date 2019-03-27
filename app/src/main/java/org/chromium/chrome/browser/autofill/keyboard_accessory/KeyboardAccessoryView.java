// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.ui.base.LocalizationUtils.isLayoutRtl;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * The Accessory sitting above the keyboard and below the content area. It is used for autofill
 * suggestions and manual entry points assisting the user in filling forms.
 */
class KeyboardAccessoryView extends LinearLayout {
    protected RecyclerView mBarItemsView;
    private KeyboardAccessoryTabLayoutView mTabLayout;

    private static class HorizontalDividerItemDecoration extends RecyclerView.ItemDecoration {
        private final int mHorizontalMargin;
        HorizontalDividerItemDecoration(int horizontalMargin) {
            this.mHorizontalMargin = horizontalMargin;
        }
        @Override
        public void getItemOffsets(
                Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.right = mHorizontalMargin;
        }
    }

    /**
     * Constructor for inflating from XML.
     */
    public KeyboardAccessoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);

        mBarItemsView = findViewById(R.id.bar_items_view);
        initializeHorizontalRecyclerView(mBarItemsView);

        mTabLayout = findViewById(R.id.tabs);

        // Apply RTL layout changes to the view's children:
        ApiCompatibilityUtils.setLayoutDirection(findViewById(R.id.accessory_bar_contents),
                isLayoutRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        ApiCompatibilityUtils.setLayoutDirection(mBarItemsView,
                isLayoutRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        // Set listener's to touch/click events so they are not propagated to the page below.
        setOnTouchListener((view, motionEvent) -> {
            performClick(); // Setting a touch listener requires this call which is a NoOp.
            return true; // Return that the motionEvent was consumed and needs no further handling.
        });
        setOnClickListener(view -> {});
        setClickable(false); // Disables the "Double-tap to activate" Talkback reading.
        setSoundEffectsEnabled(false);
    }

    KeyboardAccessoryTabLayoutView getTabLayout() {
        return mTabLayout;
    }

    void setVisible(boolean visible) {
        if (visible) {
            show();
        } else {
            hide();
        }
    }

    public void setBottomOffset(int bottomOffset) {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, bottomOffset);
        setLayoutParams(params);
    }

    void setBarItemsAdapter(RecyclerView.Adapter adapter) {
        mBarItemsView.setAdapter(adapter);
    }

    private void show() {
        bringToFront(); // Needs to overlay every component and the bottom sheet - like a keyboard.
        setVisibility(View.VISIBLE);
        announceForAccessibility(getContentDescription());
    }

    private void hide() {
        setVisibility(View.GONE);
    }

    private void initializeHorizontalRecyclerView(RecyclerView recyclerView) {
        // Set horizontal layout.
        recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        int pad = getResources().getDimensionPixelSize(R.dimen.keyboard_accessory_padding);
        // Create margins between every element.
        recyclerView.addItemDecoration(new HorizontalDividerItemDecoration(pad));

        // Remove all animations - the accessory shouldn't be visibly built anyway.
        recyclerView.setItemAnimator(null);

        recyclerView.setPadding(isLayoutRtl() ? 0 : pad, 0, isLayoutRtl() ? pad : 0, 0);
    }
}
