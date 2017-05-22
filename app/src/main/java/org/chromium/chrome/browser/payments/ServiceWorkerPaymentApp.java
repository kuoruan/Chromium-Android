// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;

import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This app class represents a service worker based payment app.
 *
 * Such apps are implemented as service workers according to the Payment
 * App API specification.
 *
 * @see https://w3c.github.io/webpayments-payment-apps-api/
 */
public class ServiceWorkerPaymentApp implements PaymentApp {
    private final WebContents mWebContents;
    private final ServiceWorkerPaymentAppBridge.Manifest mManifest;
    private final Set<String> mMethodNames;

    /**
     * Build a service worker payment app instance based on an installed manifest.
     *
     * @see https://w3c.github.io/webpayments-payment-apps-api/#payment-app-manifest
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     * @param manifest    A manifest that describes this payment app.
     */
    public ServiceWorkerPaymentApp(
            WebContents webContents, ServiceWorkerPaymentAppBridge.Manifest manifest) {
        mWebContents = webContents;
        mManifest = manifest;

        mMethodNames = new HashSet<>();
        for (ServiceWorkerPaymentAppBridge.Option option : manifest.options) {
            mMethodNames.addAll(option.enabledMethods);
        }
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> unusedMethodDataMap,
            String unusedOrigin, byte[][] unusedCertificateChain,
            final InstrumentsCallback callback) {
        final List<PaymentInstrument> instruments =
                new ArrayList<PaymentInstrument>();

        for (ServiceWorkerPaymentAppBridge.Option option : mManifest.options) {
            instruments.add(new ServiceWorkerPaymentInstrument(
                    mWebContents, mManifest.registrationId, option));
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                callback.onInstrumentsReady(ServiceWorkerPaymentApp.this, instruments);
            }
        });
    }

    @Override
    public Set<String> getAppMethodNames() {
        return Collections.unmodifiableSet(mMethodNames);
    }

    @Override
    public boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodsAndData) {
        // TODO(tommyt): crbug.com/669876. Implement this for Service Worker Payment Apps.
        return true;
    }

    @Override
    public String getAppIdentifier() {
        return "Chrome_Service_Worker_Payment_App";
    }
}
