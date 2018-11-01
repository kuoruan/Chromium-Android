// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.text.TextUtils;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ResourceId;

/**
 * The Save Password infobar asks the user whether they want to save the password for the site.
 */
public class SavePasswordInfoBar extends ConfirmInfoBar {
    private final int mTitleLinkRangeStart;
    private final int mTitleLinkRangeEnd;
    private final String mDetailsMessage;

    @CalledByNative
    private static InfoBar show(int enumeratedIconId, String message, int titleLinkStart,
            int titleLinkEnd, String detailsMessage, String primaryButtonText,
            String secondaryButtonText) {
        return new SavePasswordInfoBar(ResourceId.mapToDrawableId(enumeratedIconId), message,
                titleLinkStart, titleLinkEnd, detailsMessage, primaryButtonText,
                secondaryButtonText);
    }

    private SavePasswordInfoBar(int iconDrawbleId, String message, int titleLinkStart,
            int titleLinkEnd, String detailsMessage, String primaryButtonText,
            String secondaryButtonText) {
        super(iconDrawbleId, null, message, null, primaryButtonText, secondaryButtonText);
        mTitleLinkRangeStart = titleLinkStart;
        mTitleLinkRangeEnd = titleLinkEnd;
        mDetailsMessage = detailsMessage;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        if (mTitleLinkRangeStart != 0 && mTitleLinkRangeEnd != 0) {
            layout.setInlineMessageLink(mTitleLinkRangeStart, mTitleLinkRangeEnd);
        }
        if (!TextUtils.isEmpty(mDetailsMessage)) {
            InfoBarControlLayout detailsMessageLayout = layout.addControlLayout();
            detailsMessageLayout.addDescription(mDetailsMessage);
        }
    }
}
