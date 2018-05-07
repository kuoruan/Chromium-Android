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
public interface VrShell extends VrDialogManager {
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
    // TODO(bshe): Refactor needed. See https://crbug.com/735169.
    // TODO(mthiesse, https://crbug.com/803236): Remove this showToast parameter.
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
     *  Triggers VrShell to navigate forward.
     */
    void navigateForward();

    /**
     *  Triggers VrShell to navigate backward.
     */
    void navigateBack();

    /**
     * Simulates a user accepting the currently visible DOFF prompt.
     */
    void acceptDoffPromptForTesting();

    /**
     * @param topContentOffset The content offset (usually applied by the omnibox).
     */
    void rawTopContentOffsetChanged(float topContentOffset);
}
