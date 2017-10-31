// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.net.URI;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Native bridge for interacting with service worker based payment apps.
 */
// TODO(tommyt): crbug.com/669876. Remove these suppressions when we actually
// start using all of the functionality in this class.
@SuppressFBWarnings({"UWF_NULL_FIELD", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"})
public class ServiceWorkerPaymentAppBridge implements PaymentAppFactory.PaymentAppFactoryAddition {
    private static final String TAG = "SWPaymentApp";
    private static boolean sCanMakePaymentForTesting;

    /**
     * The interface for the requester to check whether a SW payment app can make payment.
     */
    static interface CanMakePaymentCallback {
        /**
         * Called by this app to provide an information whether can make payment asynchronously.
         *
         * @param canMakePayment Indicates whether a SW payment app can make payment.
         */
        public void onCanMakePaymentResponse(boolean canMakePayment);
    }

    @Override
    public void create(WebContents webContents, Set<String> methodNames,
            PaymentAppFactory.PaymentAppCreatedCallback callback) {
        nativeGetAllPaymentApps(webContents, callback);
    }

    /**
     * Returns whether the app can make a payment.
     *
     * @param webContents      The web contents that invoked PaymentRequest.
     * @param registrationId   The service worker registration ID of the Payment App.
     * @param origin           The origin of this merchant.
     * @param iframeOrigin     The origin of the iframe that invoked PaymentRequest. Same as origin
     *                         if PaymentRequest was not invoked from inside an iframe.
     * @param methodData       The PaymentMethodData objects that are relevant for this payment
     *                         app.
     * @param modifiers        Payment method specific modifiers to the payment items and the total.
     * @param callback         Called after the payment app is finished running.
     */
    public static void canMakePayment(WebContents webContents, long registrationId, String origin,
            String iframeOrigin, Set<PaymentMethodData> methodData,
            Set<PaymentDetailsModifier> modifiers, CanMakePaymentCallback callback) {
        if (sCanMakePaymentForTesting) {
            callback.onCanMakePaymentResponse(true);
            return;
        }
        nativeCanMakePayment(webContents, registrationId, origin, iframeOrigin,
                methodData.toArray(new PaymentMethodData[0]),
                modifiers.toArray(new PaymentDetailsModifier[0]), callback);
    }

    /**
     * Make canMakePayment() return true always for testing purpose.
     *
     * @param canMakePayment Indicates whether a SW payment app can make payment.
     */
    @VisibleForTesting
    public static void setCanMakePaymentForTesting(boolean canMakePayment) {
        sCanMakePaymentForTesting = canMakePayment;
    }

    /**
     * Invoke a payment app with a given option and matching method data.
     *
     * @param webContents      The web contents that invoked PaymentRequest.
     * @param registrationId   The service worker registration ID of the Payment App.
     * @param origin           The origin of this merchant.
     * @param iframeOrigin     The origin of the iframe that invoked PaymentRequest. Same as origin
     *                         if PaymentRequest was not invoked from inside an iframe.
     * @param paymentRequestId The unique identifier of the PaymentRequest.
     * @param methodData       The PaymentMethodData objects that are relevant for this payment
     *                         app.
     * @param total            The PaymentItem that represents the total cost of the payment.
     * @param modifiers        Payment method specific modifiers to the payment items and the total.
     * @param callback         Called after the payment app is finished running.
     */
    public static void invokePaymentApp(WebContents webContents, long registrationId, String origin,
            String iframeOrigin, String paymentRequestId, Set<PaymentMethodData> methodData,
            PaymentItem total, Set<PaymentDetailsModifier> modifiers,
            PaymentInstrument.InstrumentDetailsCallback callback) {
        nativeInvokePaymentApp(webContents, registrationId, origin, iframeOrigin, paymentRequestId,
                methodData.toArray(new PaymentMethodData[0]), total,
                modifiers.toArray(new PaymentDetailsModifier[0]), callback);
    }

    /**
     * Abort invocation of the payment app.
     *
     * @param webContents      The web contents that invoked PaymentRequest.
     * @param registrationId   The service worker registration ID of the Payment App.
     * @param callback         Called after abort invoke payment app is finished running.
     */
    public static void abortPaymentApp(WebContents webContents, long registrationId,
            PaymentInstrument.AbortCallback callback) {
        nativeAbortPaymentApp(webContents, registrationId, callback);
    }

    @CalledByNative
    private static String[] getSupportedMethodsFromMethodData(PaymentMethodData data) {
        return data.supportedMethods;
    }

    @CalledByNative
    private static String getStringifiedDataFromMethodData(PaymentMethodData data) {
        return data.stringifiedData;
    }

    @CalledByNative
    private static PaymentMethodData getMethodDataFromModifier(PaymentDetailsModifier modifier) {
        return modifier.methodData;
    }

    @CalledByNative
    private static PaymentItem getTotalFromModifier(PaymentDetailsModifier modifier) {
        return modifier.total;
    }

    @CalledByNative
    private static String getLabelFromPaymentItem(PaymentItem item) {
        return item.label;
    }

    @CalledByNative
    private static String getCurrencyFromPaymentItem(PaymentItem item) {
        return item.amount.currency;
    }

    @CalledByNative
    private static String getValueFromPaymentItem(PaymentItem item) {
        return item.amount.value;
    }

    @CalledByNative
    private static String getCurrencySystemFromPaymentItem(PaymentItem item) {
        return item.amount.currencySystem;
    }

    @CalledByNative
    private static void onPaymentAppCreated(long registrationId, String scope, String label,
            @Nullable String sublabel, @Nullable String tertiarylabel, @Nullable Bitmap icon,
            String[] methodNameArray, String[] preferredRelatedApplications,
            WebContents webContents, Object callback) {
        Context context = ChromeActivity.fromWebContents(webContents);
        if (context == null) return;
        URI scopeUri = UriUtils.parseUriFromString(scope);
        if (scopeUri == null) {
            Log.e(TAG, "%s service worker scope is not a valid URI", scope);
            return;
        }
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback)
                .onPaymentAppCreated(new ServiceWorkerPaymentApp(webContents, registrationId,
                        scopeUri, label, sublabel, tertiarylabel,
                        icon == null ? null : new BitmapDrawable(context.getResources(), icon),
                        methodNameArray, preferredRelatedApplications));
    }

    @CalledByNative
    private static void onAllPaymentAppsCreated(Object callback) {
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback).onAllPaymentAppsCreated();
    }

    @CalledByNative
    private static void onPaymentAppInvoked(
            Object callback, String methodName, String stringifiedDetails) {
        if (TextUtils.isEmpty(methodName)) {
            ((PaymentInstrument.InstrumentDetailsCallback) callback).onInstrumentDetailsError();
        } else {
            ((PaymentInstrument.InstrumentDetailsCallback) callback)
                    .onInstrumentDetailsReady(methodName, stringifiedDetails);
        }
    }

    @CalledByNative
    private static void onPaymentAppAborted(Object callback, boolean result) {
        ((PaymentInstrument.AbortCallback) callback).onInstrumentAbortResult(result);
    }

    @CalledByNative
    private static void onCanMakePayment(Object callback, boolean canMakePayment) {
        assert callback instanceof CanMakePaymentCallback;
        ((CanMakePaymentCallback) callback).onCanMakePaymentResponse(canMakePayment);
    }

    /*
     * TODO(tommyt): crbug.com/505554. Change the |callback| parameter below to
     * be of type PaymentInstrument.InstrumentDetailsCallback, once this JNI bug
     * has been resolved.
     */
    private static native void nativeGetAllPaymentApps(WebContents webContents, Object callback);

    /*
     * TODO(tommyt): crbug.com/505554. Change the |callback| parameter below to
     * be of type PaymentInstrument.InstrumentDetailsCallback, once this JNI bug
     * has been resolved.
     */
    private static native void nativeInvokePaymentApp(WebContents webContents, long registrationId,
            String topLevelOrigin, String paymentRequestOrigin, String paymentRequestId,
            PaymentMethodData[] methodData, PaymentItem total, PaymentDetailsModifier[] modifiers,
            Object callback);

    /*
     * TODO(tommyt): crbug.com/505554. Change the |callback| parameter below to
     * be of type PaymentInstrument.InstrumentDetailsCallback, once this JNI bug
     * has been resolved.
     */
    private static native void nativeAbortPaymentApp(
            WebContents webContents, long registrationId, Object callback);

    /*
     * TODO(zino): crbug.com/505554. Change the |callback| parameter below to
     * be of type PaymentInstrument.InstrumentDetailsCallback, once this JNI bug
     * has been resolved.
     */
    private static native void nativeCanMakePayment(WebContents webContents, long registrationId,
            String topLevelOrigin, String paymentRequestOrigin, PaymentMethodData[] methodData,
            PaymentDetailsModifier[] modifiers, Object callback);
}
