// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.password_manager;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.modelutil.PropertyModel;

/**
 * Data model for the password generation modal dialog.
 */

class PasswordGenerationDialogModel extends PropertyModel {
    /** The generated password to be displayed in the dialog. */
    public static final WritableObjectPropertyKey<String> GENERATED_PASSWORD =
            new WritableObjectPropertyKey<>();

    /** Explanation text for how the generated password is saved. */
    public static final WritableObjectPropertyKey<String> SAVE_EXPLANATION_TEXT =
            new WritableObjectPropertyKey<>();

    /** Callback invoked when the password is accepted or rejected by the user. */
    public static final WritableObjectPropertyKey<Callback<Boolean>> PASSWORD_ACTION_CALLBACK =
            new WritableObjectPropertyKey<>();

    /** Default constructor */
    public PasswordGenerationDialogModel() {
        super(GENERATED_PASSWORD, SAVE_EXPLANATION_TEXT, PASSWORD_ACTION_CALLBACK);
    }
}
