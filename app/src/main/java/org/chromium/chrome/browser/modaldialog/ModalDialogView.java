// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.modaldialog;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Generic builder for app modal or tab modal alert dialogs.
 */
public class ModalDialogView implements View.OnClickListener {
    /**
     * Interface that controls the actions on the modal dialog.
     */
    public interface Controller {
        /**
         * Handle click event of the buttons on the dialog.
         * @param buttonType The type of the button.
         */
        void onClick(@ButtonType int buttonType);

        /**
         * Handle dismiss event when the dialog is not dismissed by actions on the dialog such as
         * back press, and on tab modal dialog, tab switcher button click.
         */
        void onCancel();
    }

    /** Parameters that can be used to create a new ModalDialogView. */
    public static class Params {
        /** Optional: The String to show as the dialog title. */
        public String title;

        /** Optional: The String to show as descriptive text. */
        public String message;

        /**
         * Optional: The customized View to show in the dialog. Note that the message and the
         * custom view cannot be set together.
         */
        public View customView;

        /** Optional: Resource ID of the String to show on the positive button. */
        public @StringRes int positiveButtonTextId;

        /** Optional: Resource ID of the String to show on the negative button. */
        public @StringRes int negativeButtonTextId;
    }

    @IntDef({BUTTON_POSITIVE, BUTTON_NEGATIVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ButtonType {}
    public static final int BUTTON_POSITIVE = 0;
    public static final int BUTTON_NEGATIVE = 1;

    private final Controller mController;
    private final Context mContext;
    private final Params mParams;

    private final View mDialogView;
    private final TextView mTitleView;
    private final TextView mMessageView;
    private final ViewGroup mCustomView;
    private final Button mPositiveButton;
    private final Button mNegativeButton;

    /**
     * Constructor for initializing controller and views.
     * @param controller The controller for this dialog.
     */
    public ModalDialogView(@NonNull Controller controller, @NonNull Params params) {
        mController = controller;
        mContext = new ContextThemeWrapper(
                ContextUtils.getApplicationContext(), R.style.ModalDialogTheme);
        mParams = params;

        mDialogView = LayoutInflater.from(mContext).inflate(R.layout.modal_dialog_view, null);
        mTitleView = mDialogView.findViewById(R.id.title);
        mMessageView = mDialogView.findViewById(R.id.message);
        mCustomView = mDialogView.findViewById(R.id.custom);
        mPositiveButton = mDialogView.findViewById(R.id.positive_button);
        mNegativeButton = mDialogView.findViewById(R.id.negative_button);
    }

    @Override
    public void onClick(View view) {
        if (view == mPositiveButton) {
            mController.onClick(BUTTON_POSITIVE);
        } else if (view == mNegativeButton) {
            mController.onClick(BUTTON_NEGATIVE);
        }
    }

    /**
     * Prepare the contents before showing the dialog.
     */
    protected void prepareBeforeShow() {
        if (TextUtils.isEmpty(mParams.title)) {
            mTitleView.setVisibility(View.GONE);
        } else {
            mTitleView.setText(mParams.title);
        }

        if (TextUtils.isEmpty(mParams.message)) {
            ((View) mMessageView.getParent()).setVisibility(View.GONE);
        } else {
            assert mParams.customView == null;
            mMessageView.setText(mParams.message);
        }

        if (mParams.customView != null) {
            if (mParams.customView.getParent() != null) {
                ((ViewGroup) mParams.customView.getParent()).removeView(mParams.customView);
            }
            mCustomView.addView(mParams.customView);
        } else {
            mCustomView.setVisibility(View.GONE);
        }

        if (mParams.positiveButtonTextId == 0) {
            mPositiveButton.setVisibility(View.GONE);
        } else {
            mPositiveButton.setText(mParams.positiveButtonTextId);
            mPositiveButton.setOnClickListener(this);
        }

        if (mParams.negativeButtonTextId == 0) {
            mNegativeButton.setVisibility(View.GONE);
        } else {
            mNegativeButton.setText(mParams.negativeButtonTextId);
            mNegativeButton.setOnClickListener(this);
        }
    }

    /**
     * @return The {@link Context} with the modal dialog theme set.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * @return The content view of this dialog.
     */
    public View getView() {
        return mDialogView;
    }

    /**
     * @return The controller that controls the actions on the dialogs.
     */
    public Controller getController() {
        return mController;
    }
}
