// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.payment;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.chrome.browser.payments.AddressEditor;
import org.chromium.chrome.browser.payments.AutofillAddress;
import org.chromium.chrome.browser.payments.AutofillContact;
import org.chromium.chrome.browser.payments.AutofillPaymentApp;
import org.chromium.chrome.browser.payments.AutofillPaymentInstrument;
import org.chromium.chrome.browser.payments.BasicCardUtils;
import org.chromium.chrome.browser.payments.CardEditor;
import org.chromium.chrome.browser.payments.ContactEditor;
import org.chromium.chrome.browser.payments.ShippingStrings;
import org.chromium.chrome.browser.payments.ui.ContactDetailsSection;
import org.chromium.chrome.browser.payments.ui.PaymentInformation;
import org.chromium.chrome.browser.payments.ui.SectionInformation;
import org.chromium.chrome.browser.payments.ui.ShoppingCart;
import org.chromium.chrome.browser.ssl.SecurityStateModel;
import org.chromium.chrome.browser.widget.prefeditor.Completable;
import org.chromium.chrome.browser.widget.prefeditor.EditableOption;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentMethodData;
import org.chromium.payments.mojom.PaymentOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class simplifies payment request UX to get payment information for Autofill Assistant.
 *
 * TODO(crbug.com/806868): Refactor shared codes with PaymentRequestImpl to a common place when the
 * UX is fixed.
 */
public class AutofillAssistantPaymentRequest {
    private static final String BASIC_CARD_PAYMENT_METHOD = "basic-card";
    private static final Comparator<Completable> COMPLETENESS_COMPARATOR =
            (a, b) -> (b.isComplete() ? 1 : 0) - (a.isComplete() ? 1 : 0);

    private final WebContents mWebContents;
    private final PaymentOptions mPaymentOptions;
    private final String mTitle;
    private final CardEditor mCardEditor;
    private final AddressEditor mAddressEditor;
    private final Map<String, PaymentMethodData> mMethodData;
    private final Handler mHandler = new Handler();
    private final String mDefaultEmail;

    private PaymentRequestUI mUI;
    private ContactEditor mContactEditor;
    private SectionInformation mPaymentMethodsSection;
    private SectionInformation mShippingAddressesSection;
    private ContactDetailsSection mContactSection;
    private Callback<SelectedPaymentInformation> mCallback;

    /** The class to return payment information. */
    public class SelectedPaymentInformation {
        /** Whether selection succeed. */
        public boolean succeed;

        /** Selected payment card. */
        public CreditCard card;

        /** Selected shipping address. */
        public AutofillProfile address;

        /** Payer's contact name. */
        public String payerName;

        /** Payer's contact email. */
        public String payerEmail;

        /** Payer's contact phone. */
        public String payerPhone;

        /** The terms and conditions accepted checkbox state. */
        public boolean isTermsAndConditionsAccepted;
    }

    /**
     * Constructor of AutofillAssistantPaymentRequest.
     *
     * @webContents                The web contents of the payment request associated with.
     * @paymentOptions             The options to request payment information.
     * @title                      The title to display in the payment request.
     * @supportedBasicCardNetworks Optional array of supported basic card networks (see {@link
     *                             BasicCardUtils}). If non-empty, only the specified card networks
     *                             will be available for the basic-card payment method.
     * @defaultEmail               Optional email. When provided Profiles with this email will be
     *                             shown on top.
     */
    public AutofillAssistantPaymentRequest(WebContents webContents, PaymentOptions paymentOptions,
            String title, String[] supportedBasicCardNetworks, @Nullable String defaultEmail) {
        mWebContents = webContents;
        mPaymentOptions = paymentOptions;
        mTitle = title;
        mDefaultEmail = defaultEmail;

        // This feature should only works in non-incognito mode.
        mAddressEditor = new AddressEditor(/* emailFieldIncluded= */ true, /* saveToDisk= */ true);
        mCardEditor = new CardEditor(mWebContents, mAddressEditor, /* observerForTest= */ null);

        // Only enable 'basic-card' payment method.
        PaymentMethodData methodData = new PaymentMethodData();
        methodData.supportedMethod = BASIC_CARD_PAYMENT_METHOD;

        // Apply basic-card filter if specified
        if (supportedBasicCardNetworks.length > 0) {
            ArrayList<Integer> filteredNetworks = new ArrayList<>();
            Map<String, Integer> networks = getNetworkIdentifiers();
            for (int i = 0; i < supportedBasicCardNetworks.length; i++) {
                assert networks.containsKey(supportedBasicCardNetworks[i]);
                filteredNetworks.add(networks.get(supportedBasicCardNetworks[i]));
            }

            methodData.supportedNetworks = new int[filteredNetworks.size()];
            for (int i = 0; i < filteredNetworks.size(); i++) {
                methodData.supportedNetworks[i] = filteredNetworks.get(i);
            }
        }

        mMethodData = new ArrayMap<>();
        mMethodData.put(BASIC_CARD_PAYMENT_METHOD, methodData);
        mCardEditor.addAcceptedPaymentMethodIfRecognized(methodData);
    }

    /**
     * Show payment request UI to ask for payment information.
     *
     * Replace |container| with the payment request UI and restore it when the payment request UI is
     * closed.
     *
     * @param container View to replace with the payment request.
     * @param callback The callback to return payment information.
     */
    public void show(View container, Callback<SelectedPaymentInformation> callback) {
        // Do not expect calling show multiple times.
        assert mCallback == null;
        assert mUI == null;

        mCallback = callback;
        buildUI(ChromeActivity.fromWebContents(mWebContents));

        mUI.show(container);
    }

    private void buildUI(ChromeActivity activity) {
        assert activity != null;

        mPaymentMethodsSection = new SectionInformation(PaymentRequestUI.DataType.PAYMENT_METHODS,
                SectionInformation.NO_SELECTION,
                (new AutofillPaymentApp(mWebContents))
                        .getInstruments(mMethodData, /*forceReturnServerCards=*/true));
        if (!mPaymentMethodsSection.isEmpty() && mPaymentMethodsSection.getItem(0).isComplete()) {
            mPaymentMethodsSection.setSelectedItemIndex(0);
        }

        List<AutofillProfile> profiles = null;
        if (mPaymentOptions.requestShipping || mPaymentOptions.requestPayerName
                || mPaymentOptions.requestPayerPhone || mPaymentOptions.requestPayerEmail) {
            profiles = PersonalDataManager.getInstance().getProfilesToSuggest(
                    /* includeNameInLabel= */ false);

            if (mDefaultEmail != null && profiles != null) {
                // The profile with default email should be shown as first. Following profiles are
                // sorted in an alphabetic order.
                Collections.sort(profiles, (a, b) -> {
                    int compareResult = ApiCompatibilityUtils.compareBoolean(
                            mDefaultEmail.equals(b.getEmailAddress()),
                            mDefaultEmail.equals(a.getEmailAddress()));
                    if (compareResult != 0) return compareResult;
                    return b.getEmailAddress().compareTo(a.getEmailAddress());
                });
            }
        }

        if (mPaymentOptions.requestShipping) {
            createShippingSection(activity, Collections.unmodifiableList(profiles));
        }

        if (mPaymentOptions.requestPayerName || mPaymentOptions.requestPayerPhone
                || mPaymentOptions.requestPayerEmail) {
            mContactEditor = new ContactEditor(mPaymentOptions.requestPayerName,
                    mPaymentOptions.requestPayerPhone, mPaymentOptions.requestPayerEmail,
                    /* saveToDisk= */ true);
            mContactSection = new ContactDetailsSection(activity,
                    Collections.unmodifiableList(profiles), mContactEditor,
                    /* journeyLogger= */ null);
        }

        mUI = new PaymentRequestUI(activity, this, mPaymentOptions.requestShipping,
                /* requestShippingOption= */ false,
                mPaymentOptions.requestPayerName || mPaymentOptions.requestPayerPhone
                        || mPaymentOptions.requestPayerEmail,
                /* canAddCards= */ true, /* showDataSource= */ true,
                mTitle.isEmpty() ? mWebContents.getTitle() : mTitle,
                UrlFormatter.formatUrlForSecurityDisplayOmitScheme(
                        mWebContents.getLastCommittedUrl()),
                SecurityStateModel.getSecurityLevelForWebContents(mWebContents),
                new ShippingStrings(mPaymentOptions.shippingType));
        // This payment request is embedded in another flow, so update the 'Pay' button text to
        // 'Confirm'.
        mUI.updatePayButtonText(R.string.autofill_assistant_payment_info_confirm);

        mAddressEditor.setEditorDialog(mUI.getEditorDialog());
        mCardEditor.setEditorDialog(mUI.getCardEditorDialog());
        if (mContactEditor != null) mContactEditor.setEditorDialog(mUI.getEditorDialog());
    }

    private void createShippingSection(
            Context context, List<AutofillProfile> unmodifiableProfiles) {
        List<AutofillAddress> addresses = new ArrayList<>();

        for (int i = 0; i < unmodifiableProfiles.size(); i++) {
            AutofillProfile profile = unmodifiableProfiles.get(i);
            mAddressEditor.addPhoneNumberIfValid(profile.getPhoneNumber());

            // Only suggest addresses that have a street address.
            if (!TextUtils.isEmpty(profile.getStreetAddress())) {
                addresses.add(new AutofillAddress(context, profile));
            }
        }

        // Suggest complete addresses first.
        Collections.sort(addresses, COMPLETENESS_COMPARATOR);

        // Automatically select the first address if one is complete.
        int firstCompleteAddressIndex = SectionInformation.NO_SELECTION;
        if (!addresses.isEmpty() && addresses.get(0).isComplete()) {
            firstCompleteAddressIndex = 0;

            // The initial label for the selected shipping address should not include the
            // country.
            addresses.get(firstCompleteAddressIndex).setShippingAddressLabelWithoutCountry();
        }

        mShippingAddressesSection = new SectionInformation(
                PaymentRequestUI.DataType.SHIPPING_ADDRESSES, firstCompleteAddressIndex, addresses);
    }

    /** Close payment request. */
    /* package */ void close() {
        if (mUI != null) {
            // Close the UI immediately and do not wait for finishing animations.
            mUI.close();
            mUI = null;
        }

        // Do not call callback if closed by caller.
        mCallback = null;
    }

    public void getDefaultPaymentInformation(Callback<PaymentInformation> callback) {
        mHandler.post(() -> {
            if (mUI != null) {
                callback.onResult(new PaymentInformation(/* shoppingCart= */ null,
                        mShippingAddressesSection,
                        /* shippingOptions= */ null, mContactSection, mPaymentMethodsSection));
            }
        });
    }

    public void getShoppingCart(Callback<ShoppingCart> callback) {
        // Do not display anything for shopping cart.
        mHandler.post(() -> callback.onResult(null));
    }

    public void getSectionInformation(
            @PaymentRequestUI.DataType int optionType, Callback<SectionInformation> callback) {
        mHandler.post(() -> {
            if (optionType == PaymentRequestUI.DataType.SHIPPING_ADDRESSES) {
                callback.onResult(mShippingAddressesSection);
            } else if (optionType == PaymentRequestUI.DataType.CONTACT_DETAILS) {
                callback.onResult(mContactSection);
            } else if (optionType == PaymentRequestUI.DataType.PAYMENT_METHODS) {
                assert mPaymentMethodsSection != null;
                callback.onResult(mPaymentMethodsSection);
            } else {
                // Only support above sections for now.
                assert false;
            }
        });
    }

    @PaymentRequestUI.SelectionResult
    public int onSectionOptionSelected(@PaymentRequestUI.DataType int optionType,
            EditableOption option, Callback<PaymentInformation> checkedCallback) {
        if (optionType == PaymentRequestUI.DataType.SHIPPING_ADDRESSES) {
            AutofillAddress address = (AutofillAddress) option;
            if (address.isComplete()) {
                mShippingAddressesSection.setSelectedItem(option);
            } else {
                editAddress(address);
                return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
            }
        } else if (optionType == PaymentRequestUI.DataType.CONTACT_DETAILS) {
            AutofillContact contact = (AutofillContact) option;
            if (contact.isComplete()) {
                mContactSection.setSelectedItem(option);
            } else {
                editContact(contact);
                return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
            }
        } else if (optionType == PaymentRequestUI.DataType.PAYMENT_METHODS) {
            AutofillPaymentInstrument card = (AutofillPaymentInstrument) option;
            if (card.isComplete()) {
                mPaymentMethodsSection.setSelectedItem(option);
            } else {
                editCard(card);
                return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
            }
        } else {
            // Only support above sections for now.
            assert false;
        }

        return PaymentRequestUI.SelectionResult.NONE;
    }

    @PaymentRequestUI.SelectionResult
    public int onSectionEditOption(@PaymentRequestUI.DataType int optionType, EditableOption option,
            Callback<PaymentInformation> checkedCallback) {
        if (optionType == PaymentRequestUI.DataType.SHIPPING_ADDRESSES) {
            editAddress((AutofillAddress) option);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        }

        if (optionType == PaymentRequestUI.DataType.CONTACT_DETAILS) {
            editContact((AutofillContact) option);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        }

        if (optionType == PaymentRequestUI.DataType.PAYMENT_METHODS) {
            editCard((AutofillPaymentInstrument) option);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        }

        // Only support above sections for now.
        assert false;
        return PaymentRequestUI.SelectionResult.NONE;
    }

    @PaymentRequestUI.SelectionResult
    public int onSectionAddOption(@PaymentRequestUI.DataType int optionType,
            Callback<PaymentInformation> checkedCallback) {
        if (optionType == PaymentRequestUI.DataType.SHIPPING_ADDRESSES) {
            editAddress(null);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        } else if (optionType == PaymentRequestUI.DataType.CONTACT_DETAILS) {
            editContact(null);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        } else if (optionType == PaymentRequestUI.DataType.PAYMENT_METHODS) {
            editCard(null);
            return PaymentRequestUI.SelectionResult.EDITOR_LAUNCH;
        }

        // Only support above sections for now.
        assert false;
        return PaymentRequestUI.SelectionResult.NONE;
    }

    private void editAddress(final AutofillAddress toEdit) {
        mAddressEditor.edit(toEdit, new Callback<AutofillAddress>() {
            @Override
            public void onResult(AutofillAddress editedAddress) {
                if (mUI == null) return;

                if (editedAddress != null) {
                    // Sets or updates the shipping address label.
                    editedAddress.setShippingAddressLabelWithCountry();

                    mCardEditor.updateBillingAddressIfComplete(editedAddress);

                    // A partial or complete address came back from the editor (could have been from
                    // adding/editing or cancelling out of the edit flow).
                    if (!editedAddress.isComplete()) {
                        // If the address is not complete, deselect it (editor can return incomplete
                        // information when cancelled).
                        mShippingAddressesSection.setSelectedItemIndex(
                                SectionInformation.NO_SELECTION);
                    } else {
                        if (toEdit == null) {
                            // Address is complete and user was in the "Add flow": add an item to
                            // the list.
                            mShippingAddressesSection.addAndSelectItem(editedAddress);
                        }

                        if (mContactSection != null) {
                            // Update |mContactSection| with the new/edited address, which will
                            // update an existing item or add a new one to the end of the list.
                            mContactSection.addOrUpdateWithAutofillAddress(editedAddress);
                            mUI.updateSection(
                                    PaymentRequestUI.DataType.CONTACT_DETAILS, mContactSection);
                        }
                    }

                    mUI.updateSection(PaymentRequestUI.DataType.SHIPPING_ADDRESSES,
                            mShippingAddressesSection);
                }
            }
        });
    }

    private void editContact(final AutofillContact toEdit) {
        mContactEditor.edit(toEdit, new Callback<AutofillContact>() {
            @Override
            public void onResult(AutofillContact editedContact) {
                if (mUI == null) return;

                if (editedContact != null) {
                    // A partial or complete contact came back from the editor (could have been from
                    // adding/editing or cancelling out of the edit flow).
                    if (!editedContact.isComplete()) {
                        // If the contact is not complete according to the requirements of the flow,
                        // deselect it (editor can return incomplete information when cancelled).
                        mContactSection.setSelectedItemIndex(SectionInformation.NO_SELECTION);
                    } else if (toEdit == null) {
                        // Contact is complete and we were in the "Add flow": add an item to the
                        // list.
                        mContactSection.addAndSelectItem(editedContact);
                    }
                    // If contact is complete and (toEdit != null), no action needed: the contact
                    // was already selected in the UI.
                }
                // If |editedContact| is null, the user has cancelled out of the "Add flow". No
                // action to take (if a contact was selected in the UI, it will stay selected).

                mUI.updateSection(PaymentRequestUI.DataType.CONTACT_DETAILS, mContactSection);
            }
        });
    }

    private void editCard(final AutofillPaymentInstrument toEdit) {
        mCardEditor.edit(toEdit, new Callback<AutofillPaymentInstrument>() {
            @Override
            public void onResult(AutofillPaymentInstrument editedCard) {
                if (mUI == null) return;

                if (editedCard != null) {
                    // A partial or complete card came back from the editor (could have been from
                    // adding/editing or cancelling out of the edit flow).
                    if (!editedCard.isComplete()) {
                        // If the card is not complete, deselect it (editor can return incomplete
                        // information when cancelled).
                        mPaymentMethodsSection.setSelectedItemIndex(
                                SectionInformation.NO_SELECTION);
                    } else if (toEdit == null) {
                        // Card is complete and we were in the "Add flow": add an item to the list.
                        mPaymentMethodsSection.addAndSelectItem(editedCard);
                    }
                    // If card is complete and (toEdit != null), no action needed: the card was
                    // already selected in the UI.
                }
                // If |editedCard| is null, the user has cancelled out of the "Add flow". No action
                // to take (if another card was selected prior to the add flow, it will stay
                // selected).

                mUI.updateSection(
                        PaymentRequestUI.DataType.PAYMENT_METHODS, mPaymentMethodsSection);
            }
        });
    }

    /**
     * @return a complete map of string identifiers to BasicCardNetworks.
     */
    private static Map<String, Integer> getNetworkIdentifiers() {
        Map<Integer, String> networksByInt = BasicCardUtils.getNetworks();
        Map<String, Integer> networksByString = new HashMap<>();
        for (Map.Entry<Integer, String> entry : networksByInt.entrySet()) {
            networksByString.put(entry.getValue(), entry.getKey());
        }
        return networksByString;
    }

    public boolean onPayClicked(EditableOption selectedShippingAddress,
            EditableOption selectedShippingOption, EditableOption selectedPaymentMethod,
            boolean isTermsAndConditionsAccepted) {
        if (mCallback != null) {
            SelectedPaymentInformation selectedPaymentInformation =
                    new SelectedPaymentInformation();

            selectedPaymentInformation.isTermsAndConditionsAccepted = isTermsAndConditionsAccepted;
            selectedPaymentInformation.card =
                    ((AutofillPaymentInstrument) selectedPaymentMethod).getCard();
            if (mPaymentOptions.requestShipping && selectedShippingAddress != null) {
                selectedPaymentInformation.address =
                        ((AutofillAddress) selectedShippingAddress).getProfile();
            }
            if (mPaymentOptions.requestPayerName || mPaymentOptions.requestPayerPhone
                    || mPaymentOptions.requestPayerEmail) {
                EditableOption selectedContact =
                        mContactSection != null ? mContactSection.getSelectedItem() : null;
                if (selectedContact != null) {
                    selectedPaymentInformation.payerName =
                            ((AutofillContact) selectedContact).getPayerName();
                    selectedPaymentInformation.payerPhone =
                            ((AutofillContact) selectedContact).getPayerPhone();
                    selectedPaymentInformation.payerEmail =
                            ((AutofillContact) selectedContact).getPayerEmail();
                }
            }
            selectedPaymentInformation.succeed = true;
            mCallback.onResult(selectedPaymentInformation);
            mCallback = null;
        }

        return false;
    }

    public void onDismiss() {
        if (mCallback != null) {
            SelectedPaymentInformation selectedPaymentInformation =
                    new SelectedPaymentInformation();
            selectedPaymentInformation.succeed = false;
            mCallback.onResult(selectedPaymentInformation);
            mCallback = null;
        }
        close();
    }

    public void onCardAndAddressSettingsClicked() {
        // TODO(crbug.com/806868): Allow user to control cards and addresses.
    }
}
