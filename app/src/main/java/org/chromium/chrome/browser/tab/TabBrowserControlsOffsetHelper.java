// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import org.chromium.base.ObserverList;
import org.chromium.base.UserData;
import org.chromium.base.UserDataHost;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.chrome.browser.vr.VrModeObserver;
import org.chromium.chrome.browser.vr.VrModuleProvider;

/**
 * Handles browser controls offset for a Tab.
 */
public class TabBrowserControlsOffsetHelper implements VrModeObserver, UserData {
    private static final Class<TabBrowserControlsOffsetHelper> USER_DATA_KEY =
            TabBrowserControlsOffsetHelper.class;

    /**
     * Maximum duration for the control container slide-in animation. Note that this value matches
     * the one in browser_controls_offset_manager.cc.
     */
    private static final int MAX_CONTROLS_ANIMATION_DURATION_MS = 200;

    /**
     * An interface for notification about browser controls offset updates.
     */
    public interface Observer {
        /**
         * Called when the browser controls are fully visible on screen.
         */
        void onBrowserControlsFullyVisible(Tab tab);
    }

    private final Tab mTab;
    private final ObserverList<Observer> mObservers = new ObserverList<>();
    private final TabObserver mTabObserver;

    private int mPreviousTopControlsOffsetY;
    private int mPreviousBottomControlsOffsetY;
    private int mPreviousContentOffsetY;
    private boolean mAreOffsetsInitialized;

    /**
     * Whether the Android browser controls offset is overridden. This handles top controls only.
     */
    private boolean mIsControlsOffsetOverridden;

    /**
     * The animator for slide-in animation on the Android controls.
     */
    private ValueAnimator mControlsAnimator;

    /**
     * Whether the browser is currently in VR mode.
     */
    private boolean mIsInVr;

    public static TabBrowserControlsOffsetHelper from(Tab tab) {
        UserDataHost host = tab.getUserDataHost();
        TabBrowserControlsOffsetHelper helper = host.getUserData(USER_DATA_KEY);
        return helper != null
                ? helper
                : host.setUserData(USER_DATA_KEY, new TabBrowserControlsOffsetHelper(tab));
    }

    /**
     * @param tab The {@link Tab} that this class is associated with.
     */
    private TabBrowserControlsOffsetHelper(Tab tab) {
        mTab = tab;
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onCrash(Tab tab) {
                if (SadTab.isShowing(tab)) showAndroidControls(false);
            }
            @Override
            public void onRendererResponsiveStateChanged(Tab tab, boolean isResponsive) {
                if (!isResponsive) showAndroidControls(false);
            }
        };

        mTab.addObserver(mTabObserver);
        VrModuleProvider.registerVrModeObserver(this);
        if (VrModuleProvider.getDelegate().isInVr()) onEnterVr();
    }

    /**
     * @return Whether the Android browser controls offset is overridden on the browser side.
     */
    public boolean isControlsOffsetOverridden() {
        return mIsControlsOffsetOverridden;
    }

    /**
     * @return Whether the browser controls are fully visible on screen.
     */
    public boolean areBrowserControlsFullyVisible() {
        final FullscreenManager manager = mTab.getFullscreenManager();
        return Float.compare(0f, manager.getBrowserControlHiddenRatio()) == 0;
    }

    /**
     * @param observer The observer to be added to get notifications from this class.
     */
    public void addObserver(Observer observer) {
        mObservers.addObserver(observer);
    }

    /**
     * @param observer The observer to be removed to cancel notifications from this class.
     */
    public void removeObserver(Observer observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Called when offset values related with fullscreen functionality has been changed by the
     * compositor.
     * @param topControlsOffsetY The Y offset of the top controls in physical pixels.
     * @param bottomControlsOffsetY The Y offset of the bottom controls in physical pixels.
     * @param contentOffsetY The Y offset of the content in physical pixels.
     */
    void onOffsetsChanged(int topControlsOffsetY, int bottomControlsOffsetY, int contentOffsetY) {
        // Cancel any animation on the Android controls and let compositor drive the offset updates.
        resetControlsOffsetOverridden();

        mPreviousTopControlsOffsetY = topControlsOffsetY;
        mPreviousBottomControlsOffsetY = bottomControlsOffsetY;
        mPreviousContentOffsetY = contentOffsetY;
        mAreOffsetsInitialized = true;

        if (mTab.getFullscreenManager() == null) return;
        if (SadTab.isShowing(mTab) || mTab.isNativePage()) {
            showAndroidControls(false);
        } else {
            updateFullscreenManagerOffsets(false, mPreviousTopControlsOffsetY,
                    mPreviousBottomControlsOffsetY, mPreviousContentOffsetY);
        }
        TabModelImpl.setActualTabSwitchLatencyMetricRequired();
    }

    /**
     * Shows the Android browser controls view.
     * @param animate Whether a slide-in animation should be run.
     */
    public void showAndroidControls(boolean animate) {
        if (mTab.getFullscreenManager() == null) return;

        if (animate) {
            runBrowserDrivenShowAnimation();
        } else {
            updateFullscreenManagerOffsets(
                    true, 0, 0, mTab.getFullscreenManager().getContentOffset());
        }
    }

    /**
     * Resets the controls positions in {@link FullscreenManager} to the cached positions.
     */
    public void resetPositions() {
        resetControlsOffsetOverridden();
        if (mTab.getFullscreenManager() == null) return;

        // Make sure the dominant control offsets have been set.
        if (mAreOffsetsInitialized) {
            updateFullscreenManagerOffsets(false, mPreviousTopControlsOffsetY,
                    mPreviousBottomControlsOffsetY, mPreviousContentOffsetY);
        } else {
            showAndroidControls(false);
        }
        mTab.updateFullscreenEnabledState();
    }

    /**
     * Clears the cached browser controls positions.
     */
    private void clearPreviousPositions() {
        mPreviousTopControlsOffsetY = 0;
        mPreviousBottomControlsOffsetY = 0;
        mPreviousContentOffsetY = 0;
    }

    /**
     * Helper method to update offsets in {@link FullscreenManager} and notify offset changes to
     * observers if necessary.
     */
    private void updateFullscreenManagerOffsets(boolean toNonFullscreen, int topControlsOffset,
            int bottomControlsOffset, int topContentOffset) {
        final FullscreenManager manager = mTab.getFullscreenManager();
        if (manager == null) return;

        if (mIsInVr) {
            VrModuleProvider.getDelegate().rawTopContentOffsetChanged(topContentOffset);
            // The dip scale of java UI and WebContents are different while in VR, leading to a
            // mismatch in size in pixels when converting from dips. Since we hide the controls in
            // VR anyways, just set the offsets to what they're supposed to be with the controls
            // hidden.
            // TODO(mthiesse): Should we instead just set the top controls height to be 0 while in
            // VR?
            topControlsOffset = -manager.getTopControlsHeight();
            bottomControlsOffset = manager.getBottomControlsHeight();
            topContentOffset = 0;
            manager.setPositionsForTab(topControlsOffset, bottomControlsOffset, topContentOffset);
        } else if (toNonFullscreen) {
            manager.setPositionsForTabToNonFullscreen();
        } else {
            manager.setPositionsForTab(topControlsOffset, bottomControlsOffset, topContentOffset);
        }

        if (!areBrowserControlsFullyVisible()) return;
        for (Observer observer : mObservers) {
            observer.onBrowserControlsFullyVisible(mTab);
        }
    }

    /**
     * Helper method to cancel overridden offset on Android browser controls.
     */
    private void resetControlsOffsetOverridden() {
        if (!mIsControlsOffsetOverridden) return;
        if (mControlsAnimator != null) mControlsAnimator.cancel();
        mIsControlsOffsetOverridden = false;
    }

    /**
     * Helper method to run slide-in animations on the Android browser controls views.
     */
    private void runBrowserDrivenShowAnimation() {
        if (mControlsAnimator != null) return;

        mIsControlsOffsetOverridden = true;

        final FullscreenManager manager = mTab.getFullscreenManager();
        final float hiddenRatio = manager.getBrowserControlHiddenRatio();
        final int topControlHeight = manager.getTopControlsHeight();
        final int topControlOffset = manager.getTopControlOffset();

        // Set animation start value to current renderer controls offset.
        mControlsAnimator = ValueAnimator.ofInt(topControlOffset, 0);
        mControlsAnimator.setDuration(
                (long) Math.abs(hiddenRatio * MAX_CONTROLS_ANIMATION_DURATION_MS));
        mControlsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mControlsAnimator = null;
                mPreviousTopControlsOffsetY = 0;
                mPreviousContentOffsetY = topControlHeight;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                updateFullscreenManagerOffsets(false, topControlHeight, 0, topControlHeight);
            }
        });
        mControlsAnimator.addUpdateListener((animator) -> {
            updateFullscreenManagerOffsets(
                    false, (int) animator.getAnimatedValue(), 0, topControlHeight);
        });
        mControlsAnimator.start();
    }

    @Override
    public void onEnterVr() {
        mIsInVr = true;
        resetPositions();
    }

    @Override
    public void onExitVr() {
        mIsInVr = false;
        // Call resetPositions() to clear the VR-specific overrides for controls height.
        resetPositions();
        // Show the Controls explicitly because under some situations, like when we're showing a
        // Native Page, the renderer won't send any new offsets.
        showAndroidControls(false);
    }

    // UserData

    @Override
    public void destroy() {
        clearPreviousPositions();
        VrModuleProvider.unregisterVrModeObserver(this);
        mTab.removeObserver(mTabObserver);
    }
}
