// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.previews;

import org.chromium.content_public.browser.WebContents;

/**
 * Java bridge class to C++ Previews code.
 */
public final class PreviewsAndroidBridge {
    private static PreviewsAndroidBridge sBridge;

    public static PreviewsAndroidBridge getInstance() {
        if (sBridge == null) {
            sBridge = new PreviewsAndroidBridge();
        }
        return sBridge;
    }

    private final long mNativePreviewsAndroidBridge;

    private PreviewsAndroidBridge() {
        mNativePreviewsAndroidBridge = nativeInit();
    }

    public boolean shouldShowPreviewUI(WebContents webContents) {
        return nativeShouldShowPreviewUI(mNativePreviewsAndroidBridge, webContents);
    }

    private native long nativeInit();
    private native boolean nativeShouldShowPreviewUI(
            long nativePreviewsAndroidBridge, WebContents webContents);
}
