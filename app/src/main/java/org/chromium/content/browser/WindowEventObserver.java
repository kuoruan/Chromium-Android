// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Interface to implement if Window-related events are needed.
 */
public interface WindowEventObserver {
    /**
     * This is called when the container view is attached to a window.
     */
    default void onAttachedToWindow() {}

    /**
     * This is called when the container view is detached from a window.
     */
    default void onDetachedFromWindow() {}

    /**
     * @param gainFocus {@code true} if we're gaining focus.
     */
    default void onWindowFocusChanged(boolean gainFocus) {}
}
