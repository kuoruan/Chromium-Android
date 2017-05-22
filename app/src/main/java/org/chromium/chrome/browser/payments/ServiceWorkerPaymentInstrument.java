// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This instrument class represents a single payment option for a service
 * worker based payment app.
 *
 * @see org.chromium.chrome.browser.payments.ServiceWorkerPaymentApp
 *
 * @see https://w3c.github.io/webpayments-payment-apps-api/
 */
public class ServiceWorkerPaymentInstrument extends PaymentInstrument {
    private final WebContents mWebContents;
    private final long mAppRegistrationId;
    private final ServiceWorkerPaymentAppBridge.Option mOption;
    private final Set<String> mMethodNames;

    /**
     * Build a service worker based payment instrument based on a single payment option
     * of an installed payment app.
     *
     * @see https://w3c.github.io/webpayments-payment-apps-api/#payment-app-options
     *
     * @param webContents       The web contents where PaymentRequest was invoked.
     * @param appRegistrationId The registration id of the corresponding service worker payment app.
     * @param option            A payment app option from the payment app.
     */
    public ServiceWorkerPaymentInstrument(WebContents webContents, long appRegistrationId,
            ServiceWorkerPaymentAppBridge.Option option) {
        super(Long.toString(appRegistrationId) + "#" + option.id, option.label, null /* icon */,
                option.icon);
        mWebContents = webContents;
        mAppRegistrationId = appRegistrationId;
        mOption = option;

        mMethodNames = new HashSet<String>(option.enabledMethods);
    }

    @Override
    public Set<String> getInstrumentMethodNames() {
        return Collections.unmodifiableSet(mMethodNames);
    }

    @Override
    public void invokePaymentApp(String merchantName, String origin,
            byte[][] unusedCertificateChain, Map<String, PaymentMethodData> methodData,
            PaymentItem total, List<PaymentItem> displayItems,
            Map<String, PaymentDetailsModifier> modifiers, InstrumentDetailsCallback callback) {
        ServiceWorkerPaymentAppBridge.invokePaymentApp(mWebContents, mAppRegistrationId, mOption.id,
                origin, new HashSet<>(methodData.values()), total, displayItems,
                new HashSet<>(modifiers.values()));
    }

    @Override
    public void dismissInstrument() {}
}
