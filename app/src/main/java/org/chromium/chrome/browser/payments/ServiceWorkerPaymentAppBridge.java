// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.graphics.drawable.Drawable;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Native bridge for interacting with service worker based payment apps.
 */
// TODO(tommyt): crbug.com/669876. Remove these suppressions when we actually
// start using all of the functionality in this class.
@SuppressFBWarnings({"UWF_NULL_FIELD", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"})
public class ServiceWorkerPaymentAppBridge implements PaymentAppFactory.PaymentAppFactoryAddition {
    /**
     * This class represents a payment app manifest as defined in the Payment
     * App API specification.
     *
     * @see https://w3c.github.io/webpayments-payment-apps-api/#payment-app-manifest
     */
    public static class Manifest {
        /**
         * The registration ID of the service worker.
         *
         * This can be used to identify a service worker based payment app.
         */
        public long registrationId;
        public String label;
        public Drawable icon;
        public List<Option> options = new ArrayList<>();
    }

    /**
     * This class represents a payment option as defined in the Payment App API
     * specification.
     *
     * @see https://w3c.github.io/webpayments-payment-apps-api/#payment-app-options
     */
    public static class Option {
        public String id;
        public String label;
        public Drawable icon;
        public List<String> enabledMethods = new ArrayList<>();
    }

    @Override
    public void create(WebContents webContents, Set<String> methodNames,
            PaymentAppFactory.PaymentAppCreatedCallback callback) {
        nativeGetAllAppManifests(webContents, callback);
    }

    /**
     * Invoke a payment app with a given option and matching method data.
     *
     * @param webContents    The web contents that invoked PaymentRequest.
     * @param registrationId The service worker registration ID of the Payment App.
     * @param optionId       The ID of the PaymentOption that was selected by the user.
     * @param methodData     The PaymentMethodData objects that are relevant for this payment
     *                       app.
     * @param total          The PaymentItem that represents the total cost of the payment.
     * @param modifiers      Payment method specific modifiers to the payment items and the total.
     */
    public static void invokePaymentApp(WebContents webContents, long registrationId,
            String optionId, String origin, Set<PaymentMethodData> methodData, PaymentItem total,
            List<PaymentItem> displayItems, Set<PaymentDetailsModifier> modifiers) {
        nativeInvokePaymentApp(webContents, registrationId, optionId, origin,
                methodData.toArray(new PaymentMethodData[0]), total,
                modifiers.toArray(new PaymentDetailsModifier[0]));
    }

    @CalledByNative
    private static Manifest createManifest(long registrationId, String label, String icon) {
        Manifest manifest = new Manifest();
        manifest.registrationId = registrationId;
        manifest.label = label;
        // TODO(tommyt): crbug.com/669876. Handle icons.
        manifest.icon = null;
        return manifest;
    }

    @CalledByNative
    private static Option createAndAddOption(
            Manifest manifest, String id, String label, String icon) {
        Option option = new Option();
        option.id = id;
        option.label = label;
        // TODO(tommyt): crbug.com/669876. Handle icons.
        option.icon = null;
        manifest.options.add(option);
        return option;
    }

    @CalledByNative
    private static void addEnabledMethod(Option option, String enabledMethod) {
        option.enabledMethods.add(enabledMethod);
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
    private static void onGotManifest(Manifest manifest, WebContents webContents, Object callback) {
        assert callback instanceof PaymentAppFactory.PaymentAppCreatedCallback;
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback)
                .onPaymentAppCreated(new ServiceWorkerPaymentApp(webContents, manifest));
    }

    @CalledByNative
    private static void onGotAllManifests(Object callback) {
        assert callback instanceof PaymentAppFactory.PaymentAppCreatedCallback;
        ((PaymentAppFactory.PaymentAppCreatedCallback) callback).onAllPaymentAppsCreated();
    }

    /*
     * TODO(tommyt): crbug.com/505554. Change the |callback| parameter below to
     * be of type PaymentAppFactory.PaymentAppCreatedCallback, once this JNI bug
     * has been resolved.
     */
    private static native void nativeGetAllAppManifests(WebContents webContents, Object callback);

    private static native void nativeInvokePaymentApp(WebContents webContents, long registrationId,
            String optionId, String origin, PaymentMethodData[] methodData, PaymentItem total,
            PaymentDetailsModifier[] modifiers);
}
