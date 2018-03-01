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
import java.util.Arrays;
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
    private final Set<String> mMethodNames;
    private final Capabilities[] mCapabilities;
    private final boolean mCanPreselect;
    private final Set<String> mPreferredRelatedApplicationIds;
    private final boolean mIsIncognito;

    /**
     * This class represents capabilities of a payment instrument. It is currently only used for
     * 'basic-card' payment instrument.
     */
    protected static class Capabilities {
        // Stores mojom::BasicCardNetwork.
        private int[] mSupportedCardNetworks;

        // Stores mojom::BasicCardType.
        private int[] mSupportedCardTypes;

        /**
         * Build capabilities for a payment instrument.
         *
         * @param supportedCardNetworks The supported card networks of a 'basic-card' payment
         *                              instrument.
         * @param supportedCardTypes    The supported card types of a 'basic-card' payment
         *                              instrument.
         */
        /* package */ Capabilities(int[] supportedCardNetworks, int[] supportedCardTypes) {
            mSupportedCardNetworks = supportedCardNetworks;
            mSupportedCardTypes = supportedCardTypes;
        }

        /**
         * Gets supported card networks.
         *
         * @return a set of mojom::BasicCardNetwork.
         */
        /* package */ int[] getSupportedCardNetworks() {
            return mSupportedCardNetworks;
        }

        /**
         * Gets supported card types.
         *
         * @return a set of mojom::BasicCardType.
         */
        /* package */ int[] getSupportedCardTypes() {
            return mSupportedCardTypes;
        }
    }

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
     * @param capabilities                   A set of capabilities of the payment instruments in
     *                                       this payment app (only valid for basic-card payment
     *                                       method for now).
     * @param preferredRelatedApplicationIds A set of preferred related application Ids.
     */
    public ServiceWorkerPaymentApp(WebContents webContents, long registrationId, URI scope,
            String label, @Nullable String sublabel, @Nullable String tertiarylabel,
            @Nullable Drawable icon, String[] methodNames, Capabilities[] capabilities,
            String[] preferredRelatedApplicationIds) {
        super(scope.toString(), label, sublabel, tertiarylabel, icon);
        mWebContents = webContents;
        mRegistrationId = registrationId;

        // Sublabel and/or icon are set to null if fetching or processing the corresponding web app
        // manifest failed. Then do not preselect this payment app.
        mCanPreselect = !TextUtils.isEmpty(sublabel) && icon != null;

        mMethodNames = new HashSet<>();
        for (int i = 0; i < methodNames.length; i++) {
            mMethodNames.add(methodNames[i]);
        }

        mCapabilities = Arrays.copyOf(capabilities, capabilities.length);

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
        // Do not send canMakePayment event when in incognito mode or basic-card is the only
        // supported payment method for the payment request.
        if (mIsIncognito || isOnlySupportBasiccard(methodDataMap)) {
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

    // Returns true if 'basic-card' is the only supported payment method of this payment app in the
    // payment request.
    private boolean isOnlySupportBasiccard(Map<String, PaymentMethodData> methodDataMap) {
        Set<String> requestMethods = new HashSet<>(methodDataMap.keySet());
        requestMethods.retainAll(mMethodNames);
        return requestMethods.size() == 1
                && requestMethods.contains(BasicCardUtils.BASIC_CARD_METHOD_NAME);
    }

    // Matches |requestMethodData|.supportedTypes and |requestMethodData|.supportedNetwokrs for
    // 'basic-card' payment method with the Capabilities in this payment app to determine whether
    // this payment app supports |requestMethodData|.
    private boolean matchBasiccardCapabilities(PaymentMethodData requestMethodData) {
        // Empty supported card types and networks in payment request method data indicates it
        // supports all card types and networks.
        if (requestMethodData.supportedTypes.length == 0
                && requestMethodData.supportedNetworks.length == 0) {
            return true;
        }
        // Payment app with emtpy capabilities can only match payment request method data with empty
        // supported card types and networks.
        if (mCapabilities.length == 0) return false;

        Set<Integer> requestSupportedTypes = new HashSet<>();
        for (int i = 0; i < requestMethodData.supportedTypes.length; i++) {
            requestSupportedTypes.add(requestMethodData.supportedTypes[i]);
        }
        Set<Integer> requestSupportedNetworks = new HashSet<>();
        for (int i = 0; i < requestMethodData.supportedNetworks.length; i++) {
            requestSupportedNetworks.add(requestMethodData.supportedNetworks[i]);
        }

        // If requestSupportedTypes and requestSupportedNetworks are not empty, match them with the
        // capabilities. Break out of the for loop if a matched capability has been found. So 'j
        // < mCapabilities.length' indicates that there is a matched capability in this payment
        // app.
        int j = 0;
        for (; j < mCapabilities.length; j++) {
            if (!requestSupportedTypes.isEmpty()) {
                int[] supportedTypes = mCapabilities[j].getSupportedCardTypes();

                Set<Integer> capabilitiesSupportedCardTypes = new HashSet<>();
                for (int i = 0; i < supportedTypes.length; i++) {
                    capabilitiesSupportedCardTypes.add(supportedTypes[i]);
                }

                capabilitiesSupportedCardTypes.retainAll(requestSupportedTypes);
                if (capabilitiesSupportedCardTypes.isEmpty()) continue;
            }

            if (!requestSupportedNetworks.isEmpty()) {
                int[] supportedNetworks = mCapabilities[j].getSupportedCardNetworks();

                Set<Integer> capabilitiesSupportedCardNetworks = new HashSet<>();
                for (int i = 0; i < supportedNetworks.length; i++) {
                    capabilitiesSupportedCardNetworks.add(supportedNetworks[i]);
                }

                capabilitiesSupportedCardNetworks.retainAll(requestSupportedNetworks);
                if (capabilitiesSupportedCardNetworks.isEmpty()) continue;
            }

            break;
        }
        return j < mCapabilities.length;
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
    public boolean isValidForPaymentMethodData(String method, PaymentMethodData data) {
        boolean isSupportedMethod = super.isValidForPaymentMethodData(method, data);
        if (isSupportedMethod && BasicCardUtils.BASIC_CARD_METHOD_NAME.equals(method)) {
            return matchBasiccardCapabilities(data);
        }
        return isSupportedMethod;
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
