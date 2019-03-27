// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.payment;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.chromium.chrome.autofill_assistant.R;

/** Autofill Assistant specific bottom bar for the payment request UI. */
public class PaymentRequestBottomBar extends LinearLayout {
    private View mPrimaryButton;
    private View mSecondaryButton;

    /** Constructor for when the PaymentRequestBottomBar is inflated from XML. */
    public PaymentRequestBottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPrimaryButton = findViewById(R.id.button_primary);
        mSecondaryButton = findViewById(R.id.button_secondary);
    }
}
