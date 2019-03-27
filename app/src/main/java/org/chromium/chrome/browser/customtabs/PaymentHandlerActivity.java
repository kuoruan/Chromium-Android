// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.WindowManager;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.payments.ServiceWorkerPaymentAppBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.display.DisplayUtil;

/**
 * Simple wrapper around CustomTabActivity to be used when launching a payment handler tab, which
 * uses a different theme.
 */
public class PaymentHandlerActivity extends CustomTabActivity {
    private static final double BOTTOM_SHEET_HEIGHT_RATIO = 0.7;
    private boolean mHaveNotifiedServiceWorker;
    private boolean mTabObserverAdded;

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();
        updateHeight();
        getComponent().resolveTabController().addObserver(this::addTabObserverIfTabReady);
        addTabObserverIfTabReady();
    }

    private void addTabObserverIfTabReady() {
        if (mTabObserverAdded) return;
        Tab tab = getComponent().resolveTabController().getTab();
        if (tab != null) {
            ServiceWorkerPaymentAppBridge.addTabObserverForPaymentRequestTab(tab);
            mTabObserverAdded = true;
        }
    }

    @Override
    protected void handleFinishAndClose() {
        // Notify the window is closing so as to abort invoking payment app early.
        Tab tab = getActivityTab();
        WebContents webContents = tab == null ? null : tab.getWebContents();
        if (!mHaveNotifiedServiceWorker && webContents != null) {
            mHaveNotifiedServiceWorker = true;
            ServiceWorkerPaymentAppBridge.onClosingPaymentAppWindow(webContents);
        }

        super.handleFinishAndClose();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateHeight();
    }

    private void updateHeight() {
        int displayHeightInPixels = DisplayUtil.dpToPx(
                getWindowAndroid().getDisplay(), getResources().getConfiguration().screenHeightDp);
        int heightInPhysicalPixels = (int) (displayHeightInPixels * BOTTOM_SHEET_HEIGHT_RATIO);
        int minimumHeightInPhysicalPixels = getResources().getDimensionPixelSize(
                R.dimen.payments_handler_window_minimum_height);

        if (heightInPhysicalPixels < minimumHeightInPhysicalPixels) {
            heightInPhysicalPixels = minimumHeightInPhysicalPixels;
        }

        if (heightInPhysicalPixels > displayHeightInPixels) {
            heightInPhysicalPixels = WindowManager.LayoutParams.MATCH_PARENT;
        }

        WindowManager.LayoutParams attributes = getWindow().getAttributes();

        if (attributes.height == heightInPhysicalPixels) return;

        attributes.height = heightInPhysicalPixels;
        attributes.gravity = Gravity.BOTTOM;
        getWindow().setAttributes(attributes);
    }
}