// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.res.Configuration;
import android.os.Build;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.UnderlineSpan;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.blink_public.web.WebInputEventModifier;
import org.chromium.blink_public.web.WebInputEventType;
import org.chromium.blink_public.web.WebTextInputMode;
import org.chromium.content.browser.RenderCoordinates;
import org.chromium.content.browser.picker.InputDialogContainer;
import org.chromium.ui.base.ime.TextInputType;

/**
 * Adapts and plumbs android IME service onto the chrome text input API.
 * ImeAdapter provides an interface in both ways native <-> java:
 * 1. InputConnectionAdapter notifies native code of text composition state and
 *    dispatch key events from java -> WebKit.
 * 2. Native ImeAdapter notifies java side to clear composition text.
 *
 * The basic flow is:
 * 1. When InputConnectionAdapter gets called with composition or result text:
 *    If we receive a composition text or a result text, then we just need to
 *    dispatch a synthetic key event with special keycode 229, and then dispatch
 *    the composition or result text.
 * 2. Intercept dispatchKeyEvent() method for key events not handled by IME, we
 *   need to dispatch them to webkit and check webkit's reply. Then inject a
 *   new key event for further processing if webkit didn't handle it.
 *
 * Note that the native peer object does not take any strong reference onto the
 * instance of this java object, hence it is up to the client of this class (e.g.
 * the ViewEmbedder implementor) to hold a strong reference to it for the required
 * lifetime of the object.
 */
@JNINamespace("content")
public class ImeAdapter {
    private static final String TAG = "cr_Ime";
    private static final boolean DEBUG_LOGS = false;

    public static final int COMPOSITION_KEY_CODE = 229;

    /**
     * Interface for the delegate that needs to be notified of IME changes.
     */
    public interface ImeAdapterDelegate {
        /**
         * Called to notify the delegate about synthetic/real key events before sending to renderer.
         */
        void onImeEvent();

        /**
         * Called when the keyboard could not be shown due to the hardware keyboard being present.
         */
        void onKeyboardBoundsUnchanged();

        /**
         * @see BaseInputConnection#performContextMenuAction(int)
         */
        boolean performContextMenuAction(int id);

        /**
         * @return View that the keyboard should be attached to.
         */
        View getAttachedView();

        /**
         * @return Object that should be called for all keyboard show and hide requests.
         */
        ResultReceiver getNewShowKeyboardReceiver();
    }

    static char[] sSingleCharArray = new char[1];
    static KeyCharacterMap sKeyCharacterMap;

    private long mNativeImeAdapterAndroid;
    private InputMethodManagerWrapper mInputMethodManagerWrapper;
    private ChromiumBaseInputConnection mInputConnection;
    private ChromiumBaseInputConnection.Factory mInputConnectionFactory;

    private final ImeAdapterDelegate mViewEmbedder;
    // This holds the information necessary for constructing CursorAnchorInfo, and notifies to
    // InputMethodManager on appropriate timing, depending on how IME requested the information
    // via InputConnection. The update request is per InputConnection, hence for each time it is
    // re-created, the monitoring status will be reset.
    private final CursorAnchorInfoController mCursorAnchorInfoController;

    private int mTextInputType = TextInputType.NONE;
    private int mTextInputFlags;
    private int mTextInputMode = WebTextInputMode.kDefault;

    // Keep the current configuration to detect the change when onConfigurationChanged() is called.
    private Configuration mCurrentConfig;

    private int mLastSelectionStart;
    private int mLastSelectionEnd;
    private String mLastText;
    private int mLastCompositionStart;
    private int mLastCompositionEnd;
    private boolean mRestartInputOnNextStateUpdate;

    /**
     * @param wrapper InputMethodManagerWrapper that should receive all the call directed to
     *                InputMethodManager.
     * @param embedder The view that is used for callbacks from ImeAdapter.
     */
    public ImeAdapter(InputMethodManagerWrapper wrapper, ImeAdapterDelegate embedder) {
        mInputMethodManagerWrapper = wrapper;
        mViewEmbedder = embedder;
        // Deep copy newConfig so that we can notice the difference.
        mCurrentConfig = new Configuration(
                mViewEmbedder.getAttachedView().getResources().getConfiguration());
        // CursorAnchroInfo is supported only after L.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCursorAnchorInfoController = CursorAnchorInfoController.create(wrapper,
                    new CursorAnchorInfoController.ComposingTextDelegate() {
                        @Override
                        public CharSequence getText() {
                            return mLastText;
                        }
                        @Override
                        public int getSelectionStart() {
                            return mLastSelectionStart;
                        }
                        @Override
                        public int getSelectionEnd() {
                            return mLastSelectionEnd;
                        }
                        @Override
                        public int getComposingTextStart() {
                            return mLastCompositionStart;
                        }
                        @Override
                        public int getComposingTextEnd() {
                            return mLastCompositionEnd;
                        }
                    });
        } else {
            mCursorAnchorInfoController = null;
        }
    }

    private void createInputConnectionFactory() {
        if (mInputConnectionFactory != null) return;
        mInputConnectionFactory = new ThreadedInputConnectionFactory(mInputMethodManagerWrapper);
    }

    /**
     * @see View#onCreateInputConnection(EditorInfo)
     */
    public ChromiumBaseInputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // InputMethodService evaluates fullscreen mode even when the new input connection is
        // null. This makes sure IME doesn't enter fullscreen mode or open custom UI.
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        // Without this line, some third-party IMEs will try to compose text even when
        // not on an editable node. Even when we return null here, key events can still go
        // through ImeAdapter#dispatchKeyEvent().
        if (mTextInputType == TextInputType.NONE) {
            setInputConnection(null);
            if (DEBUG_LOGS) Log.w(TAG, "onCreateInputConnection returns null.");
            return null;
        }
        if (mInputConnectionFactory == null) return null;
        setInputConnection(mInputConnectionFactory.initializeAndGet(mViewEmbedder.getAttachedView(),
                this, mTextInputType, mTextInputFlags, mTextInputMode, mLastSelectionStart,
                mLastSelectionEnd, outAttrs));
        if (DEBUG_LOGS) Log.w(TAG, "onCreateInputConnection: " + mInputConnection);

        if (mCursorAnchorInfoController != null) {
            mCursorAnchorInfoController.onRequestCursorUpdates(
                    false /* not an immediate request */, false /* disable monitoring */,
                    mViewEmbedder.getAttachedView());
        }
        if (mNativeImeAdapterAndroid != 0) {
            nativeRequestCursorUpdate(mNativeImeAdapterAndroid,
                    false /* not an immediate request */, false /* disable monitoring */);
        }
        return mInputConnection;
    }

    private void setInputConnection(ChromiumBaseInputConnection inputConnection) {
        if (mInputConnection == inputConnection) return;
        // The previous input connection might be waiting for state update.
        if (mInputConnection != null) mInputConnection.unblockOnUiThread();
        mInputConnection = inputConnection;
    }

    /**
     * Overrides the InputMethodManagerWrapper that ImeAdapter uses to make calls to
     * InputMethodManager.
     * @param immw InputMethodManagerWrapper that should be used to call InputMethodManager.
     */
    @VisibleForTesting
    public void setInputMethodManagerWrapperForTest(InputMethodManagerWrapper immw) {
        mInputMethodManagerWrapper = immw;
        if (mCursorAnchorInfoController != null) {
            mCursorAnchorInfoController.setInputMethodManagerWrapperForTest(immw);
        }
    }

    @VisibleForTesting
    void setInputConnectionFactory(ChromiumBaseInputConnection.Factory factory) {
        mInputConnectionFactory = factory;
    }

    @VisibleForTesting
    ChromiumBaseInputConnection.Factory getInputConnectionFactoryForTest() {
        return mInputConnectionFactory;
    }

    /**
     * Get the current input connection for testing purposes.
     */
    @VisibleForTesting
    public ChromiumBaseInputConnection getInputConnectionForTest() {
        return mInputConnection;
    }

    private static int getModifiers(int metaState) {
        int modifiers = 0;
        if ((metaState & KeyEvent.META_SHIFT_ON) != 0) {
            modifiers |= WebInputEventModifier.ShiftKey;
        }
        if ((metaState & KeyEvent.META_ALT_ON) != 0) {
            modifiers |= WebInputEventModifier.AltKey;
        }
        if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
            modifiers |= WebInputEventModifier.ControlKey;
        }
        if ((metaState & KeyEvent.META_CAPS_LOCK_ON) != 0) {
            modifiers |= WebInputEventModifier.CapsLockOn;
        }
        if ((metaState & KeyEvent.META_NUM_LOCK_ON) != 0) {
            modifiers |= WebInputEventModifier.NumLockOn;
        }
        return modifiers;
    }

    /**
     * Updates internal representation of the text being edited and its selection and composition
     * properties.
     *
     * @param textInputType Text input type for the currently focused field in renderer.
     * @param textInputFlags Text input flags.
     * @param textInputMode Text input mode.
     * @param showIfNeeded Whether the keyboard should be shown if it is currently hidden.
     * @param text The String contents of the field being edited.
     * @param selectionStart The character offset of the selection start, or the caret position if
     *                       there is no selection.
     * @param selectionEnd The character offset of the selection end, or the caret position if there
     *                     is no selection.
     * @param compositionStart The character offset of the composition start, or -1 if there is no
     *                         composition.
     * @param compositionEnd The character offset of the composition end, or -1 if there is no
     *                       selection.
     * @param replyToRequest True when the update was requested by IME.
     */
    public void updateState(int textInputType, int textInputFlags, int textInputMode,
            boolean showIfNeeded, String text, int selectionStart, int selectionEnd,
            int compositionStart, int compositionEnd, boolean replyToRequest) {
        Log.w(TAG, "updateState: type [%d->%d], flags [%d], show [%b], ", mTextInputType,
                textInputType, textInputFlags, showIfNeeded);
        boolean needsRestart = false;
        if (mRestartInputOnNextStateUpdate) {
            needsRestart = true;
            mRestartInputOnNextStateUpdate = false;
        }

        mTextInputFlags = textInputFlags;
        if (mTextInputMode != textInputMode) {
            mTextInputMode = textInputMode;
            needsRestart = true;
        }
        if (mTextInputType != textInputType) {
            mTextInputType = textInputType;
            needsRestart = true;
        }
        if (mCursorAnchorInfoController != null && (!TextUtils.equals(mLastText, text)
                || mLastSelectionStart != selectionStart || mLastSelectionEnd != selectionEnd
                || mLastCompositionStart != compositionStart
                || mLastCompositionEnd != compositionEnd)) {
            mCursorAnchorInfoController.invalidateLastCursorAnchorInfo();
        }
        mLastText = text;
        mLastSelectionStart = selectionStart;
        mLastSelectionEnd = selectionEnd;
        mLastCompositionStart = compositionStart;
        mLastCompositionEnd = compositionEnd;

        if (textInputType == TextInputType.NONE) {
            hideKeyboard();
        } else {
            if (needsRestart) restartInput();
            // There is no API for us to get notified of user's dismissal of keyboard.
            // Therefore, we should try to show keyboard even when text input type hasn't changed.
            if (showIfNeeded) showSoftKeyboard();
        }

        if (mInputConnection == null) return;
        boolean singleLine = mTextInputType != TextInputType.TEXT_AREA
                && mTextInputType != TextInputType.CONTENT_EDITABLE;
        mInputConnection.updateStateOnUiThread(text, selectionStart, selectionEnd, compositionStart,
                compositionEnd, singleLine, replyToRequest);
    }

    /**
     * Attaches the imeAdapter to its native counterpart. This is needed to start forwarding
     * keyboard events to WebKit.
     * @param nativeImeAdapter The pointer to the native ImeAdapter object.
     */
    public void attach(long nativeImeAdapter) {
        if (DEBUG_LOGS) Log.d(TAG, "attach");
        if (mNativeImeAdapterAndroid == nativeImeAdapter) return;
        if (mNativeImeAdapterAndroid != 0) {
            nativeResetImeAdapter(mNativeImeAdapterAndroid);
        }
        if (nativeImeAdapter != 0) {
            nativeAttachImeAdapter(nativeImeAdapter);
        }
        mNativeImeAdapterAndroid = nativeImeAdapter;
        if (nativeImeAdapter != 0) {
            createInputConnectionFactory();
        }
        resetAndHideKeyboard();
    }

    /**
     * Show soft keyboard only if it is the current keyboard configuration.
     */
    private void showSoftKeyboard() {
        if (DEBUG_LOGS) Log.w(TAG, "showSoftKeyboard");
        mInputMethodManagerWrapper.showSoftInput(
                mViewEmbedder.getAttachedView(), 0, mViewEmbedder.getNewShowKeyboardReceiver());
        if (mViewEmbedder.getAttachedView().getResources().getConfiguration().keyboard
                != Configuration.KEYBOARD_NOKEYS) {
            mViewEmbedder.onKeyboardBoundsUnchanged();
        }
    }

    /**
     * Hide soft keyboard.
     */
    private void hideKeyboard() {
        if (DEBUG_LOGS) Log.w(TAG, "hideKeyboard");
        View view = mViewEmbedder.getAttachedView();
        if (mInputMethodManagerWrapper.isActive(view)) {
            // NOTE: we should not set ResultReceiver here. Otherwise, IMM will own ContentViewCore
            // and ImeAdapter even after input method goes away and result gets received.
            mInputMethodManagerWrapper.hideSoftInputFromWindow(view.getWindowToken(), 0, null);
        }
        // Detach input connection by returning null from onCreateInputConnection().
        if (mTextInputType == TextInputType.NONE && mInputConnection != null) {
            ChromiumBaseInputConnection inputConnection = mInputConnection;
            restartInput();  // resets mInputConnection
            // crbug.com/666982: Restart input may not happen if view is detached from window, but
            // we need to unblock in any case. We want to call this after restartInput() to
            // ensure that there is no additional IME operation in the queue.
            inputConnection.unblockOnUiThread();
        }
    }

    /**
     * Call this when keyboard configuration has changed.
     */
    public void onKeyboardConfigurationChanged(Configuration newConfig) {
        // If configuration unchanged, do nothing.
        if (mCurrentConfig.keyboard == newConfig.keyboard
                && mCurrentConfig.keyboardHidden == newConfig.keyboardHidden
                && mCurrentConfig.hardKeyboardHidden == newConfig.hardKeyboardHidden) {
            return;
        }

        // Deep copy newConfig so that we can notice the difference.
        mCurrentConfig = new Configuration(newConfig);
        if (DEBUG_LOGS) {
            Log.w(TAG, "onKeyboardConfigurationChanged: mTextInputType [%d]", mTextInputType);
        }
        if (mTextInputType != TextInputType.NONE) {
            restartInput();
            // By default, we show soft keyboard on keyboard changes. This is useful
            // when the user switches from hardware keyboard to software keyboard.
            // TODO(changwan): check if we can skip this for hardware keyboard configurations.
            showSoftKeyboard();
        }
    }

    /**
     * Call this when window's focus has changed.
     * @param gainFocus True if we're gaining focus.
     */
    public void onWindowFocusChanged(boolean gainFocus) {
        if (mInputConnectionFactory != null) {
            mInputConnectionFactory.onWindowFocusChanged(gainFocus);
        }
    }

    /**
     * Call this when view is attached to window.
     */
    public void onViewAttachedToWindow() {
        if (mInputConnectionFactory != null) {
            mInputConnectionFactory.onViewAttachedToWindow();
        }
    }

    /**
     * Call this when view is detached from window.
     */
    public void onViewDetachedFromWindow() {
        resetAndHideKeyboard();
        if (mInputConnectionFactory != null) {
            mInputConnectionFactory.onViewDetachedFromWindow();
        }
    }

    /**
     * Call this when view's focus has changed.
     * @param gainFocus True if we're gaining focus.
     * @param hideKeyboardOnBlur True if we should hide soft keyboard when losing focus.
     */
    public void onViewFocusChanged(boolean gainFocus, boolean hideKeyboardOnBlur) {
        if (DEBUG_LOGS) Log.w(TAG, "onViewFocusChanged: gainFocus [%b]", gainFocus);
        if (!gainFocus && hideKeyboardOnBlur) resetAndHideKeyboard();
        if (mInputConnectionFactory != null) {
            mInputConnectionFactory.onViewFocusChanged(gainFocus);
        }
    }

    @VisibleForTesting
    void setInputTypeForTest(int textInputType) {
        mTextInputType = textInputType;
    }

    private static boolean isTextInputType(int type) {
        return type != TextInputType.NONE && !InputDialogContainer.isDialogInputType(type);
    }

    public boolean hasTextInputType() {
        return isTextInputType(mTextInputType);
    }

    /**
     * See {@link View#dispatchKeyEvent(KeyEvent)}
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (DEBUG_LOGS) Log.w(TAG, "dispatchKeyEvent: action [%d], keycode [%d]", event.getAction(),
                event.getKeyCode());
        if (mInputConnection != null) return mInputConnection.sendKeyEventOnUiThread(event);
        return sendKeyEvent(event);
    }

    /**
     * Resets IME adapter and hides keyboard. Note that this will also unblock input connection.
     */
    public void resetAndHideKeyboard() {
        if (DEBUG_LOGS) Log.w(TAG, "resetAndHideKeyboard");
        mTextInputType = TextInputType.NONE;
        mTextInputFlags = 0;
        mTextInputMode = WebTextInputMode.kDefault;
        mRestartInputOnNextStateUpdate = false;
        // This will trigger unblocking if necessary.
        hideKeyboard();
    }

    /**
     * Update selection to input method manager.
     *
     * @param selectionStart The selection start.
     * @param selectionEnd The selection end.
     * @param compositionStart The composition start.
     * @param compositionEnd The composition end.
     */
    void updateSelection(
            int selectionStart, int selectionEnd, int compositionStart, int compositionEnd) {
        mInputMethodManagerWrapper.updateSelection(mViewEmbedder.getAttachedView(),
                selectionStart, selectionEnd, compositionStart, compositionEnd);
    }

    /**
     * Restart input (finish composition and change EditorInfo, such as input type).
     */
    void restartInput() {
        // This will eventually cause input method manager to call View#onCreateInputConnection().
        mInputMethodManagerWrapper.restartInput(mViewEmbedder.getAttachedView());
        if (mInputConnection != null) mInputConnection.onRestartInputOnUiThread();
    }

    /**
     * @see BaseInputConnection#performContextMenuAction(int)
     */
    boolean performContextMenuAction(int id) {
        if (DEBUG_LOGS) Log.w(TAG, "performContextMenuAction: id [%d]", id);
        return mViewEmbedder.performContextMenuAction(id);
    }

    boolean performEditorAction(int actionCode) {
        if (mNativeImeAdapterAndroid == 0) return false;
        if (actionCode == EditorInfo.IME_ACTION_NEXT) {
            sendSyntheticKeyPress(KeyEvent.KEYCODE_TAB,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION);
        } else {
            sendSyntheticKeyPress(KeyEvent.KEYCODE_ENTER,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION);
        }
        return true;
    }

    void notifyUserAction() {
        mInputMethodManagerWrapper.notifyUserAction();
    }

    @VisibleForTesting
    protected void sendSyntheticKeyPress(int keyCode, int flags) {
        long eventTime = SystemClock.uptimeMillis();
        sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
        sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
    }

    boolean sendCompositionToNative(
            CharSequence text, int newCursorPosition, boolean isCommit, int unicodeFromKeyEvent) {
        if (mNativeImeAdapterAndroid == 0) return false;

        // One WebView app detects Enter in JS by looking at KeyDown (http://crbug/577967).
        if (TextUtils.equals(text, "\n")) {
            sendSyntheticKeyPress(KeyEvent.KEYCODE_ENTER,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
            return true;
        }

        mViewEmbedder.onImeEvent();
        long timestampMs = SystemClock.uptimeMillis();
        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.RawKeyDown, 0,
                timestampMs, COMPOSITION_KEY_CODE, 0, false, unicodeFromKeyEvent);

        if (isCommit) {
            nativeCommitText(mNativeImeAdapterAndroid, text, text.toString(), newCursorPosition);
        } else {
            nativeSetComposingText(
                    mNativeImeAdapterAndroid, text, text.toString(), newCursorPosition);
        }

        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.KeyUp, 0, timestampMs,
                COMPOSITION_KEY_CODE, 0, false, unicodeFromKeyEvent);
        return true;
    }

    @VisibleForTesting
    boolean finishComposingText() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeFinishComposingText(mNativeImeAdapterAndroid);
        return true;
    }

    boolean sendKeyEvent(KeyEvent event) {
        if (mNativeImeAdapterAndroid == 0) return false;

        int action = event.getAction();
        int type;
        if (action == KeyEvent.ACTION_DOWN) {
            type = WebInputEventType.KeyDown;
        } else if (action == KeyEvent.ACTION_UP) {
            type = WebInputEventType.KeyUp;
        } else {
            // In theory, KeyEvent.ACTION_MULTIPLE is a valid value, but in practice
            // this seems to have been quietly deprecated and we've never observed
            // a case where it's sent (holding down physical keyboard key also
            // sends ACTION_DOWN), so it's fine to silently drop it.
            return false;
        }
        mViewEmbedder.onImeEvent();

        return nativeSendKeyEvent(mNativeImeAdapterAndroid, event, type,
                getModifiers(event.getMetaState()), event.getEventTime(), event.getKeyCode(),
                             event.getScanCode(), /*isSystemKey=*/false, event.getUnicodeChar());
    }

    /**
     * Send a request to the native counterpart to delete a given range of characters.
     * @param beforeLength Number of characters to extend the selection by before the existing
     *                     selection.
     * @param afterLength Number of characters to extend the selection by after the existing
     *                    selection.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean deleteSurroundingText(int beforeLength, int afterLength) {
        mViewEmbedder.onImeEvent();
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.RawKeyDown, 0,
                SystemClock.uptimeMillis(), COMPOSITION_KEY_CODE, 0, false, 0);
        nativeDeleteSurroundingText(mNativeImeAdapterAndroid, beforeLength, afterLength);
        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.KeyUp, 0,
                SystemClock.uptimeMillis(), COMPOSITION_KEY_CODE, 0, false, 0);
        return true;
    }

    /**
     * Send a request to the native counterpart to delete a given range of characters.
     * @param beforeLength Number of code points to extend the selection by before the existing
     *                     selection.
     * @param afterLength Number of code points to extend the selection by after the existing
     *                    selection.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        mViewEmbedder.onImeEvent();
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.RawKeyDown, 0,
                SystemClock.uptimeMillis(), COMPOSITION_KEY_CODE, 0, false, 0);
        nativeDeleteSurroundingTextInCodePoints(
                mNativeImeAdapterAndroid, beforeLength, afterLength);
        nativeSendKeyEvent(mNativeImeAdapterAndroid, null, WebInputEventType.KeyUp, 0,
                SystemClock.uptimeMillis(), COMPOSITION_KEY_CODE, 0, false, 0);
        return true;
    }

    /**
     * Send a request to the native counterpart to set the selection to given range.
     * @param start Selection start index.
     * @param end Selection end index.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean setEditableSelectionOffsets(int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSetEditableSelectionOffsets(mNativeImeAdapterAndroid, start, end);
        return true;
    }

    /**
     * Send a request to the native counterpart to set composing region to given indices.
     * @param start The start of the composition.
     * @param end The end of the composition.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean setComposingRegion(int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        if (start <= end) {
            nativeSetComposingRegion(mNativeImeAdapterAndroid, start, end);
        } else {
            nativeSetComposingRegion(mNativeImeAdapterAndroid, end, start);
        }
        return true;
    }

    @CalledByNative
    private void focusedNodeChanged(boolean isEditable) {
        if (DEBUG_LOGS) Log.w(TAG, "focusedNodeChanged: isEditable [%b]", isEditable);

        // Update controller before the connection is restarted.
        if (mCursorAnchorInfoController != null) {
            mCursorAnchorInfoController.focusedNodeChanged(isEditable);
        }

        if (mTextInputType != TextInputType.NONE && mInputConnection != null && isEditable) {
            mRestartInputOnNextStateUpdate = true;
        }
    }

    /**
     * Send a request to the native counterpart to give the latest text input state update.
     */
    boolean requestTextInputStateUpdate() {
        if (mNativeImeAdapterAndroid == 0) return false;
        // You won't get state update anyways.
        if (mInputConnection == null) return false;
        return nativeRequestTextInputStateUpdate(mNativeImeAdapterAndroid);
    }

    /**
     * Notified when IME requested Chrome to change the cursor update mode.
     */
    public boolean onRequestCursorUpdates(int cursorUpdateMode) {
        final boolean immediateRequest =
                (cursorUpdateMode & InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
        final boolean monitorRequest =
                (cursorUpdateMode & InputConnection.CURSOR_UPDATE_MONITOR) != 0;

        if (mNativeImeAdapterAndroid != 0) {
            nativeRequestCursorUpdate(mNativeImeAdapterAndroid, immediateRequest, monitorRequest);
        }
        if (mCursorAnchorInfoController == null) return false;
        return mCursorAnchorInfoController.onRequestCursorUpdates(immediateRequest, monitorRequest,
                mViewEmbedder.getAttachedView());
    }

    /**
     * Notified when a frame has been produced by the renderer and all the associated metadata.
     * @param renderCoordinates coordinate information to convert CSS (document) coordinates to
     *                          View-local Physical (screen) coordinates
     * @param hasInsertionMarker Whether the insertion marker is visible or not.
     * @param insertionMarkerHorizontal X coordinates (in view-local DIP pixels) of the insertion
     *                                  marker if it exists. Will be ignored otherwise.
     * @param insertionMarkerTop Y coordinates (in view-local DIP pixels) of the top of the
     *                           insertion marker if it exists. Will be ignored otherwise.
     * @param insertionMarkerBottom Y coordinates (in view-local DIP pixels) of the bottom of
     *                              the insertion marker if it exists. Will be ignored otherwise.
     */
    public void onUpdateFrameInfo(RenderCoordinates renderCoordinates, boolean hasInsertionMarker,
            boolean isInsertionMarkerVisible, float insertionMarkerHorizontal,
            float insertionMarkerTop, float insertionMarkerBottom) {
        if (mCursorAnchorInfoController == null) return;
        mCursorAnchorInfoController.onUpdateFrameInfo(renderCoordinates, hasInsertionMarker,
                isInsertionMarkerVisible, insertionMarkerHorizontal, insertionMarkerTop,
                insertionMarkerBottom, mViewEmbedder.getAttachedView());
    }

    @CalledByNative
    private void populateUnderlinesFromSpans(CharSequence text, long underlines) {
        if (DEBUG_LOGS) {
            Log.w(TAG, "populateUnderlinesFromSpans: text [%s], underlines [%d]", text, underlines);
        }
        if (!(text instanceof SpannableString)) return;

        SpannableString spannableString = ((SpannableString) text);
        CharacterStyle spans[] =
                spannableString.getSpans(0, text.length(), CharacterStyle.class);
        for (CharacterStyle span : spans) {
            if (span instanceof BackgroundColorSpan) {
                nativeAppendBackgroundColorSpan(underlines, spannableString.getSpanStart(span),
                        spannableString.getSpanEnd(span),
                        ((BackgroundColorSpan) span).getBackgroundColor());
            } else if (span instanceof UnderlineSpan) {
                nativeAppendUnderlineSpan(underlines, spannableString.getSpanStart(span),
                        spannableString.getSpanEnd(span));
            }
        }
    }

    @CalledByNative
    private void cancelComposition() {
        if (DEBUG_LOGS) Log.w(TAG, "cancelComposition");
        if (mInputConnection != null) restartInput();
    }

    @CalledByNative
    private void setCharacterBounds(float[] characterBounds) {
        if (mCursorAnchorInfoController == null) return;
        mCursorAnchorInfoController.setCompositionCharacterBounds(characterBounds,
                mViewEmbedder.getAttachedView());
    }

    @CalledByNative
    private void detach() {
        if (DEBUG_LOGS) Log.w(TAG, "detach");
        mNativeImeAdapterAndroid = 0;
        if (mCursorAnchorInfoController != null) {
            mCursorAnchorInfoController.focusedNodeChanged(false);
        }
    }

    private native boolean nativeSendKeyEvent(long nativeImeAdapterAndroid, KeyEvent event,
            int type, int modifiers, long timestampMs, int keyCode, int scanCode,
            boolean isSystemKey, int unicodeChar);
    private static native void nativeAppendUnderlineSpan(long underlinePtr, int start, int end);
    private static native void nativeAppendBackgroundColorSpan(long underlinePtr, int start,
            int end, int backgroundColor);
    private native void nativeSetComposingText(long nativeImeAdapterAndroid, CharSequence text,
            String textStr, int newCursorPosition);
    private native void nativeCommitText(
            long nativeImeAdapterAndroid, CharSequence text, String textStr, int newCursorPosition);
    private native void nativeFinishComposingText(long nativeImeAdapterAndroid);
    private native void nativeAttachImeAdapter(long nativeImeAdapterAndroid);
    private native void nativeSetEditableSelectionOffsets(long nativeImeAdapterAndroid,
            int start, int end);
    private native void nativeSetComposingRegion(long nativeImeAdapterAndroid, int start, int end);
    private native void nativeDeleteSurroundingText(long nativeImeAdapterAndroid,
            int before, int after);
    private native void nativeDeleteSurroundingTextInCodePoints(
            long nativeImeAdapterAndroid, int before, int after);
    private native void nativeResetImeAdapter(long nativeImeAdapterAndroid);
    private native boolean nativeRequestTextInputStateUpdate(long nativeImeAdapterAndroid);
    private native void nativeRequestCursorUpdate(long nativeImeAdapterAndroid,
            boolean immediateRequest, boolean monitorRequest);
}
