// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;

import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * This app class represents a service worker based payment app.
 *
 * Such apps are implemented as service workers according to the Payment
 * Handler API specification.
 *
 * @see https://w3c.github.io/webpayments-payment-handler/
 */
public class ServiceWorkerPaymentApp extends PaymentInstrument implements PaymentApp {
    private final WebContents mWebContents;
    private final long mRegistrationId;
    private final Drawable mIcon;
    private final Set<String> mMethodNames;
    private final boolean mCanPreselect;

    /**
     * Build a service worker payment app instance per origin.
     *
     * @see https://w3c.github.io/webpayments-payment-handler/#structure-of-a-web-payment-app
     *
     * @param webContents       The web contents where PaymentRequest was invoked.
     * @param registrationId    The registration id of the corresponding service worker payment app.
     * @param label             The label of the payment app.
     * @param sublabel          The sublabel of the payment app.
     * @param icon              The drawable icon of the payment app.
     * @param methodNames       A set of payment method names supported by the payment app.
     */
    public ServiceWorkerPaymentApp(WebContents webContents, long registrationId, String label,
            @Nullable String sublabel, @Nullable Drawable icon, String[] methodNames) {
        super(label + sublabel, label, sublabel, icon);
        mWebContents = webContents;
        mRegistrationId = registrationId;
        mIcon = icon;

        // Sublabel and/or icon are set to null if fetching or processing the corresponding web app
        // manifest failed. Then do not preselect this payment app.
        mCanPreselect = !TextUtils.isEmpty(sublabel) && icon != null;

        mMethodNames = new HashSet<>();
        for (int i = 0; i < methodNames.length; i++) {
            mMethodNames.add(methodNames[i]);
        }
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> unusedMethodDataMap,
            String unusedOrigin, String unusedIFrameOrigin, byte[][] unusedCertificateChain,
            PaymentItem unusedItem, final InstrumentsCallback callback) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                List<PaymentInstrument> instruments = new ArrayList();
                instruments.add(ServiceWorkerPaymentApp.this);
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
        Set<String> methodNames = new HashSet<>(methodsAndData.keySet());
        methodNames.retainAll(mMethodNames);
        return !methodNames.isEmpty();
    }

    @Override
    public String getAppIdentifier() {
        return getIdentifier();
    }

    @Override
    public int getAdditionalAppTextResourceId() {
        return 0;
    }

    @Override
    public Set<String> getInstrumentMethodNames() {
        return getAppMethodNames();
    }

    @Override
    public void invokePaymentApp(String id, String merchantName, String origin, String iframeOrigin,
            byte[][] unusedCertificateChain, Map<String, PaymentMethodData> methodData,
            PaymentItem total, List<PaymentItem> displayItems,
            Map<String, PaymentDetailsModifier> modifiers, InstrumentDetailsCallback callback) {
        ServiceWorkerPaymentAppBridge.invokePaymentApp(mWebContents, mRegistrationId, origin,
                iframeOrigin, id, new HashSet<>(methodData.values()), total,
                new HashSet<>(modifiers.values()), callback);
    }

    @Override
    public void dismissInstrument() {}

    @Override
    public boolean canPreselect() {
        return mCanPreselect;
    }
}
