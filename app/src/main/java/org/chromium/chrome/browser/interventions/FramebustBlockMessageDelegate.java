// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.interventions;

import android.support.annotation.DrawableRes;

/**
 * Defines the appearance and callbacks for
 * {@link org.chromium.chrome.browser.infobar.FramebustBlockInfoBar}.
 */
public interface FramebustBlockMessageDelegate {
    /** The full message describing the intervention, visible when the infobar is expanded. */
    String getLongMessage();

    /** The short message describing the intervention, visible when the infobar is collapsed. */
    String getShortMessage();

    /** The destination URL for the blocked redirection. */
    String getBlockedUrl();

    /** The icon to show in this infobar. */
    @DrawableRes
    int getIconResourceId();

    /** Callback called when the featured link is tapped. */
    void onLinkTapped();
}
