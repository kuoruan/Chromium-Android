// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.toolbar.ThemeColorProvider.ThemeColorObserver;
import org.chromium.chrome.browser.util.FeatureUtilities;

/**
 * The home button.
 */
class HomeButton extends AppCompatImageButton implements ThemeColorObserver {
    /** A provider that notifies components when the theme color changes.*/
    private ThemeColorProvider mThemeColorProvider;

    public HomeButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        final int homeButtonIcon = FeatureUtilities.isNewTabPageButtonEnabled()
                ? R.drawable.ic_home
                : R.drawable.btn_toolbar_home;
        setImageDrawable(ContextCompat.getDrawable(context, homeButtonIcon));
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
    }
}
