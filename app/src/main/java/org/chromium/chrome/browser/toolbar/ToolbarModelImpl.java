// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.dom_distiller.DomDistillerServiceFactory;
import org.chromium.chrome.browser.dom_distiller.DomDistillerTabUtils;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ToolbarModel.ToolbarModelDelegate;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.components.dom_distiller.core.DomDistillerService;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Contains the data and state for the toolbar.
 */
class ToolbarModelImpl extends ToolbarModel implements ToolbarDataProvider, ToolbarModelDelegate {
    private final BottomSheet mBottomSheet;
    private Tab mTab;
    private boolean mIsIncognito;
    private int mPrimaryColor;
    private boolean mIsUsingBrandColor;

    /**
     * Default constructor for this class.
     */
    public ToolbarModelImpl(@Nullable BottomSheet bottomSheet) {
        super();
        mBottomSheet = bottomSheet;
        mPrimaryColor = ApiCompatibilityUtils.getColor(
                ContextUtils.getApplicationContext().getResources(),
                R.color.default_primary_color);
    }

    /**
     * Handle any initialization that must occur after native has been initialized.
     */
    public void initializeWithNative() {
        initialize(this);
    }

    @Override
    public WebContents getActiveWebContents() {
        if (!hasTab()) return null;
        return mTab.getWebContents();
    }

    /**
     * Sets the tab that contains the information to be displayed in the toolbar.
     * @param tab The tab associated currently with the toolbar.
     * @param isIncognito Whether the incognito model is currently selected, which must match the
     *                    passed in tab if non-null.
     */
    public void setTab(Tab tab, boolean isIncognito) {
        assert tab == null || tab.isIncognito() == isIncognito;
        mTab = tab;
        mIsIncognito = isIncognito;
        updateUsingBrandColor();
    }

    @Override
    public Tab getTab() {
        return hasTab() ? mTab : null;
    }

    @Override
    public boolean hasTab() {
        // TODO(dtrainor, tedchoc): Remove the isInitialized() check when we no longer wait for
        // TAB_CLOSED events to remove this tab.  Otherwise there is a chance we use this tab after
        // {@link ChromeTab#destroy()} is called.
        return mTab != null && mTab.isInitialized();
    }

    @Override
    public String getCurrentUrl() {
        // TODO(yusufo) : Consider using this for all calls from getTab() for accessing url.
        if (!hasTab()) return "";
        // Tab.getUrl() returns empty string if it does not have a URL.
        return getTab().getUrl().trim();
    }

    @Override
    public NewTabPage getNewTabPageForCurrentTab() {
        if (hasTab() && mTab.getNativePage() instanceof NewTabPage) {
            return (NewTabPage) mTab.getNativePage();
        }
        return null;
    }

    @Override
    public String getText() {
        if (clearUrlForBottomSheetOpen()) return "";

        String displayText = super.getText();

        if (!hasTab() || mTab.isFrozen()) return displayText;

        String url = getCurrentUrl();
        if (DomDistillerUrlUtils.isDistilledPage(url)) {
            if (isStoredArticle(url)) {
                DomDistillerService domDistillerService =
                        DomDistillerServiceFactory.getForProfile(getProfile());
                String originalUrl = domDistillerService.getUrlForEntry(
                        DomDistillerUrlUtils.getValueForKeyInUrl(url, "entry_id"));
                displayText =
                        DomDistillerTabUtils.getFormattedUrlFromOriginalDistillerUrl(originalUrl);
            } else if (DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url) != null) {
                String originalUrl = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
                displayText =
                        DomDistillerTabUtils.getFormattedUrlFromOriginalDistillerUrl(originalUrl);
            }
        } else if (isOfflinePage()) {
            String originalUrl = mTab.getOriginalUrl();
            displayText = OfflinePageUtils.stripSchemeFromOnlineUrl(
                  DomDistillerTabUtils.getFormattedUrlFromOriginalDistillerUrl(originalUrl));
        }

        return displayText;
    }

    private boolean isStoredArticle(String url) {
        DomDistillerService domDistillerService =
                DomDistillerServiceFactory.getForProfile(getProfile());
        String entryIdFromUrl = DomDistillerUrlUtils.getValueForKeyInUrl(url, "entry_id");
        if (TextUtils.isEmpty(entryIdFromUrl)) return false;
        return domDistillerService.hasEntry(entryIdFromUrl);
    }

    @Override
    public boolean isIncognito() {
        return mIsIncognito;
    }

    @Override
    public Profile getProfile() {
        Profile lastUsedProfile = Profile.getLastUsedProfile();
        if (mIsIncognito) {
            assert lastUsedProfile.hasOffTheRecordProfile();
            return lastUsedProfile.getOffTheRecordProfile();
        }
        return lastUsedProfile.getOriginalProfile();
    }

    /**
     * Sets the primary color and changes the state for isUsingBrandColor.
     * @param color The primary color for the current tab.
     */
    public void setPrimaryColor(int color) {
        mPrimaryColor = color;
        updateUsingBrandColor();
    }

    private void updateUsingBrandColor() {
        Context context = ContextUtils.getApplicationContext();
        mIsUsingBrandColor = !isIncognito()
                && mPrimaryColor
                        != ApiCompatibilityUtils.getColor(
                                   context.getResources(), R.color.default_primary_color)
                && hasTab() && !mTab.isNativePage();
    }

    @Override
    public int getPrimaryColor() {
        if (mBottomSheet != null) {
            Resources res = ContextUtils.getApplicationContext().getResources();
            return ColorUtils.getDefaultThemeColor(res, true, isIncognito());
        }
        return mPrimaryColor;
    }

    @Override
    public boolean isUsingBrandColor() {
        return mIsUsingBrandColor && mBottomSheet == null;
    }

    @Override
    public boolean isOfflinePage() {
        return hasTab() && OfflinePageUtils.isOfflinePage(mTab);
    }

    @Override
    public boolean shouldShowGoogleG(String urlBarText) {
        LocaleManager localeManager = LocaleManager.getInstance();
        if (localeManager.hasCompletedSearchEnginePromo()
                || localeManager.hasShownSearchEnginePromoThisSession()) {
            return false;
        }

        // Only access ChromeFeatureList and TemplateUrlService after the NTP check,
        // to prevent native method calls before the native side has been initialized.
        NewTabPage ntp = getNewTabPageForCurrentTab();
        boolean isShownInRegularNtp = ntp != null && ntp.isLocationBarShownInNTP()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_SHOW_GOOGLE_G_IN_OMNIBOX);

        boolean isShownInBottomSheet = mBottomSheet != null && !mBottomSheet.isShowingNewTab()
                && mBottomSheet.isSheetOpen() && TextUtils.isEmpty(urlBarText)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_CLEAR_URL_ON_OPEN)
                && ChromeFeatureList.isEnabled(
                           ChromeFeatureList.CHROME_HOME_SHOW_GOOGLE_G_WHEN_URL_CLEARED);

        return (isShownInRegularNtp || isShownInBottomSheet)
                && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
    }

    @Override
    public boolean shouldShowSecurityIcon() {
        return !clearUrlForBottomSheetOpen() && getSecurityIconResource() != 0;
    }

    @Override
    public boolean shouldShowVerboseStatus() {
        // Because is offline page is cleared a bit slower, we also ensure that connection security
        // level is NONE or HTTP_SHOW_WARNING (http://crbug.com/671453).
        int securityLevel = getSecurityLevel();
        return !clearUrlForBottomSheetOpen() && isOfflinePage()
                && (securityLevel == ConnectionSecurityLevel.NONE
                           || securityLevel == ConnectionSecurityLevel.HTTP_SHOW_WARNING);
    }

    @Override
    public int getSecurityLevel() {
        return getSecurityLevel(getTab(), isOfflinePage());
    }

    @Override
    public int getSecurityIconResource() {
        return getSecurityIconResource(
                getSecurityLevel(), !DeviceFormFactor.isTablet(), isOfflinePage());
    }

    @VisibleForTesting
    @ConnectionSecurityLevel
    static int getSecurityLevel(Tab tab, boolean isOfflinePage) {
        if (tab == null || isOfflinePage) {
            return ConnectionSecurityLevel.NONE;
        }
        return tab.getSecurityLevel();
    }

    @VisibleForTesting
    @DrawableRes
    static int getSecurityIconResource(
            int securityLevel, boolean isSmallDevice, boolean isOfflinePage) {
        if (isOfflinePage) {
            return R.drawable.offline_pin_round;
        }

        switch (securityLevel) {
            case ConnectionSecurityLevel.NONE:
                return isSmallDevice ? 0 : R.drawable.omnibox_info;
            case ConnectionSecurityLevel.HTTP_SHOW_WARNING:
                return R.drawable.omnibox_info;
            case ConnectionSecurityLevel.DANGEROUS:
                return R.drawable.omnibox_https_invalid;
            case ConnectionSecurityLevel.SECURE_WITH_POLICY_INSTALLED_CERT:
            case ConnectionSecurityLevel.SECURE:
            case ConnectionSecurityLevel.EV_SECURE:
                return R.drawable.omnibox_https_valid;
            default:
                assert false;
        }
        return 0;
    }

    private boolean clearUrlForBottomSheetOpen() {
        return mBottomSheet != null && mBottomSheet.isSheetOpen()
                && mBottomSheet.getTargetSheetState() != BottomSheet.SHEET_STATE_PEEK
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_CLEAR_URL_ON_OPEN);
    }
}
