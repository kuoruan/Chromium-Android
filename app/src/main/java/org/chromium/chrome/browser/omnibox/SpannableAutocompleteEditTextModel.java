// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.text.Editable;
import android.text.Selection;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An autocomplete model that appends autocomplete text at the end of query/URL text as
 * SpannableString. By wrapping all the keyboard related operations in a batch edit, we can
 * effectively hide the existence of autocomplete text from keyboard.
 */
public class SpannableAutocompleteEditTextModel implements AutocompleteEditTextModelBase {
    private static final String TAG = "cr_SpanAutocomplete";

    private static final boolean DEBUG = false;

    // A pattern that matches strings consisting of English and European character sets, numbers,
    // punctuations, and a white space.
    private static final Pattern NON_COMPOSITIONAL_TEXT_PATTERN = Pattern.compile(
            "[\\p{script=latin}\\p{script=cyrillic}\\p{script=greek}\\p{script=hebrew}\\p{Punct} "
            + "0-9]*");

    private final AutocompleteEditTextModelBase.Delegate mDelegate;

    // The current state that reflects EditText view's current state through callbacks such as
    // onSelectionChanged() and onTextChanged(). It reflects all the latest changes even in a batch
    // edit. The autocomplete text here is meaningless in the middle of a change.
    private final AutocompleteState mCurrentState;

    // This keeps track of the state in which previous notification was sent. It prevents redundant
    // or unnecessary notification.
    private final AutocompleteState mPreviouslyNotifiedState;

    // This keeps track of the autocompletetext that we need to show (at the end of batch edit if
    // we are in the middle of it), and also the matching user text that it should be appended to.
    // Note that this potentially allows the controller to update in a delayed manner.
    private final AutocompleteState mPreviouslySetState;

    private final SpanCursorController mSpanCursorController;

    private AutocompleteInputConnection mInputConnection;
    private boolean mLastEditWasTyping = true;
    private boolean mIgnoreTextChangeFromAutocomplete = true;
    private int mBatchEditNestCount;

    // For testing.
    private int mLastUpdateSelStart;
    private int mLastUpdateSelEnd;

    public SpannableAutocompleteEditTextModel(AutocompleteEditTextModelBase.Delegate delegate) {
        if (DEBUG) Log.i(TAG, "constructor");
        mDelegate = delegate;
        mCurrentState = new AutocompleteState(delegate.getText().toString(), "",
                delegate.getSelectionStart(), delegate.getSelectionEnd());
        mPreviouslyNotifiedState = new AutocompleteState(mCurrentState);
        mPreviouslySetState = new AutocompleteState(mCurrentState);

        mSpanCursorController = new SpanCursorController(delegate);
    }

    @Override
    public InputConnection onCreateInputConnection(InputConnection inputConnection) {
        mLastUpdateSelStart = mDelegate.getSelectionStart();
        mLastUpdateSelEnd = mDelegate.getSelectionEnd();
        mBatchEditNestCount = 0;
        if (inputConnection == null) {
            if (DEBUG) Log.i(TAG, "onCreateInputConnection: null");
            mInputConnection = null;
            return null;
        }
        if (DEBUG) Log.i(TAG, "onCreateInputConnection");
        mInputConnection = new AutocompleteInputConnection();
        mInputConnection.setTarget(inputConnection);
        return mInputConnection;
    }

    /**
     * @param editable The editable.
     * @return Debug string for the given {@Editable}.
     */
    private static String getEditableDebugString(Editable editable) {
        return String.format(Locale.US, "Editable {[%s] SEL[%d %d] COM[%d %d]}",
                editable.toString(), Selection.getSelectionStart(editable),
                Selection.getSelectionEnd(editable),
                BaseInputConnection.getComposingSpanStart(editable),
                BaseInputConnection.getComposingSpanEnd(editable));
    }

    private void notifyAutocompleteTextStateChanged() {
        if (DEBUG) {
            Log.i(TAG, "notifyAutocompleteTextStateChanged PRV[%s] CUR[%s] IGN[%b]",
                    mPreviouslyNotifiedState, mCurrentState, mIgnoreTextChangeFromAutocomplete);
        }
        if (mBatchEditNestCount > 0) {
            // crbug.com/764749
            Log.w(TAG, "Did not notify - in batch edit.");
            return;
        }
        if (mCurrentState.equals(mPreviouslyNotifiedState)) {
            // crbug.com/764749
            Log.w(TAG, "Did not notify - no change.");
            return;
        }
        // Nothing has changed except that autocomplete text has been set or modified.
        if (mCurrentState.equalsExceptAutocompleteText(mPreviouslyNotifiedState)
                && mCurrentState.hasAutocompleteText()) {
            // Autocomplete text is set by the controller, we should not notify the controller with
            // the same information.
            mPreviouslyNotifiedState.copyFrom(mCurrentState);
            return;
        }
        mPreviouslyNotifiedState.copyFrom(mCurrentState);
        if (mIgnoreTextChangeFromAutocomplete) {
            // crbug.com/764749
            Log.w(TAG, "Did not notify - ignored.");
            return;
        }
        // The current model's mechanism always moves the cursor at the end of user text, so we
        // don't need to update the display.
        mDelegate.onAutocompleteTextStateChanged(false /* updateDisplay */);
    }

    private void clearAutocompleteText() {
        if (DEBUG) Log.i(TAG, "clearAutocomplete");
        mPreviouslySetState.clearAutocompleteText();
        mCurrentState.clearAutocompleteText();
    }

    private void clearAutocompleteTextAndUpdateSpanCursor() {
        if (DEBUG) Log.i(TAG, "clearAutocompleteAndUpdateSpanCursor");
        clearAutocompleteText();
        // Take effect and notify if not already in a batch edit.
        if (mInputConnection != null) {
            mInputConnection.beginBatchEdit();
            mInputConnection.endBatchEdit();
        } else {
            mSpanCursorController.removeSpan();
            notifyAutocompleteTextStateChanged();
        }
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (DEBUG) Log.i(TAG, "dispatchKeyEvent");
        if (mInputConnection == null) {
            return mDelegate.super_dispatchKeyEvent(event);
        }
        mInputConnection.beginBatchEdit();
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            mInputConnection.commitAutocomplete();
        }
        boolean retVal = mDelegate.super_dispatchKeyEvent(event);
        mInputConnection.endBatchEdit();
        return retVal;
    }

    @Override
    public void onSetText(CharSequence text) {
        if (DEBUG) Log.i(TAG, "onSetText: " + text);
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd) {
        if (DEBUG) Log.i(TAG, "onSelectionChanged [%d,%d]", selStart, selEnd);
        mCurrentState.setSelection(selStart, selEnd);
        if (mBatchEditNestCount > 0) return;
        int len = mCurrentState.getUserText().length();
        if (mCurrentState.hasAutocompleteText()) {
            if (selStart > len || selEnd > len) {
                if (DEBUG) Log.i(TAG, "Autocomplete text is being touched. Make it real.");
                if (mInputConnection != null) mInputConnection.commitAutocomplete();
            } else {
                if (DEBUG) Log.i(TAG, "Touching before the cursor removes autocomplete.");
                clearAutocompleteTextAndUpdateSpanCursor();
            }
        }
        notifyAutocompleteTextStateChanged();
        updateSelectionForTesting();
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (DEBUG) Log.i(TAG, "onFocusChanged: " + focused);
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int beforeLength, int afterLength) {
        if (DEBUG) Log.i(TAG, "onTextChanged: " + text);
        mSpanCursorController.reflectTextUpdateInState(mCurrentState, text);
        if (mBatchEditNestCount > 0) return; // let endBatchEdit() handles changes from IME.
        // An external change such as text paste occurred.
        mLastEditWasTyping = false;
        clearAutocompleteTextAndUpdateSpanCursor();
    }

    @Override
    public void onPaste() {
        if (DEBUG) Log.i(TAG, "onPaste");
    }

    @Override
    public String getTextWithAutocomplete() {
        String retVal = mCurrentState.getText();
        if (DEBUG) Log.i(TAG, "getTextWithAutocomplete: %s", retVal);
        return retVal;
    }

    @Override
    public String getTextWithoutAutocomplete() {
        String retVal = mCurrentState.getUserText();
        if (DEBUG) Log.i(TAG, "getTextWithoutAutocomplete: " + retVal);
        return retVal;
    }

    @Override
    public String getAutocompleteText() {
        return mCurrentState.getAutocompleteText();
    }

    @Override
    public void setIgnoreTextChangeFromAutocomplete(boolean ignore) {
        if (DEBUG) Log.i(TAG, "setIgnoreText: " + ignore);
        mIgnoreTextChangeFromAutocomplete = ignore;
    }

    @Override
    public void setAutocompleteText(CharSequence userText, CharSequence inlineAutocompleteText) {
        setAutocompleteTextInternal(userText.toString(), inlineAutocompleteText.toString());
    }

    private void setAutocompleteTextInternal(String userText, String autocompleteText) {
        if (DEBUG) Log.i(TAG, "setAutocompleteText: %s[%s]", userText, autocompleteText);
        mPreviouslySetState.set(userText, autocompleteText, userText.length(), userText.length());
        // TODO(changwan): avoid any unnecessary removal and addition of autocomplete text when it
        // is not changed or when it is appended to the existing autocomplete text.
        if (mInputConnection != null) {
            mInputConnection.beginBatchEdit();
            mInputConnection.endBatchEdit();
        }
    }

    @Override
    public boolean shouldAutocomplete() {
        boolean retVal = mBatchEditNestCount == 0 && mLastEditWasTyping
                && mCurrentState.isCursorAtEndOfUserText()
                && isNonCompositionalText(getTextWithoutAutocomplete());
        if (DEBUG) Log.i(TAG, "shouldAutocomplete: " + retVal);
        return retVal;
    }

    @VisibleForTesting
    public static boolean isNonCompositionalText(String text) {
        // To start with, we are only activating this for English alphabets, European characters,
        // numbers and URLs to avoid potential bad interactions with more complex IMEs.
        // The rationale for including character sets with diacritical marks is that backspacing on
        // a letter with a diacritical mark most likely deletes the whole character instead of
        // removing the diacritical mark.
        // TODO(changwan): also scan for other traditionally non-IME charsets.
        return NON_COMPOSITIONAL_TEXT_PATTERN.matcher(text).matches();
    }

    @Override
    public boolean hasAutocomplete() {
        boolean retVal = mCurrentState.hasAutocompleteText();
        if (DEBUG) Log.i(TAG, "hasAutocomplete: " + retVal);
        return retVal;
    }

    @Override
    public InputConnection getInputConnection() {
        return mInputConnection;
    }

    private void updateSelectionForTesting() {
        int selStart = mDelegate.getSelectionStart();
        int selEnd = mDelegate.getSelectionEnd();
        if (selStart == mLastUpdateSelStart && selEnd == mLastUpdateSelEnd) return;

        mLastUpdateSelStart = selStart;
        mLastUpdateSelEnd = selEnd;
        mDelegate.onUpdateSelectionForTesting(selStart, selEnd);
    }

    /**
     * A class to set and remove, or do other operations on Span and SpannableString of autocomplete
     * text that will be appended to the user text. In addition, cursor will be hidden whenever we
     * are showing span to the user.
     */
    private static class SpanCursorController {
        private final Delegate mDelegate;
        private BackgroundColorSpan mSpan;

        public SpanCursorController(Delegate delegate) {
            mDelegate = delegate;
        }

        public void setSpan(AutocompleteState state) {
            int sel = state.getSelStart();

            if (mSpan == null) mSpan = new BackgroundColorSpan(mDelegate.getHighlightColor());
            SpannableString spanString = new SpannableString(state.getAutocompleteText());
            spanString.setSpan(
                    mSpan, 0, state.getAutocompleteText().length(), Spanned.SPAN_INTERMEDIATE);
            Editable editable = mDelegate.getEditableText();
            editable.append(spanString);

            // Keep the original selection before adding spannable string.
            Selection.setSelection(editable, sel, sel);
            mDelegate.setCursorVisible(false);
            if (DEBUG) Log.i(TAG, "setSpan: " + getEditableDebugString(editable));
        }

        private int getSpanIndex(Editable editable) {
            if (editable == null || mSpan == null) return -1;
            return editable.getSpanStart(mSpan); // returns -1 if mSpan is not attached
        }

        public boolean removeSpan() {
            mDelegate.setCursorVisible(true);
            Editable editable = mDelegate.getEditableText();
            int idx = getSpanIndex(editable);
            if (idx == -1) return false;
            if (DEBUG) Log.i(TAG, "removeSpan IDX[%d]", idx);
            editable.removeSpan(mSpan);
            editable.delete(idx, editable.length());
            mSpan = null;
            if (DEBUG) {
                Log.i(TAG, "removeSpan - after removal: " + getEditableDebugString(editable));
            }
            return true;
        }

        public void commitSpan() {
            mDelegate.getEditableText().removeSpan(mSpan);
            mDelegate.setCursorVisible(true);
        }

        public void reflectTextUpdateInState(AutocompleteState state, CharSequence text) {
            if (text instanceof Editable) {
                Editable editable = (Editable) text;
                int idx = getSpanIndex(editable);
                if (idx != -1) {
                    // We do not set autocomplete text here as model should solely control it.
                    state.setUserText(editable.subSequence(0, idx).toString());
                    return;
                }
            }
            state.setUserText(text.toString());
        }
    }

    private class AutocompleteInputConnection extends InputConnectionWrapper {
        private final AutocompleteState mPreBatchEditState;

        public AutocompleteInputConnection() {
            super(null, true);
            mPreBatchEditState = new AutocompleteState(mCurrentState);
        }

        private boolean incrementBatchEditCount() {
            ++mBatchEditNestCount;
            // After the outermost super.beginBatchEdit(), EditText will stop selection change
            // update to the IME app.
            return super.beginBatchEdit();
        }

        private boolean decrementBatchEditCount() {
            --mBatchEditNestCount;
            boolean retVal = super.endBatchEdit();
            if (mBatchEditNestCount == 0) {
                // At the outermost super.endBatchEdit(), EditText will resume selection change
                // update to the IME app.
                updateSelectionForTesting();
            }
            return retVal;
        }

        public void commitAutocomplete() {
            if (DEBUG) Log.i(TAG, "commitAutocomplete");
            if (!hasAutocomplete()) return;
            mCurrentState.commitAutocompleteText();
            // Invalidate mPreviouslySetState.
            mPreviouslySetState.copyFrom(mCurrentState);
            mLastEditWasTyping = false;
            incrementBatchEditCount(); // avoids additional notifyAutocompleteTextStateChanged()
            mSpanCursorController.commitSpan();
            decrementBatchEditCount();
        }

        @Override
        public boolean beginBatchEdit() {
            if (DEBUG) Log.i(TAG, "beginBatchEdit");
            onBeginImeCommand();
            boolean retVal = incrementBatchEditCount();
            onEndImeCommand();
            return retVal;
        }

        private boolean onBeginImeCommand() {
            if (DEBUG) Log.i(TAG, "onBeginImeCommand: " + mBatchEditNestCount);
            boolean retVal = incrementBatchEditCount();
            if (mBatchEditNestCount == 1) {
                mPreBatchEditState.copyFrom(mCurrentState);
            }
            mSpanCursorController.removeSpan();
            return retVal;
        }

        private void restoreBackspacedText(String diff) {
            if (DEBUG) Log.i(TAG, "restoreBackspacedText. diff: " + diff);
            incrementBatchEditCount(); // avoids additional notifyAutocompleteTextStateChanged()
            Editable editable = mDelegate.getEditableText();
            editable.append(diff);
            decrementBatchEditCount();
        }

        private boolean setAutocompleteSpan() {
            mSpanCursorController.removeSpan();
            if (DEBUG) {
                Log.i(TAG, "setAutocompleteSpan. %s->%s", mPreviouslySetState, mCurrentState);
            }
            if (!mCurrentState.isCursorAtEndOfUserText()) return false;

            if (mCurrentState.reuseAutocompleteTextIfPrefixExtension(mPreviouslySetState)) {
                mSpanCursorController.setSpan(mCurrentState);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean endBatchEdit() {
            if (DEBUG) Log.i(TAG, "endBatchEdit");
            onBeginImeCommand();
            boolean retVal = decrementBatchEditCount();
            onEndImeCommand();
            return retVal;
        }

        private boolean onEndImeCommand() {
            if (DEBUG) Log.i(TAG, "onEndImeCommand: " + (mBatchEditNestCount - 1));
            if (mBatchEditNestCount > 1) {
                String diff = mCurrentState.getBackwardDeletedTextFrom(mPreBatchEditState);
                if (diff == null) setAutocompleteSpan();
                return decrementBatchEditCount();
            }

            String diff = mCurrentState.getBackwardDeletedTextFrom(mPreBatchEditState);
            if (diff != null) {
                // Update selection first such that keyboard app gets what it expects.
                boolean retVal = decrementBatchEditCount();

                if (mPreBatchEditState.hasAutocompleteText()) {
                    // Undo delete to retain the last character and only remove autocomplete text.
                    restoreBackspacedText(diff);
                }
                mLastEditWasTyping = false;
                clearAutocompleteText();
                notifyAutocompleteTextStateChanged();
                return retVal;
            }
            if (!setAutocompleteSpan()) {
                clearAutocompleteText();
            }
            boolean retVal = decrementBatchEditCount();
            // Simply typed some characters or whole text selection has been overridden.
            if (mCurrentState.isForwardTypedFrom(mPreBatchEditState)
                    || (mPreBatchEditState.isWholeUserTextSelected()
                               && mCurrentState.getUserText().length() > 0
                               && mCurrentState.isCursorAtEndOfUserText())) {
                mLastEditWasTyping = true;
            }
            notifyAutocompleteTextStateChanged();
            return retVal;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (DEBUG) Log.i(TAG, "commitText: " + text);
            onBeginImeCommand();
            boolean retVal = super.commitText(text, newCursorPosition);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            if (DEBUG) Log.i(TAG, "setComposingText: " + text);
            onBeginImeCommand();
            boolean retVal = super.setComposingText(text, newCursorPosition);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            if (DEBUG) Log.i(TAG, "setComposingRegion: [%d,%d]", start, end);
            onBeginImeCommand();
            boolean retVal = super.setComposingRegion(start, end);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean finishComposingText() {
            if (DEBUG) Log.i(TAG, "finishComposingText");
            onBeginImeCommand();
            boolean retVal = super.finishComposingText();
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean deleteSurroundingText(final int beforeLength, final int afterLength) {
            if (DEBUG) Log.i(TAG, "deleteSurroundingText [%d,%d]", beforeLength, afterLength);
            onBeginImeCommand();
            boolean retVal = super.deleteSurroundingText(beforeLength, afterLength);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean setSelection(final int start, final int end) {
            if (DEBUG) Log.i(TAG, "setSelection [%d,%d]", start, end);
            onBeginImeCommand();
            boolean retVal = super.setSelection(start, end);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean performEditorAction(final int editorAction) {
            if (DEBUG) Log.i(TAG, "performEditorAction: " + editorAction);
            onBeginImeCommand();
            commitAutocomplete();
            boolean retVal = super.performEditorAction(editorAction);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public boolean sendKeyEvent(final KeyEvent event) {
            if (DEBUG) Log.i(TAG, "sendKeyEvent: " + event.getKeyCode());
            onBeginImeCommand();
            boolean retVal = super.sendKeyEvent(event);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public ExtractedText getExtractedText(final ExtractedTextRequest request, final int flags) {
            if (DEBUG) Log.i(TAG, "getExtractedText");
            onBeginImeCommand();
            ExtractedText retVal = super.getExtractedText(request, flags);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public CharSequence getTextAfterCursor(final int n, final int flags) {
            if (DEBUG) Log.i(TAG, "getTextAfterCursor");
            onBeginImeCommand();
            CharSequence retVal = super.getTextAfterCursor(n, flags);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public CharSequence getTextBeforeCursor(final int n, final int flags) {
            if (DEBUG) Log.i(TAG, "getTextBeforeCursor");
            onBeginImeCommand();
            CharSequence retVal = super.getTextBeforeCursor(n, flags);
            onEndImeCommand();
            return retVal;
        }

        @Override
        public CharSequence getSelectedText(final int flags) {
            if (DEBUG) Log.i(TAG, "getSelectedText");
            onBeginImeCommand();
            CharSequence retVal = super.getSelectedText(flags);
            onEndImeCommand();
            return retVal;
        }
    }
}
