// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.metrics.RecordHistogram;

/**
 * A class used to record journey metrics for the Payment Request feature.
 */
public class PaymentRequestJourneyLogger {
    // The index of the different sections of a Payment Request. Used to record journey stats.
    public static final int SECTION_CONTACT_INFO = 0;
    public static final int SECTION_CREDIT_CARDS = 1;
    public static final int SECTION_SHIPPING_ADDRESS = 2;
    public static final int SECTION_MAX = 3;

    // For the CanMakePayment histograms.
    public static final int CAN_MAKE_PAYMENT_USED = 0;
    public static final int CAN_MAKE_PAYMENT_NOT_USED = 1;
    public static final int CAN_MAKE_PAYMENT_USE_MAX = 2;

    // Used to log different parameters' effect on whether the transaction was completed.
    public static final int COMPLETION_STATUS_COMPLETED = 0;
    public static final int COMPLETION_STATUS_ABORTED = 1;
    public static final int COMPLETION_STATUS_MAX = 2;

    // Used to mesure the impact of the CanMakePayment return value on whether the Payment Request
    // is shown to the user.
    public static final int CMP_SHOW_COULD_NOT_MAKE_PAYMENT_AND_DID_NOT_SHOW = 0;
    public static final int CMP_SHOW_DID_SHOW = 1 << 0;
    public static final int CMP_SHOW_COULD_MAKE_PAYMENT = 1 << 1;
    public static final int CMP_SHOW_MAX = 4;

    // The minimum expected value of CustomCountHistograms is always set to 1. It is still possible
    // to log the value 0 to that type of histogram.
    private static final int MIN_EXPECTED_SAMPLE = 1;
    private static final int MAX_EXPECTED_SAMPLE = 49;
    private static final int NUMBER_BUCKETS = 50;

    private static class SectionStats {
        private int mNumberSuggestionsShown;
        private int mNumberSelectionChanges;
        private int mNumberSelectionEdits;
        private int mNumberSelectionAdds;
        private boolean mIsRequested;
    }

    private SectionStats[] mSections;

    private boolean mWasCanMakePaymentUsed;
    private boolean mCouldMakePayment;
    private boolean mWasShowCalled;

    public PaymentRequestJourneyLogger() {
        mSections = new SectionStats[SECTION_MAX];
        for (int i = 0; i < mSections.length; ++i) {
            mSections[i] = new SectionStats();
        }
    }

    /**
     * Sets the number of suggestions shown for the specified section.
     *
     * @param section The section for which to log.
     * @param number The number of suggestions.
     */
    public void setNumberOfSuggestionsShown(int section, int number) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSuggestionsShown = number;
        mSections[section].mIsRequested = true;
    }

    /**
     * Increments the number of selection changes for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionChanges(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionChanges++;
    }

    /**
     * Increments the number of selection edits for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionEdits(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionEdits++;
    }

    /**
     * Increments the number of selection adds for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionAdds(int section) {
        assert section < SECTION_MAX;
        mSections[section].mNumberSelectionAdds++;
    }

    /**
     * Records the fact that the merchant called CanMakePayment and records it's return value.
     *
     * @param value The return value of the CanMakePayment call.
     */
    public void setCanMakePaymentValue(boolean value) {
        mWasCanMakePaymentUsed = true;
        mCouldMakePayment |= value;
    }

    /**
     * Records the fact that the Payment Request was shown to the user.
     */
    public void setShowCalled() {
        mWasShowCalled = true;
    }

    /*
     * Records the histograms for all the sections that were requested by the merchant and for the
     * usage of the CanMakePayment method and its effect on the transaction. This method should be
     * called when the payment request has either been completed or aborted.
     *
     * @param submissionType A string indicating the way the payment request was concluded.
     */
    public void recordJourneyStatsHistograms(String submissionType) {
        recordSectionSpecificStats(submissionType);

        // Record the CanMakePayment metrics based on whether the transaction was completed or
        // aborted by the user (UserAborted) or otherwise (OtherAborted).
        recordCanMakePaymentStats(submissionType.contains("Abort") ? COMPLETION_STATUS_ABORTED
                                                                   : COMPLETION_STATUS_COMPLETED);
    }

    /**
     * Records the histograms for all the sections that were requested by the merchant.
     *
     * @param submissionType A string indicating the way the payment request was concluded.
     */
    private void recordSectionSpecificStats(String submissionType) {
        // Record whether the user had suggestions for each requested information.
        boolean userHadAllRequestedInformation = true;

        for (int i = 0; i < mSections.length; ++i) {
            String nameSuffix = "";
            switch (i) {
                case SECTION_SHIPPING_ADDRESS:
                    nameSuffix = "ShippingAddress." + submissionType;
                    break;
                case SECTION_CONTACT_INFO:
                    nameSuffix = "ContactInfo." + submissionType;
                    break;
                case SECTION_CREDIT_CARDS:
                    nameSuffix = "CreditCards." + submissionType;
                    break;
                default:
                    break;
            }

            assert !nameSuffix.isEmpty();

            // Only log the metrics for a section if it was requested by the merchant.
            if (mSections[i].mIsRequested) {
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionAdds." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionAdds, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionChanges." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionChanges, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSelectionEdits." + nameSuffix,
                        Math.min(mSections[i].mNumberSelectionEdits, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);
                RecordHistogram.recordCustomCountHistogram(
                        "PaymentRequest.NumberOfSuggestionsShown." + nameSuffix,
                        Math.min(mSections[i].mNumberSuggestionsShown, MAX_EXPECTED_SAMPLE),
                        MIN_EXPECTED_SAMPLE, MAX_EXPECTED_SAMPLE, NUMBER_BUCKETS);

                if (mSections[i].mNumberSuggestionsShown == 0) {
                    userHadAllRequestedInformation = false;
                }
            }
        }

        // Record metrics about completion based on whether the user had suggestions for each
        // requested information.
        int completionStatus = submissionType.contains("Abort") ? COMPLETION_STATUS_ABORTED
                                                                : COMPLETION_STATUS_COMPLETED;
        if (userHadAllRequestedInformation) {
            RecordHistogram.recordEnumeratedHistogram(
                    "PaymentRequest.UserHadSuggestionsForEverything.EffectOnCompletion",
                    completionStatus, COMPLETION_STATUS_MAX);
        } else {
            RecordHistogram.recordEnumeratedHistogram(
                    "PaymentRequest.UserDidNotHaveSuggestionsForEverything.EffectOnCompletion",
                    completionStatus, COMPLETION_STATUS_MAX);
        }
    }

    /**
     * Records the metrics related the the CanMakePayment method.
     *
     * @param completionStatus Whether the transaction was completed or aborted.
     */
    private void recordCanMakePaymentStats(int completionStatus) {
        // Record CanMakePayment usage.
        RecordHistogram.recordEnumeratedHistogram("PaymentRequest.CanMakePayment.Usage",
                mWasCanMakePaymentUsed ? CAN_MAKE_PAYMENT_USED : CAN_MAKE_PAYMENT_NOT_USED,
                CAN_MAKE_PAYMENT_USE_MAX);

        recordCanMakePaymentEffectOnShow();
        recordCanMakePaymentEffectOnCompletion(completionStatus);
    }

    /**
     * Records CanMakePayment's return value effect on whether the Payment Request was shown or not.
     */
    private void recordCanMakePaymentEffectOnShow() {
        if (!mWasCanMakePaymentUsed) return;

        int effectOnShow = 0;
        if (mWasShowCalled) effectOnShow |= CMP_SHOW_DID_SHOW;
        if (mCouldMakePayment) effectOnShow |= CMP_SHOW_COULD_MAKE_PAYMENT;

        RecordHistogram.recordEnumeratedHistogram(
                "PaymentRequest.CanMakePayment.Used.EffetOnShow", effectOnShow, CMP_SHOW_MAX);
    }

    /**
     * Records the completion status depending on the the usage and return value of the
     * CanMakePaymentMethod.
     *
     * @param completionStatus Whether the transaction was completed or aborted.
     */
    private void recordCanMakePaymentEffectOnCompletion(int completionStatus) {
        if (!mWasShowCalled) return;

        String histogramName = "PaymentRequest.CanMakePayment.";

        if (!mWasCanMakePaymentUsed) {
            histogramName += "NotUsed.WithShowEffectOnCompletion";
        } else if (mCouldMakePayment) {
            histogramName += "Used.TrueWithShowEffectOnCompletion";
        } else {
            histogramName += "Used.FalseWithShowEffectOnCompletion";
        }

        RecordHistogram.recordEnumeratedHistogram(
                histogramName, completionStatus, COMPLETION_STATUS_MAX);
    }
}