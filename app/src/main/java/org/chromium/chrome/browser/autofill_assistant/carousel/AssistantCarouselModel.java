// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.carousel;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State for the carousel of the Autofill Assistant.
 */
@JNINamespace("autofill_assistant")
public class AssistantCarouselModel extends PropertyModel {
    static final WritableBooleanPropertyKey REVERSE_LAYOUT = new WritableBooleanPropertyKey();

    private final ListModel<AssistantChip> mChipsModel = new ListModel<>();

    public AssistantCarouselModel() {
        super(REVERSE_LAYOUT);
    }

    public ListModel<AssistantChip> getChipsModel() {
        return mChipsModel;
    }

    @CalledByNative
    private void setChips(int[] types, String[] texts, AssistantCarouselDelegate delegate) {
        assert types.length == texts.length;
        List<AssistantChip> chips = new ArrayList<>();
        boolean reverseLayout = false;
        for (int i = 0; i < types.length; i++) {
            int index = i;
            int type = types[i];

            if (type != AssistantChipType.CHIP_ASSISTIVE) {
                reverseLayout = true;
            }

            chips.add(new AssistantChip(type, texts[i], () -> {
                clearChips();
                delegate.onChipSelected(index);
            }));
        }

        mChipsModel.set(chips);
        set(REVERSE_LAYOUT, reverseLayout);
    }

    @CalledByNative
    public void clearChips() {
        mChipsModel.set(Collections.emptyList());
    }
}
