// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.top;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.compositor.Invalidator;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.fullscreen.BrowserStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.MenuButton;
import org.chromium.chrome.browser.toolbar.TabCountProvider;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;
import org.chromium.chrome.browser.toolbar.ToolbarTabController;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.ToolbarProgressBar;
import org.chromium.ui.AsyncViewProvider;

/**
 * A coordinator for the top toolbar component.
 */
public class TopToolbarCoordinator implements Toolbar {
    static final int TAB_SWITCHER_MODE_NORMAL_ANIMATION_DURATION_MS = 200;

    private final AsyncViewProvider<ToolbarLayout> mToolbarProvider;
    private @Nullable ToolbarLayout mToolbarLayout;

    /**
     * The coordinator for the tab switcher mode toolbar (phones only). This will be lazily created
     * after ToolbarLayout is inflated.
     */
    private @Nullable TabSwitcherModeTTCoordinatorPhone mTabSwitcherModeCoordinatorPhone;

    private HomepageManager.HomepageStateListener mHomepageStateListener =
            new HomepageManager.HomepageStateListener() {
                @Override
                public void onHomepageStateUpdated() {
                    mToolbarProvider.whenLoaded(
                            (toolbar)
                                    -> mToolbarLayout.onHomeButtonUpdate(
                                            HomepageManager.isHomepageEnabled()
                                            || FeatureUtilities.isNewTabPageButtonEnabled()));
                }
            };

    /**
     * Creates a new {@link TopToolbarCoordinator}.
     * @param controlContainer The {@link ToolbarControlContainer} for the containing activity.
     * @param toolbarProvider The {@link AsyncViewProvider} for the {@link ToolbarLayout}.
     */
    public TopToolbarCoordinator(ToolbarControlContainer controlContainer,
            AsyncViewProvider<ToolbarLayout> toolbarProvider) {
        mToolbarProvider = toolbarProvider;
        mToolbarProvider.whenLoaded((toolbar) -> {
            mToolbarLayout = toolbar;
            if (mToolbarLayout instanceof ToolbarPhone) {
                mTabSwitcherModeCoordinatorPhone = new TabSwitcherModeTTCoordinatorPhone(
                        controlContainer.getRootView().findViewById(
                                R.id.tab_switcher_toolbar_stub));
            }
            controlContainer.setToolbar(this);
            HomepageManager.getInstance().addListener(mHomepageStateListener);
        });
    }

    /**
     * Initialize the external dependencies required for view interaction.
     * @param toolbarDataProvider The provider for toolbar data.
     * @param tabController       The controller that handles interactions with the tab.
     * @param appMenuButtonHelper The helper for managing menu button interactions.
     */
    public void initialize(ToolbarDataProvider toolbarDataProvider,
            ToolbarTabController tabController, AppMenuButtonHelper appMenuButtonHelper) {
        mToolbarLayout.initialize(toolbarDataProvider, tabController, appMenuButtonHelper);
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.setAppMenuButtonHelper(appMenuButtonHelper);
        }
    }

    /**
     * Initialize the coordinator with the components that have native initialization dependencies.
     * <p>
     * Calling this must occur after the native library have completely loaded.
     *
     * @param tabModelSelector The selector that handles tab management.
     * @param controlsVisibilityDelegate The delegate to handle visibility of browser controls.
     * @param layoutManager A {@link LayoutManager} instance used to watch for scene changes.
     * @param tabSwitcherClickHandler The click handler for the tab switcher button.
     * @param newTabClickHandler The click handler for the new tab button.
     * @param bookmarkClickHandler The click handler for the bookmarks button.
     * @param customTabsBackClickHandler The click handler for the custom tabs back button.
     */
    public void initializeWithNative(TabModelSelector tabModelSelector,
            BrowserStateBrowserControlsVisibilityDelegate controlsVisibilityDelegate,
            LayoutManager layoutManager, OnClickListener tabSwitcherClickHandler,
            OnClickListener newTabClickHandler, OnClickListener bookmarkClickHandler,
            OnClickListener customTabsBackClickHandler) {
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.setOnTabSwitcherClickHandler(tabSwitcherClickHandler);
            mTabSwitcherModeCoordinatorPhone.setOnNewTabClickHandler(newTabClickHandler);
            mTabSwitcherModeCoordinatorPhone.setTabModelSelector(tabModelSelector);
        }

        mToolbarLayout.setTabModelSelector(tabModelSelector);
        getLocationBar().updateVisualsForState();
        getLocationBar().setUrlToPageUrl();
        mToolbarLayout.setBrowserControlsVisibilityDelegate(controlsVisibilityDelegate);
        mToolbarLayout.setOnTabSwitcherClickHandler(tabSwitcherClickHandler);
        mToolbarLayout.setBookmarkClickHandler(bookmarkClickHandler);
        mToolbarLayout.setCustomTabCloseClickHandler(customTabsBackClickHandler);
        mToolbarLayout.setLayoutUpdateHost(layoutManager);

        mToolbarLayout.onNativeLibraryReady();
    }

    /**
     * Cleans up any code as necessary.
     */
    public void destroy() {
        if (mToolbarLayout != null) {
            HomepageManager.getInstance().removeListener(mHomepageStateListener);
            mToolbarProvider.destroy((toolbar) -> mToolbarLayout.destroy());
        }
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.destroy();
        }
    }

    @Override
    public void disableMenuButton() {
        mToolbarLayout.disableMenuButton();
    }

    /** Notified that the menu was shown. */
    public void onMenuShown() {
        mToolbarLayout.onMenuShown();
    }

    /**
     * @return The view containing the menu button and menu button badge.
     */
    public MenuButton getMenuButtonWrapper() {
        if (mToolbarLayout == null) return null;
        View menuButtonWrapper = mToolbarLayout.getMenuButtonWrapper();
        if (menuButtonWrapper instanceof MenuButton) return (MenuButton) menuButtonWrapper;
        return null;
    }

    /**
     * @return The {@link ImageButton} containing the menu button.
     */
    public @Nullable ImageButton getMenuButton() {
        return mToolbarLayout == null ? null : mToolbarLayout.getMenuButton();
    }

    @Override
    public ToolbarProgressBar getProgressBar() {
        return mToolbarLayout.getProgressBar();
    }

    @Override
    public int getPrimaryColor() {
        return mToolbarLayout.getToolbarDataProvider().getPrimaryColor();
    }

    @Override
    public void getPositionRelativeToContainer(View containerView, int[] position) {
        mToolbarLayout.getPositionRelativeToContainer(containerView, position);
    }

    /**
     * Sets the {@link Invalidator} that will be called when the toolbar attempts to invalidate the
     * drawing surface.  This will give the object that registers as the host for the
     * {@link Invalidator} a chance to defer the actual invalidate to sync drawing.
     * @param invalidator An {@link Invalidator} instance.
     */
    public void setPaintInvalidator(Invalidator invalidator) {
        mToolbarLayout.setPaintInvalidator(invalidator);
    }

    /**
     * Gives inheriting classes the chance to respond to
     * {@link org.chromium.chrome.browser.widget.findinpage.FindToolbar} state changes.
     * @param showing Whether or not the {@code FindToolbar} will be showing.
     */
    public void handleFindLocationBarStateChange(boolean showing) {
        mToolbarLayout.handleFindLocationBarStateChange(showing);
    }

    /**
     * Sets whether the urlbar should be hidden on first page load.
     */
    public void setUrlBarHidden(boolean hidden) {
        mToolbarProvider.whenLoaded((toolbar) -> mToolbarLayout.setUrlBarHidden(hidden));
    }

    /**
     * @return The name of the publisher of the content if it can be reliably extracted, or null
     *         otherwise.
     */
    public String getContentPublisher() {
        return mToolbarLayout == null ? null : mToolbarLayout.getContentPublisher();
    }

    /**
     * Tells the Toolbar to update what buttons it is currently displaying.
     */
    public void updateButtonVisibility() {
        mToolbarLayout.updateButtonVisibility();
    }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * back button.
     * @param canGoBack Whether or not the current tab has any history to go back to.
     */
    public void updateBackButtonVisibility(boolean canGoBack) {
        mToolbarLayout.updateBackButtonVisibility(canGoBack);
    }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * forward button.
     * @param canGoForward Whether or not the current tab has any history to go forward to.
     */
    public void updateForwardButtonVisibility(boolean canGoForward) {
        mToolbarLayout.updateForwardButtonVisibility(canGoForward);
    }

    /**
     * Gives inheriting classes the chance to update the visibility of the
     * reload button.
     * @param isReloading Whether or not the current tab is loading.
     */
    public void updateReloadButtonVisibility(boolean isReloading) {
        mToolbarLayout.updateReloadButtonVisibility(isReloading);
    }

    /**
     * Gives inheriting classes the chance to update the visual status of the
     * bookmark button.
     * @param isBookmarked Whether or not the current tab is already bookmarked.
     * @param editingAllowed Whether or not bookmarks can be modified (added, edited, or removed).
     */
    public void updateBookmarkButton(boolean isBookmarked, boolean editingAllowed) {
        mToolbarLayout.updateBookmarkButton(isBookmarked, editingAllowed);
    }

    /**
     * Gives inheriting classes the chance to respond to accessibility state changes.
     * @param enabled Whether or not accessibility is enabled.
     */
    public void onAccessibilityStatusChanged(boolean enabled) {
        mToolbarProvider.whenLoaded((toolbar) -> {
            mToolbarLayout.onAccessibilityStatusChanged(enabled);
            if (mTabSwitcherModeCoordinatorPhone != null) {
                mTabSwitcherModeCoordinatorPhone.onAccessibilityStatusChanged(enabled);
            }
        });
    }

    /**
     * Gives inheriting classes the chance to do the necessary UI operations after Chrome is
     * restored to a previously saved state.
     */
    public void onStateRestored() {
        mToolbarLayout.onStateRestored();
    }

    /**
     * Triggered when the current tab or model has changed.
     * <p>
     * As there are cases where you can select a model with no tabs (i.e. having incognito
     * tabs but no normal tabs will still allow you to select the normal model), this should
     * not guarantee that the model's current tab is non-null.
     */
    public void onTabOrModelChanged() {
        mToolbarLayout.onTabOrModelChanged();
    }

    /**
     * For extending classes to override and carry out the changes related with the primary color
     * for the current tab changing.
     */
    public void onPrimaryColorChanged(boolean shouldAnimate) {
        mToolbarProvider.whenLoaded(
                (toolbar) -> mToolbarLayout.onPrimaryColorChanged(shouldAnimate));
    }

    /**
     * Sets whether a title should be shown within the Toolbar.
     * @param showTitle Whether a title should be shown.
     */
    public void setShowTitle(boolean showTitle) {
        mToolbarProvider.whenLoaded((toolbar) -> getLocationBar().setShowTitle(showTitle));
    }

    /**
     * Sets the icon drawable that the close button in the toolbar (if any) should show, or hides
     * it if {@code drawable} is {@code null}.
     */
    public void setCloseButtonImageResource(@Nullable Drawable drawable) {
        mToolbarProvider.whenLoaded(
                (toolbar) -> mToolbarLayout.setCloseButtonImageResource(drawable));
    }

    /**
     * Adds a custom action button to the toolbar layout, if it is supported.
     * @param drawable The icon for the button.
     * @param description The content description for the button.
     * @param listener The {@link View.OnClickListener} to use for clicks to the button.
     */
    public void addCustomActionButton(
            Drawable drawable, String description, View.OnClickListener listener) {
        mToolbarProvider.whenLoaded(
                (toolbar) -> mToolbarLayout.addCustomActionButton(drawable, description, listener));
    }

    /**
     * Updates the visual appearance of a custom action button in the toolbar layout,
     * if it is supported.
     * @param index The index of the button.
     * @param drawable The icon for the button.
     * @param description The content description for the button.
     */
    public void updateCustomActionButton(int index, Drawable drawable, String description) {
        mToolbarProvider.whenLoaded(
                (toolbar) -> mToolbarLayout.updateCustomActionButton(index, drawable, description));
    }

    @Override
    public int getTabStripHeight() {
        return mToolbarLayout.getTabStripHeight();
    }

    /**
     * Triggered when the content view for the specified tab has changed.
     */
    public void onTabContentViewChanged() {
        mToolbarLayout.onTabContentViewChanged();
    }

    @Override
    public boolean isReadyForTextureCapture() {
        return mToolbarLayout.isReadyForTextureCapture();
    }

    @Override
    public boolean setForceTextureCapture(boolean forceTextureCapture) {
        return mToolbarLayout.setForceTextureCapture(forceTextureCapture);
    }

    /**
     * @param attached Whether or not the web content is attached to the view heirarchy.
     */
    public void setContentAttached(boolean attached) {
        mToolbarLayout.setContentAttached(attached);
    }

    /**
     * Gives inheriting classes the chance to show or hide the TabSwitcher mode of this toolbar.
     * @param inTabSwitcherMode Whether or not TabSwitcher mode should be shown or hidden.
     * @param showToolbar    Whether or not to show the normal toolbar while animating.
     * @param delayAnimation Whether or not to delay the animation until after the transition has
     *                       finished (which can be detected by a call to
     *                       {@link #onTabSwitcherTransitionFinished()}).
     */
    public void setTabSwitcherMode(
            boolean inTabSwitcherMode, boolean showToolbar, boolean delayAnimation) {
        mToolbarLayout.setTabSwitcherMode(inTabSwitcherMode, showToolbar, delayAnimation);
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.setTabSwitcherMode(inTabSwitcherMode);
        }
    }

    /**
     * Gives inheriting classes the chance to update their state when the TabSwitcher transition has
     * finished.
     */
    public void onTabSwitcherTransitionFinished() {
        mToolbarLayout.onTabSwitcherTransitionFinished();
    }

    /**
     * Gives inheriting classes the chance to observe tab count changes.
     * @param tabCountProvider The {@link TabCountProvider} subclasses can observe.
     */
    public void setTabCountProvider(TabCountProvider tabCountProvider) {
        mToolbarLayout.setTabCountProvider(tabCountProvider);
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.setTabCountProvider(tabCountProvider);
        }
    }

    /**
     * @param provider The provider used to determine incognito state.
     */
    public void setIncognitoStateProvider(IncognitoStateProvider provider) {
        if (mTabSwitcherModeCoordinatorPhone != null) {
            mTabSwitcherModeCoordinatorPhone.setIncognitoStateProvider(provider);
        }
    }

    /**
     * @param provider The provider used to determine theme color.
     */
    public void setThemeColorProvider(ThemeColorProvider provider) {
        final MenuButton menuButtonWrapper = getMenuButtonWrapper();
        if (menuButtonWrapper == null) return;
        menuButtonWrapper.setThemeColorProvider(provider);
    }

    /**
     * Gives inheriting classes the chance to update themselves based on default search engine
     * changes.
     */
    public void onDefaultSearchEngineChanged() {
        mToolbarLayout.onDefaultSearchEngineChanged();
    }

    @Override
    public void getLocationBarContentRect(Rect outRect) {
        mToolbarLayout.getLocationBarContentRect(outRect);
    }

    @Override
    public void setTextureCaptureMode(boolean textureMode) {
        mToolbarLayout.setTextureCaptureMode(textureMode);
    }

    @Override
    public boolean shouldIgnoreSwipeGesture() {
        return mToolbarLayout.shouldIgnoreSwipeGesture();
    }

    /**
     * Triggered when the URL input field has gained or lost focus.
     * @param hasFocus Whether the URL field has gained focus.
     */
    public void onUrlFocusChange(boolean hasFocus) {
        mToolbarLayout.onUrlFocusChange(hasFocus);
    }

    /**
     * Returns the elapsed realtime in ms of the time at which first draw for the toolbar occurred.
     */
    public long getFirstDrawTime() {
        return mToolbarLayout.getFirstDrawTime();
    }

    /**
     * Notified when a navigation to a different page has occurred.
     */
    public void onNavigatedToDifferentPage() {
        mToolbarLayout.onNavigatedToDifferentPage();
    }

    /**
     * @param enabled Whether the progress bar is enabled.
     */
    public void setProgressBarEnabled(boolean enabled) {
        mToolbarProvider.whenLoaded(
                (toolbar) -> getProgressBar().setVisibility(enabled ? View.VISIBLE : View.GONE));
    }

    /**
     * @param anchor The view to use as an anchor.
     */
    public void setProgressBarAnchorView(@Nullable View anchor) {
        mToolbarProvider.whenLoaded(toolbar -> getProgressBar().setAnchorView(anchor));
    }

    /**
     * Starts load progress.
     */
    public void startLoadProgress() {
        mToolbarLayout.startLoadProgress();
    }

    /**
     * Sets load progress.
     * @param progress The load progress between 0 and 1.
     */
    public void setLoadProgress(float progress) {
        mToolbarLayout.setLoadProgress(progress);
    }

    /**
     * Finishes load progress.
     * @param delayed Whether hiding progress bar should be delayed to give enough time for user to
     *                        recognize the last state.
     */
    public void finishLoadProgress(boolean delayed) {
        mToolbarLayout.finishLoadProgress(delayed);
    }

    /**
     * @return True if the progress bar is started.
     */
    public boolean isProgressStarted() {
        return mToolbarLayout.isProgressStarted();
    }

    /**
     * Finish any toolbar animations.
     */
    public void finishAnimations() {
        mToolbarLayout.finishAnimations();
    }

    /**
     * @return {@link LocationBar} object this {@link ToolbarLayout} contains.
     */
    public LocationBar getLocationBar() {
        return mToolbarLayout.getLocationBar();
    }

    @Override
    public void setMenuButtonHighlight(boolean highlight) {
        mToolbarLayout.setMenuButtonHighlight(highlight);
    }

    @Override
    public void showAppMenuUpdateBadge() {
        mToolbarProvider.whenLoaded((toolbar) -> mToolbarLayout.showAppMenuUpdateBadge(true));
    }

    @Override
    public boolean isShowingAppMenuUpdateBadge() {
        return mToolbarLayout == null ? false : mToolbarLayout.isShowingAppMenuUpdateBadge();
    }

    @Override
    public void removeAppMenuUpdateBadge(boolean animate) {
        mToolbarProvider.whenLoaded((toolbar) -> mToolbarLayout.removeAppMenuUpdateBadge(animate));
    }

    /**
     * Enable the experimental toolbar button.
     * @param onClickListener The {@link View.OnClickListener} to be called when the button is
     *                        clicked.
     * @param drawableResId The resource id of the drawable to display for the button.
     * @param contentDescriptionResId The resource id of the content description for the button.
     */
    public void enableExperimentalButton(View.OnClickListener onClickListener,
            @DrawableRes int drawableResId, @StringRes int contentDescriptionResId) {
        mToolbarProvider.whenLoaded((toolbar) -> {
            mToolbarLayout.enableExperimentalButton(
                    onClickListener, drawableResId, contentDescriptionResId);
        });
    }

    /**
     * @return The experimental toolbar button if it exists.
     */
    public @Nullable View getExperimentalButtonView() {
        return mToolbarLayout == null ? null : mToolbarLayout.getExperimentalButtonView();
    }

    /**
     * Disable the experimental toolbar button.
     */
    public void disableExperimentalButton() {
        mToolbarProvider.whenLoaded((toolbarLayout) -> mToolbarLayout.disableExperimentalButton());
    }

    @Override
    public int getHeight() {
        return mToolbarLayout.getHeight();
    }

    /**
     * @return The {@link ToolbarLayout} that constitutes the toolbar.
     */
    @VisibleForTesting
    public ToolbarLayout getToolbarLayoutForTesting() {
        return mToolbarLayout;
    }
}
