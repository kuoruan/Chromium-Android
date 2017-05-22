// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;

/**
 * Utility methods for the browsing history manager.
 */
public class HistoryManagerUtils {
    private static final Object NATIVE_HISTORY_ENABLED_LOCK = new Object();
    private static Boolean sNativeHistoryEnabled;

    /**
    * @return Whether the Android-specific browsing history manager is enabled.
    */
    public static boolean isAndroidHistoryManagerEnabled() {
        synchronized (NATIVE_HISTORY_ENABLED_LOCK) {
            if (sNativeHistoryEnabled == null) {
                sNativeHistoryEnabled = ChromeFeatureList.isEnabled("AndroidHistoryManager");
            }
        }

        return sNativeHistoryEnabled;
    }

    /**
    * Opens the browsing history manager.
    */
    public static void showHistoryManager(Activity activity, Tab tab) {
        if (!isAndroidHistoryManagerEnabled()) {
            tab.loadUrl(new LoadUrlParams(UrlConstants.HISTORY_URL, PageTransition.AUTO_TOPLEVEL));
            return;
        }

        Context appContext = ContextUtils.getApplicationContext();
        if (DeviceFormFactor.isTablet(appContext)) {
            // History shows up as a tab on tablets.
            LoadUrlParams params = new LoadUrlParams(UrlConstants.NATIVE_HISTORY_URL);
            tab.loadUrl(params);
        } else {
            Intent intent = new Intent();
            intent.setClass(appContext, HistoryActivity.class);
            intent.putExtra(IntentHandler.EXTRA_PARENT_COMPONENT, activity.getComponentName());
            activity.startActivity(intent);
        }
    }
}
