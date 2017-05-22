// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Interface to a client that implements Contextual Search handling for the content layer.
 */
public interface ContextualSearchClient {
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
}
