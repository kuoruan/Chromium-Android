// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contacts_picker;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A data adapter for the Contacts Picker.
 */
public class PickerAdapter extends Adapter<ViewHolder> {
    // The category view to use to show the contacts.
    private PickerCategoryView mCategoryView;

    // The content resolver to query data from.
    private ContentResolver mContentResolver;

    // A cursor containing the raw contacts data.
    private Cursor mContactsCursor;

    /**
     * Holds on to a {@link ContactView} that displays information about a contact.
     */
    public class ContactViewHolder extends ViewHolder {
        /**
         * The ContactViewHolder.
         * @param itemView The {@link ContactView} view for showing the contact details.
         */
        public ContactViewHolder(ContactView itemView) {
            super(itemView);
        }
    }

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
    };

    /**
     * The PickerAdapter constructor.
     * @param categoryView The category view to use to show the contacts.
     */
    public PickerAdapter(PickerCategoryView categoryView, ContentResolver contentResolver) {
        mCategoryView = categoryView;
        mContentResolver = contentResolver;
        mContactsCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION,
                null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");
    }

    /**
     * Sets the search query (filter) for the contact list. Filtering is by display name.
     * @param query The search term to use.
     */
    public void setSearchString(String query) {
        String searchString = "%" + query + "%";
        String[] selectionArgs = {searchString};
        mContactsCursor.close();

        mContactsCursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?", selectionArgs,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");
        notifyDataSetChanged();
    }

    /**
     * Fetches all known contacts and their emails.
     * @return The contact list as a set.
     */
    public Set<ContactDetails> getAllContacts() {
        Set<ContactDetails> contacts = new HashSet<>();
        if (!mContactsCursor.moveToFirst()) return contacts;
        do {
            String id = mContactsCursor.getString(
                    mContactsCursor.getColumnIndex(ContactsContract.Contacts._ID));
            String name = mContactsCursor.getString(
                    mContactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
            contacts.add(new ContactDetails(id, name, getEmails()));
        } while (mContactsCursor.moveToNext());

        return contacts;
    }

    // RecyclerView.Adapter:

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ContactView itemView = (ContactView) LayoutInflater.from(parent.getContext())
                                       .inflate(R.layout.contact_view, parent, false);
        itemView.setCategoryView(mCategoryView);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String id = "";
        String name = "";
        if (mContactsCursor.moveToPosition(position)) {
            id = mContactsCursor.getString(
                    mContactsCursor.getColumnIndex(ContactsContract.Contacts._ID));
            name = mContactsCursor.getString(
                    mContactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
        }

        ((ContactView) holder.itemView).initialize(new ContactDetails(id, name, getEmails()));
    }

    private Cursor getEmailCursor(String id) {
        Cursor emailCursor =
                mContentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = " + id, null,
                        ContactsContract.CommonDataKinds.Email.DATA + " ASC");
        return emailCursor;
    }

    private ArrayList<String> getEmails() {
        // Look up all associated emails for this contact.
        // TODO(finnur): Investigate whether we can do this in one go with the original cursor...
        String id = mContactsCursor.getString(
                mContactsCursor.getColumnIndex(ContactsContract.Contacts._ID));
        Cursor emailCursor = getEmailCursor(id);
        ArrayList<String> emails = new ArrayList<String>();
        while (emailCursor.moveToNext()) {
            emails.add(emailCursor.getString(
                    emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)));
        }
        emailCursor.close();
        return emails;
    }

    @Override
    public int getItemCount() {
        return mContactsCursor.getCount();
    }
}
