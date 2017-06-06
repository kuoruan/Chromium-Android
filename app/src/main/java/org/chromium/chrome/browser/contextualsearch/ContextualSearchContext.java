// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.base.annotations.CalledByNative;

/**
 * Provides a context in which to search, and links to the native ContextualSearchContext.
 * Includes the selection, selection offsets, surrounding page content, etc.
 */
public class ContextualSearchContext {
    // The initial selection that established this context, or null.
    private final String mSelection;

    // Pointer to the native instance of this class.
    private long mNativePointer;

    /**
     * Constructs a context that cannot resolve a search term and has a small amount of
     * page content.
     */
    ContextualSearchContext() {
        mNativePointer = nativeInit();
        mSelection = null;
    }

    /**
     * Constructs a context that can resolve a search term and has a large amount of
     * page content.
     * @param selection The current selection.
     * @param homeCountry The country where the user usually resides, or an empty string if not
     *        known.
     * @param maySendBasePageUrl Whether policy allows sending the base-page URL to the server.
     */
    ContextualSearchContext(String selection, String homeCountry, boolean maySendBasePageUrl) {
        mNativePointer = nativeInit();
        mSelection = selection;
        nativeSetResolveProperties(getNativePointer(), selection, homeCountry, maySendBasePageUrl);
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.  The nativeDestroy will call the destructor on the native instance.
     */
    void destroy() {
        assert mNativePointer != 0;
        nativeDestroy(mNativePointer);
        mNativePointer = 0;
    }

    /**
     * @return the original selection.
     */
    String getSelection() {
        return mSelection;
    }

    // ============================================================================================
    // Native callback support.
    // ============================================================================================

    @CalledByNative
    private long getNativePointer() {
        assert mNativePointer != 0;
        return mNativePointer;
    }

    // ============================================================================================
    // Native methods.
    // ============================================================================================
    private native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchContext);
    private native void nativeSetResolveProperties(long nativeContextualSearchContext,
            String selection, String homeCountry, boolean maySendBasePageUrl);
}
