// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.password_manager;

import org.chromium.base.Callback;
import org.chromium.ui.modaldialog.ModalDialogProperties;
import org.chromium.ui.modelutil.PropertyModel;

/** Class responsible for binding the model and the view. On bind, it lazily initializes the view
 * since all the needed data was made available at this point.
 */
public class PasswordGenerationDialogViewBinder {
    private static class PasswordGenerationDialogController
            implements ModalDialogProperties.Controller {
        private final Callback<Boolean> mPasswordActionCallback;

        public PasswordGenerationDialogController(Callback<Boolean> passwordActionCallback) {
            mPasswordActionCallback = passwordActionCallback;
        }

        @Override
        public void onClick(PropertyModel model, int buttonType) {
            switch (buttonType) {
                case ModalDialogProperties.ButtonType.POSITIVE:
                    mPasswordActionCallback.onResult(true);
                    break;
                case ModalDialogProperties.ButtonType.NEGATIVE:
                    mPasswordActionCallback.onResult(false);
                    break;
                default:
                    assert false : "Unexpected button pressed in dialog: " + buttonType;
            }
        }

        @Override
        public void onDismiss(PropertyModel model, int dismissalCause) {
            mPasswordActionCallback.onResult(false);
        }
    }

    public static void bind(
            PasswordGenerationDialogModel model, PasswordGenerationDialogCustomView viewHolder) {
        viewHolder.setGeneratedPassword(
                model.get(PasswordGenerationDialogModel.GENERATED_PASSWORD));
        viewHolder.setSaveExplanationText(
                model.get(PasswordGenerationDialogModel.SAVE_EXPLANATION_TEXT));
    }
}
