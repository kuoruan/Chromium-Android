// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetMetrics;

/**
 * The new tab page to display when Chrome Home is enabled.
 */
public class ChromeHomeNewTabPage implements NativePage, TemplateUrlServiceObserver {
    private final Tab mTab;
    private final TabObserver mTabObserver;
    private final TabModelSelector mTabModelSelector;
    private final LogoView.Delegate mLogoDelegate;
    private final OverviewModeObserver mOverviewModeObserver;
    @Nullable
    private final LayoutManagerChrome mLayoutManager;
    private final BottomSheet mBottomSheet;

    private final View mView;
    private final LogoView mLogoView;
    private final TintedImageButton mCloseButton;
    private final View mFadingBackgroundView;

    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;

    private boolean mShowOverviewOnClose;

    /**
     * Constructs a ChromeHomeNewTabPage.
     * @param context The context used to inflate the view.
     * @param tab The Tab that is showing this new tab page.
     * @param tabModelSelector The {@link TabModelSelector} used to open tabs.
     * @param layoutManager The {@link LayoutManagerChrome} used to observe overview mode changes.
     *                      This may be null if the NTP is created on startup due to
     *                      PartnerBrowserCustomizations.
     */
    public ChromeHomeNewTabPage(final Context context, final Tab tab,
            final TabModelSelector tabModelSelector,
            @Nullable final LayoutManagerChrome layoutManager) {
        mTab = tab;
        mTabModelSelector = tabModelSelector;
        mLayoutManager = layoutManager;
        mFadingBackgroundView = mTab.getActivity().getFadingBackgroundView();
        mBottomSheet = mTab.getActivity().getBottomSheet();

        mView = LayoutInflater.from(context).inflate(R.layout.chrome_home_new_tab_page, null);
        mLogoView = (LogoView) mView.findViewById(R.id.search_provider_logo);
        mCloseButton = (TintedImageButton) mView.findViewById(R.id.close_button);

        Resources res = context.getResources();
        mTitle = res.getString(R.string.button_new_tab);
        mBackgroundColor = ApiCompatibilityUtils.getColor(res, R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(res, R.color.default_primary_color);

        // A new tab may be created on startup due to PartnerBrowserCustomizations before the
        // LayoutManagerChrome has been created (see ChromeTabbedActivity#initializeState()).
        if (mLayoutManager != null) {
            mShowOverviewOnClose = mLayoutManager.overviewVisible();

            // TODO(twellington): Long term we will not allow NTPs to remain open after the user
            // navigates away from them. Remove this observer after that happens.
            mOverviewModeObserver = new EmptyOverviewModeObserver() {
                @Override
                public void onOverviewModeFinishedHiding() {
                    mShowOverviewOnClose = mTabModelSelector.getCurrentTab() == mTab;
                }
            };
            mLayoutManager.addOverviewModeObserver(mOverviewModeObserver);
        } else {
            mOverviewModeObserver = null;
        }

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab) {
                onNewTabPageShown();
            }

            @Override
            public void onHidden(Tab tab) {
                mFadingBackgroundView.setEnabled(true);
                if (!mTab.isClosing()) mShowOverviewOnClose = false;
            }
        };
        mTab.addObserver(mTabObserver);

        mLogoDelegate = initializeLogoView();
        initializeCloseButton();

        // If the tab is already showing TabObserver#onShown() won't be called, so we need to call
        // #onNewTabPageShown() directly.
        boolean tabAlreadyShowing = mTabModelSelector.getCurrentTab() == mTab;
        if (tabAlreadyShowing) onNewTabPageShown();

        // TODO(twellington): disallow moving the NTP to the other window in Android N+
        //                    multi-window mode.
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getUrl() {
        return UrlConstants.NTP_URL;
    }

    @Override
    public String getHost() {
        return UrlConstants.NTP_HOST;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }

    @Override
    public boolean needsToolbarShadow() {
        return false;
    }

    @Override
    public void updateForUrl(String url) {}

    @Override
    public void destroy() {
        mLogoDelegate.destroy();

        // The next tab will be selected before this one is destroyed. If the currently selected
        // tab is a Chrome Home new tab page, the FadingBackgroundView should not be enabled.
        mFadingBackgroundView.setEnabled(
                !isTabChromeHomeNewTabPage(mTabModelSelector.getCurrentTab()));

        if (mLayoutManager != null) {
            mLayoutManager.removeOverviewModeObserver(mOverviewModeObserver);
        }
        mTab.removeObserver(mTabObserver);
    }

    private void updateSearchProviderLogoVisibility() {
        boolean hasLogo = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
        mLogoView.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
    }

    private void onNewTabPageShown() {
        mFadingBackgroundView.setEnabled(false);

        // This method may be called when an NTP is selected due to the user switching tab models.
        // In this case, we do not want the bottom sheet to open. Unfortunately, without observing
        // OverviewModeBehavior, we have no good signal to show the BottomSheet when an NTP is
        // selected in the tab switcher. Eventually this won't matter because we will not allow
        // NTPs to remain open after the user leaves them.
        if (getLayoutManager() != null && getLayoutManager().overviewVisible()) return;

        mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_HALF, true);
        mBottomSheet.getBottomSheetMetrics().recordSheetOpenReason(
                BottomSheetMetrics.OPENED_BY_NEW_TAB_CREATION);
    }

    private boolean isTabChromeHomeNewTabPage(Tab tab) {
        return tab != null && tab.getUrl().equals(getUrl()) && !tab.isIncognito();
    }

    private LogoView.Delegate initializeLogoView() {
        TemplateUrlService.getInstance().addObserver(this);

        final LogoView.Delegate logoDelegate = new LogoDelegateImpl(mTab, mLogoView);
        logoDelegate.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;
                mLogoView.setDelegate(logoDelegate);
                mLogoView.updateLogo(logo);
                // TODO(twellington): The new logo may be taller than the default logo. Adjust
                //                    the view positioning.
            }
        });
        updateSearchProviderLogoVisibility();
        return logoDelegate;
    }

    private void initializeCloseButton() {
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
                if (mShowOverviewOnClose && getLayoutManager() != null) {
                    getLayoutManager().showOverview(false);
                }

                // Close the tab after showing the overview mode so the bottom sheet doesn't open
                // if another NTP is selected when this one is closed.
                // TODO(twellington): remove this comment after only one NTP may be open at a time.
                mTabModelSelector.closeTab(mTab);
            }
        });
    }

    private LayoutManagerChrome getLayoutManager() {
        if (mLayoutManager != null) return mLayoutManager;

        return ((ChromeTabbedActivity) mTab.getActivity()).getLayoutManager();
    }

    // TemplateUrlServiceObserver overrides.

    @Override
    public void onTemplateURLServiceChanged() {
        updateSearchProviderLogoVisibility();
    }

    // Methods for testing.

    @VisibleForTesting
    public TintedImageButton getCloseButtonForTests() {
        return mCloseButton;
    }
}
