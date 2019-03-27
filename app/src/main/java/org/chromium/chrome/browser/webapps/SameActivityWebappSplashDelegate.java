// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.metrics.SameActivityWebappUmaCache;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.webapk.lib.common.splash.SplashLayout;

/**
 * Delegate for when splash screen is shown by Chrome (as opposed to by external non-Chrome
 * activity).
 */
public class SameActivityWebappSplashDelegate implements WebappSplashDelegate {
    /** View to which the splash screen is added. */
    private ViewGroup mParentView;

    private ViewGroup mSplashScreen;

    /** Whether the splash screen is visible and not in the process of hiding. */
    private boolean mIsSplashVisible;

    /** Whether native was loaded. Native must be loaded in order to record metrics. */
    private boolean mNativeLoaded;

    private SameActivityWebappUmaCache mUmaCache;

    private WebApkOfflineDialog mOfflineDialog;

    private static class SingleShotOnDrawListener implements ViewTreeObserver.OnDrawListener {
        private final View mView;
        private final Runnable mAction;
        private boolean mHasRun;

        public static void install(View view, Runnable action) {
            SingleShotOnDrawListener listener = new SingleShotOnDrawListener(view, action);
            view.getViewTreeObserver().addOnDrawListener(listener);
        }

        private SingleShotOnDrawListener(View view, Runnable action) {
            mView = view;
            mAction = action;
        }

        @Override
        public void onDraw() {
            if (mHasRun) return;
            mHasRun = true;
            mAction.run();
            // Cannot call removeOnDrawListener within OnDraw, so do on next tick.
            mView.post(() -> mView.getViewTreeObserver().removeOnDrawListener(this));
        }
    };

    @Override
    public void showSplash(ViewGroup parentView, WebappInfo webappInfo) {
        mParentView = parentView;
        mIsSplashVisible = true;

        Context context = ContextUtils.getApplicationContext();
        final int backgroundColor = ColorUtils.getOpaqueColor(webappInfo.backgroundColor(
                ApiCompatibilityUtils.getColor(context.getResources(), R.color.webapp_default_bg)));

        mSplashScreen = new FrameLayout(context);
        mSplashScreen.setBackgroundColor(backgroundColor);
        mParentView.addView(mSplashScreen);
        startSplashscreenTraceEvents();

        if (webappInfo.isForWebApk()) {
            initializeLayout(webappInfo, backgroundColor, ((WebApkInfo) webappInfo).splashIcon());
            return;
        }

        WebappDataStorage storage =
                WebappRegistry.getInstance().getWebappDataStorage(webappInfo.id());
        if (storage == null) {
            initializeLayout(webappInfo, backgroundColor, null);
            return;
        }

        storage.getSplashScreenImage(new WebappDataStorage.FetchCallback<Bitmap>() {
            @Override
            public void onDataRetrieved(Bitmap splashImage) {
                initializeLayout(webappInfo, backgroundColor, splashImage);
            }
        });
    }

    @Override
    public void onNativeLoaded() {
        mNativeLoaded = true;
        if (mUmaCache != null) mUmaCache.commitMetrics();
    }

    @Override
    public void hideSplash(final Runnable finishedHidingCallback) {
        assert mIsSplashVisible;

        mIsSplashVisible = false;
        mSplashScreen.animate().alpha(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                mParentView.removeView(mSplashScreen);
                finishSplashscreenTraceEvents();
                mSplashScreen = null;
                finishedHidingCallback.run();
            }
        });
    }

    @Override
    public boolean isSplashVisible() {
        return mIsSplashVisible;
    }

    @Override
    public ViewGroup getSplashViewIfChildOf(ViewGroup parent) {
        return (mParentView == parent) ? mSplashScreen : null;
    }

    @Override
    public boolean isNetworkErrorDialogVisible() {
        return mOfflineDialog != null && mOfflineDialog.isShowing();
    }

    @Override
    public void showNetworkErrorDialog(Tab tab, String errorMsg) {
        mOfflineDialog = new WebApkOfflineDialog();
        mOfflineDialog.show(tab.getActivity(), errorMsg);
    }

    @Override
    public void hideNetworkErrorDialog() {
        if (mOfflineDialog == null) return;
        mOfflineDialog.cancel();
        mOfflineDialog = null;
    }

    /** Sets the splash screen layout and sets the splash screen's title and icon. */
    private void initializeLayout(WebappInfo webappInfo, int backgroundColor, Bitmap splashImage) {
        Context context = ContextUtils.getApplicationContext();
        Resources resources = context.getResources();

        Bitmap selectedIcon = splashImage;
        boolean selectedIconGenerated = false;
        boolean selectedIconAdaptive = false;
        if (selectedIcon == null) {
            selectedIcon = webappInfo.icon();
            selectedIconGenerated = webappInfo.isIconGenerated();
            selectedIconAdaptive = webappInfo.isIconAdaptive();
        }
        @SplashLayout.IconClassification
        int selectedIconClassification = SplashLayout.classifyIcon(
                context.getResources(), selectedIcon, selectedIconGenerated);

        SplashLayout.createLayout(context, mSplashScreen, selectedIcon, selectedIconAdaptive,
                selectedIconClassification, webappInfo.name(),
                ColorUtils.shouldUseLightForegroundOnBackground(backgroundColor));

        recordUma(resources, webappInfo, selectedIconClassification, selectedIcon,
                (splashImage != null));
    }

    /**
     * Records splash screen UMA metrics.
     * @param resources
     * @param webappInfo
     * @param selectedIconClassification.
     * @param selectedIcon The icon used on the splash screen.
     * @param usingDedicatedIcon Whether the PWA provides different icons for the splash screen and
     *                           for the app icon.
     */
    private void recordUma(Resources resources, WebappInfo webappInfo,
            @SplashLayout.IconClassification int selectedIconClassification, Bitmap selectedIcon,
            boolean usingDedicatedIcon) {
        mUmaCache = new SameActivityWebappUmaCache();
        mUmaCache.recordSplashscreenBackgroundColor(webappInfo.hasValidBackgroundColor()
                        ? SameActivityWebappUmaCache.SplashColorStatus.CUSTOM
                        : SameActivityWebappUmaCache.SplashColorStatus.DEFAULT);
        mUmaCache.recordSplashscreenThemeColor(webappInfo.hasValidThemeColor()
                        ? SameActivityWebappUmaCache.SplashColorStatus.CUSTOM
                        : SameActivityWebappUmaCache.SplashColorStatus.DEFAULT);

        mUmaCache.recordSplashscreenIconType(selectedIconClassification, usingDedicatedIcon);
        if (selectedIconClassification != SplashLayout.IconClassification.INVALID) {
            DisplayMetrics displayMetrics = resources.getDisplayMetrics();
            mUmaCache.recordSplashscreenIconSize(Math.round(
                    selectedIcon.getScaledWidth(displayMetrics) / displayMetrics.density));
        }

        if (mNativeLoaded) mUmaCache.commitMetrics();
    }

    private void startSplashscreenTraceEvents() {
        TraceEvent.startAsync("WebappSplashScreen", hashCode());
        SingleShotOnDrawListener.install(mParentView,
                () -> { TraceEvent.startAsync("WebappSplashScreen.visible", hashCode()); });
    }

    private void finishSplashscreenTraceEvents() {
        TraceEvent.finishAsync("WebappSplashScreen", hashCode());
        SingleShotOnDrawListener.install(mParentView,
                () -> { TraceEvent.finishAsync("WebappSplashScreen.visible", hashCode()); });
    }
}
