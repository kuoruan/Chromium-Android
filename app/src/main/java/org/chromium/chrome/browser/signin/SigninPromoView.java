// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.ui.widget.ButtonCompat;

/**
 * Container view for signin promos.
 */
public class SigninPromoView extends LinearLayout {
    private ImageView mImage;
    private ImageButton mDismissButton;
    private ButtonCompat mSigninButton;
    private Button mChooseAccountButton;

    public SigninPromoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImage = (ImageView) findViewById(R.id.signin_promo_image);
        mDismissButton = (ImageButton) findViewById(R.id.signin_promo_close_button);
        mSigninButton = (ButtonCompat) findViewById(R.id.signin_promo_signin_button);
        mChooseAccountButton = (Button) findViewById(R.id.signin_promo_choose_account_button);
    }

    /**
     * Returns a reference to the image of the promo.
     */
    public ImageView getImage() {
        return mImage;
    }

    /**
     * Returns a reference to the dismiss button.
     */
    public ImageButton getDismissButton() {
        return mDismissButton;
    }

    /**
     * Returns a reference to the signin button.
     */
    public ButtonCompat getSigninButton() {
        return mSigninButton;
    }

    /**
     * Returns a reference to the choose account button.
     */
    public Button getChooseAccountButton() {
        return mChooseAccountButton;
    }
}
