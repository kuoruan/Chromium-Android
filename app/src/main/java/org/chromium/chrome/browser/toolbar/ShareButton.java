// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ActivityTabProvider.HintlessActivityTabObserver;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.toolbar.ThemeColorProvider.ThemeColorObserver;

/**
 * The share button.
 */
class ShareButton extends AppCompatImageButton implements ThemeColorObserver {
    /** A provider that notifies components when the theme color changes.*/
    private ThemeColorProvider mThemeColorProvider;

    /** The {@link HintlessActivityTabObserver} used to know when the activity tab changed. */
    private HintlessActivityTabObserver mHintlessActivityTabObserver;

    /** The {@link ActivityTabProvider} used to know when the activity tab changed. */
    private ActivityTabProvider mActivityTabProvider;

    /** A {@link TabModelSelector} used to know when a new page has loaded. */
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;

    public ShareButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setThemeColorProvider(ThemeColorProvider themeStateProvider) {
        mThemeColorProvider = themeStateProvider;
        mThemeColorProvider.addObserver(this);
    }

    void setActivityTabProvider(ActivityTabProvider activityTabProvider) {
        mActivityTabProvider = activityTabProvider;
        mHintlessActivityTabObserver = new HintlessActivityTabObserver() {
            @Override
            public void onActivityTabChanged(Tab tab) {
                if (tab == null) return;
                setEnabled(shouldEnableShare(tab));
            }
        };
        mActivityTabProvider.addObserverAndTrigger(mHintlessActivityTabObserver);
    }

    void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(tabModelSelector) {
            @Override
            public void onPageLoadFinished(Tab tab) {
                if (tab == null) return;
                setEnabled(shouldEnableShare(tab));
            }
        };
    }

    void destroy() {
        if (mThemeColorProvider != null) {
            mThemeColorProvider.removeObserver(this);
            mThemeColorProvider = null;
        }
        if (mTabModelSelectorTabObserver != null) {
            mTabModelSelectorTabObserver.destroy();
            mTabModelSelectorTabObserver = null;
        }
        if (mActivityTabProvider != null) {
            mActivityTabProvider.removeObserver(mHintlessActivityTabObserver);
            mActivityTabProvider = null;
        }
    }

    private static boolean shouldEnableShare(Tab tab) {
        final String url = tab.getUrl();
        final boolean isChromeScheme = url.startsWith(UrlConstants.CHROME_URL_PREFIX)
                || url.startsWith(UrlConstants.CHROME_NATIVE_URL_PREFIX);
        return !isChromeScheme && !tab.isShowingInterstitialPage();
    }

    @Override
    public void onThemeColorChanged(ColorStateList tint, int primaryColor) {
        ApiCompatibilityUtils.setImageTintList(this, tint);
    }
}
