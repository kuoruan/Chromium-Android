// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.previews;

import org.chromium.content_public.browser.WebContents;

import java.net.URI;
import java.net.URISyntaxException;

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

    /**
     * Returns the original host name for visibleURL. This should only be used on preview pages.
     */
    public String getOriginalHost(String visibleURL) {
        try {
            return new URI(getOriginalURL(visibleURL)).getHost();
        } catch (URISyntaxException e) {
        }
        return "";
    }

    /**
     * Returns the original URL of the given visible URL if the given URL is for a HTTPS Server
     * Preview. Otherwise, the given visibleURL is returned.
     */
    public String getOriginalURL(String visibleURL) {
        final String originalURL =
                nativeGetLitePageRedirectOriginalURL(mNativePreviewsAndroidBridge, visibleURL);
        if (originalURL == null) return visibleURL;
        return originalURL;
    }

    /**
     * If the current preview is a stale preview, this returns the timestamp text to display to the
     * user. An empty string is returned if the current preview is not a stale preview.
     */
    public String getStalePreviewTimestamp(WebContents webContents) {
        assert shouldShowPreviewUI(webContents)
            : "getStalePreviewTimestamp called on a non-preview page";
        return nativeGetStalePreviewTimestamp(mNativePreviewsAndroidBridge, webContents);
    }

    /**
     * Requests that the original page be loaded.
     */
    public void loadOriginal(WebContents webContents) {
        assert shouldShowPreviewUI(webContents) : "loadOriginal called on a non-preview page";
        nativeLoadOriginal(mNativePreviewsAndroidBridge, webContents);
    }

    /**
     * Returns the committed preview type as a String.
     */
    public String getPreviewsType(WebContents webContents) {
        return nativeGetPreviewsType(mNativePreviewsAndroidBridge, webContents);
    }

    private native long nativeInit();
    private native boolean nativeShouldShowPreviewUI(
            long nativePreviewsAndroidBridge, WebContents webContents);
    private native String nativeGetLitePageRedirectOriginalURL(
            long nativePreviewsAndroidBridge, String visibleURL);
    private native String nativeGetStalePreviewTimestamp(
            long nativePreviewsAndroidBridge, WebContents webContents);
    private native void nativeLoadOriginal(
            long nativePreviewsAndroidBridge, WebContents webContents);
    private native String nativeGetPreviewsType(
            long nativePreviewsAndroidBridge, WebContents webContents);
}
