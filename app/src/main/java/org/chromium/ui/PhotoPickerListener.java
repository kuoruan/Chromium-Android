// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.support.annotation.Nullable;

import java.util.Map;

/**
 * The callback used to indicate what action the user took in the picker.
 */
public interface PhotoPickerListener {
    /**
     * The action the user took in the picker.
     */
    enum Action {
        CANCEL,
        PHOTOS_SELECTED,
        LAUNCH_CAMERA,
        LAUNCH_GALLERY,
    }

    /**
     * The types of requests supported.
     */
    static final int TAKE_PHOTO_REQUEST = 1;
    static final int SHOW_GALLERY = 2;

    /**
     * Called when the user has selected an action. For possible actions see above.
     *
     * @param photos The photos that were selected.
     */
    void onPickerUserAction(Action action, String[] photos);

    /**
     * Used during testing to provide pre-canned data to the dialog.
     *
     * @return The test files to use (Map<String, Long>, mapping file paths to creation times) or
     *     null when run outside of a test.
     */
    @Nullable
    Map<String, Long> getFilesForTesting();
}
