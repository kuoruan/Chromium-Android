// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.payment;

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.chromium.base.Promise;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill_assistant.payment.AutofillAssistantPaymentRequest.SelectedPaymentInformation;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentOptions;

// TODO(crbug.com/806868): Refactor AutofillAssistantPaymentRequest and merge with this file.
// TODO(crbug.com/806868): Use mCarouselCoordinator to show chips.
/**
 * Coordinator for the Payment Request.
 */
public class AssistantPaymentRequestCoordinator {
    private final WebContents mWebContents;
    @Nullable
    private Runnable mOnVisibilityChanged;
    private final ViewGroup mView;

    private Promise<SelectedPaymentInformation> mCurrentPromise;

    public AssistantPaymentRequestCoordinator(ChromeActivity activity, WebContents webContents) {
        mWebContents = webContents;
        assert webContents != null;

        // TODO(crbug.com/806868): Remove this.
        mView = new LinearLayout(activity);
        mView.addView(new View(activity));

        // Payment request is initially hidden.
        setVisible(false);
    }

    public View getView() {
        return mView;
    }

    public void setVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        boolean changed = mView.getVisibility() != visibility;
        if (changed) {
            mView.setVisibility(visibility);
            if (mOnVisibilityChanged != null) {
                mOnVisibilityChanged.run();
            }
        }
    }

    /**
     * Set the listener that should be triggered when changing the listener of this coordinator
     * view.
     */
    public void setVisibilityChangedListener(Runnable listener) {
        mOnVisibilityChanged = listener;
    }

    public Promise<SelectedPaymentInformation> reset(
            PaymentOptions options, String[] supportedBasicCardNetworks, String defaultEmail) {
        assert mCurrentPromise
                == null : "AssistantPaymentRequestCoordinator does not support concurrent calls "
                          + "to requestPaymentInformation";
        Promise<SelectedPaymentInformation> thisPromise = new Promise<>();
        mCurrentPromise = thisPromise;

        setVisible(true);
        AutofillAssistantPaymentRequest paymentRequest = new AutofillAssistantPaymentRequest(
                mWebContents, options, /* title= */ "", supportedBasicCardNetworks, defaultEmail);
        paymentRequest.show(mView.getChildAt(0), selectedPaymentInformation -> {
            setVisible(false);
            mCurrentPromise = null;
            thisPromise.fulfill(selectedPaymentInformation);
        });
        return this.mCurrentPromise;
    }
}
