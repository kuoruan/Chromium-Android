// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * A utility class for querying information about the default browser setting.
 */
public class DefaultBrowserInfo {
    private static final String SAMPLE_URL = "https://www.madeupdomainforcheck123.com/";

    /** A lock to synchronize background tasks to retrieve browser information. */
    private static final Object sDirCreationLock = new Object();

    private static AsyncTask<Void, Void, ArrayList<String>> sDefaultBrowserFetcher;

    /**
     * Initialize an AsyncTask for getting menu title of opening a link in default browser.
     */
    public static void initBrowserFetcher() {
        synchronized (sDirCreationLock) {
            if (sDefaultBrowserFetcher == null) {
                sDefaultBrowserFetcher = new AsyncTask<Void, Void, ArrayList<String>>() {
                    @Override
                    protected ArrayList<String> doInBackground(Void... params) {
                        Context context = ContextUtils.getApplicationContext();
                        ArrayList<String> menuTitles = new ArrayList<String>(2);
                        // Store the package label of current application.
                        menuTitles.add(getTitleFromPackageLabel(
                                context, BuildInfo.getPackageLabel(context)));

                        PackageManager pm = context.getPackageManager();
                        ResolveInfo info = getResolveInfoForViewIntent(pm);

                        // Caches whether Chrome is set as a default browser on the device.
                        boolean isDefault = (info != null && info.match != 0
                                && context.getPackageName().equals(info.activityInfo.packageName));
                        ChromePreferenceManager.getInstance().setCachedChromeDefaultBrowser(
                                isDefault);

                        // Check if there is a default handler for the Intent.  If so, store its
                        // label.
                        String packageLabel = null;
                        if (info != null && info.match != 0 && info.loadLabel(pm) != null) {
                            packageLabel = info.loadLabel(pm).toString();
                        }
                        menuTitles.add(getTitleFromPackageLabel(context, packageLabel));
                        return menuTitles;
                    }
                };
                sDefaultBrowserFetcher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    private static String getTitleFromPackageLabel(Context context, String packageLabel) {
        return packageLabel == null
                ? context.getString(R.string.menu_open_in_product_default)
                : context.getString(R.string.menu_open_in_product, packageLabel);
    }

    /**
     * @return Default ResolveInfo to handle a VIEW intent for a url.
     * @param pm The PackageManager of current context.
     */
    public static ResolveInfo getResolveInfoForViewIntent(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SAMPLE_URL));
        return pm.resolveActivity(intent, 0);
    }

    /**
     * @return Title of the menu item for opening a link in the default browser.
     * @param forceChromeAsDefault Whether the Custom Tab is created by Chrome.
     */
    public static String getTitleOpenInDefaultBrowser(final boolean forceChromeAsDefault) {
        if (sDefaultBrowserFetcher == null) {
            initBrowserFetcher();
        }
        try {
            // If the Custom Tab was created by Chrome, Chrome should handle the action for the
            // overflow menu.
            return forceChromeAsDefault ? sDefaultBrowserFetcher.get().get(0)
                                        : sDefaultBrowserFetcher.get().get(1);
        } catch (InterruptedException | ExecutionException e) {
            return ContextUtils.getApplicationContext().getString(
                    R.string.menu_open_in_product_default);
        }
    }
}
