// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

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
    @Override
    public void create(WebContents webContents, Set<String> methodNames,
            PaymentAppFactory.PaymentAppCreatedCallback callback) {
        nativeGetAllPaymentApps(webContents, callback);
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
    private static void onPaymentAppCreated(long registrationId, String label,
            @Nullable String sublabel, @Nullable Bitmap icon, String[] methodNameArray,
            WebContents webContents, Object callback) {
        assert callback instanceof PaymentAppFactory.PaymentAppCreatedCallback;
        Context context = ChromeActivity.fromWebContents(webContents);
        if (context == null) return;
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback)
                .onPaymentAppCreated(new ServiceWorkerPaymentApp(webContents, registrationId, label,
                        sublabel,
                        icon == null ? null : new BitmapDrawable(context.getResources(), icon),
                        methodNameArray));
    }

    @CalledByNative
    private static void onAllPaymentAppsCreated(Object callback) {
        assert callback instanceof PaymentAppFactory.PaymentAppCreatedCallback;
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback).onAllPaymentAppsCreated();
    }

    @CalledByNative
    private static void onPaymentAppInvoked(
            Object callback, String methodName, String stringifiedDetails) {
        assert callback instanceof PaymentInstrument.InstrumentDetailsCallback;
        if (methodName == null) {
            ((PaymentInstrument.InstrumentDetailsCallback) callback).onInstrumentDetailsError();
        } else {
            ((PaymentInstrument.InstrumentDetailsCallback) callback)
                    .onInstrumentDetailsReady(methodName, stringifiedDetails);
        }
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
}
