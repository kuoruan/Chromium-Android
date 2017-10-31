// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View.OnClickListener;
import android.view.textclassifier.TextClassifier;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.SmartSelectionClient;

/**
 * Interface to a content layer client that can process and modify selection text.
 */
public interface SelectionClient {
    /**
     * The result of the text analysis.
     */
    public static class Result {
        /**
         * The number of characters that the left boundary of the original
         * selection should be moved. Negative number means moving left.
         */
        public int startAdjust;

        /**
         * The number of characters that the right boundary of the original
         * selection should be moved. Negative number means moving left.
         */
        public int endAdjust;

        /**
         * Label for the suggested menu item.
         */
        public CharSequence label;

        /**
         * Icon for the suggested menu item.
         */
        public Drawable icon;

        /**
         * Intent for the suggested menu item.
         */
        public Intent intent;

        /**
         * OnClickListener for the suggested menu item.
         */
        public OnClickListener onClickListener;

        /**
         * A helper method that returns true if the result has both visual info
         * and an action so that, for instance, one can make a new menu item.
         */
        public boolean hasNamedAction() {
            return (label != null || icon != null) && (intent != null || onClickListener != null);
        }
    }

    /**
     * The interface that returns the result of the selected text analysis.
     */
    public interface ResultCallback {
        /**
         * The result is delivered with this method.
         */
        void onClassified(Result result);
    }

    /**
     * Notification that the web content selection has changed, regardless of the causal action.
     * @param selection The newly established selection.
     */
    void onSelectionChanged(String selection);

    /**
     * Notification that a user-triggered selection or insertion-related event has occurred.
     * @param eventType The selection event type, see {@link SelectionEventType}.
     * @param posXPix The x coordinate of the selection start handle.
     * @param posYPix The y coordinate of the selection start handle.
     */
    void onSelectionEvent(int eventType, float posXPix, float posYPix);

    /**
     * Requests to show the UI for an unhandled tap, if needed.
     * @param x The x coordinate of the tap.
     * @param y The y coordinate of the tap.
     */
    void showUnhandledTapUIIfNeeded(int x, int y);

    /**
     * Acknowledges that a selectWordAroundCaret action has completed with the given result.
     * @param didSelect Whether a word was actually selected or not.
     * @param startAdjust The adjustment to the selection start offset needed to select the word.
     *        This is typically a negative number (expressed in terms of number of characters).
     * @param endAdjust The adjustment to the selection end offset needed to select the word.
     *        This is typically a positive number (expressed in terms of number of characters).
     */
    void selectWordAroundCaretAck(boolean didSelect, int startAdjust, int endAdjust);

    /**
     * Notifies the SelectionClient that the selection menu has been requested.
     * @param shouldSuggest Whether SelectionClient should suggest and classify or just classify.
     * @return True if embedder should wait for a response before showing selection menu.
     */
    boolean requestSelectionPopupUpdates(boolean shouldSuggest);

    /**
     * Cancel any outstanding requests the embedder had previously requested using
     * SelectionClient.requestSelectionPopupUpdates().
     */
    void cancelAllRequests();

    // The clang-format tool is confused by the java 8 usage of default in an interface.
    // TODO(donnd): remove this once it's supported.
    // clang-format off
    /**
     * Sets the TextClassifier for the Smart Text Selection feature. Pass {@code null} to use the
     * system classifier.
     */
    default void setTextClassifier(TextClassifier textClassifier) {}

    /**
     * Gets TextClassifier that is used for the Smart Text selection. If the custom classifier
     * has been set with setTextClassifier, returns that object, otherwise returns the system
     * classifier.
     */
    default TextClassifier getTextClassifier() {
        return null;
    }

    /**
     * Returns the TextClassifier which has been set with setTextClassifier(), or null.
     */
    default TextClassifier getCustomTextClassifier() {
        return null;
    }

    /** Creates a {@link SelectionClient} instance. */
    public static SelectionClient createSmartSelectionClient(WebContents webContents) {
        SelectionClient.ResultCallback callback =
                ContentViewCore.fromWebContents(webContents).getPopupControllerResultCallback();
        return SmartSelectionClient.create(callback, webContents);
    }
        // clang-format on
}
