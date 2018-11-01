// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contacts_picker;

import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A class to keep track of the metadata associated with a contact.
 */
public class ContactDetails implements Comparable<ContactDetails> {
    // The unique id for the contact.
    private String mId;

    // The display name for this contact.
    private String mDisplayName;

    // The list of emails registered for this contact.
    private List<String> mEmails;

    /**
     * The ContactDetails constructor.
     * @param id The unique identifier of this contact.
     * @param displayName The display name of this contact.
     * @param emails The emails registered for this contact.
     */
    public ContactDetails(String id, String displayName, List<String> emails) {
        mDisplayName = displayName;
        mEmails = emails;
        mId = id;
    }

    /**
     * Accessor for the display name.
     * @return The full display name.
     */
    public String getDisplayName() {
        return mDisplayName;
    }

    /**
     * Accessor for the abbreviated display name (first letter of first name and first letter of
     * last name).
     * @return The display name, abbreviated to two characters.
     */
    public String getDisplayNameAbbreviation() {
        // Display the two letter abbreviation of the display name.
        String displayChars = "";
        if (mDisplayName.length() > 0) {
            displayChars += mDisplayName.charAt(0);
            String[] parts = mDisplayName.split(" ");
            if (parts.length > 1) {
                displayChars += parts[parts.length - 1].charAt(0);
            }
        }

        return displayChars;
    }

    /**
     * Accessor for the list of emails (as strings separated by newline).
     * @return A string containing all the emails registered for this contact.
     */
    public String getEmailsAsString() {
        int count = 0;
        StringBuilder builder = new StringBuilder();
        for (String email : mEmails) {
            if (count++ > 0) {
                builder.append("\n");
            }
            builder.append(email);
        }

        return builder.toString();
    }

    /**
     * A comparison function (results in a full name ascending sorting).
     * @param other The other ContactDetails object to compare it with.
     * @return 0, 1, or -1, depending on which is bigger.
     */
    @Override
    public int compareTo(ContactDetails other) {
        return other.mDisplayName.compareTo(mDisplayName);
    }

    @Override
    public int hashCode() {
        Object[] values = {mId, mDisplayName, mEmails};
        return Arrays.hashCode(values);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (object == null) return false;
        if (object == this) return true;
        if (!(object instanceof ContactDetails)) return false;

        ContactDetails otherInfo = (ContactDetails) object;
        return mId.equals(otherInfo.mId);
    }
}
