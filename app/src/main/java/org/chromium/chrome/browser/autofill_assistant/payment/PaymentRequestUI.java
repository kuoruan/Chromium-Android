// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.payment;

import static org.chromium.chrome.browser.payments.ui.PaymentRequestSection.EDIT_BUTTON_GONE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.IntDef;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.chromium.base.Callback;
import org.chromium.chrome.autofill_assistant.R;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.payments.ShippingStrings;
import org.chromium.chrome.browser.payments.ui.PaymentInformation;
import org.chromium.chrome.browser.payments.ui.PaymentRequestSection;
import org.chromium.chrome.browser.payments.ui.PaymentRequestSection.LineItemBreakdownSection;
import org.chromium.chrome.browser.payments.ui.PaymentRequestSection.OptionSection;
import org.chromium.chrome.browser.payments.ui.PaymentRequestSection.SectionSeparator;
import org.chromium.chrome.browser.payments.ui.PaymentRequestUiErrorView;
import org.chromium.chrome.browser.payments.ui.SectionInformation;
import org.chromium.chrome.browser.payments.ui.ShoppingCart;
import org.chromium.chrome.browser.widget.FadingEdgeScrollView;
import org.chromium.chrome.browser.widget.animation.FocusAnimator;
import org.chromium.chrome.browser.widget.prefeditor.EditableOption;
import org.chromium.chrome.browser.widget.prefeditor.EditorDialog;
import org.chromium.ui.text.SpanApplier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * The PaymentRequest UI.
 *
 * Note: This is an Autofill Assistant specific fork of payments/ui/PaymentRequestUI.java.
 */
public class PaymentRequestUI implements DialogInterface.OnDismissListener, View.OnClickListener,
                                         PaymentRequestSection.SectionDelegate {
    @IntDef({DataType.SHIPPING_ADDRESSES, DataType.SHIPPING_OPTIONS, DataType.CONTACT_DETAILS,
            DataType.PAYMENT_METHODS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DataType {
        int SHIPPING_ADDRESSES = 1;
        int SHIPPING_OPTIONS = 2;
        int CONTACT_DETAILS = 3;
        int PAYMENT_METHODS = 4;
    }

    @IntDef({SelectionResult.ASYNCHRONOUS_VALIDATION, SelectionResult.EDITOR_LAUNCH,
            SelectionResult.NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectionResult {
        int ASYNCHRONOUS_VALIDATION = 1;
        int EDITOR_LAUNCH = 2;
        int NONE = 3;
    }

    /**
     * Length of the animation to either show the UI or expand it to full height.
     * Note that click of 'Pay' button is not accepted until the animation is done, so this duration
     * also serves the function of preventing the user from accidently double-clicking on the screen
     * when triggering payment and thus authorizing unwanted transaction.
     */
    private static final int DIALOG_ENTER_ANIMATION_MS = 225;

    /** Length of the animation to hide the bottom sheet UI. */
    private static final int DIALOG_EXIT_ANIMATION_MS = 195;

    private final Context mContext;
    private final AutofillAssistantPaymentRequest mClient;
    private final boolean mRequestShipping;
    private final boolean mRequestShippingOption;
    private final boolean mRequestContactDetails;
    private final boolean mShowDataSource;

    private final EditorDialog mEditorDialog;
    private final EditorDialog mCardEditorDialog;
    private final ViewGroup mRequestView;
    private final PaymentRequestUiErrorView mErrorView;
    private final Callback<PaymentInformation> mUpdateSectionsCallback;
    private final ShippingStrings mShippingStrings;

    private FadingEdgeScrollView mPaymentContainer;
    private LinearLayout mPaymentContainerLayout;
    private ViewGroup mBottomBar;
    private Button mPayButton;
    private View mSpinnyLayout;
    // View used to store a view to be replaced with the current payment request UI.
    private View mBackupView;
    private RadioButton mAcceptThirdPartyConditions;
    private RadioButton mReviewThirdPartyConditions;

    private LineItemBreakdownSection mOrderSummarySection;
    private OptionSection mShippingAddressSection;
    private OptionSection mShippingOptionSection;
    private OptionSection mContactDetailsSection;
    private OptionSection mPaymentMethodSection;
    private List<SectionSeparator> mSectionSeparators;

    private PaymentRequestSection mSelectedSection;
    private boolean mIsExpandedToFullHeight;
    private boolean mIsProcessingPayClicked;
    private boolean mIsClientClosing;
    private boolean mIsClientCheckingSelection;
    private boolean mIsShowingSpinner;
    private boolean mIsEditingPaymentItem;
    private boolean mIsClosing;

    private SectionInformation mPaymentMethodSectionInformation;
    private SectionInformation mShippingAddressSectionInformation;
    private SectionInformation mShippingOptionsSectionInformation;
    private SectionInformation mContactDetailsSectionInformation;

    private Animator mSheetAnimator;
    private FocusAnimator mSectionAnimator;
    private int mAnimatorTranslation;

    /**
     * Builds the UI for PaymentRequest.
     *
     * @param activity              The activity on top of which the UI should be displayed.
     * @param client                The AutofillAssistantPaymentRequest.
     * @param requestShipping       Whether the UI should show the shipping address selection.
     * @param requestShippingOption Whether the UI should show the shipping option selection.
     * @param requestContact        Whether the UI should show the payer name, email address and
     *                              phone number selection.
     * @param canAddCards           Whether the UI should show the [+ADD CARD] button. This can be
     *                              false, for example, when the merchant does not accept credit
     *                              cards, so there's no point in adding cards within PaymentRequest
     *                              UI.
     * @param showDataSource        Whether the UI should describe the source of Autofill data.
     * @param title                 The title to show at the top of the UI. This can be, for
     *                              example, the &lt;title&gt; of the merchant website. If the
     *                              string is too long for UI, it elides at the end.
     * @param origin                The origin (https://tools.ietf.org/html/rfc6454) to show under
     *                              the title. For example, "https://shop.momandpop.com". If the
     *                              origin is too long for the UI, it should elide according to:
     * https://www.chromium.org/Home/chromium-security/enamel#TOC-Eliding-Origin-Names-And-Hostnames
     * @param securityLevel   The security level of the page that invoked PaymentRequest.
     * @param shippingStrings The string resource identifiers to use in the shipping sections.
     */
    public PaymentRequestUI(Activity activity, AutofillAssistantPaymentRequest client,
            boolean requestShipping, boolean requestShippingOption, boolean requestContact,
            boolean canAddCards, boolean showDataSource, String title, String origin,
            int securityLevel, ShippingStrings shippingStrings) {
        mContext = activity;
        mClient = client;
        mRequestShipping = requestShipping;
        mRequestShippingOption = requestShippingOption;
        mRequestContactDetails = requestContact;
        mShowDataSource = showDataSource;
        mAnimatorTranslation =
                mContext.getResources().getDimensionPixelSize(R.dimen.payments_ui_translation);

        mErrorView = (PaymentRequestUiErrorView) LayoutInflater.from(mContext).inflate(
                R.layout.payment_request_error, null);
        mErrorView.initialize(title, origin, securityLevel);

        // This callback will be fired if mIsClientCheckingSelection is true.
        mUpdateSectionsCallback = new Callback<PaymentInformation>() {
            @Override
            public void onResult(PaymentInformation result) {
                mIsClientCheckingSelection = false;
                updateOrderSummarySection(result.getShoppingCart());
                if (mRequestShipping) {
                    updateSection(DataType.SHIPPING_ADDRESSES, result.getShippingAddresses());
                }
                if (mRequestShippingOption) {
                    updateSection(DataType.SHIPPING_OPTIONS, result.getShippingOptions());
                }
                if (mRequestContactDetails) {
                    updateSection(DataType.CONTACT_DETAILS, result.getContactDetails());
                }
                updateSection(DataType.PAYMENT_METHODS, result.getPaymentMethods());
                if (mShippingAddressSectionInformation.getSelectedItem() == null) {
                    expand(mShippingAddressSection);
                } else {
                    expand(null);
                }
                updatePayButtonEnabled();
            }
        };

        mShippingStrings = shippingStrings;

        mRequestView = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.autofill_assistant_payment_request, null);
        prepareRequestView(mContext, title, origin, securityLevel, canAddCards);

        mEditorDialog = new EditorDialog(activity, null,
                /*deleteRunnable =*/null);

        mCardEditorDialog = new EditorDialog(activity, null,
                /*deleteRunnable =*/null);

        // Allow screenshots of the credit card number in Canary, Dev, and developer builds.
        if (ChromeVersionInfo.isBetaBuild() || ChromeVersionInfo.isStableBuild()) {
            mCardEditorDialog.disableScreenshots();
        }
    }

    /**
     * Shows the PaymentRequest UI. This will dim the background behind the PaymentRequest UI.
     *
     * The show replaces the |container| view with the payment request view. The original content of
     * |container| is saved and restored when the payment request UI is closed.
     * restore it when the payment request UI is closed.
     *
     * TODO(crbug.com/806868): Move the mBackupView handling to the AutofillAssistantUiDelegate.
     *
     * @param container View to replace with the payment request UI.
     */
    public void show(View container) {
        // Clear the current Autofill Assistant sheet and show the request view.
        LinearLayout.LayoutParams sheetParams =
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sheetParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;

        // Swap the horizontal scroll view |container| with the payment request view and save the
        // original.
        ViewGroup parent = (ViewGroup) container.getParent();
        assert parent != null;
        final int index = parent.indexOfChild(container);
        mBackupView = container;
        parent.removeView(container);
        parent.addView(mRequestView, index, sheetParams);

        mClient.getDefaultPaymentInformation(new Callback<PaymentInformation>() {
            @Override
            public void onResult(PaymentInformation result) {
                updateOrderSummarySection(result.getShoppingCart());

                if (mRequestShipping) {
                    updateSection(DataType.SHIPPING_ADDRESSES, result.getShippingAddresses());
                }

                if (mRequestShippingOption) {
                    updateSection(DataType.SHIPPING_OPTIONS, result.getShippingOptions());
                }

                if (mRequestContactDetails) {
                    updateSection(DataType.CONTACT_DETAILS, result.getContactDetails());
                }

                mPaymentMethodSection.setDisplaySummaryInSingleLineInNormalMode(
                        result.getPaymentMethods()
                                .getDisplaySelectedItemSummaryInSingleLineInNormalMode());
                updateSection(DataType.PAYMENT_METHODS, result.getPaymentMethods());
                updatePayButtonEnabled();

                // Hide the loading indicators and show the real sections.
                changeSpinnerVisibility(false);
                mRequestView.addOnLayoutChangeListener(new SheetEnlargingAnimator(false));
            }
        });
    }

    /**
     * Prepares the PaymentRequestUI for initial display.
     *
     * TODO(dfalcantara): Ideally, everything related to the request and its views would just be put
     *                    into its own class but that'll require yanking out a lot of this class.
     *
     * @param context       The application context.
     * @param title         Title of the page.
     * @param origin        The RFC6454 origin of the page.
     * @param securityLevel The security level of the page that invoked PaymentRequest.
     * @param canAddCards   Whether new cards can be added.
     */
    private void prepareRequestView(
            Context context, String title, String origin, int securityLevel, boolean canAddCards) {
        mSpinnyLayout = mRequestView.findViewById(R.id.payment_request_spinny);
        assert mSpinnyLayout.getVisibility() == View.VISIBLE;
        mIsShowingSpinner = true;

        // Indicate that we're preparing the dialog for display.
        TextView messageView = (TextView) mRequestView.findViewById(R.id.message);
        messageView.setText(R.string.payments_loading_message);

        // Set up the buttons.
        mBottomBar = (ViewGroup) mRequestView.findViewById(R.id.bottom_bar);
        mPayButton = (Button) mBottomBar.findViewById(R.id.button_primary);
        mPayButton.setOnClickListener(this);

        // Set terms & conditions text.
        mAcceptThirdPartyConditions = mRequestView.findViewById(R.id.terms_checkbox_agree);
        mReviewThirdPartyConditions = mRequestView.findViewById(R.id.terms_checkbox_review);
        StyleSpan boldSpan = new StyleSpan(android.graphics.Typeface.BOLD);
        mAcceptThirdPartyConditions.setText(SpanApplier.applySpans(
                context.getString(R.string.autofill_assistant_3rd_party_terms_accept, origin),
                new SpanApplier.SpanInfo("<b>", "</b>", boldSpan)));
        mReviewThirdPartyConditions.setText(SpanApplier.applySpans(
                context.getString(R.string.autofill_assistant_3rd_party_terms_review, origin),
                new SpanApplier.SpanInfo("<b>", "</b>", boldSpan)));
        mAcceptThirdPartyConditions.setOnClickListener(this);
        mReviewThirdPartyConditions.setOnClickListener(this);

        // Create all the possible sections.
        mSectionSeparators = new ArrayList<>();
        mPaymentContainer = (FadingEdgeScrollView) mRequestView.findViewById(R.id.option_container);
        mPaymentContainerLayout =
                (LinearLayout) mRequestView.findViewById(R.id.payment_container_layout);
        mOrderSummarySection = new LineItemBreakdownSection(context,
                context.getString(R.string.payments_order_summary_label), this,
                context.getString(R.string.payments_updated_label));
        mShippingAddressSection = new OptionSection(
                context, context.getString(mShippingStrings.getAddressLabel()), this);
        mShippingOptionSection = new OptionSection(
                context, context.getString(mShippingStrings.getOptionLabel()), this);
        mContactDetailsSection = new OptionSection(
                context, context.getString(R.string.payments_contact_details_label), this);
        mPaymentMethodSection = new OptionSection(
                context, context.getString(R.string.payments_method_of_payment_label), this);

        // Display the summary of the selected address in multiple lines on bottom sheet.
        mShippingAddressSection.setDisplaySummaryInSingleLineInNormalMode(false);

        // Display selected shipping option name in the left summary text view and
        // the cost in the right summary text view on bottom sheet.
        mShippingOptionSection.setSplitSummaryInDisplayModeNormal(true);

        // Some sections conditionally allow adding new options.
        mShippingOptionSection.setCanAddItems(false);
        mPaymentMethodSection.setCanAddItems(canAddCards);

        // Add the necessary sections to the layout.
        mPaymentContainerLayout.addView(mOrderSummarySection,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Always show separator at the top.
        mPaymentContainer.setEdgeVisibility(
                FadingEdgeScrollView.EdgeType.HARD, FadingEdgeScrollView.EdgeType.FADING);

        if (mRequestContactDetails) {
            mPaymentContainerLayout.addView(mContactDetailsSection,
                    new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        if (mRequestShipping) {
            if (mRequestContactDetails)
                mSectionSeparators.add(new SectionSeparator(mPaymentContainerLayout));
            // The shipping breakout sections are only added if they are needed.
            mPaymentContainerLayout.addView(mShippingAddressSection,
                    new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        if (mRequestContactDetails || mRequestShipping)
            mSectionSeparators.add(new SectionSeparator(mPaymentContainerLayout));
        mPaymentContainerLayout.addView(mPaymentMethodSection,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        PaymentRequestSection sections[] =
                new PaymentRequestSection[] {mOrderSummarySection, mShippingAddressSection,
                        mShippingOptionSection, mContactDetailsSection, mPaymentMethodSection};
        for (int i = 0; i < sections.length; i++) {
            clearLeftRightPadding(sections[i]);
        }

        // Always expand separators to make them align with the rest of the UI.
        for (int i = 0; i < mSectionSeparators.size(); i++) {
            mSectionSeparators.get(i).expand();
        }

        mRequestView.addOnLayoutChangeListener(new PeekingAnimator());

        // Enabled in updatePayButtonEnabled() when the user has selected all payment options.
        mPayButton.setEnabled(false);

        // Force the initial appearance of edit chevrons next to all sections.
        updateSectionVisibility();
    }

    private void clearLeftRightPadding(PaymentRequestSection section) {
        section.setPadding(
                /*left=*/0, section.getPaddingTop(), /*right=*/0, section.getPaddingBottom());
    }

    /**
     * Closes the UI. Can be invoked in response to, for example:
     * <ul>
     *  <li>Successfully processing the payment.</li>
     *  <li>Failure to process the payment.</li>
     *  <li>The JavaScript calling the abort() method in PaymentRequest API.</li>
     *  <li>The PaymentRequest JavaScript object being destroyed.</li>
     * </ul>
     *
     * Does not call Delegate.onDismissed().
     *
     * Should not be called multiple times.
     */
    public void close() {
        // Restore the UI before we showed the payment request.
        ViewGroup parent = (ViewGroup) mRequestView.getParent();
        assert parent != null;
        final int index = parent.indexOfChild(mRequestView);
        parent.removeView(mRequestView);
        parent.addView(mBackupView, index);
        mBackupView = null;
    }

    /**
     * Update default text on the pay button to the given text.
     *
     * @param textResId The resource id of the text to be shown on the button.
     */
    public void updatePayButtonText(int textResId) {
        mPayButton.setText(textResId);
    }

    /**
     * Updates the line items in response to a changed shipping address or option.
     *
     * @param cart The shopping cart, including the line items and the total.
     */
    /* package */ void updateOrderSummarySection(ShoppingCart cart) {
        if (cart == null || cart.getTotal() == null) {
            mOrderSummarySection.setVisibility(View.GONE);
        } else {
            mOrderSummarySection.setVisibility(View.VISIBLE);
            mOrderSummarySection.update(cart);
        }
    }

    /**
     * Updates the UI to account for changes in payment information.
     *
     * @param section The shipping options.
     */
    public void updateSection(@DataType int whichSection, SectionInformation section) {
        if (whichSection == DataType.SHIPPING_ADDRESSES) {
            mShippingAddressSectionInformation = section;
            mShippingAddressSection.update(section);
        } else if (whichSection == DataType.SHIPPING_OPTIONS) {
            mShippingOptionsSectionInformation = section;
            mShippingOptionSection.update(section);
            showShippingOptionSectionIfNecessary();
        } else if (whichSection == DataType.CONTACT_DETAILS) {
            mContactDetailsSectionInformation = section;
            mContactDetailsSection.update(section);
        } else if (whichSection == DataType.PAYMENT_METHODS) {
            mPaymentMethodSectionInformation = section;
            mPaymentMethodSection.update(section);
        }

        boolean isFinishingEditItem = mIsEditingPaymentItem;
        mIsEditingPaymentItem = false;
        updateSectionButtons();
        updatePayButtonEnabled();
    }

    // Only show shipping option section once there are shipping options.
    private void showShippingOptionSectionIfNecessary() {
        if (!mRequestShippingOption || mShippingOptionsSectionInformation.isEmpty()
                || mPaymentContainerLayout.indexOfChild(mShippingOptionSection) != -1) {
            return;
        }

        // Shipping option section is added below shipping address section.
        int addressSectionIndex = mPaymentContainerLayout.indexOfChild(mShippingAddressSection);
        SectionSeparator sectionSeparator =
                new SectionSeparator(mPaymentContainerLayout, addressSectionIndex + 1);
        mSectionSeparators.add(sectionSeparator);
        if (mIsExpandedToFullHeight) sectionSeparator.expand();
        mPaymentContainerLayout.addView(mShippingOptionSection, addressSectionIndex + 2,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mPaymentContainerLayout.requestLayout();
    }

    @Override
    public void onEditableOptionChanged(
            final PaymentRequestSection section, EditableOption option) {
        @SelectionResult
        int result = SelectionResult.NONE;
        if (section == mShippingAddressSection
                && mShippingAddressSectionInformation.getSelectedItem() != option) {
            mShippingAddressSectionInformation.setSelectedItem(option);
            result = mClient.onSectionOptionSelected(
                    DataType.SHIPPING_ADDRESSES, option, mUpdateSectionsCallback);
        } else if (section == mShippingOptionSection
                && mShippingOptionsSectionInformation.getSelectedItem() != option) {
            mShippingOptionsSectionInformation.setSelectedItem(option);
            result = mClient.onSectionOptionSelected(
                    DataType.SHIPPING_OPTIONS, option, mUpdateSectionsCallback);
        } else if (section == mContactDetailsSection) {
            mContactDetailsSectionInformation.setSelectedItem(option);
            result = mClient.onSectionOptionSelected(DataType.CONTACT_DETAILS, option, null);
        } else if (section == mPaymentMethodSection) {
            mPaymentMethodSectionInformation.setSelectedItem(option);
            result = mClient.onSectionOptionSelected(DataType.PAYMENT_METHODS, option, null);
        }

        updateStateFromResult(section, result);
    }

    @Override
    public void onEditEditableOption(final PaymentRequestSection section, EditableOption option) {
        @SelectionResult
        int result = SelectionResult.NONE;

        assert section != mOrderSummarySection;
        assert section != mShippingOptionSection;
        if (section == mShippingAddressSection) {
            assert mShippingAddressSectionInformation.getSelectedItem() == option;
            result = mClient.onSectionEditOption(
                    DataType.SHIPPING_ADDRESSES, option, mUpdateSectionsCallback);
        }

        if (section == mContactDetailsSection) {
            assert mContactDetailsSectionInformation.getSelectedItem() == option;
            result = mClient.onSectionEditOption(DataType.CONTACT_DETAILS, option, null);
        }

        if (section == mPaymentMethodSection) {
            assert mPaymentMethodSectionInformation.getSelectedItem() == option;
            result = mClient.onSectionEditOption(DataType.PAYMENT_METHODS, option, null);
        }

        updateStateFromResult(section, result);
    }

    @Override
    public void onAddEditableOption(PaymentRequestSection section) {
        assert section != mShippingOptionSection;

        @SelectionResult
        int result = SelectionResult.NONE;
        if (section == mShippingAddressSection) {
            result = mClient.onSectionAddOption(
                    DataType.SHIPPING_ADDRESSES, mUpdateSectionsCallback);
        } else if (section == mContactDetailsSection) {
            result = mClient.onSectionAddOption(DataType.CONTACT_DETAILS, null);
        } else if (section == mPaymentMethodSection) {
            result = mClient.onSectionAddOption(DataType.PAYMENT_METHODS, null);
        }

        updateStateFromResult(section, result);
    }

    void updateStateFromResult(PaymentRequestSection section, @SelectionResult int result) {
        mIsClientCheckingSelection = result == SelectionResult.ASYNCHRONOUS_VALIDATION;
        mIsEditingPaymentItem = result == SelectionResult.EDITOR_LAUNCH;

        if (mIsClientCheckingSelection) {
            mSelectedSection = section;
            updateSectionVisibility();
            section.setDisplayMode(PaymentRequestSection.DISPLAY_MODE_CHECKING);
        } else {
            expand(null);
        }

        updatePayButtonEnabled();
    }

    @Override
    public boolean isBoldLabelNeeded(PaymentRequestSection section) {
        return section == mShippingAddressSection;
    }

    /** @return The common editor user interface. */
    public EditorDialog getEditorDialog() {
        return mEditorDialog;
    }

    /**
     * @return The card editor user interface. Distinct from the common editor user interface,
     * because the credit card editor can launch the address editor.
     */
    public EditorDialog getCardEditorDialog() {
        return mCardEditorDialog;
    }

    /**
     * Called when user clicks anything in the dialog.
     */
    @Override
    public void onClick(View v) {
        if (!isAcceptingCloseButton()) return;

        if (!isAcceptingUserInput()) return;

        // Users can only expand incomplete sections by clicking on their edit buttons.
        if (v instanceof PaymentRequestSection) {
            PaymentRequestSection section = (PaymentRequestSection) v;
            if (section.getEditButtonState() != EDIT_BUTTON_GONE) return;
        }

        if (v == mOrderSummarySection) {
            expand(mOrderSummarySection);
        } else if (v == mShippingAddressSection) {
            expand(mShippingAddressSection);
        } else if (v == mShippingOptionSection) {
            expand(mShippingOptionSection);
        } else if (v == mContactDetailsSection) {
            expand(mContactDetailsSection);
        } else if (v == mPaymentMethodSection) {
            expand(mPaymentMethodSection);
        } else if (v == mPayButton) {
            processPayButton();
        }

        updatePayButtonEnabled();
    }

    private void processPayButton() {
        assert !mIsShowingSpinner;
        mIsProcessingPayClicked = true;

        boolean shouldShowSpinner = mClient.onPayClicked(mShippingAddressSectionInformation == null
                        ? null
                        : mShippingAddressSectionInformation.getSelectedItem(),
                mShippingOptionsSectionInformation == null
                        ? null
                        : mShippingOptionsSectionInformation.getSelectedItem(),
                mPaymentMethodSectionInformation.getSelectedItem(),
                mAcceptThirdPartyConditions.isChecked());

        if (shouldShowSpinner) {
            changeSpinnerVisibility(true);
        }
    }

    /**
     * Called when user cancelled out of the UI that was shown after they clicked [PAY] button.
     */
    public void onPayButtonProcessingCancelled() {
        assert mIsProcessingPayClicked;
        mIsProcessingPayClicked = false;
        changeSpinnerVisibility(false);
        updatePayButtonEnabled();
    }

    /**
     *  Called to show the processing message after instrument details have been loaded
     *  in the case the payment request UI has been skipped.
     */
    public void showProcessingMessageAfterUiSkip() {
        // Button was clicked before but not marked as clicked because we skipped the UI.
        mIsProcessingPayClicked = true;
        showProcessingMessage();
    }

    /**
     * Called when the user has clicked on pay. The message is shown while the payment information
     * is processed right until a confimation from the merchant is received.
     */
    public void showProcessingMessage() {
        assert mIsProcessingPayClicked;

        changeSpinnerVisibility(true);
    }

    private void changeSpinnerVisibility(boolean showSpinner) {
        if (mIsShowingSpinner == showSpinner) return;
        mIsShowingSpinner = showSpinner;

        if (showSpinner) {
            mPaymentContainer.setVisibility(View.GONE);
            mBottomBar.setVisibility(View.GONE);
            mSpinnyLayout.setVisibility(View.VISIBLE);

            // Turn the bottom sheet back into a collapsed bottom sheet showing only the spinner.
            // TODO(dfalcantara): Animate this: https://crbug.com/621955
            ((FrameLayout.LayoutParams) mRequestView.getLayoutParams()).height =
                    LayoutParams.WRAP_CONTENT;
            mRequestView.requestLayout();
        } else {
            mPaymentContainer.setVisibility(View.VISIBLE);
            mBottomBar.setVisibility(View.VISIBLE);
            mSpinnyLayout.setVisibility(View.GONE);

            if (mIsExpandedToFullHeight) {
                ((FrameLayout.LayoutParams) mRequestView.getLayoutParams()).height =
                        LayoutParams.MATCH_PARENT;
                mRequestView.requestLayout();
            }
        }
    }

    private void updatePayButtonEnabled() {
        boolean contactInfoOk = !mRequestContactDetails
                || (mContactDetailsSectionInformation != null
                        && mContactDetailsSectionInformation.getSelectedItem() != null);
        boolean shippingInfoOk = !mRequestShipping
                || (mShippingAddressSectionInformation != null
                        && mShippingAddressSectionInformation.getSelectedItem() != null);
        boolean shippingOptionInfoOk = !mRequestShippingOption
                || (mShippingOptionsSectionInformation != null
                        && mShippingOptionsSectionInformation.getSelectedItem() != null);
        boolean termsOptionOk =
                mAcceptThirdPartyConditions.isChecked() || mReviewThirdPartyConditions.isChecked();
        mPayButton.setEnabled(contactInfoOk && shippingInfoOk && shippingOptionInfoOk
                && mPaymentMethodSectionInformation != null
                && mPaymentMethodSectionInformation.getSelectedItem() != null
                && !mIsClientCheckingSelection && !mIsEditingPaymentItem && !mIsClosing
                && termsOptionOk);
    }

    /** @return Whether or not the dialog can be closed via the X close button. */
    private boolean isAcceptingCloseButton() {
        return mSheetAnimator == null && mSectionAnimator == null && !mIsProcessingPayClicked
                && !mIsEditingPaymentItem && !mIsClosing;
    }

    /** @return Whether or not the dialog is accepting user input. */
    @Override
    public boolean isAcceptingUserInput() {
        return isAcceptingCloseButton() && mPaymentMethodSectionInformation != null
                && !mIsClientCheckingSelection;
    }

    /**
     * Sets the observer to be called when the shipping address section gains or loses focus.
     *
     * @param observer The observer to notify.
     */
    public void setShippingAddressSectionFocusChangedObserver(
            OptionSection.FocusChangedObserver observer) {
        mShippingAddressSection.setOptionSectionFocusChangedObserver(observer);
    }

    private void expand(PaymentRequestSection section) {
        if (!mIsExpandedToFullHeight && section != null) {
            // Container now takes the full height of the screen, animating towards it.
            mRequestView.getLayoutParams().height = LayoutParams.MATCH_PARENT;
            mRequestView.addOnLayoutChangeListener(new SheetEnlargingAnimator(true));
            mPaymentContainerLayout.requestLayout();

            // Disable all but the first button.
            updateSectionButtons();
        }

        // Update the section contents when they're selected.
        mSelectedSection = section;
        mIsExpandedToFullHeight = mSelectedSection != null;
        if (mSelectedSection == mOrderSummarySection) {
            mClient.getShoppingCart(new Callback<ShoppingCart>() {
                @Override
                public void onResult(ShoppingCart result) {
                    updateOrderSummarySection(result);
                    updateSectionVisibility();
                }
            });
        } else if (mSelectedSection == mShippingAddressSection) {
            mClient.getSectionInformation(DataType.SHIPPING_ADDRESSES,
                    createUpdateSectionCallback(DataType.SHIPPING_ADDRESSES));
        } else if (mSelectedSection == mShippingOptionSection) {
            mClient.getSectionInformation(DataType.SHIPPING_OPTIONS,
                    createUpdateSectionCallback(DataType.SHIPPING_OPTIONS));
        } else if (mSelectedSection == mContactDetailsSection) {
            mClient.getSectionInformation(DataType.CONTACT_DETAILS,
                    createUpdateSectionCallback(DataType.CONTACT_DETAILS));
        } else if (mSelectedSection == mPaymentMethodSection) {
            mClient.getSectionInformation(DataType.PAYMENT_METHODS,
                    createUpdateSectionCallback(DataType.PAYMENT_METHODS));
        } else {
            updateSectionVisibility();
        }
    }

    private Callback<SectionInformation> createUpdateSectionCallback(@DataType final int type) {
        return new Callback<SectionInformation>() {
            @Override
            public void onResult(SectionInformation result) {
                updateSection(type, result);
                updateSectionVisibility();
            }
        };
    }

    /** Update the display status of each expandable section in the full dialog. */
    private void updateSectionVisibility() {
        startSectionResizeAnimation();
        mOrderSummarySection.focusSection(mSelectedSection == mOrderSummarySection);
        mShippingAddressSection.focusSection(mSelectedSection == mShippingAddressSection);
        mShippingOptionSection.focusSection(mSelectedSection == mShippingOptionSection);
        mContactDetailsSection.focusSection(mSelectedSection == mContactDetailsSection);
        mPaymentMethodSection.focusSection(mSelectedSection == mPaymentMethodSection);
        updateSectionButtons();
    }

    /**
     * Updates the enabled/disabled state of each section's edit button.
     *
     * Only the top-most button is enabled -- the others are disabled so the user is directed
     * through the form from top to bottom.
     */
    private void updateSectionButtons() {
        // Disable edit buttons when the client is checking a selection.
        boolean mayEnableButton = !mIsClientCheckingSelection;
        for (int i = 0; i < mPaymentContainerLayout.getChildCount(); i++) {
            View child = mPaymentContainerLayout.getChildAt(i);
            if (!(child instanceof PaymentRequestSection)) continue;

            PaymentRequestSection section = (PaymentRequestSection) child;
            section.setIsEditButtonEnabled(mayEnableButton);
            if (section.getEditButtonState() != EDIT_BUTTON_GONE) mayEnableButton = false;
        }
    }

    /**
     * Called when the dialog is dismissed. Can be caused by:
     * <ul>
     *  <li>User click on the "back" button on the phone.</li>
     *  <li>User click on the "X" button in the top-right corner of the dialog.</li>
     *  <li>User click on the "CANCEL" button on the bottom of the dialog.</li>
     *  <li>Successfully processing the payment.</li>
     *  <li>Failure to process the payment.</li>
     *  <li>The JavaScript calling the abort() method in PaymentRequest API.</li>
     *  <li>The PaymentRequest JavaScript object being destroyed.</li>
     *  <li>User closing all incognito windows with PaymentRequest UI open in an incognito
     *      window.</li>
     * </ul>
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        dismiss();
    }

    private void dismiss() {
        mIsClosing = true;
        if (mEditorDialog.isShowing()) mEditorDialog.dismiss();
        if (mCardEditorDialog.isShowing()) mCardEditorDialog.dismiss();
        mClient.onDismiss();
    }

    @Override
    public String getAdditionalText(PaymentRequestSection section) {
        if (section == mShippingAddressSection) {
            int selectedItemIndex = mShippingAddressSectionInformation.getSelectedItemIndex();
            if (selectedItemIndex != SectionInformation.NO_SELECTION
                    && selectedItemIndex != SectionInformation.INVALID_SELECTION) {
                return null;
            }

            String customErrorMessage = mShippingAddressSectionInformation.getErrorMessage();
            if (selectedItemIndex == SectionInformation.INVALID_SELECTION
                    && !TextUtils.isEmpty(customErrorMessage)) {
                return customErrorMessage;
            }

            return mContext.getString(selectedItemIndex == SectionInformation.NO_SELECTION
                            ? mShippingStrings.getSelectPrompt()
                            : mShippingStrings.getUnsupported());
        } else if (section == mPaymentMethodSection) {
            return mPaymentMethodSectionInformation.getAdditionalText();
        } else {
            return null;
        }
    }

    @Override
    public boolean isAdditionalTextDisplayingWarning(PaymentRequestSection section) {
        return section == mShippingAddressSection && mShippingAddressSectionInformation != null
                && mShippingAddressSectionInformation.getSelectedItemIndex()
                == SectionInformation.INVALID_SELECTION;
    }

    @Override
    public void onSectionClicked(PaymentRequestSection section) {
        expand(section);
    }

    /**
     * Animates the different sections of the dialog expanding and contracting into their final
     * positions.
     */
    private void startSectionResizeAnimation() {
        Runnable animationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mSectionAnimator = null;
            }
        };

        mSectionAnimator =
                new FocusAnimator(mPaymentContainerLayout, mSelectedSection, animationEndRunnable);
    }

    /**
     * Animates the bottom sheet UI translating upwards from the bottom of the screen.
     * Can be canceled when a {@link SheetEnlargingAnimator} starts and expands the dialog.
     */
    private class PeekingAnimator
            extends AnimatorListenerAdapter implements OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            mRequestView.removeOnLayoutChangeListener(this);

            mSheetAnimator = ObjectAnimator.ofFloat(
                    mRequestView, View.TRANSLATION_Y, mAnimatorTranslation, 0);
            mSheetAnimator.setDuration(DIALOG_ENTER_ANIMATION_MS);
            mSheetAnimator.setInterpolator(new LinearOutSlowInInterpolator());
            mSheetAnimator.addListener(this);
            mSheetAnimator.start();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mSheetAnimator = null;
        }
    }

    /** Animates the bottom sheet expanding to a larger sheet. */
    private class SheetEnlargingAnimator
            extends AnimatorListenerAdapter implements OnLayoutChangeListener {
        private final boolean mIsBottomBarLockedInPlace;
        private int mContainerHeightDifference;

        public SheetEnlargingAnimator(boolean isBottomBarLockedInPlace) {
            mIsBottomBarLockedInPlace = isBottomBarLockedInPlace;
        }

        /**
         * Updates the animation.
         *
         * @param progress How far along the animation is.  In the range [0,1], with 1 being done.
         */
        private void update(float progress) {
            // The dialog container initially starts off translated downward, gradually decreasing
            // the translation until it is in the right place on screen.
            float containerTranslation = mContainerHeightDifference * progress;
            mRequestView.setTranslationY(containerTranslation);

            if (mIsBottomBarLockedInPlace) {
                // The bottom bar is translated along the dialog so that is looks like it stays in
                // place at the bottom while the entire bottom sheet is translating upwards.
                mBottomBar.setTranslationY(-containerTranslation);

                // The payment container is sandwiched between the header and the bottom bar.
                // Expansion animates by changing where its "bottom" is, letting its shadows appear
                // and disappear as it changes size.
                int paymentContainerBottom =
                        Math.min(mPaymentContainer.getTop() + mPaymentContainer.getMeasuredHeight(),
                                mBottomBar.getTop());
                mPaymentContainer.setBottom(paymentContainerBottom);
            }
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            if (mSheetAnimator != null) mSheetAnimator.cancel();

            mRequestView.removeOnLayoutChangeListener(this);
            mContainerHeightDifference = (bottom - top) - (oldBottom - oldTop);

            ValueAnimator containerAnimator = ValueAnimator.ofFloat(1f, 0f);
            containerAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float alpha = (Float) animation.getAnimatedValue();
                    update(alpha);
                }
            });

            mSheetAnimator = containerAnimator;
            mSheetAnimator.setDuration(DIALOG_ENTER_ANIMATION_MS);
            mSheetAnimator.setInterpolator(new LinearOutSlowInInterpolator());
            mSheetAnimator.addListener(this);
            mSheetAnimator.start();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            // Reset the layout so that everything is in the expected place.
            mRequestView.setTranslationY(0);
            mBottomBar.setTranslationY(0);
            mRequestView.requestLayout();

            // Indicate that the dialog is ready to use.
            mSheetAnimator = null;
        }
    }
}
