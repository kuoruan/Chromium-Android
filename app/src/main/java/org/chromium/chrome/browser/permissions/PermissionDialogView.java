// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.permissions;

import android.content.DialogInterface;
import android.os.Build;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;

import java.lang.reflect.Field;

/**
 * The Permission dialog that is either app modal or tab modal.
 */
public class PermissionDialogView {
    private static final String TAG = "PermissionDialogView";

    private AlertDialog mDialog;
    private PermissionDialogDelegate mDialogDelegate;

    /**
     * Constructor for the Dialog View. Creates the AlertDialog.
     */
    public PermissionDialogView(PermissionDialogDelegate delegate) {
        mDialogDelegate = delegate;
        ChromeActivity activity = mDialogDelegate.getTab().getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        mDialog = builder.create();
        mDialog.getDelegate().setHandleNativeActionModesEnabled(false);
        mDialog.setCanceledOnTouchOutside(false);
    }

    /**
     * Prepares the dialog before show. Creates the View inside of the dialog,
     * and adds the buttons. Callbacks that are needed for buttons and dismiss
     * are the input.
     * @param positiveClickListener callback for positive button.
     * @param negativeClickListener callback for negative button.
     * @param dismissListener is called when user dismissed the dialog.
     */
    public void createView(DialogInterface.OnClickListener positiveClickListener,
            DialogInterface.OnClickListener negativeClickListener,
            DialogInterface.OnDismissListener dismissListener) {
        ChromeActivity activity = mDialogDelegate.getTab().getActivity();
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.permission_dialog, null);
        TextView messageTextView = (TextView) view.findViewById(R.id.text);
        String messageText = mDialogDelegate.getMessageText();
        assert !TextUtils.isEmpty(messageText);
        messageTextView.setText(messageText);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.announceForAccessibility(messageText);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                messageTextView, mDialogDelegate.getDrawableId(), 0, 0, 0);
        mDialog.setView(view);
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE, mDialogDelegate.getPrimaryButtonText(),
                positiveClickListener);
        mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, mDialogDelegate.getSecondaryButtonText(),
                negativeClickListener);
        mDialog.setOnDismissListener(dismissListener);
    }

    /* Shows the dialog */
    public void show() {
        mDialog.show();
        filterTouchForSecurity();
    }

    /* Dismiss the dialog */
    public void dismiss() {
        mDialog.dismiss();
    }

    /**
     * Returns the {@link Button} from the dialog, or null if
     * a button does not exist.
     * @param whichButton The identifier of the button that should be returned.
     */
    public Button getButton(int whichButton) {
        return mDialog.getButton(whichButton);
    }

    /**
     * Filter touch events on buttons when there is an overlay window overlaps the permission dialog
     * window.
     */
    private void filterTouchForSecurity() {
        Button positiveButton = getButton(DialogInterface.BUTTON_POSITIVE);
        Button negativeButton = getButton(DialogInterface.BUTTON_NEGATIVE);
        View.OnTouchListener onTouchListener = (View v, MotionEvent ev) -> {
            // Filter touch events based MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED which is
            // introduced on M+.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

            try {
                Field field = MotionEvent.class.getField("FLAG_WINDOW_IS_PARTIALLY_OBSCURED");
                if ((ev.getFlags() & field.getInt(null)) != 0) return true;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Log.e(TAG, "Reflection failure: " + e);
            }
            return false;
        };

        positiveButton.setFilterTouchesWhenObscured(true);
        positiveButton.setOnTouchListener(onTouchListener);
        negativeButton.setFilterTouchesWhenObscured(true);
        negativeButton.setOnTouchListener(onTouchListener);
    }
}
