// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.widget.FrameLayout;

import org.chromium.chrome.browser.tab.Tab;

/**
 * Abstracts away the VrShell class, which may or may not be present at runtime depending on
 * compile flags.
 */
public interface VrShell {
    /**
     * Performs native VrShell initialization.
     */
    void initializeNative(
            Tab currentTab, boolean forWebVr, boolean webVrAutopresentationExpected, boolean inCct);

    /**
     * Pauses VrShell.
     */
    void pause();

    /**
     * Resumes VrShell.
     */
    void resume();

    /**
     * Destroys VrShell.
     */
    void teardown();

    /**
     * Sets whether we're presenting WebVR content or not.
     */
    // TODO: Refactor needed. See crbug.com/735169.
    void setWebVrModeEnabled(boolean enabled, boolean showToast);

    /**
     * Returns true if we're presenting WebVR content.
     */
    boolean getWebVrModeEnabled();

    /**
     * Returns true if our URL bar is showing a string.
     */
    boolean isDisplayingUrlForTesting();

    /**
     * Returns the GVRLayout as a FrameLayout.
     */
    FrameLayout getContainer();

    /**
     * Returns whether the back button is enabled.
     */
    Boolean isBackButtonEnabled();

    /**
     * Returns whether the forward button is enabled.
     */
    Boolean isForwardButtonEnabled();

    /**
     * Requests to exit VR.
     */
    void requestToExitVr(@UiUnsupportedMode int reason);

    /**
     * Gives VrShell a chance to clean up any view-dependent state before removing
     * VrShell from the view hierarchy.
     */
    void onBeforeWindowDetached();

    /**
     *  Triggers VrShell to navigate forward.
     */
    void navigateForward();

    /**
     *  Triggers VrShell to navigate backward.
     */
    void navigateBack();

    /**
     * Should be called when the density changes. Updates UI in response to the new density.
     */
    void onDensityChanged(float oldDpi, float newDpi);
}
