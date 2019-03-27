// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.autofill_assistant.carousel.AssistantCarouselModel;
import org.chromium.chrome.browser.autofill_assistant.details.AssistantDetailsModel;
import org.chromium.chrome.browser.autofill_assistant.header.AssistantHeaderModel;
import org.chromium.chrome.browser.autofill_assistant.overlay.AssistantOverlayModel;

/**
 * State for the Autofill Assistant UI.
 */
@JNINamespace("autofill_assistant")
class AssistantModel {
    private final AssistantOverlayModel mOverlayModel = new AssistantOverlayModel();
    private final AssistantHeaderModel mHeaderModel = new AssistantHeaderModel();
    private final AssistantDetailsModel mDetailsModel = new AssistantDetailsModel();
    private final AssistantCarouselModel mCarouselModel = new AssistantCarouselModel();

    @CalledByNative
    public AssistantOverlayModel getOverlayModel() {
        return mOverlayModel;
    }

    @CalledByNative
    public AssistantHeaderModel getHeaderModel() {
        return mHeaderModel;
    }

    @CalledByNative
    public AssistantDetailsModel getDetailsModel() {
        return mDetailsModel;
    }

    @CalledByNative
    public AssistantCarouselModel getCarouselModel() {
        return mCarouselModel;
    }
}
