// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.translate;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.WebContents;

/**
 * Bridge class that lets Android code access native code to execute translate on a tab.
 */
public class TranslateBridge {
    /**
     * Translates the given tab when the necessary state has been computed (e.g. source language).
     */
    public static void translateTabWhenReady(Tab tab) {
        nativeManualTranslateWhenReady(tab.getWebContents());
    }

    /**
     * Returns true iff the current tab can be manually translated.
     */
    public static boolean canManuallyTranslate(Tab tab) {
        return nativeCanManuallyTranslate(tab.getWebContents());
    }

    /**
     * Returns true iff we're in a state where the manual translate IPH could be shown.
     */
    public static boolean shouldShowManualTranslateIPH(Tab tab) {
        return nativeShouldShowManualTranslateIPH(tab.getWebContents());
    }

    private static native void nativeManualTranslateWhenReady(WebContents webContents);
    private static native boolean nativeCanManuallyTranslate(WebContents webContents);
    private static native boolean nativeShouldShowManualTranslateIPH(WebContents webContents);
}
