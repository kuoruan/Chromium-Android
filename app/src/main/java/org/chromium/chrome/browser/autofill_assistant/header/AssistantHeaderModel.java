// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.header;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * State for the header of the Autofill Assistant.
 */
@JNINamespace("autofill_assistant")
public class AssistantHeaderModel extends PropertyModel {
    @VisibleForTesting
    public static final WritableObjectPropertyKey<String> STATUS_MESSAGE =
            new WritableObjectPropertyKey<>();

    // TODO(crbug.com/806868): Change visibility to package-private once this is only set through
    // native calls.
    public static final WritableBooleanPropertyKey FEEDBACK_VISIBLE =
            new WritableBooleanPropertyKey();

    // TODO(crbug.com/806868): Change visibility to package-private once this is only set through
    // native calls.
    public static final WritableBooleanPropertyKey CLOSE_VISIBLE = new WritableBooleanPropertyKey();

    static final WritableIntPropertyKey PROGRESS = new WritableIntPropertyKey();

    static final WritableBooleanPropertyKey PROGRESS_PULSING = new WritableBooleanPropertyKey();

    static final WritableObjectPropertyKey<Runnable> FEEDBACK_BUTTON_CALLBACK =
            new WritableObjectPropertyKey<>();

    @VisibleForTesting
    public static final WritableObjectPropertyKey<Runnable> CLOSE_BUTTON_CALLBACK =
            new WritableObjectPropertyKey<>();

    public AssistantHeaderModel() {
        super(STATUS_MESSAGE, FEEDBACK_VISIBLE, CLOSE_VISIBLE, PROGRESS, PROGRESS_PULSING,
                FEEDBACK_BUTTON_CALLBACK, CLOSE_BUTTON_CALLBACK);
    }

    @CalledByNative
    private void setStatusMessage(String statusMessage) {
        set(STATUS_MESSAGE, statusMessage);
    }

    @CalledByNative
    private void setProgress(int progress) {
        set(PROGRESS, progress);
    }

    @CalledByNative
    private void setProgressPulsingEnabled(boolean enabled) {
        set(PROGRESS_PULSING, enabled);
    }

    @CalledByNative
    private void setDelegate(AssistantHeaderDelegate delegate) {
        set(FEEDBACK_BUTTON_CALLBACK, delegate::onFeedbackButtonClicked);
        set(CLOSE_BUTTON_CALLBACK, delegate::onCloseButtonClicked);
    }
}
