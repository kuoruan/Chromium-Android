// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.carousel;

/**
 * A chip to display to the user.
 */
public class AssistantChip {
    private final @AssistantChipType int mType;
    private final String mText;
    private final Runnable mSelectedListener;

    public AssistantChip(@AssistantChipType int type, String text, Runnable selectedListener) {
        mType = type;
        mText = text;
        mSelectedListener = selectedListener;
    }

    public int getType() {
        return mType;
    }

    public String getText() {
        return mText;
    }

    public Runnable getSelectedListener() {
        return mSelectedListener;
    }
}
