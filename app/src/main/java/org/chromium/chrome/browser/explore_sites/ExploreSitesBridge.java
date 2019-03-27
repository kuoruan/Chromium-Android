// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * The model and controller for a group of explore options.
 */
@JNINamespace("explore_sites")
public class ExploreSitesBridge {
    private static final String TAG = "ExploreSitesBridge";

    private static List<ExploreSitesCategory> sCatalogForTesting;
    public static void setCatalogForTesting(List<ExploreSitesCategory> catalog) {
        sCatalogForTesting = catalog;
    }

    /**
     * Fetches the catalog data for Explore page.
     *
     * Callback will be called with |null| if an error occurred.
     */
    public static void getEspCatalog(
            Profile profile, Callback<List<ExploreSitesCategory>> callback) {
        if (sCatalogForTesting != null) {
            callback.onResult(sCatalogForTesting);
            return;
        }

        List<ExploreSitesCategory> result = new ArrayList<>();
        nativeGetEspCatalog(profile, result, callback);
    }

    public static void getSiteImage(Profile profile, int siteID, Callback<Bitmap> callback) {
        if (sCatalogForTesting != null) {
            callback.onResult(null);
        }
        nativeGetIcon(profile, siteID, callback);
    }

    public static void getCategoryImage(
            Profile profile, int categoryID, int pixelSize, Callback<Bitmap> callback) {
        if (sCatalogForTesting != null) {
            callback.onResult(null);
        }
        nativeGetCategoryImage(profile, categoryID, pixelSize, callback);
    }

    /**
     * Causes a network request for updating the catalog.
     */
    public static void updateCatalogFromNetwork(
            Profile profile, boolean isImmediateFetch, Callback<Boolean> finishedCallback) {
        nativeUpdateCatalogFromNetwork(profile, isImmediateFetch, finishedCallback);
    }

    /**
     * Adds a site to the blacklist when the user chooses "remove" from the long press menu.
     */
    public static void blacklistSite(Profile profile, String url) {
        nativeBlacklistSite(profile, url);
    }

    /**
     * Records that a site has been clicked.
     */
    public static void recordClick(
            Profile profile, String url, @ExploreSitesCategory.CategoryType int type) {
        nativeRecordClick(profile, url, type);
    }

    /**
     * Gets the current Finch variation that is configured by flag or experiment.
     */
    @ExploreSitesVariation
    public static int getVariation() {
        return nativeGetVariation();
    }

    public static boolean isEnabled(@ExploreSitesVariation int variation) {
        return variation == ExploreSitesVariation.ENABLED
                || variation == ExploreSitesVariation.PERSONALIZED;
    }

    public static boolean isExperimental(@ExploreSitesVariation int variation) {
        return variation == ExploreSitesVariation.EXPERIMENT;
    }

    /**
     * Increments the ntp_shown_count for a particular category.
     * @param categoryId the row id of the category to increment show count for.
     */
    public static void incrementNtpShownCount(Profile profile, int categoryId) {
        nativeIncrementNtpShownCount(profile, categoryId);
    }

    @CalledByNative
    static void scheduleDailyTask() {
        ExploreSitesBackgroundTask.schedule(false /* updateCurrent */);
    }

    /**
     * Returns the scale factor on this device.
     */
    @CalledByNative
    static float getScaleFactorFromDevice() {
        // Get DeviceMetrics from context.
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) ContextUtils.getApplicationContext().getSystemService(
                 Context.WINDOW_SERVICE))
                .getDefaultDisplay()
                .getMetrics(metrics);
        // Get density and return it.
        return metrics.density;
    }

    static native int nativeGetVariation();
    private static native void nativeGetEspCatalog(Profile profile,
            List<ExploreSitesCategory> result, Callback<List<ExploreSitesCategory>> callback);

    private static native void nativeGetIcon(
            Profile profile, int siteID, Callback<Bitmap> callback);

    private static native void nativeUpdateCatalogFromNetwork(
            Profile profile, boolean isImmediateFetch, Callback<Boolean> callback);

    private static native void nativeGetCategoryImage(
            Profile profile, int categoryID, int pixelSize, Callback<Bitmap> callback);

    private static native void nativeBlacklistSite(Profile profile, String url);

    private static native void nativeRecordClick(Profile profile, String url, int type);

    private static native void nativeIncrementNtpShownCount(Profile profile, int categoryId);
}
