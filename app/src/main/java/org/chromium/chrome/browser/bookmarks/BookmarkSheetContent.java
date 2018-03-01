// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.selection.SelectableBottomSheetContent;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * A {@link BottomSheetContent} holding a {@link BookmarkManager} for display in the BottomSheet.
 */
public class BookmarkSheetContent extends SelectableBottomSheetContent<BookmarkId> {
    /**
     * @param activity The activity displaying the bookmark manager UI.
     * @param snackbarManager The {@link SnackbarManager} used to display snackbars.
     */
    public BookmarkSheetContent(final ChromeActivity activity, SnackbarManager snackbarManager) {
        BookmarkManager bookmarkManager = new BookmarkManager(activity, false, snackbarManager);
        bookmarkManager.updateForUrl(BookmarkUtils.getLastUsedUrl(activity));

        initialize(activity, bookmarkManager);
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_BOOKMARKS;
    }
}
