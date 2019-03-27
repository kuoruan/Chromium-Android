// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.header;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.autofill_assistant.R;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

/**
 * This class is responsible for pushing updates to the Autofill Assistant header view. These
 * updates are pulled from the {@link AssistantHeaderModel} when a notification of an update is
 * received.
 */
class AssistantHeaderViewBinder
        implements PropertyModelChangeProcessor.ViewBinder<AssistantHeaderModel,
                AssistantHeaderViewBinder.ViewHolder, PropertyKey> {
    /**
     * A wrapper class that holds the different views of the header.
     */
    static class ViewHolder {
        final TextView mStatusMessage;
        final AnimatedProgressBar mProgressBar;
        final View mFeedbackButton;
        final View mCloseButton;

        public ViewHolder(Context context, View headerView) {
            mStatusMessage = headerView.findViewById(R.id.status_message);
            mProgressBar = new AnimatedProgressBar(headerView.findViewById(R.id.progress_bar),
                    context.getResources().getColor(R.color.modern_blue_600),
                    context.getResources().getColor(R.color.modern_blue_600_alpha_38_opaque));
            mFeedbackButton = headerView.findViewById(R.id.feedback_button);
            mCloseButton = headerView.findViewById(R.id.close_button);
        }
    }

    @Override
    public void bind(AssistantHeaderModel model, ViewHolder view, PropertyKey propertyKey) {
        if (AssistantHeaderModel.STATUS_MESSAGE == propertyKey) {
            String message = model.get(AssistantHeaderModel.STATUS_MESSAGE);
            view.mStatusMessage.setText(message);
            view.mStatusMessage.announceForAccessibility(message);
        } else if (AssistantHeaderModel.FEEDBACK_VISIBLE == propertyKey) {
            view.mFeedbackButton.setVisibility(
                    model.get(AssistantHeaderModel.FEEDBACK_VISIBLE) ? View.VISIBLE : View.GONE);
        } else if (AssistantHeaderModel.CLOSE_VISIBLE == propertyKey) {
            view.mCloseButton.setVisibility(
                    model.get(AssistantHeaderModel.CLOSE_VISIBLE) ? View.VISIBLE : View.GONE);
        } else if (AssistantHeaderModel.PROGRESS == propertyKey) {
            view.mProgressBar.maybeIncreaseProgress(model.get(AssistantHeaderModel.PROGRESS));
        } else if (AssistantHeaderModel.PROGRESS_PULSING == propertyKey) {
            if (model.get(AssistantHeaderModel.PROGRESS_PULSING)) {
                view.mProgressBar.enablePulsing();
            } else {
                view.mProgressBar.disablePulsing();
            }
        } else if (AssistantHeaderModel.FEEDBACK_BUTTON_CALLBACK == propertyKey) {
            Runnable listener = model.get(AssistantHeaderModel.FEEDBACK_BUTTON_CALLBACK);
            view.mFeedbackButton.setOnClickListener(unusedView -> listener.run());
        } else if (AssistantHeaderModel.CLOSE_BUTTON_CALLBACK == propertyKey) {
            Runnable listener = model.get(AssistantHeaderModel.CLOSE_BUTTON_CALLBACK);
            view.mCloseButton.setOnClickListener(unusedView -> listener.run());
        } else {
            assert false : "Unhandled property detected in AssistantHeaderViewBinder!";
        }
    }
}
