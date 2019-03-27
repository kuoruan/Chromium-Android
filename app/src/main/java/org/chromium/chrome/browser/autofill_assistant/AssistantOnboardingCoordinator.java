// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.base.Promise;
import org.chromium.chrome.autofill_assistant.R;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

/**
 * Coordinator responsible for showing the onboarding screen when the user is using the Autofill
 * Assistant for the first time.
 */
class AssistantOnboardingCoordinator {
    /**
     * Shows the onboarding screen and returns whether we should proceed.
     */
    static Promise<Boolean> show(Context context, ViewGroup root) {
        Promise<Boolean> promise = new Promise<>();
        View initView = LayoutInflater.from(context)
                                .inflate(R.layout.autofill_assistant_onboarding, root)
                                .findViewById(R.id.assistant_onboarding);

        TextView termsTextView = initView.findViewById(R.id.google_terms_message);
        String termsString = context.getApplicationContext().getString(
                R.string.autofill_assistant_google_terms_description);

        NoUnderlineClickableSpan termsSpan = new NoUnderlineClickableSpan(
                (widget)
                        -> CustomTabActivity.showInfoPage(context.getApplicationContext(),
                                context.getApplicationContext().getString(
                                        R.string.autofill_assistant_google_terms_url)));
        SpannableString spannableMessage = SpanApplier.applySpans(
                termsString, new SpanApplier.SpanInfo("<link>", "</link>", termsSpan));
        termsTextView.setText(spannableMessage);
        termsTextView.setMovementMethod(LinkMovementMethod.getInstance());

        // Set focusable for accessibility.
        initView.setFocusable(true);

        initView.findViewById(R.id.button_init_ok)
                .setOnClickListener(unusedView -> onClicked(true, root, initView, promise));
        initView.findViewById(R.id.button_init_not_ok)
                .setOnClickListener(unusedView -> onClicked(false, root, initView, promise));
        initView.announceForAccessibility(
                context.getString(R.string.autofill_assistant_first_run_accessibility));
        return promise;
    }

    private static void onClicked(
            boolean accept, ViewGroup root, View initView, Promise<Boolean> promise) {
        AutofillAssistantPreferencesUtil.setInitialPreferences(accept);
        root.removeView(initView);
        promise.fulfill(accept);
    }
}
