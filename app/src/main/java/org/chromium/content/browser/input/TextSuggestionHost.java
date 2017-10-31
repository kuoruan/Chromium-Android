// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;

/**
 * Handles displaying the Android spellcheck/text suggestion menu (provided by
 * SuggestionsPopupWindow) when requested by the C++ class TextSuggestionHostAndroid and applying
 * the commands in that menu (by calling back to the C++ class).
 */
@JNINamespace("content")
public class TextSuggestionHost {
    private long mNativeTextSuggestionHost;
    private final ContentViewCore mContentViewCore;

    private SpellCheckPopupWindow mSpellCheckPopupWindow;
    private TextSuggestionsPopupWindow mTextSuggestionsPopupWindow;

    public TextSuggestionHost(ContentViewCore contentViewCore) {
        mContentViewCore = contentViewCore;
        mNativeTextSuggestionHost = nativeInit(contentViewCore.getWebContents());
    }

    @CalledByNative
    private void showSpellCheckSuggestionMenu(
            double caretXPx, double caretYPx, String markedText, String[] suggestions) {
        if (!mContentViewCore.isAttachedToWindow()) {
            // This can happen if a new browser window is opened immediately after tapping a spell
            // check underline, before the timer to open the menu fires.
            onSuggestionMenuClosed(false);
            return;
        }

        hidePopups();
        mSpellCheckPopupWindow = new SpellCheckPopupWindow(mContentViewCore.getContext(), this,
                mContentViewCore.getContainerView(), mContentViewCore);

        mSpellCheckPopupWindow.show(caretXPx,
                caretYPx + mContentViewCore.getRenderCoordinates().getContentOffsetYPix(),
                markedText, suggestions);
    }

    @CalledByNative
    private void showTextSuggestionMenu(
            double caretXPx, double caretYPx, String markedText, SuggestionInfo[] suggestions) {
        if (!mContentViewCore.isAttachedToWindow()) {
            // This can happen if a new browser window is opened immediately after tapping a spell
            // check underline, before the timer to open the menu fires.
            onSuggestionMenuClosed(false);
            return;
        }

        hidePopups();
        mTextSuggestionsPopupWindow = new TextSuggestionsPopupWindow(mContentViewCore.getContext(),
                this, mContentViewCore.getContainerView(), mContentViewCore);

        mTextSuggestionsPopupWindow.show(caretXPx,
                caretYPx + mContentViewCore.getRenderCoordinates().getContentOffsetYPix(),
                markedText, suggestions);
    }

    /**
     * Hides the text suggestion menu (and informs Blink that it was closed).
     */
    @CalledByNative
    public void hidePopups() {
        if (mTextSuggestionsPopupWindow != null && mTextSuggestionsPopupWindow.isShowing()) {
            mTextSuggestionsPopupWindow.dismiss();
            mTextSuggestionsPopupWindow = null;
        }

        if (mSpellCheckPopupWindow != null && mSpellCheckPopupWindow.isShowing()) {
            mSpellCheckPopupWindow.dismiss();
            mSpellCheckPopupWindow = null;
        }
    }

    /**
     * Tells Blink to replace the active suggestion range with the specified replacement.
     */
    public void applySpellCheckSuggestion(String suggestion) {
        nativeApplySpellCheckSuggestion(mNativeTextSuggestionHost, suggestion);
    }

    /**
     * Tells Blink to replace the active suggestion range with the specified suggestion on the
     * specified marker.
     */
    public void applyTextSuggestion(int markerTag, int suggestionIndex) {
        nativeApplyTextSuggestion(mNativeTextSuggestionHost, markerTag, suggestionIndex);
    }

    /**
     * Tells Blink to delete the active suggestion range.
     */
    public void deleteActiveSuggestionRange() {
        nativeDeleteActiveSuggestionRange(mNativeTextSuggestionHost);
    }

    /**
     * Tells Blink to remove spelling markers under all instances of the specified word.
     */
    public void onNewWordAddedToDictionary(String word) {
        nativeOnNewWordAddedToDictionary(mNativeTextSuggestionHost, word);
    }

    /**
     * Tells Blink the suggestion menu was closed (and also clears the reference to the
     * SuggestionsPopupWindow instance so it can be garbage collected).
     */
    public void onSuggestionMenuClosed(boolean dismissedByItemTap) {
        if (!dismissedByItemTap) {
            nativeOnSuggestionMenuClosed(mNativeTextSuggestionHost);
        }
        mSpellCheckPopupWindow = null;
        mTextSuggestionsPopupWindow = null;
    }

    @CalledByNative
    private void destroy() {
        hidePopups();
        mNativeTextSuggestionHost = 0;
    }

    /**
     * @return The TextSuggestionsPopupWindow, if one exists.
     */
    @VisibleForTesting
    public SuggestionsPopupWindow getTextSuggestionsPopupWindowForTesting() {
        return mTextSuggestionsPopupWindow;
    }

    /**
     * @return The SpellCheckPopupWindow, if one exists.
     */
    @VisibleForTesting
    public SuggestionsPopupWindow getSpellCheckPopupWindowForTesting() {
        return mSpellCheckPopupWindow;
    }

    private native long nativeInit(WebContents webContents);
    private native void nativeApplySpellCheckSuggestion(
            long nativeTextSuggestionHostAndroid, String suggestion);
    private native void nativeApplyTextSuggestion(
            long nativeTextSuggestionHostAndroid, int markerTag, int suggestionIndex);
    private native void nativeDeleteActiveSuggestionRange(long nativeTextSuggestionHostAndroid);
    private native void nativeOnNewWordAddedToDictionary(
            long nativeTextSuggestionHostAndroid, String word);
    private native void nativeOnSuggestionMenuClosed(long nativeTextSuggestionHostAndroid);
}
