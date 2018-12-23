// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.toolbar.ThemeColorProvider.ThemeColorObserver;
import org.chromium.chrome.browser.util.ColorUtils;

/**
 * The search accelerator.
 */
class SearchAccelerator extends AppCompatImageButton implements ThemeColorObserver {
    /** A provider that notifies components when the theme color changes.*/
    private ThemeColorProvider mThemeColorProvider;

    /** The gray pill background behind the search icon. */
    private final Drawable mBackground;

    /** The {@link Resources} used to compute the background color. */
    private final Resources mResources;

    public SearchAccelerator(Context context, AttributeSet attrs) {
        super(context, attrs);

        mResources = context.getResources();

        mBackground = ApiCompatibilityUtils.getDrawable(mResources, R.drawable.ntp_search_box);
        mBackground.mutate();
        setBackground(mBackground);
    }

    void setThemeColorProvider(ThemeColorProvider themeColorProvider) {
        mThemeColorProvider = themeColorProvider;
        mThemeColorProvider.addObserver(this);
    }

    void destroy() {
        if (mThemeColorProvider != null) {
            mThemeColorProvider.removeObserver(this);
            mThemeColorProvider = null;
        }
    }

    @Override
    public void onThemeColorChanged(ColorStateList tint, int primaryColor) {
        ApiCompatibilityUtils.setImageTintList(this, tint);
        mBackground.setColorFilter(
                ColorUtils.getTextBoxColorForToolbarBackground(mResources, false, primaryColor),
                PorterDuff.Mode.SRC_IN);
    }
}
