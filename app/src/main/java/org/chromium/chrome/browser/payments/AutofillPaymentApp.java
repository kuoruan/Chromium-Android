// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;
import android.text.TextUtils;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.CardType;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.BasicCardNetwork;
import org.chromium.payments.mojom.BasicCardType;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to locally stored user credit cards.
 */
public class AutofillPaymentApp implements PaymentApp {
    /** The method name for any type of credit card. */
    public static final String BASIC_CARD_METHOD_NAME = "basic-card";

    /** The total number of all possible card types (i.e., credit, debit, prepaid, unknown). */
    private static final int TOTAL_NUMBER_OF_CARD_TYPES = 4;

    private final WebContents mWebContents;
    private Set<Integer> mBasicCardTypes;

    /**
     * Builds a payment app backed by autofill cards.
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     */
    public AutofillPaymentApp(WebContents webContents) {
        mWebContents = webContents;
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> methodDataMap, String unusedOrigin,
            String unusedIFRameOrigin, byte[][] unusedCertificateChain, PaymentItem unusedTotal,
            final InstrumentsCallback callback) {
        PersonalDataManager pdm = PersonalDataManager.getInstance();
        List<CreditCard> cards = pdm.getCreditCardsToSuggest();
        final List<PaymentInstrument> instruments = new ArrayList<>(cards.size());

        Set<String> basicCardSupportedNetworks =
                convertBasicCardToNetworks(methodDataMap.get(BASIC_CARD_METHOD_NAME));
        mBasicCardTypes = convertBasicCardToTypes(methodDataMap.get(BASIC_CARD_METHOD_NAME));

        for (int i = 0; i < cards.size(); i++) {
            CreditCard card = cards.get(i);
            AutofillProfile billingAddress = TextUtils.isEmpty(card.getBillingAddressId())
                    ? null
                    : pdm.getProfile(card.getBillingAddressId());

            if (billingAddress != null
                    && AutofillAddress.checkAddressCompletionStatus(
                               billingAddress, AutofillAddress.IGNORE_PHONE_COMPLETENESS_CHECK)
                            != AutofillAddress.COMPLETE) {
                billingAddress = null;
            }

            if (billingAddress == null) card.setBillingAddressId(null);

            String methodName = null;
            if (basicCardSupportedNetworks != null
                    && basicCardSupportedNetworks.contains(card.getBasicCardIssuerNetwork())) {
                methodName = BASIC_CARD_METHOD_NAME;
            } else if (methodDataMap.containsKey(card.getBasicCardIssuerNetwork())) {
                methodName = card.getBasicCardIssuerNetwork();
            }

            if (methodName != null && mBasicCardTypes.contains(card.getCardType())) {
                // Whether this card matches the card type (credit, debit, prepaid) exactly. If the
                // merchant requests all card types, then this is always true. If the merchant
                // requests only a subset of card types, then this is false for "unknown" card
                // types. The "unknown" card types is where Chrome is unable to determine the type
                // of card. Cards that don't match the card type exactly cannot be pre-selected in
                // the UI.
                boolean matchesMerchantCardTypeExactly = card.getCardType() != CardType.UNKNOWN
                        || mBasicCardTypes.size() == TOTAL_NUMBER_OF_CARD_TYPES;

                instruments.add(new AutofillPaymentInstrument(mWebContents, card, billingAddress,
                        methodName, matchesMerchantCardTypeExactly));
            }
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                callback.onInstrumentsReady(AutofillPaymentApp.this, instruments);
            }
        });
    }

    /** @return A set of card networks (e.g., "visa", "amex") accepted by "basic-card" method. */
    public static Set<String> convertBasicCardToNetworks(PaymentMethodData data) {
        // Merchant website does not support any issuer networks.
        if (data == null) return null;

        // Merchant website supports all issuer networks.
        Map<Integer, String> networks = getNetworks();
        if (data.supportedNetworks == null || data.supportedNetworks.length == 0) {
            return new HashSet<>(networks.values());
        }

        // Merchant website supports some issuer networks.
        Set<String> result = new HashSet<>();
        for (int i = 0; i < data.supportedNetworks.length; i++) {
            String network = networks.get(data.supportedNetworks[i]);
            if (network != null) result.add(network);
        }
        return result;
    }

    /**
     * @return A set of card types (e.g., CardType.DEBIT, CardType.PREPAID)
     * accepted by "basic-card" method.
     */
    public static Set<Integer> convertBasicCardToTypes(PaymentMethodData data) {
        Set<Integer> result = new HashSet<>();
        result.add(CardType.UNKNOWN);

        Map<Integer, Integer> cardTypes = getCardTypes();
        if (data == null || data.supportedTypes == null || data.supportedTypes.length == 0) {
            // Merchant website supports all card types.
            result.addAll(cardTypes.values());
        } else {
            // Merchant website supports some card types.
            for (int i = 0; i < data.supportedTypes.length; i++) {
                Integer cardType = cardTypes.get(data.supportedTypes[i]);
                if (cardType != null) result.add(cardType);
            }
        }

        return result;
    }

    private static Map<Integer, String> getNetworks() {
        Map<Integer, String> networks = new HashMap<>();
        networks.put(BasicCardNetwork.AMEX, "amex");
        networks.put(BasicCardNetwork.DINERS, "diners");
        networks.put(BasicCardNetwork.DISCOVER, "discover");
        networks.put(BasicCardNetwork.JCB, "jcb");
        networks.put(BasicCardNetwork.MASTERCARD, "mastercard");
        networks.put(BasicCardNetwork.MIR, "mir");
        networks.put(BasicCardNetwork.UNIONPAY, "unionpay");
        networks.put(BasicCardNetwork.VISA, "visa");
        return networks;
    }

    private static Map<Integer, Integer> getCardTypes() {
        Map<Integer, Integer> cardTypes = new HashMap<>();
        cardTypes.put(BasicCardType.CREDIT, CardType.CREDIT);
        cardTypes.put(BasicCardType.DEBIT, CardType.DEBIT);
        cardTypes.put(BasicCardType.PREPAID, CardType.PREPAID);
        return cardTypes;
    }

    @Override
    public Set<String> getAppMethodNames() {
        Set<String> methods = new HashSet<>(getNetworks().values());
        methods.add(BASIC_CARD_METHOD_NAME);
        return methods;
    }

    @Override
    public boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodDataMap) {
        return merchantSupportsAutofillPaymentInstruments(methodDataMap);
    }

    /** @return True if the merchant methodDataMap supports autofill payment instruments. */
    public static boolean merchantSupportsAutofillPaymentInstruments(
            Map<String, PaymentMethodData> methodDataMap) {
        assert methodDataMap != null;
        PaymentMethodData basicCardData = methodDataMap.get(BASIC_CARD_METHOD_NAME);
        if (basicCardData != null) {
            Set<String> basicCardNetworks = convertBasicCardToNetworks(basicCardData);
            if (basicCardNetworks != null && !basicCardNetworks.isEmpty()) return true;
        }

        Set<String> methodNames = new HashSet<>(methodDataMap.keySet());
        methodNames.retainAll(getNetworks().values());
        return !methodNames.isEmpty();
    }

    @Override
    public String getAppIdentifier() {
        return "Chrome_Autofill_Payment_App";
    }

    @Override
    public int getAdditionalAppTextResourceId() {
        // If the merchant has restricted the accepted card types (credit, debit, prepaid), then the
        // list of payment instruments should include a message describing the accepted card types,
        // e.g., "Debit cards are accepted" or "Debit and prepaid cards are accepted."
        if (mBasicCardTypes == null || mBasicCardTypes.size() == TOTAL_NUMBER_OF_CARD_TYPES) {
            return 0;
        }

        int credit = mBasicCardTypes.contains(CardType.CREDIT) ? 1 : 0;
        int debit = mBasicCardTypes.contains(CardType.DEBIT) ? 1 : 0;
        int prepaid = mBasicCardTypes.contains(CardType.PREPAID) ? 1 : 0;
        int[][][] resourceIds = new int[2][2][2];
        resourceIds[0][0][0] = 0;
        resourceIds[0][0][1] = R.string.payments_prepaid_cards_are_accepted_label;
        resourceIds[0][1][0] = R.string.payments_debit_cards_are_accepted_label;
        resourceIds[0][1][1] = R.string.payments_debit_prepaid_cards_are_accepted_label;
        resourceIds[1][0][0] = R.string.payments_credit_cards_are_accepted_label;
        resourceIds[1][0][1] = R.string.payments_credit_prepaid_cards_are_accepted_label;
        resourceIds[1][1][0] = R.string.payments_credit_debit_cards_are_accepted_label;
        resourceIds[1][1][1] = 0;
        return resourceIds[credit][debit][prepaid];
    }
}
