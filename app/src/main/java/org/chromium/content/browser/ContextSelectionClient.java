// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.support.annotation.IntDef;
import android.text.TextUtils;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.touch_selection.SelectionEventType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A class that controls the classification of the textual selection.
 * It requests the selection together with its surrounding text from the focused frame and sends it
 * to ContextSelectionProvider which does the classification itself.
 */
@JNINamespace("content")
public class ContextSelectionClient implements SelectionClient {
    @IntDef({CLASSIFY, SUGGEST_AND_CLASSIFY})
    @Retention(RetentionPolicy.SOURCE)
    private @interface RequestType {}

    // Request to obtain the type (e.g. phone number, e-mail address) and the most
    // appropriate operation for the selected text.
    private static final int CLASSIFY = 0;

    // Request to obtain the type (e.g. phone number, e-mail address), the most
    // appropriate operation for the selected text and a better selection boundaries.
    private static final int SUGGEST_AND_CLASSIFY = 1;

    // The maximal number of characters on the left and on the right from the current selection.
    // Used for surrounding text request.
    private static final int NUM_EXTRA_CHARS = 240;

    private long mNativeContextSelectionClient;
    private ContextSelectionProvider mProvider;
    private ContextSelectionProvider.ResultCallback mCallback;

    /**
     * Creates the ContextSelectionClient. Returns null in case ContextSelectionProvider
     * does not exist in the system.
     */
    public static ContextSelectionClient create(ContextSelectionProvider.ResultCallback callback,
            WindowAndroid windowAndroid, WebContents webContents) {
        ContextSelectionProvider provider =
                ContentClassFactory.get().createContextSelectionProvider(callback, windowAndroid);

        // ContextSelectionProvider might not exist.
        if (provider == null) return null;

        return new ContextSelectionClient(provider, callback, webContents);
    }

    private ContextSelectionClient(ContextSelectionProvider provider,
            ContextSelectionProvider.ResultCallback callback, WebContents webContents) {
        mProvider = provider;
        mCallback = callback;
        mNativeContextSelectionClient = nativeInit(webContents);
    }

    @CalledByNative
    private void onNativeSideDestroyed(long nativeContextSelectionClient) {
        assert nativeContextSelectionClient == mNativeContextSelectionClient;
        mNativeContextSelectionClient = 0;
        mProvider.cancelAllRequests();
    }

    // SelectionClient implementation
    @Override
    public void onSelectionChanged(String selection) {}

    @Override
    public void onSelectionEvent(int eventType, float posXPix, float posYPix) {
        switch (eventType) {
            case SelectionEventType.SELECTION_HANDLES_SHOWN:
                // This event is sent when the long press is detected which causes
                // selection to appear for the first time. Temporarily hiding the
                // handles that happens e.g. during scroll does not affect this event.
                requestSurroundingText(SUGGEST_AND_CLASSIFY);
                break;

            case SelectionEventType.SELECTION_HANDLES_CLEARED:
                // The ActionMode should be stopped when this event comes.
                cancelAllRequests();
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STOPPED:
                // This event is sent after a user stopped dragging one of the
                // selection handles, i.e. stopped modifying the selection.
                requestSurroundingText(CLASSIFY);
                break;

            default:
                break; // ignore
        }
    }

    @Override
    public void showUnhandledTapUIIfNeeded(int x, int y) {}

    @Override
    public boolean sendsSelectionPopupUpdates() {
        return true;
    }

    private void cancelAllRequests() {
        if (mNativeContextSelectionClient != 0) {
            nativeCancelAllRequests(mNativeContextSelectionClient);
        }

        mProvider.cancelAllRequests();
    }

    private void requestSurroundingText(@RequestType int callbackData) {
        if (mNativeContextSelectionClient == 0) {
            onSurroundingTextReceived(callbackData, "", 0, 0);
            return;
        }

        nativeRequestSurroundingText(mNativeContextSelectionClient, NUM_EXTRA_CHARS, callbackData);
    }

    @CalledByNative
    private void onSurroundingTextReceived(
            @RequestType int callbackData, String text, int start, int end) {
        if (TextUtils.isEmpty(text)) {
            mCallback.onClassified(new ContextSelectionProvider.Result());
            return;
        }

        switch (callbackData) {
            case SUGGEST_AND_CLASSIFY:
                mProvider.sendSuggestAndClassifyRequest(text, start, end, null);
                break;

            case CLASSIFY:
                mProvider.sendClassifyRequest(text, start, end, null);
                break;

            default:
                assert false : "Unexpected callback data";
                break;
        }
    }

    private native long nativeInit(WebContents webContents);
    private native void nativeRequestSurroundingText(
            long nativeContextSelectionClient, int numExtraCharacters, int callbackData);
    private native void nativeCancelAllRequests(long nativeContextSelectionClient);
}
