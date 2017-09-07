// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.support.customtabs.CustomTabsIntent;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.AsyncTabParamsManager;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;

import java.net.URISyntaxException;

/**
 * Asynchronously creates Tabs for navigation originating from an installed PWA.
 *
 * This is the same as the parent class with exception of checking for a specialized native handlers
 * first, and if none are found opening a Custom Tab instead of creating a new tab in Chrome.
 */
public class WebappTabDelegate extends TabDelegate {
    private static final String TAG = "WebappTabDelegate";

    public WebappTabDelegate(boolean incognito) {
        super(incognito);
    }

    @Override
    public void createNewTab(AsyncTabCreationParams asyncParams, TabLaunchType type, int parentId) {
        String url = asyncParams.getLoadUrlParams().getUrl();
        if (maybeStartExternalActivity(url)) {
            return;
        }

        int assignedTabId = TabIdManager.getInstance().generateValidId(Tab.INVALID_TAB_ID);
        AsyncTabParamsManager.add(assignedTabId, asyncParams);

        Intent intent = new CustomTabsIntent.Builder().setShowTitle(true).build().intent;
        intent.setData(Uri.parse(url));
        intent.putExtra(CustomTabIntentDataProvider.EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER, true);
        intent.putExtra(CustomTabIntentDataProvider.EXTRA_IS_OPENED_BY_CHROME, true);
        addAsyncTabExtras(asyncParams, parentId, false /* isChromeUI */, assignedTabId, intent);

        IntentHandler.startActivityForTrustedIntent(intent);
    }

    private boolean maybeStartExternalActivity(String url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException ex) {
            Log.w(TAG, "Bad URI %s", url, ex);
            return false;
        }

        // See http://crbug.com/613977 for more context.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            // Launch a native app iff there is a specialized handler for a given URL.
            if (ExternalNavigationDelegateImpl.isPackageSpecializedHandler(null, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ContextUtils.getApplicationContext().startActivity(intent);
                return true;
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        return false;
    }
}
