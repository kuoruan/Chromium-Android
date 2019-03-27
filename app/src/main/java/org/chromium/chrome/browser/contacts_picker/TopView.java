// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contacts_picker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.text.NumberFormat;

/**
 * A container class for the Disclaimer and Select All functionality (and both associated labels).
 */
public class TopView extends RelativeLayout implements CompoundButton.OnCheckedChangeListener {
    /**
     * An interface for communicating when the Select All checkbox is toggled.
     */
    public interface SelectAllToggleCallback {
        /**
         * Called when the Select All checkbox is toggled.
         * @param allSelected Whether the Select All checkbox is checked.
         */
        void onSelectAllToggled(boolean allSelected);
    }

    private final Context mContext;

    // The container box for the checkbox and its label and contact count.
    private View mCheckboxContainer;

    // The Select All checkbox.
    private CheckBox mSelectAllBox;

    // The label showing how many contacts were found.
    private TextView mContactCount;

    // The callback to use when notifying that the Select All checkbox was toggled.
    private SelectAllToggleCallback mSelectAllCallback;

    // Whether to temporarily ignore clicks on the checkbox.
    private boolean mIgnoreCheck;

    public TopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCheckboxContainer = findViewById(R.id.content);
        mSelectAllBox = findViewById(R.id.checkbox);
        mContactCount = findViewById(R.id.checkbox_details);

        TextView title = findViewById(R.id.checkbox_title);
        title.setText(R.string.contacts_picker_all_contacts);
    }

    /**
     * Set the string explaining which site the dialog will be sharing the data with.
     * @param origin The origin string to display.
     */
    public void setSiteString(String origin) {
        String siteString = mContext.getString(R.string.disclaimer_sharing_contact_details, origin);
        TextView explanation = findViewById(R.id.explanation);
        explanation.setText(siteString);
    }

    /**
     * Register a callback to use to notify that Select All was toggled.
     * @param callback The callback to use.
     */
    public void registerSelectAllCallback(SelectAllToggleCallback callback) {
        mSelectAllCallback = callback;
    }

    /**
     * Updates the visibility of the Select All checkbox.
     * @param visible Whether the checkbox should be visible.
     */
    public void updateCheckboxVisibility(boolean visible) {
        if (visible) {
            mSelectAllBox.setOnCheckedChangeListener(this);
        } else {
            mCheckboxContainer.setVisibility(GONE);
        }
    }

    /**
     * Updates the total number of contacts found in the dialog.
     * @param count The number of contacts found.
     */
    public void updateContactCount(int count) {
        mContactCount.setText(NumberFormat.getInstance().format(count));
    }

    /**
     * Toggles the Select All checkbox.
     */
    public void toggle() {
        mSelectAllBox.setChecked(!mSelectAllBox.isChecked());
    }

    /**
     * Updates the state of the checkbox to reflect whether everything is selected.
     * @param allSelected
     */
    public void updateSelectAllCheckbox(boolean allSelected) {
        mIgnoreCheck = true;
        mSelectAllBox.setChecked(allSelected);
        mIgnoreCheck = false;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (!mIgnoreCheck) mSelectAllCallback.onSelectAllToggled(mSelectAllBox.isChecked());
    }
}
