// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.ui.R;
import org.chromium.ui.widget.Toast;

/**
 * Simple proxy that provides C++ code with an access pathway to the Android
 * clipboard.
 */
@JNINamespace("ui")
public class Clipboard {
    // Necessary for coercing clipboard contents to text if they require
    // access to network resources, etceteras (e.g., URI in clipboard)
    private final Context mContext;

    private final ClipboardManager mClipboardManager;

    /**
     * Use the factory constructor instead.
     *
     * @param context for accessing the clipboard
     */
    public Clipboard(final Context context) {
        mContext = context;
        mClipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Returns a new Clipboard object bound to the specified context.
     *
     * @param context for accessing the clipboard
     * @return the new object
     */
    @CalledByNative
    private static Clipboard create(final Context context) {
        return new Clipboard(context);
    }

    /**
     * Emulates the behavior of the now-deprecated
     * {@link android.text.ClipboardManager#getText()} by invoking
     * {@link android.content.ClipData.Item#coerceToText(Context)} on the first
     * item in the clipboard (if any) and returning the result as a string.
     * <p>
     * This is quite different than simply calling {@link Object#toString()} on
     * the clip; consumers of this API should familiarize themselves with the
     * process described in
     * {@link android.content.ClipData.Item#coerceToText(Context)} before using
     * this method.
     *
     * @return a string representation of the first item on the clipboard, if
     *         the clipboard currently has an item and coercion of the item into
     *         a string is possible; otherwise, <code>null</code>
     */
    @SuppressWarnings("javadoc")
    @CalledByNative
    private String getCoercedText() {
        // getPrimaryClip() has been observed to throw unexpected exceptions for some devices (see
        // crbug.com/654802 and b/31501780)
        try {
            return mClipboardManager.getPrimaryClip()
                    .getItemAt(0)
                    .coerceToText(mContext)
                    .toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the HTML text of top item on the primary clip on the Android clipboard.
     *
     * @return a Java string with the html text if any, or null if there is no html
     *         text or no entries on the primary clip.
     */
    @CalledByNative
    private String getHTMLText() {
        // getPrimaryClip() has been observed to throw unexpected exceptions for some devices (see
        // crbug/654802 and b/31501780)
        try {
            return mClipboardManager.getPrimaryClip().getItemAt(0).getHtmlText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Emulates the behavior of the now-deprecated
     * {@link android.text.ClipboardManager#setText(CharSequence)}, setting the
     * clipboard's current primary clip to a plain-text clip that consists of
     * the specified string.
     * @param text  will become the content of the clipboard's primary clip
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @CalledByNative
    public void setText(final String text) {
        setPrimaryClipNoException(ClipData.newPlainText("text", text));
    }

    /**
     * Writes HTML to the clipboard, together with a plain-text representation
     * of that very data.
     *
     * @param html  The HTML content to be pasted to the clipboard.
     * @param text  Plain-text representation of the HTML content.
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @CalledByNative
    private void setHTMLText(final String html, final String text) {
        setPrimaryClipNoException(ClipData.newHtmlText("html", text, html));
    }

    /**
     * Clears the Clipboard Primary clip.
     *
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @CalledByNative
    private void clear() {
        setPrimaryClipNoException(ClipData.newPlainText(null, null));
    }

    private void setPrimaryClipNoException(ClipData clip) {
        try {
            mClipboardManager.setPrimaryClip(clip);
        } catch (Exception ex) {
            // Ignore any exceptions here as certain devices have bugs and will fail.
            String text = mContext.getString(R.string.copy_to_clipboard_failure_message);
            Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
        }
    }
}
