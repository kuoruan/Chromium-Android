// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.client;

import static org.chromium.webapk.lib.common.WebApkConstants.WEBAPK_PACKAGE_PREFIX;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.util.Log;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Checks whether a URL belongs to a WebAPK, and whether a WebAPK is signed by the WebAPK Minting
 * Server.
 */
public class WebApkValidator {

    private static final String TAG = "WebApkValidator";
    private static byte[] sExpectedSignature;

    /**
     * Queries the PackageManager to determine whether a WebAPK can handle the URL. Ignores
     * whether the user has selected a default handler for the URL and whether the default
     * handler is the WebAPK.
     *
     * NOTE(yfriedman): This can fail if multiple WebAPKs can match the supplied url.
     *
     * @param context The application context.
     * @param url The url to check.
     * @return Package name of WebAPK which can handle the URL. Null if the url should not be
     * handled by a WebAPK.
     */
    public static String queryWebApkPackage(Context context, String url) {
        return findWebApkPackage(context, resolveInfosForUrl(context, url));
    }

    /**
     * Queries the PackageManager to determine whether a WebAPK can handle the URL. Ignores
     * whether the user has selected a default handler for the URL and whether the default
     * handler is the WebAPK.
     *
     * NOTE: This can fail if multiple WebAPKs can match the supplied url.
     *
     * @param context The application context.
     * @param url The url to check.
     * @return Resolve Info of a WebAPK which can handle the URL. Null if the url should not be
     * handled by a WebAPK.
     */
    public static ResolveInfo queryResolveInfo(Context context, String url) {
        return findResolveInfo(context, resolveInfosForUrl(context, url));
    }

    /**
     * Fetches the list of resolve infos from the PackageManager that can handle the URL.
     *
     * @param context The application context.
     * @param url The url to check.
     * @return The list of resolve infos found that can handle the URL.
     */
    private static List<ResolveInfo> resolveInfosForUrl(Context context, String url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return new LinkedList<>();
        }

        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        Intent selector = intent.getSelector();
        if (selector != null) {
            selector.addCategory(Intent.CATEGORY_BROWSABLE);
            selector.setComponent(null);
        }
        return context.getPackageManager().queryIntentActivities(
                intent, PackageManager.GET_RESOLVED_FILTER);
    }

    /**
     * @param context The context to use to check whether WebAPK is valid.
     * @param infos The ResolveInfos to search.
     * @return Package name of the ResolveInfo which corresponds to a WebAPK. Null if none of the
     * ResolveInfos corresponds to a WebAPK.
     */
    public static String findWebApkPackage(Context context, List<ResolveInfo> infos) {
        ResolveInfo resolveInfo = findResolveInfo(context, infos);
        if (resolveInfo != null) {
            return resolveInfo.activityInfo.packageName;
        }
        return null;
    }

    /**
     * @param context The context to use to check whether WebAPK is valid.
     * @param infos The ResolveInfos to search.
     * @return ResolveInfo which corresponds to a WebAPK. Null if none of the ResolveInfos
     * corresponds to a WebAPK.
     */
    private static ResolveInfo findResolveInfo(Context context, List<ResolveInfo> infos) {
        for (ResolveInfo info : infos) {
            if (info.activityInfo != null
                    && isValidWebApk(context, info.activityInfo.packageName)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Returns whether the provided WebAPK is installed and passes signature checks.
     * @param context A context
     * @param webappPackageName The package name to check
     * @return true iff the WebAPK is installed and passes security checks
     */
    public static boolean isValidWebApk(Context context, String webappPackageName) {
        if (sExpectedSignature == null) {
            Log.wtf(TAG, "WebApk validation failure - expected signature not set."
                    + "missing call to WebApkValidator.initWithBrowserHostSignature");
        }
        if (!webappPackageName.startsWith(WEBAPK_PACKAGE_PREFIX)) {
            return false;
        }
        // check signature
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(webappPackageName,
                    PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            Log.d(TAG, "WebApk not found");
            return false;
        }

        final Signature[] arrSignatures = packageInfo.signatures;
        if (arrSignatures != null && arrSignatures.length == 2) {
            for (Signature signature : arrSignatures) {
                if (Arrays.equals(sExpectedSignature, signature.toByteArray())) {
                    Log.d(TAG, "WebApk valid - signature match!");
                    return true;
                }
            }
        }
        Log.d(TAG, "WebApk invalid");
        return false;
    }

    /**
     * Initializes the WebApkValidator with the expected signature that WebAPKs must be signed
     * with for the current host.
     * @param expectedSignature
     */
    public static void initWithBrowserHostSignature(byte[] expectedSignature) {
        if (sExpectedSignature != null) {
            return;
        }
        sExpectedSignature = Arrays.copyOf(expectedSignature, expectedSignature.length);
    }
}
