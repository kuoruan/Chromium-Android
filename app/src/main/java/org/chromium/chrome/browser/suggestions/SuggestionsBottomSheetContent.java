// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;

import org.chromium.base.CollectionUtil;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.TouchEnabledDelegate;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.ntp.LogoDelegateImpl;
import org.chromium.chrome.browser.ntp.LogoView;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetNewTabController;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

import java.util.List;

/**
 * Provides content to be displayed inside of the Home tab of bottom sheet.
 */
public class SuggestionsBottomSheetContent implements BottomSheet.BottomSheetContent,
                                                      BottomSheetNewTabController.Observer,
                                                      TemplateUrlServiceObserver {
    private final View mView;
    private final SuggestionsRecyclerView mRecyclerView;
    private final ContextMenuManager mContextMenuManager;
    private final SuggestionsUiDelegateImpl mSuggestionsUiDelegate;
    private final TileGroup.Delegate mTileGroupDelegate;
    @Nullable
    private final SuggestionsCarousel mSuggestionsCarousel;
    private final SuggestionsSheetVisibilityChangeObserver mBottomSheetObserver;
    private final ChromeActivity mActivity;
    private final BottomSheet mSheet;
    private final LogoView mLogoView;
    private final LogoDelegateImpl mLogoDelegate;
    private final ViewGroup mControlContainerView;
    private final View mToolbarPullHandle;
    private final View mToolbarShadow;

    private boolean mNewTabShown;
    private boolean mSearchProviderHasLogo = true;
    private float mLastSheetHeightFraction = 1f;

    /**
     * Whether {@code mView} is currently attached to the window. This is used in place of
     * {@link View#isAttachedToWindow()} to support older versions of Android (KitKat & JellyBean).
     */
    private boolean mIsAttachedToWindow;

    public SuggestionsBottomSheetContent(final ChromeActivity activity, final BottomSheet sheet,
            TabModelSelector tabModelSelector, SnackbarManager snackbarManager) {
        SuggestionsDependencyFactory depsFactory = SuggestionsDependencyFactory.getInstance();
        Profile profile = Profile.getLastUsedProfile();
        SuggestionsNavigationDelegate navigationDelegate =
                new SuggestionsNavigationDelegateImpl(activity, profile, sheet, tabModelSelector);
        mActivity = activity;
        mSheet = sheet;
        mTileGroupDelegate =
                new TileGroupDelegateImpl(activity, profile, navigationDelegate, snackbarManager);
        mSuggestionsUiDelegate = new SuggestionsUiDelegateImpl(
                depsFactory.createSuggestionSource(profile), depsFactory.createEventReporter(),
                navigationDelegate, profile, sheet, activity.getReferencePool(), snackbarManager);

        mView = LayoutInflater.from(activity).inflate(
                R.layout.suggestions_bottom_sheet_content, null);
        Resources resources = mView.getResources();
        int backgroundColor = SuggestionsConfig.getBackgroundColor(resources);
        mView.setBackgroundColor(backgroundColor);
        mRecyclerView = mView.findViewById(R.id.recycler_view);
        mRecyclerView.setBackgroundColor(backgroundColor);

        TouchEnabledDelegate touchEnabledDelegate = activity.getBottomSheet()::setTouchEnabled;
        mContextMenuManager =
                new ContextMenuManager(activity, navigationDelegate, touchEnabledDelegate);
        activity.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        mSuggestionsUiDelegate.addDestructionObserver(() -> {
            activity.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
        });

        UiConfig uiConfig = new UiConfig(mRecyclerView);
        mRecyclerView.init(uiConfig, mContextMenuManager);

        OfflinePageBridge offlinePageBridge = OfflinePageBridge.getForProfile(profile);

        mSuggestionsCarousel =
                ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_CAROUSEL)
                ? new SuggestionsCarousel(
                          uiConfig, mSuggestionsUiDelegate, mContextMenuManager, offlinePageBridge)
                : null;

        final NewTabPageAdapter adapter = new NewTabPageAdapter(mSuggestionsUiDelegate,
                /* aboveTheFoldView = */ null, uiConfig, offlinePageBridge, mContextMenuManager,
                mTileGroupDelegate, mSuggestionsCarousel);

        mBottomSheetObserver = new SuggestionsSheetVisibilityChangeObserver(this, activity) {
            @Override
            public void onContentShown(boolean isFirstShown) {
                // TODO(dgn): Temporary workaround to trigger an event in the backend when the
                // sheet is opened following inactivity. See https://crbug.com/760974. Should be
                // moved back to the "new opening of the sheet" path once we are able to trigger it
                // in that case.
                mSuggestionsUiDelegate.getEventReporter().onSurfaceOpened();
                SuggestionsMetrics.recordSurfaceVisible();

                if (isFirstShown) {
                    adapter.refreshSuggestions();

                    maybeUpdateContextualSuggestions();

                    // Set the adapter on the RecyclerView after updating it, to avoid sending
                    // notifications that might confuse its internal state.
                    // See https://crbug.com/756514.
                    mRecyclerView.setAdapter(adapter);
                    mRecyclerView.scrollToPosition(0);
                    mRecyclerView.getScrollEventReporter().reset();
                }
            }

            @Override
            public void onContentHidden() {
                SuggestionsMetrics.recordSurfaceHidden();
            }

            @Override
            public void onContentStateChanged(@BottomSheet.SheetState int contentState) {
                if (contentState == BottomSheet.SHEET_STATE_HALF) {
                    SuggestionsMetrics.recordSurfaceHalfVisible();
                    mRecyclerView.setScrollEnabled(false);
                } else if (contentState == BottomSheet.SHEET_STATE_FULL) {
                    SuggestionsMetrics.recordSurfaceFullyVisible();
                    mRecyclerView.setScrollEnabled(true);
                }

                updateLogoTransition();
            }

            @Override
            public void onSheetClosed(@StateChangeReason int reason) {
                super.onSheetClosed(reason);
                mRecyclerView.setAdapter(null);
                updateLogoTransition();
            }

            @Override
            public void onSheetOffsetChanged(float heightFraction) {
                mLastSheetHeightFraction = heightFraction;
                updateLogoTransition();
            }
        };

        final LocationBar locationBar = (LocationBar) sheet.findViewById(R.id.location_bar);
        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (locationBar != null && locationBar.isUrlBarFocused()) {
                    locationBar.setUrlBarFocus(false);
                }

                // Never intercept the touch event.
                return false;
            }
        });

        mLogoView = mView.findViewById(R.id.search_provider_logo);
        mControlContainerView = (ViewGroup) activity.findViewById(R.id.control_container);
        mToolbarPullHandle = activity.findViewById(R.id.toolbar_handle);
        mToolbarShadow = activity.findViewById(R.id.bottom_toolbar_shadow);
        mLogoDelegate = new LogoDelegateImpl(navigationDelegate, mLogoView, profile);
        updateSearchProviderHasLogo();
        if (mSearchProviderHasLogo) {
            mLogoView.showSearchProviderInitialView();
            loadSearchProviderLogo();
        }
        TemplateUrlService.getInstance().addObserver(this);
        sheet.getNewTabController().addObserver(this);

        mView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mIsAttachedToWindow = true;
                updateLogoTransition();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                mIsAttachedToWindow = false;
                updateLogoTransition();
            }
        });
    }

    @Override
    public View getContentView() {
        return mView;
    }

    @Override
    public List<View> getViewsForPadding() {
        return CollectionUtil.newArrayList(mRecyclerView);
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    @Override
    public boolean isUsingLightToolbarTheme() {
        return false;
    }

    @Override
    public boolean isIncognitoThemedContent() {
        return false;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mRecyclerView.computeVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        mBottomSheetObserver.onDestroy();
        mSuggestionsUiDelegate.onDestroy();
        mTileGroupDelegate.destroy();
        TemplateUrlService.getInstance().removeObserver(this);
        mSheet.getNewTabController().removeObserver(this);
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_SUGGESTIONS;
    }

    @Override
    public boolean applyDefaultTopPadding() {
        return false;
    }

    @Override
    public void onNewTabShown() {
        mNewTabShown = true;
    }

    @Override
    public void onNewTabHidden() {
        mNewTabShown = false;
    }

    @Override
    public void onTemplateURLServiceChanged() {
        updateSearchProviderHasLogo();
        loadSearchProviderLogo();
        updateLogoTransition();
    }

    private void maybeUpdateContextualSuggestions() {
        if (mSuggestionsCarousel == null) return;
        assert ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_CAROUSEL);

        Tab activeTab = mSheet.getActiveTab();
        final String currentUrl = activeTab == null ? null : activeTab.getUrl();

        mSuggestionsCarousel.refresh(mSheet.getContext(), currentUrl);
    }

    private void updateSearchProviderHasLogo() {
        mSearchProviderHasLogo = TemplateUrlService.getInstance().doesDefaultSearchEngineHaveLogo();
    }

    /**
     * Loads the search provider logo, if any.
     */
    private void loadSearchProviderLogo() {
        if (!mSearchProviderHasLogo) return;

        boolean isShowingLogo = mLogoView.showSearchProviderInitialView();

        // Doodles appear too small in the sheet to be useful, so only fetch a search provider logo
        // if the default logo isn't shown, which effectively disables non-3p (Google) doodles.
        // TODO(https://crbug.com/773657): Make doodles work in the smaller view; until that
        // happens, ensure that any third-party doodles look acceptable.
        if (isShowingLogo) return;

        mLogoDelegate.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;

                mLogoView.setDelegate(mLogoDelegate);
                mLogoView.updateLogo(logo);
            }
        });
    }

    private void updateLogoTransition() {
        boolean showLogo = mSearchProviderHasLogo && mNewTabShown && mSheet.isSheetOpen()
                && !mActivity.getTabModelSelector().isIncognitoSelected() && mIsAttachedToWindow
                && !mSheet.isSmallScreen()
                && mActivity.getTabModelSelector().getModel(false).getCount() > 0
                && FeatureUtilities.isChromeHomeDoodleEnabled();

        if (!showLogo) {
            mLogoView.setVisibility(View.GONE);
            mControlContainerView.setTranslationY(0);
            mToolbarPullHandle.setTranslationY(0);
            mToolbarShadow.setTranslationY(0);
            mRecyclerView.setTranslationY(0);
            ViewUtils.setAncestorsShouldClipChildren(mControlContainerView, true);
            return;
        }

        mLogoView.setVisibility(View.VISIBLE);
        ViewUtils.setAncestorsShouldClipChildren(mControlContainerView, false);

        ViewGroup.MarginLayoutParams logoParams =
                (ViewGroup.MarginLayoutParams) mLogoView.getLayoutParams();
        float maxToolbarOffset = logoParams.height + logoParams.topMargin + logoParams.bottomMargin;

        // Transform the sheet height fraction back to pixel scale.
        float rangePx =
                (mSheet.getFullRatio() - mSheet.getPeekRatio()) * mSheet.getSheetContainerHeight();
        float sheetHeightPx = mLastSheetHeightFraction * rangePx;

        // Calculate the transition fraction for hiding the logo: 0 means the logo is fully visible,
        // 1 means it is fully invisible.
        // The transition starts when the sheet is `2 * maxToolbarOffset` from the bottom or the
        // top. This makes the toolbar stay vertically centered in the sheet during the
        // bottom transition.
        float transitionFraction =
                Math.max(Math.max(1 - sheetHeightPx / (2 * maxToolbarOffset),
                                 1 + (sheetHeightPx - rangePx) / (2 * maxToolbarOffset)),
                        0);

        mLogoView.setTranslationY(logoParams.topMargin * -transitionFraction);
        mLogoView.setAlpha(Math.max(0.0f, 1.0f - (transitionFraction * 3.0f)));

        float toolbarOffset = maxToolbarOffset * (1.0f - transitionFraction);
        mControlContainerView.setTranslationY(toolbarOffset);
        mToolbarPullHandle.setTranslationY(-toolbarOffset);
        mToolbarShadow.setTranslationY(-toolbarOffset);
        mRecyclerView.setTranslationY(toolbarOffset);
    }

    @Override
    public void scrollToTop() {
        mRecyclerView.smoothScrollToPosition(0);
    }
}
