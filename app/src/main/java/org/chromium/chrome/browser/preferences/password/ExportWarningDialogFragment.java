// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * Shows the dialog that gives the user some tips for how to treat the exported passwords securely.
 */
public class ExportWarningDialogFragment extends DialogFragment {
    // This handler is used to answer the user actions on the dialog.
    private DialogInterface.OnClickListener mHandler;

    public void setExportWarningHandler(DialogInterface.OnClickListener handler) {
        mHandler = handler;
    }

    /**
     * Opens the dialog with the warning and sets the button listener to a fragment identified by ID
     * passed in arguments.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity(), R.style.SimpleDialog)
                .setPositiveButton(R.string.save_password_preferences_export_action_title, mHandler)
                .setNegativeButton(R.string.cancel, mHandler)
                .setMessage(getActivity().getResources().getString(
                        R.string.settings_passwords_export_description))
                .create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If there is savedInstanceState, then the dialog is being recreated by Android and will
        // lack the necessary callbacks. Dismiss immediately as the settings page will need to be
        // recreated anyway.
        if (savedInstanceState != null) {
            dismiss();
            return;
        }
    }
}
