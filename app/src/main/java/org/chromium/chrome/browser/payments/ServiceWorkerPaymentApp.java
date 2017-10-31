// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.net.URI;
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
    private final Set<String> mPreferredRelatedApplicationIds;
    private final boolean mIsIncognito;

    /**
     * Build a service worker payment app instance per origin.
     *
     * @see https://w3c.github.io/webpayments-payment-handler/#structure-of-a-web-payment-app
     *
     * @param webContents                    The web contents where PaymentRequest was invoked.
     * @param registrationId                 The registration id of the corresponding service worker
     *                                       payment app.
     * @param scope                          The registration scope of the corresponding service
     *                                       worker.
     * @param label                          The label of the payment app.
     * @param sublabel                       The sublabel of the payment app.
     * @param tertiarylabel                  The tertiary label of the payment app.
     * @param icon                           The drawable icon of the payment app.
     * @param methodNames                    A set of payment method names supported by the payment
     *                                       app.
     * @param preferredRelatedApplicationIds A set of preferred related application Ids.
     */
    public ServiceWorkerPaymentApp(WebContents webContents, long registrationId, URI scope,
            String label, @Nullable String sublabel, @Nullable String tertiarylabel,
            @Nullable Drawable icon, String[] methodNames,
            String[] preferredRelatedApplicationIds) {
        super(scope.toString(), label, sublabel, tertiarylabel, icon);
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

        mPreferredRelatedApplicationIds = new HashSet<>();
        Collections.addAll(mPreferredRelatedApplicationIds, preferredRelatedApplicationIds);

        ChromeActivity activity = ChromeActivity.fromWebContents(mWebContents);
        mIsIncognito = activity != null && activity.getCurrentTabModel() != null
                && activity.getCurrentTabModel().isIncognito();
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> methodDataMap, String origin,
            String iframeOrigin, byte[][] unusedCertificateChain,
            Map<String, PaymentDetailsModifier> modifiers, final InstrumentsCallback callback) {
        if (mIsIncognito) {
            new Handler().post(() -> {
                List<PaymentInstrument> instruments = new ArrayList();
                instruments.add(ServiceWorkerPaymentApp.this);
                callback.onInstrumentsReady(ServiceWorkerPaymentApp.this, instruments);
            });
            return;
        }

        ServiceWorkerPaymentAppBridge.canMakePayment(mWebContents, mRegistrationId, origin,
                iframeOrigin, new HashSet<>(methodDataMap.values()),
                new HashSet<>(modifiers.values()), (boolean canMakePayment) -> {
                    List<PaymentInstrument> instruments = new ArrayList();
                    if (canMakePayment) {
                        instruments.add(ServiceWorkerPaymentApp.this);
                    }
                    callback.onInstrumentsReady(ServiceWorkerPaymentApp.this, instruments);
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
    public Set<String> getPreferredRelatedApplicationIds() {
        return Collections.unmodifiableSet(mPreferredRelatedApplicationIds);
    }

    @Override
    public String getAppIdentifier() {
        return getIdentifier();
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
    public void abortPaymentApp(AbortCallback callback) {
        ServiceWorkerPaymentAppBridge.abortPaymentApp(mWebContents, mRegistrationId, callback);
    }

    @Override
    public void dismissInstrument() {}

    @Override
    public boolean canPreselect() {
        return mCanPreselect;
    }
}
