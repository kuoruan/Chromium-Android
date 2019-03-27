// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.details;

import android.support.annotation.Nullable;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Java side equivalent of autofill_assistant::DetailsProto.
 */
@JNINamespace("autofill_assistant")
public class AssistantDetails {
    private static final String RFC_3339_FORMAT_WITHOUT_TIMEZONE = "yyyy'-'MM'-'dd'T'HH':'mm':'ss";

    private final String mTitle;
    private final String mUrl;
    @Nullable
    private final Date mDate;
    private final String mDescription;
    private final String mMid;
    /** Whether user approval is required (i.e., due to changes). */
    private boolean mUserApprovalRequired;
    /** Whether the title should be highlighted. */
    private boolean mHighlightTitle;
    /** Whether the date should be highlighted. */
    private boolean mHighlightDate;
    /** Whether empty fields should have the animated placeholder background. */
    private final boolean mShowPlaceholdersForEmptyFields;
    /**
     * The correctly formatted price for the client locale, including the currency.
     * Example: '$20.50' or '20.50 €'.
     */
    private final String mPrice;
    // NOTE: When adding a new field, update the clearChangedFlags and toJSONObject methods.

    public AssistantDetails(String title, String url, @Nullable Date date, String description,
            String mId, @Nullable String price, boolean userApprovalRequired,
            boolean highlightTitle, boolean highlightDate, boolean showPlaceholdersForEmptyFields) {
        this.mTitle = title;
        this.mUrl = url;
        this.mDate = date;
        this.mDescription = description;
        this.mMid = mId;
        this.mPrice = price;
        this.mUserApprovalRequired = userApprovalRequired;
        this.mHighlightTitle = highlightTitle;
        this.mHighlightDate = highlightDate;
        this.mShowPlaceholdersForEmptyFields = showPlaceholdersForEmptyFields;
    }

    String getTitle() {
        return mTitle;
    }

    String getUrl() {
        return mUrl;
    }

    @Nullable
    Date getDate() {
        return mDate;
    }

    String getDescription() {
        return mDescription;
    }

    private String getMid() {
        return mMid;
    }

    @Nullable
    String getPrice() {
        return mPrice;
    }

    boolean getUserApprovalRequired() {
        return mUserApprovalRequired;
    }

    boolean getHighlightTitle() {
        return mHighlightTitle;
    }

    boolean getHighlightDate() {
        return mHighlightDate;
    }

    boolean getShowPlaceholdersForEmptyFields() {
        return mShowPlaceholdersForEmptyFields;
    }

    /**
     * Create details with the given values.
     */
    @CalledByNative
    private static AssistantDetails create(String title, String url, String description, String mId,
            String price, String datetime, long year, int month, int day, int hour, int minute,
            int second, boolean userApprovalRequired, boolean highlightTitle,
            boolean highlightDate) {
        Date date = null;
        if (year > 0 && month > 0 && day > 0 && hour >= 0 && minute >= 0 && second >= 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            // Month in Java Date is 0-based, but the one we receive from the server is 1-based.
            calendar.set((int) year, month - 1, day, hour, minute, second);
            date = calendar.getTime();
        } else if (!datetime.isEmpty()) {
            try {
                // The parameter contains the timezone shift from the current location, that we
                // don't care about.
                date = new SimpleDateFormat(RFC_3339_FORMAT_WITHOUT_TIMEZONE, Locale.ROOT)
                               .parse(datetime);
            } catch (ParseException e) {
                // Ignore.
            }
        }

        if (price.length() == 0) price = null;

        return new AssistantDetails(title, url, date, description, mId, price, userApprovalRequired,
                highlightTitle, highlightDate,
                /* showPlaceholdersForEmptyFields= */ false);
    }
}
