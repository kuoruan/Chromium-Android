// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.client;

import static org.chromium.webapk.lib.common.WebApkConstants.WEBAPK_PACKAGE_PREFIX;
import static org.chromium.webapk.lib.common.WebApkMetaDataKeys.SCOPE;
import static org.chromium.webapk.lib.common.WebApkMetaDataKeys.START_URL;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Checks whether a URL belongs to a WebAPK, and whether a WebAPK is signed by the WebAPK Minting
 * Server.
 */
public class WebApkValidator {
    private static final String TAG = "WebApkValidator";
    private static final String KEY_FACTORY = "EC"; // aka "ECDSA"
    private static final String MAPSLITE_PACKAGE_NAME = "com.google.android.apps.mapslite";
    private static final String MAPSLITE_URL_PREFIX =
            "https://www.google.com/maps"; // Matches scope.

    private static byte[] sExpectedSignature;
    private static byte[] sCommentSignedPublicKeyBytes;
    private static PublicKey sCommentSignedPublicKey;
    private static boolean sOverrideValidationForTesting;

    /**
     * Queries the PackageManager to determine whether a WebAPK can handle the URL. Ignores whether
     * the user has selected a default handler for the URL and whether the default handler is the
     * WebAPK.
     *
     * <p>NOTE(yfriedman): This can fail if multiple WebAPKs can match the supplied url.
     *
     * @param context The application context.
     * @param url The url to check.
     * @return Package name of WebAPK which can handle the URL. Null if the url should not be
     * handled by a WebAPK.
     */
    public static @Nullable String queryWebApkPackage(Context context, String url) {
        return findWebApkPackage(context, resolveInfosForUrl(context, url));
    }

    /**
     * Queries the PackageManager to determine whether a WebAPK can handle the URL. Ignores whether
     * the user has selected a default handler for the URL and whether the default handler is the
     * WebAPK.
     *
     * <p>NOTE: This can fail if multiple WebAPKs can match the supplied url.
     *
     * @param context The application context.
     * @param url The url to check.
     * @return Resolve Info of a WebAPK which can handle the URL. Null if the url should not be
     *     handled by a WebAPK.
     */
    public static @Nullable ResolveInfo queryWebApkResolveInfo(Context context, String url) {
        return findWebApkResolveInfo(context, resolveInfosForUrl(context, url));
    }

    /**
     * @param context The context to use to check whether WebAPK is valid.
     * @param infos The {@link ResolveInfo}s to search.
     * @return Package name of the {@link ResolveInfo} which corresponds to a WebAPK. Null if none
     *         of the {@link ResolveInfo}s corresponds to a WebAPK.
     */
    public static @Nullable String findWebApkPackage(Context context, List<ResolveInfo> infos) {
        ResolveInfo resolveInfo = findWebApkResolveInfo(context, infos);
        if (resolveInfo != null) {
            return resolveInfo.activityInfo.packageName;
        }
        return null;
    }

    /**
     * Whether the given package corresponds to a WebAPK that can handle the URL.
     * @param context The application context.
     * @param webApkPackage The package to consider.
     * @param url The URL the package must be able to handle.
     * @return Whether the URL can be handled by that package.
     */
    public static boolean canWebApkHandleUrl(Context context, String webApkPackage, String url) {
        List<ResolveInfo> infos = resolveInfosForUrlAndOptionalPackage(context, url, webApkPackage);
        for (ResolveInfo info : infos) {
            if (info.activityInfo != null
                    && isValidWebApk(context, info.activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fetches a list of {@link ResolveInfo}s from the PackageManager that can handle the URL.
     * @param context The application context.
     * @param url The URL to check.
     * @return The list of {@link ResolveInfo}s found that can handle the URL.
     */
    public static List<ResolveInfo> resolveInfosForUrl(Context context, String url) {
        return resolveInfosForUrlAndOptionalPackage(context, url, null);
    }

    /**
     * Fetches the list of {@link ResolveInfo}s from the PackageManager that can handle the URL.
     *
     * @param context The application context.
     * @param url The URL to check.
     * @param applicationPackage The optional package name to set for intent resolution.
     * @return The list of {@link ResolveInfo}s found that can handle the URL.
     */
    private static List<ResolveInfo> resolveInfosForUrlAndOptionalPackage(
            Context context, String url, @Nullable String applicationPackage) {
        Intent intent;
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return new LinkedList<>();
        }

        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if (applicationPackage != null) {
            intent.setPackage(applicationPackage);
        } else {
            intent.setComponent(null);
        }
        Intent selector = intent.getSelector();
        if (selector != null) {
            selector.addCategory(Intent.CATEGORY_BROWSABLE);
            selector.setComponent(null);
        }

        // StrictMode is relaxed due to https://crbug.com/843092.
        StrictMode.ThreadPolicy policy = StrictMode.allowThreadDiskReads();
        try {
            return context.getPackageManager().queryIntentActivities(
                    intent, PackageManager.GET_RESOLVED_FILTER);
        } catch (Exception e) {
            // We used to catch only java.util.MissingResourceException, but we need to catch more
            // exceptions to handle "Package manager has died" exception.
            // http://crbug.com/794363
            return new LinkedList<>();
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }

    /**
     * Searches the given {@link ResolveInfo}s for one corresponding to a WebAPK.
     * @param context The context to use to check whether WebAPK is valid.
     * @param infos The {@link ResolveInfo}s to search.
     * @return {@link ResolveInfo} which corresponds to a WebAPK. Null if none of the ResolveInfos
     * corresponds to a WebAPK.
     */
    private static @Nullable ResolveInfo findWebApkResolveInfo(
            Context context, List<ResolveInfo> infos) {
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
        if (sExpectedSignature == null || sCommentSignedPublicKeyBytes == null) {
            Log.wtf(TAG,
                    "WebApk validation failure - expected signature not set."
                            + "missing call to WebApkValidator.initWithBrowserHostSignature");
            return false;
        }
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(webappPackageName,
                    PackageManager.GET_SIGNATURES | PackageManager.GET_META_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "WebApk not found");
            return false;
        }
        if (isNotWebApkQuick(packageInfo)) {
            return false;
        }
        if (sOverrideValidationForTesting) {
            Log.d(TAG, "Ok! Looks like a WebApk (has start url) and validation is disabled.");
            return true;
        }
        if (verifyV1WebApk(packageInfo, webappPackageName)) {
            return true;
        }
        if (verifyMapsLite(packageInfo, webappPackageName)) {
            Log.d(TAG, "Matches Maps Lite");
            return true;
        }
        return verifyCommentSignedWebApk(packageInfo);
    }

    /** Determine quickly whether this is definitely not a WebAPK */
    private static boolean isNotWebApkQuick(PackageInfo packageInfo) {
        if (packageInfo.applicationInfo == null || packageInfo.applicationInfo.metaData == null) {
            Log.e(TAG, "no application info, or metaData retrieved.");
            return true;
        }
        // Having the startURL in AndroidManifest.xml is a strong signal.
        String startUrl = packageInfo.applicationInfo.metaData.getString(START_URL);
        return TextUtils.isEmpty(startUrl);
    }

    private static boolean verifyV1WebApk(PackageInfo packageInfo, String webappPackageName) {
        if (packageInfo.signatures == null || packageInfo.signatures.length != 2
                || !webappPackageName.startsWith(WEBAPK_PACKAGE_PREFIX)) {
            return false;
        }
        for (Signature signature : packageInfo.signatures) {
            if (Arrays.equals(sExpectedSignature, signature.toByteArray())) {
                Log.d(TAG, "WebApk valid - signature match!");
                return true;
            }
        }
        return false;
    }

    private static boolean verifyMapsLite(PackageInfo packageInfo, String webappPackageName) {
        if (!webappPackageName.equals(MAPSLITE_PACKAGE_NAME)) {
            return false;
        }
        String startUrl = packageInfo.applicationInfo.metaData.getString(START_URL);
        if (startUrl == null || !startUrl.startsWith(MAPSLITE_URL_PREFIX)) {
            Log.d(TAG, "mapslite invalid startUrl prefix");
            return false;
        }
        String scope = packageInfo.applicationInfo.metaData.getString(SCOPE);
        if (scope == null || !scope.equals(MAPSLITE_URL_PREFIX)) {
            Log.d(TAG, "mapslite invalid scope prefix");
            return false;
        }
        return true;
    }

    /** Verify that the comment signed webapk matches the public key. */
    private static boolean verifyCommentSignedWebApk(PackageInfo packageInfo) {
        PublicKey commentSignedPublicKey;
        try {
            commentSignedPublicKey = getCommentSignedPublicKey();
        } catch (Exception e) {
            Log.e(TAG, "WebApk failed to get Public Key", e);
            return false;
        }
        if (commentSignedPublicKey == null) {
            Log.e(TAG, "WebApk validation failure - unable to decode public key");
            return false;
        }
        if (packageInfo.applicationInfo == null || packageInfo.applicationInfo.sourceDir == null) {
            Log.e(TAG, "WebApk validation failure - missing applicationInfo sourcedir");
            return false;
        }

        String packageFilename = packageInfo.applicationInfo.sourceDir;
        RandomAccessFile file = null;
        FileChannel inChannel = null;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();

        try {
            file = new RandomAccessFile(packageFilename, "r");
            inChannel = file.getChannel();

            MappedByteBuffer buf =
                    inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
            buf.load();

            WebApkVerifySignature v = new WebApkVerifySignature(buf);
            int result = v.read();
            if (result != WebApkVerifySignature.ERROR_OK) {
                Log.e(TAG, String.format("Failure reading %s: %s", packageFilename, result));
                return false;
            }
            result = v.verifySignature(commentSignedPublicKey);

            // TODO(scottkirkwood): remove this log once well tested.
            Log.d(TAG, "File " + packageFilename + ": " + result);
            return result == WebApkVerifySignature.ERROR_OK;
        } catch (Exception e) {
            Log.e(TAG, "WebApk file error for file " + packageFilename, e);
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException e) {
                }
            }
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Initializes the WebApkValidator.
     * @param expectedSignature V1 WebAPK RSA signature.
     * @param v2PublicKeyBytes New comment signed public key bytes as x509 encoded public key.
     */
    public static void init(byte[] expectedSignature, byte[] v2PublicKeyBytes) {
        if (sExpectedSignature == null) {
            sExpectedSignature = expectedSignature;
        }
        if (sCommentSignedPublicKeyBytes == null) {
            sCommentSignedPublicKeyBytes = v2PublicKeyBytes;
        }
    }

    /**
     * Disables all verification performed by this class. This is meant only for development with
     * unsigned WebApks and should never be enabled in a real build.
     */
    public static void disableValidationForTesting() {
        sOverrideValidationForTesting = true;
    }

    /**
     * Lazy evaluate the creation of the Public Key as the KeyFactories may not yet be initialized.
     * @return The decoded PublicKey or null
     */
    private static PublicKey getCommentSignedPublicKey() throws Exception {
        if (sCommentSignedPublicKey == null) {
            sCommentSignedPublicKey =
                    KeyFactory.getInstance(KEY_FACTORY)
                            .generatePublic(new X509EncodedKeySpec(sCommentSignedPublicKeyBytes));
        }
        return sCommentSignedPublicKey;
    }
}
