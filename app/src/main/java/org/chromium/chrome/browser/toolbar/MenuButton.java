// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedImageButton;

/**
 * The overflow menu button.
 */
class MenuButton extends FrameLayout {
    /** The {@link TintedImageButton} for the menu button. */
    private TintedImageButton mMenuTintedImageButton;

    /** The view for the update badge. */
    private View mUpdateBadgeView;

    public MenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMenuTintedImageButton = findViewById(R.id.menu_button);
        mUpdateBadgeView = findViewById(R.id.menu_badge);
    }

    /**
     * @param onTouchListener An {@link OnTouchListener} that is triggered when the menu button is
     *                        clicked.
     */
    void setTouchListener(OnTouchListener onTouchListener) {
        mMenuTintedImageButton.setOnTouchListener(onTouchListener);
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        mMenuTintedImageButton.setAccessibilityDelegate(delegate);
    }

    /**
     * @param visible Whether the update badge should be visible.
     */
    void setUpdateBadgeVisibility(boolean visible) {
        mUpdateBadgeView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * @return Whether the update badge is showing.
     */
    boolean isShowingAppMenuUpdateBadge() {
        return mUpdateBadgeView.getVisibility() == View.VISIBLE;
    }

    View getMenuButton() {
        return mMenuTintedImageButton;
    }

    /**
     * @param tintList The {@link ColorStateList} that will tint the menu button (the badge is not
     *                 tinted).
     */
    void setTint(ColorStateList tintList) {
        mMenuTintedImageButton.setTint(tintList);
    }
}
