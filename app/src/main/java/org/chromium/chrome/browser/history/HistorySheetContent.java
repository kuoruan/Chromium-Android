// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.selection.SelectableBottomSheetContent;

/**
 * A {@link BottomSheetContent} holding a {@link HistoryManager} for display in the BottomSheet.
 */
public class HistorySheetContent extends SelectableBottomSheetContent<HistoryItem> {
    /**
     * @param activity The activity displaying the history manager UI.
     * @param snackbarManager The {@link SnackbarManager} used to display snackbars.
     */
    public HistorySheetContent(final ChromeActivity activity, SnackbarManager snackbarManager) {
        initialize(activity,
                new HistoryManager(activity, false, snackbarManager,
                        activity.getTabModelSelector().isIncognitoSelected()));
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_HISTORY;
    }
}
