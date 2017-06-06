// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 *  Main callback class used by ContentView.
 *
 *  This contains the superset of callbacks required to implement the browser UI and the callbacks
 *  required to implement the WebView API.
 *  The memory and reference ownership of this class is unusual - see the .cc file and ContentView
 *  for more details.
 *
 *  WARNING: ConteViewClient is going away. Do not add new stuff in this class.
 */
public class ContentViewClient {
    /**
     * Called when an ImeEvent is sent to the page. Can be used to know when some text is entered
     * in a page.
     */
    public void onImeEvent() {}

    /**
     * Notified when the editability of the focused node changes.
     *
     * @param editable Whether the focused node is editable.
     */
    public void onFocusedNodeEditabilityChanged(boolean editable) {}

    /**
     * Returns the bottom system window inset in pixels. The system window inset represents the area
     * of a full-screen window that is partially or fully obscured by the status bar, navigation
     * bar, IME or other system windows.
     * @return The bottom system window inset.
     */
    public int getSystemWindowInsetBottom() {
        return 0;
    }
}
