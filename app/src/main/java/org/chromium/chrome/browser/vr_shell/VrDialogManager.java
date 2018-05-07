// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.view.View;

/**
 * Interface that is needed to manage a VR dialogs.
 */
public interface VrDialogManager {
    /**
     * Sets the top View that is placed inside of a dialog. This View should be
     * shown on as a texture on a quad in VR.
     */
    void setDialogView(View view);

    /*
     * Close the popup Dialog in VR.
     */
    void closeVrDialog();

    /**
     * Set size of the Dialog in VR.
     */
    void setDialogSize(int width, int height);

    /**
     * Initialize the Dialog in VR.
     */
    void initVrDialog(int width, int height);
}