// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.carousel;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

@JNINamespace("autofill_assistant")
class AssistantCarouselDelegate {
    private long mNativeAssistantCarouselDelegate;

    @CalledByNative
    private static AssistantCarouselDelegate create(long nativeAssistantCarouselDelegate) {
        return new AssistantCarouselDelegate(nativeAssistantCarouselDelegate);
    }

    private AssistantCarouselDelegate(long nativeAssistantCarouselDelegate) {
        mNativeAssistantCarouselDelegate = nativeAssistantCarouselDelegate;
    }

    void onChipSelected(int index) {
        if (mNativeAssistantCarouselDelegate != 0) {
            nativeOnChipSelected(mNativeAssistantCarouselDelegate, index);
        }
    }

    @CalledByNative
    private void clearNativePtr() {
        mNativeAssistantCarouselDelegate = 0;
    }

    private native void nativeOnChipSelected(long nativeAssistantCarouselDelegate, int index);
}
