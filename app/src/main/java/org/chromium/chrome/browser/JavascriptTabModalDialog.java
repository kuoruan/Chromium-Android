// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.ui.base.WindowAndroid;

/**
 * A dialog shown via JavaScript. This can be an alert dialog, a prompt dialog or a confirm dialog.
 */
public class JavascriptTabModalDialog implements DialogInterface.OnClickListener {
    private static final String TAG = "JavaScriptDialog";

    private final String mTitle;
    private final String mMessage;
    private final int mPositiveButtonTextId;
    private final int mNegativeButtonTextId;
    private long mNativeDialogPointer;
    private AlertDialog mDialog;
    private TextView mPromptTextView;

    private JavascriptTabModalDialog(
            String title, String message, int positiveButtonTextId, int negativeButtonTextId) {
        mTitle = title;
        mMessage = message;
        mPositiveButtonTextId = positiveButtonTextId;
        mNegativeButtonTextId = negativeButtonTextId;
    }

    @CalledByNative
    private static JavascriptTabModalDialog createAlertDialog(String title, String message) {
        return new JavascriptTabModalDialog(title, message, R.string.ok, 0);
    }

    @CalledByNative
    private static JavascriptTabModalDialog createConfirmDialog(String title, String message) {
        return new JavascriptTabModalDialog(title, message, R.string.ok, R.string.cancel);
    }

    @CalledByNative
    private static JavascriptTabModalDialog createPromptDialog(
            String title, String message, String defaultPromptText) {
        return new JavascriptPromptDialog(title, message, defaultPromptText);
    }

    @CalledByNative
    private void showDialog(WindowAndroid window, long nativeDialogPointer) {
        assert window != null;
        Context context = window.getActivity().get();
        // If the activity has gone away, then just clean up the native pointer.
        if (context == null) {
            nativeCancel(nativeDialogPointer);
            return;
        }

        // Cache the native dialog pointer so that we can use it to return the response.
        mNativeDialogPointer = nativeDialogPointer;

        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.js_modal_dialog, null);
        prepare(layout);

        mPromptTextView = layout.findViewById(R.id.js_modal_dialog_prompt);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                                              .setView(layout)
                                              .setTitle(mTitle)
                                              .setOnCancelListener(dialog -> cancel());
        if (mPositiveButtonTextId != 0) builder.setPositiveButton(mPositiveButtonTextId, this);
        if (mNegativeButtonTextId != 0) builder.setNegativeButton(mNegativeButtonTextId, this);

        mDialog = builder.create();
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.getDelegate().setHandleNativeActionModesEnabled(false);
        mDialog.show();
    }

    @CalledByNative
    private String getUserInput() {
        return mPromptTextView.getText().toString();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                accept(mPromptTextView.getText().toString());
                mDialog.dismiss();
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                cancel();
                mDialog.dismiss();
                break;
            default:
                Log.e(TAG, "Unexpected button pressed in dialog: " + which);
        }
    }

    protected void prepare(final ViewGroup layout) {
        // TabModal dialog do not have the option of suppressing dialogs.
        layout.findViewById(R.id.suppress_js_modal_dialogs).setVisibility(View.GONE);

        // If the message is null or empty do not display the message text view.
        // Hide parent scroll view instead of text view in order to prevent ui discrepancies.
        if (TextUtils.isEmpty(mMessage)) {
            layout.findViewById(R.id.js_modal_dialog_scroll_view).setVisibility(View.GONE);
        } else {
            ((TextView) layout.findViewById(R.id.js_modal_dialog_message)).setText(mMessage);

            layout.findViewById(R.id.js_modal_dialog_scroll_view)
                    .addOnLayoutChangeListener(
                            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                                boolean isScrollable = v.getMeasuredHeight() - v.getPaddingTop()
                                                - v.getPaddingBottom()
                                        < ((ViewGroup) v).getChildAt(0).getMeasuredHeight();

                                v.setFocusable(isScrollable);
                            });
        }
    }

    private void accept(String promptResult) {
        if (mNativeDialogPointer != 0) {
            nativeAccept(mNativeDialogPointer, promptResult);
        }
    }

    private void cancel() {
        if (mNativeDialogPointer != 0) {
            nativeCancel(mNativeDialogPointer);
        }
    }

    @CalledByNative
    private void dismiss() {
        mDialog.dismiss();
        mNativeDialogPointer = 0;
    }

    /**
     * Returns the AlertDialog associated with this JavascriptAppPromptDialog.
     */
    @VisibleForTesting
    public AlertDialog getDialogForTest() {
        return mDialog;
    }

    private static class JavascriptPromptDialog extends JavascriptTabModalDialog {
        private final String mDefaultPromptText;

        JavascriptPromptDialog(String title, String message, String defaultPromptText) {
            super(title, message, R.string.ok, R.string.cancel);
            mDefaultPromptText = defaultPromptText;
        }

        @Override
        protected void prepare(ViewGroup layout) {
            super.prepare(layout);
            EditText prompt = layout.findViewById(R.id.js_modal_dialog_prompt);
            prompt.setVisibility(View.VISIBLE);

            if (mDefaultPromptText.length() > 0) {
                prompt.setText(mDefaultPromptText);
                prompt.selectAll();
            }
        }
    }

    private native void nativeAccept(long nativeJavaScriptDialogAndroid, String prompt);
    private native void nativeCancel(long nativeJavaScriptDialogAndroid);
}
