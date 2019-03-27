// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.top;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.support.v7.content.res.AppCompatResources;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.LinearInterpolator;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.IncognitoStateProvider;
import org.chromium.chrome.browser.toolbar.IncognitoToggleTabLayout;
import org.chromium.chrome.browser.toolbar.MenuButton;
import org.chromium.chrome.browser.toolbar.NewTabButton;
import org.chromium.chrome.browser.toolbar.TabCountProvider;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.animation.CancelAwareAnimatorListener;
import org.chromium.ui.UiUtils;
import org.chromium.ui.widget.OptimizedFrameLayout;

/** The tab switcher mode top toolbar shown on phones. */
public class TabSwitcherModeTTPhone extends OptimizedFrameLayout
        implements View.OnClickListener, IncognitoStateProvider.IncognitoStateObserver {
    private View.OnClickListener mNewTabListener;

    private TabCountProvider mTabCountProvider;
    private TabModelSelector mTabModelSelector;
    private IncognitoStateProvider mIncognitoStateProvider;

    private @Nullable IncognitoToggleTabLayout mIncognitoToggleTabLayout;

    // The following three buttons are not used when Duet is enabled.
    private @Nullable NewTabButton mNewTabButton;
    private @Nullable MenuButton mMenuButton;
    private @Nullable ToggleTabStackButton mToggleTabStackButton;

    private int mPrimaryColor;
    private boolean mUseLightIcons;
    private ColorStateList mLightIconTint;
    private ColorStateList mDarkIconTint;

    private boolean mIsIncognito;

    private ObjectAnimator mVisiblityAnimator;

    public TabSwitcherModeTTPhone(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mNewTabButton = findViewById(R.id.new_tab_button);
        mMenuButton = findViewById(R.id.menu_button_wrapper);
        mToggleTabStackButton = findViewById(R.id.tab_switcher_mode_tab_switcher_button);

        boolean isBottomToolbarEnabled = FeatureUtilities.isBottomToolbarEnabled();

        if (isBottomToolbarEnabled) {
            UiUtils.removeViewFromParent(mNewTabButton);
            mNewTabButton.destroy();
            mNewTabButton = null;

            UiUtils.removeViewFromParent(mMenuButton);
            mMenuButton.destroy();
            mMenuButton = null;

            UiUtils.removeViewFromParent(mToggleTabStackButton);
            mToggleTabStackButton.destroy();
            mToggleTabStackButton = null;
        } else {
            // TODO(twellington): Try to make NewTabButton responsible for handling its own clicks.
            //                    TabSwitcherBottomToolbarCoordinator also uses NewTabButton and
            //                    sets an onClickListener directly on NewTabButton rather than
            //                    acting as the click listener itself so the behavior between this
            //                    class and the bottom toolbar will need to be unified.
            mNewTabButton.setOnClickListener(this);
        }

        if (usingHorizontalTabSwitcher()
                && PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
            updateTabSwitchingElements(true);
        }
    }

    @Override
    public void onClick(View v) {
        if (mNewTabButton == v) {
            v.setEnabled(false);
            if (mNewTabListener != null) mNewTabListener.onClick(v);
        }
    }

    /**
     * Cleans up any code and removes observers as necessary.
     */
    void destroy() {
        if (mIncognitoStateProvider != null) {
            mIncognitoStateProvider.removeObserver(this);
            mIncognitoStateProvider = null;
        }
        if (mNewTabButton != null) {
            mNewTabButton.destroy();
            mNewTabButton = null;
        }
        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.destroy();
            mToggleTabStackButton = null;
        }
        if (mIncognitoToggleTabLayout != null) {
            mIncognitoToggleTabLayout.destroy();
            mIncognitoToggleTabLayout = null;
        }
        if (mMenuButton != null) {
            mMenuButton.destroy();
            mMenuButton = null;
        }
    }

    /**
     * Called when tab switcher mode is entered or exited.
     * @param inTabSwitcherMode Whether or not tab switcher mode should be shown or hidden.
     */
    void setTabSwitcherMode(boolean inTabSwitcherMode) {
        if (mVisiblityAnimator != null) mVisiblityAnimator.cancel();

        setVisibility(View.VISIBLE);
        // TODO(twellington): Handle interrupted animations to avoid jumps to 1.0 or 0.f.
        setAlpha(inTabSwitcherMode ? 0.0f : 1.0f);

        mVisiblityAnimator =
                ObjectAnimator.ofFloat(this, View.ALPHA, inTabSwitcherMode ? 1.0f : 0.0f);
        mVisiblityAnimator.setDuration(
                TopToolbarCoordinator.TAB_SWITCHER_MODE_NORMAL_ANIMATION_DURATION_MS);
        mVisiblityAnimator.setInterpolator(new LinearInterpolator());

        // TODO(https://crbug.com/914868): Use consistent logic here for setting clickable/enabled
        // on mIncognitoToggleTabLayout & mNewTabButton?
        if (!inTabSwitcherMode) {
            if (mIncognitoToggleTabLayout != null) mIncognitoToggleTabLayout.setClickable(false);
        } else {
            if (mNewTabButton != null) mNewTabButton.setEnabled(true);
        }

        mVisiblityAnimator.addListener(new CancelAwareAnimatorListener() {
            @Override
            public void onEnd(Animator animation) {
                setAlpha(1.0f);

                if (!inTabSwitcherMode) {
                    setVisibility(View.GONE);
                }

                if (mIncognitoToggleTabLayout != null) {
                    mIncognitoToggleTabLayout.setClickable(true);
                }

                mVisiblityAnimator = null;
            }
        });

        mVisiblityAnimator.start();

        if (DeviceClassManager.enableAccessibilityLayout()) mVisiblityAnimator.end();
    }

    /**
     * @param appMenuButtonHelper The helper for managing menu button interactions.
     */
    void setAppMenuButtonHelper(AppMenuButtonHelper appMenuButtonHelper) {
        if (mMenuButton == null) return;

        mMenuButton.getImageButton().setOnTouchListener(appMenuButtonHelper);
        mMenuButton.getImageButton().setAccessibilityDelegate(appMenuButtonHelper);
    }

    /**
     * Sets the OnClickListener that will be notified when the TabSwitcher button is pressed.
     * @param listener The callback that will be notified when the TabSwitcher button is pressed.
     */
    void setOnTabSwitcherClickHandler(View.OnClickListener listener) {
        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.setOnTabSwitcherClickHandler(listener);
        }
    }

    /**
     * Sets the OnClickListener that will be notified when the New Tab button is pressed.
     * @param listener The callback that will be notified when the New Tab button is pressed.
     */
    void setOnNewTabClickHandler(View.OnClickListener listener) {
        mNewTabListener = listener;
    }

    /**
     * @param tabCountProvider The {@link TabCountProvider} used to observe the number of tabs in
     *                         the current model.
     */
    void setTabCountProvider(TabCountProvider tabCountProvider) {
        mTabCountProvider = tabCountProvider;
        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.setTabCountProvider(tabCountProvider);
        }
        if (mIncognitoToggleTabLayout != null) {
            mIncognitoToggleTabLayout.setTabCountProvider(tabCountProvider);
        }
    }

    /**
     * Sets the current TabModelSelector so the toolbar can pass it into buttons that need access to
     * it.
     */
    void setTabModelSelector(TabModelSelector selector) {
        mTabModelSelector = selector;
        if (mIncognitoToggleTabLayout != null) {
            mIncognitoToggleTabLayout.setTabModelSelector(selector);
        }
    }

    /**
     * @param provider The provider used to determine incognito state.
     */
    void setIncognitoStateProvider(IncognitoStateProvider provider) {
        mIncognitoStateProvider = provider;
        mIncognitoStateProvider.addIncognitoStateObserverAndTrigger(this);

        if (mNewTabButton != null) mNewTabButton.setIncognitoStateProvider(mIncognitoStateProvider);
    }

    @Override
    public void onIncognitoStateChanged(boolean isIncognito) {
        mIsIncognito = isIncognito;
        updatePrimaryColorAndTint();
    }

    /** Called when accessibility status changes. */
    void onAccessibilityStatusChanged(boolean enabled) {
        if (mNewTabButton != null) mNewTabButton.onAccessibilityStatusChanged();

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID)
                && PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
            updateTabSwitchingElements(!enabled);
        }

        updatePrimaryColorAndTint();
    }

    private void updatePrimaryColorAndTint() {
        int primaryColor = getToolbarColorForCurrentState();
        if (mPrimaryColor != primaryColor) {
            mPrimaryColor = primaryColor;
            setBackgroundColor(primaryColor);
        }

        boolean useLightIcons = mIsIncognito
                && (usingHorizontalTabSwitcher() || DeviceClassManager.enableAccessibilityLayout());

        if (mUseLightIcons == useLightIcons) return;

        mUseLightIcons = useLightIcons;

        if (mLightIconTint == null) {
            mLightIconTint =
                    AppCompatResources.getColorStateList(getContext(), R.color.light_mode_tint);
            mDarkIconTint =
                    AppCompatResources.getColorStateList(getContext(), R.color.dark_mode_tint);
        }

        ColorStateList tintList = useLightIcons ? mLightIconTint : mDarkIconTint;
        if (mMenuButton != null) {
            ApiCompatibilityUtils.setImageTintList(mMenuButton.getImageButton(), tintList);
        }

        if (mToggleTabStackButton != null) {
            mToggleTabStackButton.setUseLightDrawables(useLightIcons);
        }
    }

    private int getToolbarColorForCurrentState() {
        if (DeviceClassManager.enableAccessibilityLayout()) {
            int colorId = mIsIncognito ? R.color.incognito_modern_primary_color
                                       : R.color.modern_primary_color;
            return ApiCompatibilityUtils.getColor(getResources(), colorId);
        }

        return Color.TRANSPARENT;
    }

    private boolean usingHorizontalTabSwitcher() {
        // The horizontal tab switcher flag does not affect the accessibility switcher. We do the
        // enableAccessibilityLayout() check first here to avoid logging an experiment exposure for
        // these users.
        return !DeviceClassManager.enableAccessibilityLayout()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID);
    }

    private void inflateIncognitoToggle() {
        ViewStub incognitoToggleTabsStub = findViewById(R.id.incognito_tabs_stub);
        mIncognitoToggleTabLayout = (IncognitoToggleTabLayout) incognitoToggleTabsStub.inflate();

        if (mTabCountProvider != null) {
            mIncognitoToggleTabLayout.setTabCountProvider(mTabCountProvider);
        }

        if (mTabModelSelector != null) {
            mIncognitoToggleTabLayout.setTabModelSelector(mTabModelSelector);
        }
    }

    private void setIncognitoToggleVisibility(boolean showIncognitoToggle) {
        if (mIncognitoToggleTabLayout == null) {
            if (showIncognitoToggle) inflateIncognitoToggle();
        } else {
            mIncognitoToggleTabLayout.setVisibility(showIncognitoToggle ? View.VISIBLE : View.GONE);
        }
    }

    private void setToggleTabStackButtonVisibility(boolean showToggleTabStackButton) {
        if (mToggleTabStackButton == null) return;
        mToggleTabStackButton.setVisibility(showToggleTabStackButton ? View.VISIBLE : View.GONE);
    }

    private void updateTabSwitchingElements(boolean showIncognitoToggle) {
        setIncognitoToggleVisibility(showIncognitoToggle);
        setToggleTabStackButtonVisibility(!showIncognitoToggle);
    }
}
