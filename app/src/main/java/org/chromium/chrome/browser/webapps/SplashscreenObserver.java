// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

/**
 * Observer interface for WebApp activity splashscreen.
 */
public interface SplashscreenObserver {
    /**
     * Called when the splashscreen is shown.
     * @param timestamp Time that the splash screen was shown.
     */
    void onSplashscreenShown(long timestamp);

    /**
     * Called when the splashscreen is hidden.
     * @param timestamp Time that the splash screen was hidden.
     */
    void onSplashscreenHidden(long timestamp);
}
