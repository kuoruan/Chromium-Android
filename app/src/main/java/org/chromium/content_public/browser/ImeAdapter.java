// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import android.os.ResultReceiver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.base.VisibleForTesting;
import org.chromium.content.browser.input.ImeAdapterImpl;
import org.chromium.content.browser.input.InputMethodManagerWrapper;

/**
 * Adapts and plumbs android IME service onto the chrome text input API.
 */
public interface ImeAdapter {
    /** Composition key code sent when user either hit a key or hit a selection. */
    @VisibleForTesting
    static final int COMPOSITION_KEY_CODE = 229;

    /**
     * @param webContents {@link WebContents} object.
     * @return {@link ImeAdapter} object used for the give WebContents.
     *         {@code null} if not available.
     */
    static ImeAdapter fromWebContents(WebContents webContents) {
        return ImeAdapterImpl.fromWebContents(webContents);
    }

    /**
     * Add {@link ImeEventObserver} object to {@link ImeAdapter}.
     * @param observer imeEventObserver instance to add.
     */
    void addEventObserver(ImeEventObserver observer);

    /**
     * @see View#onCreateInputConnection(EditorInfo)
     */
    InputConnection onCreateInputConnection(EditorInfo outAttrs);

    /**
     * @see View#onCheckIsTextEditor()
     */
    boolean onCheckIsTextEditor();

    /**
     * @return a newly instantiated {@link ResultReceiver} used to scroll to the editable
     *     node at the right timing.
     */
    @VisibleForTesting
    ResultReceiver getNewShowKeyboardReceiver();

    /**
     * Get the current input connection for testing purposes.
     */
    @VisibleForTesting
    InputConnection getInputConnectionForTest();

    /**
     * Overrides the InputMethodManagerWrapper that ImeAdapter uses to make calls to
     * InputMethodManager.
     * @param immw InputMethodManagerWrapper that should be used to call InputMethodManager.
     */
    @VisibleForTesting
    void setInputMethodManagerWrapperForTest(InputMethodManagerWrapper immw);
}
