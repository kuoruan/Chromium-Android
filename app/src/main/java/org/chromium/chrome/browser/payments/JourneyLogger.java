// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.annotations.JNINamespace;

/**
 * A class used to record journey metrics for the Payment Request feature.
 */
@JNINamespace("payments")
public class JourneyLogger {
    // Note: The constants should always be in sync with those in the
    // components/payments/core/journey_logger.h file.
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
    public static final int COMPLETION_STATUS_USER_ABORTED = 1;
    public static final int COMPLETION_STATUS_OTHER_ABORTED = 2;
    public static final int COMPLETION_STATUS_MAX = 3;

    // Used to mesure the impact of the CanMakePayment return value on whether the Payment Request
    // is shown to the user.
    public static final int CMP_SHOW_COULD_NOT_MAKE_PAYMENT_AND_DID_NOT_SHOW = 0;
    public static final int CMP_SHOW_DID_SHOW = 1 << 0;
    public static final int CMP_SHOW_COULD_MAKE_PAYMENT = 1 << 1;
    public static final int CMP_SHOW_MAX = 4;

    // The events that can occur during a Payment Request.
    public static final int EVENT_INITIATED = 0;
    public static final int EVENT_SHOWN = 1 << 0;
    public static final int EVENT_PAY_CLICKED = 1 << 1;
    public static final int EVENT_RECEIVED_INSTRUMENT_DETAILS = 1 << 2;
    public static final int EVENT_SKIPPED_SHOW = 1 << 3;
    public static final int EVENT_MAX = 16;

    // The minimum expected value of CustomCountHistograms is always set to 1. It is still possible
    // to log the value 0 to that type of histogram.
    private static final int MIN_EXPECTED_SAMPLE = 1;
    private static final int MAX_EXPECTED_SAMPLE = 49;
    private static final int NUMBER_BUCKETS = 50;

    /**
     * Pointer to the native implementation.
     */
    private long mJourneyLoggerAndroid;

    public JourneyLogger(boolean isIncognito, String url) {
        // Note that this pointer could leak the native object. The called must call destroy() to
        // ensure that the native object is destroyed.
        mJourneyLoggerAndroid = nativeInitJourneyLoggerAndroid(isIncognito, url);
    }

    /** Will destroy the native object. This class shouldn't be used afterwards. */
    public void destroy() {
        if (mJourneyLoggerAndroid != 0) {
            nativeDestroy(mJourneyLoggerAndroid);
            mJourneyLoggerAndroid = 0;
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
        nativeSetNumberOfSuggestionsShown(mJourneyLoggerAndroid, section, number);
    }

    /**
     * Increments the number of selection changes for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionChanges(int section) {
        assert section < SECTION_MAX;
        nativeIncrementSelectionChanges(mJourneyLoggerAndroid, section);
    }

    /**
     * Increments the number of selection edits for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionEdits(int section) {
        assert section < SECTION_MAX;
        nativeIncrementSelectionEdits(mJourneyLoggerAndroid, section);
    }

    /**
     * Increments the number of selection adds for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionAdds(int section) {
        assert section < SECTION_MAX;
        nativeIncrementSelectionAdds(mJourneyLoggerAndroid, section);
    }

    /**
     * Records the fact that the merchant called CanMakePayment and records it's return value.
     *
     * @param value The return value of the CanMakePayment call.
     */
    public void setCanMakePaymentValue(boolean value) {
        nativeSetCanMakePaymentValue(mJourneyLoggerAndroid, value);
    }

    /**
     * Records the fact that the Payment Request was shown to the user.
     */
    public void setShowCalled() {
        nativeSetShowCalled(mJourneyLoggerAndroid);
    }

    /**
     * Records that an event occurred.
     */
    public void setEventOccurred(int event) {
        assert event < EVENT_MAX;
        nativeSetEventOccurred(mJourneyLoggerAndroid, event);
    }

    /*
     * Records the histograms for all the sections that were requested by the merchant and for the
     * usage of the CanMakePayment method and its effect on the transaction. This method should be
     * called when the payment request has either been completed or aborted.
     *
     * @param submissionType An int indicating the way the payment request was concluded.
     */
    public void recordJourneyStatsHistograms(int completionStatus) {
        nativeRecordJourneyStatsHistograms(mJourneyLoggerAndroid, completionStatus);
    }

    private native long nativeInitJourneyLoggerAndroid(boolean isIncognito, String url);
    private native void nativeDestroy(long nativeJourneyLoggerAndroid);
    private native void nativeSetNumberOfSuggestionsShown(
            long nativeJourneyLoggerAndroid, int section, int number);
    private native void nativeIncrementSelectionChanges(
            long nativeJourneyLoggerAndroid, int section);
    private native void nativeIncrementSelectionEdits(long nativeJourneyLoggerAndroid, int section);
    private native void nativeIncrementSelectionAdds(long nativeJourneyLoggerAndroid, int section);
    private native void nativeSetCanMakePaymentValue(
            long nativeJourneyLoggerAndroid, boolean value);
    private native void nativeSetShowCalled(long nativeJourneyLoggerAndroid);
    private native void nativeSetEventOccurred(long nativeJourneyLoggerAndroid, int event);
    private native void nativeRecordJourneyStatsHistograms(
            long nativeJourneyLoggerAndroid, int completionStatus);
}