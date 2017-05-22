// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.BottomSheet;

/**
 * The new tab page to display when Chrome Home is enabled.
 */
public class ChromeHomeNewTabPage implements NativePage, TemplateUrlServiceObserver {
    private final Tab mTab;
    private final TabModelObserver mTabModelObserver;
    private final TabModelSelector mTabModelSelector;
    private final LogoView.Delegate mLogoDelegate;

    private final View mView;
    private final LogoView mLogoView;

    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;

    /**
     * Constructs a ChromeHomeNewTabPage.
     * @param context The context used to inflate the view.
     * @param tab The Tab that is showing this new tab page.
     * @param tabModelSelector The TabModelSelector used to open tabs.
     */
    public ChromeHomeNewTabPage(
            final Context context, final Tab tab, final TabModelSelector tabModelSelector) {
        mTab = tab;
        mTabModelSelector = tabModelSelector;
        mView = LayoutInflater.from(context).inflate(R.layout.chrome_home_new_tab_page, null);

        Resources res = context.getResources();
        mTitle = res.getString(R.string.button_new_tab);
        mBackgroundColor = ApiCompatibilityUtils.getColor(res, R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(res, R.color.default_primary_color);

        TemplateUrlService.getInstance().addObserver(this);

        mLogoView = (LogoView) mView.findViewById(R.id.search_provider_logo);
        mLogoDelegate = new LogoDelegateImpl(tab, mLogoView);
        mLogoDelegate.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;
                mLogoView.setDelegate(mLogoDelegate);
                mLogoView.updateLogo(logo);
            }
        });
        updateSearchProviderLogoVisibility();

        final ChromeActivity activity = tab.getActivity();

        // TODO(twellington): remove this after only one NTP may be open at a time and NTP is
        //                    destroyed after user navigates to a different tab.
        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                boolean isNewTabPage = isTabChromeHomeNewTabPage(tab);
                activity.getFadingBackgroundView().setEnabled(!isNewTabPage);
                if (isNewTabPage) onNewTabPageShown();
            }
        };
        mTabModelSelector.getModel(false).addObserver(mTabModelObserver);

        View closeButton = mView.findViewById(R.id.close_button);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
                mTabModelSelector.closeTab(tab);
                // TODO(twellington): show overview mode.
            }
        });

        onNewTabPageShown();

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
        mTab.getActivity().getFadingBackgroundView().setEnabled(
                !isTabChromeHomeNewTabPage(mTabModelSelector.getCurrentTab()));

        mTabModelSelector.getModel(false).removeObserver(mTabModelObserver);
    }

    private void updateSearchProviderLogoVisibility() {
        boolean hasLogo = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
        mLogoView.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
    }

    private void onNewTabPageShown() {
        mTab.getActivity().getFadingBackgroundView().setEnabled(false);

        // This method may be called when an NTP is selected due to the user switching tab models.
        // In this case, we do not want the bottom sheet to open. Unfortunately, without observing
        // OverviewModeBehavior, we have no good signal to show the BottomSheet when an NTP is
        // selected in the tab switcher. Eventually this won't matter because we will not allow
        // NTPs to remain open after the user leaves them.
        if (mTab.getActivity().isInOverviewMode()) return;

        mTab.getActivity().getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_HALF, true);
    }

    private boolean isTabChromeHomeNewTabPage(Tab tab) {
        return tab != null && tab.getUrl().equals(getUrl()) && !tab.isIncognito();
    }

    // TemplateUrlServiceObserver overrides

    @Override
    public void onTemplateURLServiceChanged() {
        updateSearchProviderLogoVisibility();
    }
}
