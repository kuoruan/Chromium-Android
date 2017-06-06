// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.payments.PaymentAppFactory.PaymentAppCreatedCallback;
import org.chromium.chrome.browser.payments.PaymentManifestVerifier.ManifestVerifyCallback;
import org.chromium.components.payments.PaymentManifestDownloader;
import org.chromium.components.payments.PaymentManifestParser;
import org.chromium.content_public.browser.WebContents;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Finds installed native Android payment apps and verifies their signatures according to the
 * payment method manifests. The manifests are located based on the payment method name, which
 * is a URI that starts with "https://". The "basic-card" payment method is an exception: it's a
 * common payment method that does not have a manifest and can be used by any payment app.
 */
public class AndroidPaymentAppFinder implements ManifestVerifyCallback {
    private static final String TAG = "cr_PaymentAppFinder";

    /** The name of the intent for the service to check whether an app is ready to pay. */
    /* package */ static final String ACTION_IS_READY_TO_PAY =
                          "org.chromium.intent.action.IS_READY_TO_PAY";

    /**
     * The basic-card payment method name used by merchant and defined by W3C:
     * https://w3c.github.io/webpayments-methods-card/#method-id
     */
    /* package */ static final String BASIC_CARD_PAYMENT_METHOD = "basic-card";

    /**
     * Meta data name of an app's supported payment method names.
     */
    static final String META_DATA_NAME_OF_PAYMENT_METHOD_NAMES =
            "org.chromium.payment_method_names";

    /**
     * Meta data name of an app's supported default payment method name.
     */
    static final String META_DATA_NAME_OF_DEFAULT_PAYMENT_METHOD_NAME =
            "org.chromium.default_payment_method_name";

    /** The maximum number of payment method manifests to download. */
    private static final int MAX_NUMBER_OF_MANIFESTS = 10;

    private final WebContents mWebContents;
    private final boolean mQueryBasicCard;
    private final Set<URI> mPaymentMethods;
    private final PaymentManifestDownloader mDownloader;
    private final PaymentManifestParser mParser;
    private final PackageManagerDelegate mPackageManagerDelegate;
    private final PaymentAppCreatedCallback mCallback;
    private final boolean mIsIncognito;

    /**
     * A map of payment method names to the list of (yet) unverified Android apps that claim to
     * handle these methods. Example payment method names in this data structure:
     * "https://bobpay.com", "https://android.com/pay". Basic card is excluded.
     */
    private final Map<URI, Set<ResolveInfo>> mPendingApps;

    /** A map of Android package name to the payment app. */
    private final Map<String, AndroidPaymentApp> mResult;

    /**
     * Whether payment apps are required to have an intent filter with a single PAY action and no
     * additional data, i.e., whether payments apps are required to show up in "Autofill and
     * Payments" settings.
     */
    private final boolean mRequireShowInSettings;

    /**
     * The intent filter for a single PAY action and no additional data. Used to filter out payment
     * apps that don't show up in "Autofill and Payments" settings.
     */
    private final Intent mSettingsLookup;

    /**
     * Finds native Android payment apps.
     *
     * @param webContents            The web contents that invoked the web payments API.
     * @param methods                The list of payment methods requested by the merchant. For
     *                               example, "https://bobpay.com", "https://android.com/pay",
     *                               "basic-card".
     * @param requireShowInSettings  Whether payment apps are required to show up in "autofill and
     *                               Payments" settings.
     * @param downloader             The manifest downloader.
     * @param parser                 The manifest parser.
     * @param packageManagerDelegate The package information retriever.
     * @param callback               The asynchronous callback to be invoked (on the UI thread) when
     *                               all Android payment apps have been found.
     */
    public static void find(WebContents webContents, Set<String> methods,
            boolean requireShowInSettings, PaymentManifestDownloader downloader,
            PaymentManifestParser parser, PackageManagerDelegate packageManagerDelegate,
            PaymentAppCreatedCallback callback) {
        new AndroidPaymentAppFinder(webContents, methods, requireShowInSettings, downloader, parser,
                packageManagerDelegate, callback)
                .findAndroidPaymentApps();
    }

    private AndroidPaymentAppFinder(WebContents webContents, Set<String> methods,
            boolean requireShowInSettings, PaymentManifestDownloader downloader,
            PaymentManifestParser parser, PackageManagerDelegate packageManagerDelegate,
            PaymentAppCreatedCallback callback) {
        mWebContents = webContents;
        mQueryBasicCard = methods.contains(BASIC_CARD_PAYMENT_METHOD);
        mPaymentMethods = new HashSet<>();
        for (String method : methods) {
            assert !TextUtils.isEmpty(method);

            if (!method.startsWith(UrlConstants.HTTPS_URL_PREFIX)) continue;

            URI uri;
            try {
                // Don't use java.net.URL, because it performs a synchronous DNS lookup in
                // the constructor.
                uri = new URI(method);
            } catch (URISyntaxException e) {
                continue;
            }

            if (uri.isAbsolute()) {
                assert UrlConstants.HTTPS_SCHEME.equals(uri.getScheme());
                mPaymentMethods.add(uri);
            }
        }

        mDownloader = downloader;
        mParser = parser;
        mPackageManagerDelegate = packageManagerDelegate;
        mCallback = callback;
        mPendingApps = new HashMap<>();
        mResult = new HashMap<>();
        ChromeActivity activity = ChromeActivity.fromWebContents(mWebContents);
        mIsIncognito = activity != null && activity.getCurrentTabModel() != null
                && activity.getCurrentTabModel().isIncognito();
        mRequireShowInSettings = requireShowInSettings;
        mSettingsLookup = new Intent(AndroidPaymentApp.ACTION_PAY);
    }

    private void findAndroidPaymentApps() {
        Intent payIntent = new Intent(AndroidPaymentApp.ACTION_PAY);
        List<ResolveInfo> apps =
                mPackageManagerDelegate.getActivitiesThatCanRespondToIntentWithMetaData(payIntent);
        if (apps.isEmpty()) {
            onSearchFinished();
            return;
        }

        List<String[]> appSupportedMethods = new ArrayList<String[]>();
        for (int i = 0; i < apps.size(); i++) {
            appSupportedMethods.add(getPaymentMethodNames(apps.get(i).activityInfo));
        }

        List<String> appSupportedDefaultMethods = new ArrayList<String>();
        for (int i = 0; i < apps.size(); i++) {
            appSupportedDefaultMethods.add(getDefaultPaymentMethodName(apps.get(i).activityInfo));
        }

        List<PaymentManifestVerifier> verifiers = new ArrayList<>();
        for (URI methodName : mPaymentMethods) {
            List<ResolveInfo> supportedApps = filterAppsByMethodName(
                    apps, appSupportedMethods, appSupportedDefaultMethods, methodName.toString());
            if (supportedApps.isEmpty()) continue;

            // Start the parser utility process as soon as possible, once we know that a
            // manifest file needs to be parsed. The startup can take up to 2 seconds.
            if (!mParser.isUtilityProcessRunning()) mParser.startUtilityProcess();

            verifiers.add(new PaymentManifestVerifier(methodName, supportedApps, mDownloader,
                    mParser, mPackageManagerDelegate, this /* callback */));
            mPendingApps.put(methodName, new HashSet<>(supportedApps));

            if (verifiers.size() == MAX_NUMBER_OF_MANIFESTS) {
                Log.d(TAG, "Reached maximum number of allowed payment app manifests.");
                break;
            }
        }

        if (mQueryBasicCard) {
            List<ResolveInfo> supportedApps = filterAppsByMethodName(apps, appSupportedMethods,
                    appSupportedDefaultMethods, BASIC_CARD_PAYMENT_METHOD);
            for (int i = 0; i < supportedApps.size(); i++) {
                // Chrome does not verify app manifests for "basic-card" support.
                onValidPaymentApp(BASIC_CARD_PAYMENT_METHOD, supportedApps.get(i));
            }
        }

        if (verifiers.isEmpty()) {
            onSearchFinished();
            return;
        }

        for (int i = 0; i < verifiers.size(); i++) {
            verifiers.get(i).verify();
        }
    }

    @Nullable
    private String[] getPaymentMethodNames(ActivityInfo activityInfo) {
        if (activityInfo.metaData == null) return null;

        int resId = activityInfo.metaData.getInt(META_DATA_NAME_OF_PAYMENT_METHOD_NAMES);
        if (resId == 0) return null;

        Resources resources =
                mPackageManagerDelegate.getResourcesForApplication(activityInfo.applicationInfo);
        if (resources == null) return null;
        return resources.getStringArray(resId);
    }

    @Nullable
    private String getDefaultPaymentMethodName(ActivityInfo activityInfo) {
        if (activityInfo.metaData == null) return null;

        return activityInfo.metaData.getString(META_DATA_NAME_OF_DEFAULT_PAYMENT_METHOD_NAME);
    }

    private static List<ResolveInfo> filterAppsByMethodName(List<ResolveInfo> apps,
            List<String[]> appsMethods, List<String> appsDefaultMethods, String targetMethodName) {
        assert apps.size() == appsMethods.size();
        assert apps.size() == appsDefaultMethods.size();

        // Note that apps, appsMethods and appsDefaultMethods must have the same size. And the
        // information at the same index must correspond to the same app.
        List<ResolveInfo> supportedApps = new ArrayList<ResolveInfo>();
        for (int i = 0; i < apps.size(); i++) {
            if (targetMethodName.equals(appsDefaultMethods.get(i))) {
                supportedApps.add(apps.get(i));
                continue;
            }

            String[] methods = appsMethods.get(i);
            if (methods == null) continue;
            for (int j = 0; j < methods.length; j++) {
                if (targetMethodName.equals(methods[j])) {
                    supportedApps.add(apps.get(i));
                    break;
                }
            }
        }
        return supportedApps;
    }

    @Override
    public void onValidPaymentApp(URI methodName, ResolveInfo resolveInfo) {
        onValidPaymentApp(methodName.toString(), resolveInfo);
        removePendingApp(methodName, resolveInfo);
    }

    /** Same as above, but also works for non-URI method names, e.g., "basic-card". */
    private void onValidPaymentApp(String methodName, ResolveInfo resolveInfo) {
        String packageName = resolveInfo.activityInfo.packageName;
        AndroidPaymentApp app = mResult.get(packageName);
        if (app == null) {
            if (mRequireShowInSettings) {
                mSettingsLookup.setPackage(packageName);
                if (mPackageManagerDelegate.resolveActivity(mSettingsLookup) == null) return;
            }
            CharSequence label = mPackageManagerDelegate.getAppLabel(resolveInfo);
            if (TextUtils.isEmpty(label)) {
                Log.d(TAG,
                        String.format(Locale.getDefault(), "Skipping '%s' because of empty label.",
                                packageName));
                return;
            }
            app = new AndroidPaymentApp(mWebContents, packageName, resolveInfo.activityInfo.name,
                    label.toString(), mPackageManagerDelegate.getAppIcon(resolveInfo),
                    mIsIncognito);
            mResult.put(packageName, app);
        }
        app.addMethodName(methodName);
    }

    @Override
    public void onInvalidPaymentApp(URI methodName, ResolveInfo resolveInfo) {
        removePendingApp(methodName, resolveInfo);
    }

    /** Removes the (method, app) pair from the list of pending information to be verified. */
    private void removePendingApp(URI methodName, ResolveInfo resolveInfo) {
        Set<ResolveInfo> pendingAppsForMethod = mPendingApps.get(methodName);
        pendingAppsForMethod.remove(resolveInfo);
        if (pendingAppsForMethod.isEmpty()) mPendingApps.remove(methodName);
        if (mPendingApps.isEmpty()) onSearchFinished();
    }

    @Override
    public void onInvalidManifest(URI methodName) {
        mPendingApps.remove(methodName);
        if (mPendingApps.isEmpty()) onSearchFinished();
    }

    /**
     * Checks for IS_READY_TO_PAY service in each valid payment app and returns the valid apps
     * to the caller. Called when finished verifying all payment methods and apps.
     */
    private void onSearchFinished() {
        assert mPendingApps.isEmpty();

        if (mParser.isUtilityProcessRunning()) mParser.stopUtilityProcess();

        if (!mIsIncognito) {
            List<ResolveInfo> resolveInfos =
                    mPackageManagerDelegate.getServicesThatCanRespondToIntent(
                            new Intent(ACTION_IS_READY_TO_PAY));
            for (int i = 0; i < resolveInfos.size(); i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                AndroidPaymentApp app = mResult.get(resolveInfo.serviceInfo.packageName);
                if (app != null) app.setIsReadyToPayAction(resolveInfo.serviceInfo.name);
            }
        }

        for (Map.Entry<String, AndroidPaymentApp> entry : mResult.entrySet()) {
            mCallback.onPaymentAppCreated(entry.getValue());
        }

        mCallback.onAllPaymentAppsCreated();
    }
}
