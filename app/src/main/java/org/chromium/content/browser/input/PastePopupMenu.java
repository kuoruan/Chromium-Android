// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

/**
 * Paste popup implementation based on TextView.PastePopupMenu.
 */
public interface PastePopupMenu {
    /**
     * Provider of paste functionality for the given popup.
     */
    public interface PastePopupMenuDelegate {
        /**
         * Called to initiate a paste after the popup has been tapped.
         */
        void paste();

        /**
         * @return Whether clipboard is nonempty.
         */
        boolean canPaste();
    }

    /**
     * Shows the paste popup at an appropriate location relative to the specified position.
     */
    public void show(int x, int y);

    /**
     * Hides the paste popup.
     */
    public void hide();

    /**
     * @return Whether the popup is active and showing.
     */
    public boolean isShowing();
}
