// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.explore_sites;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
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

    /**
     * Fetches the catalog data for Explore page.
     */
    public static void getEspCatalog(
            Profile profile, Callback<List<ExploreSitesCategory>> callback) {
        List<ExploreSitesCategory> result = new ArrayList<>();
        nativeGetEspCatalog(profile, result, callback);
    }

    /**
     * Fetches the catalog data for New Tab page.
     */
    public static void getNtpCatalog(
            Profile profile, Callback<List<ExploreSitesCategory>> callback) {
        List<ExploreSitesCategory> result = new ArrayList<>();
        nativeGetNtpCatalog(profile, result, callback);
    }

    /* These methods for Experimental Explore Sites */

    /**
     * Fetches a JSON string from URL, returning the parsed JSONobject in a callback.
     * This will cancel any pending JSON fetches.
     */
    public static void getNtpCategories(
            Profile profile, final Callback<List<ExploreSitesCategoryTile>> callback) {
        List<ExploreSitesCategoryTile> result = new ArrayList<>();
        nativeGetNtpCategories(profile, result, callback);
    }

    /**
     * Fetches an icon from a url and returns in a Bitmap image. The callback argument will be null
     * if the operation fails.
     */
    public static void getIcon(
            Profile profile, final String iconUrl, final Callback<Bitmap> callback) {
        nativeGetIcon(profile, iconUrl, callback);
    }

    private static native void nativeGetNtpCategories(Profile profile,
            List<ExploreSitesCategoryTile> result,
            Callback<List<ExploreSitesCategoryTile>> callback);
    private static native void nativeGetIcon(
            Profile profile, String iconUrl, Callback<Bitmap> callback);
    public static native String nativeGetCatalogUrl();

    private static native void nativeGetNtpCatalog(Profile profile,
            List<ExploreSitesCategory> result, Callback<List<ExploreSitesCategory>> callback);
    private static native void nativeGetEspCatalog(Profile profile,
            List<ExploreSitesCategory> result, Callback<List<ExploreSitesCategory>> callback);
}
