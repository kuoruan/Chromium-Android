// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.RenderCoordinates;
import org.chromium.content.browser.webcontents.WebContentsImpl;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native accessibility for a {@link WebContents}. Lazily created upon the first request
 * from Android framework on {@link AccessibilityNodeProvider} and shares the lifetime
 * with {@link WebContents}.
 */
@JNINamespace("content")
public class WebContentsAccessibility extends AccessibilityNodeProvider {
    // Constants from AccessibilityNodeInfo defined in the K SDK.
    private static final int ACTION_COLLAPSE = 0x00080000;
    private static final int ACTION_EXPAND = 0x00040000;

    // Constants from AccessibilityNodeInfo defined in the L SDK.
    private static final int ACTION_SET_TEXT = 0x200000;
    private static final String ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE =
            "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    private static final int WINDOW_CONTENT_CHANGED_DELAY_MS = 500;

    // Constants from AccessibilityNodeInfo defined in the M SDK.
    // Source: https://developer.android.com/reference/android/R.id.html
    protected static final int ACTION_CONTEXT_CLICK = 0x0102003c;
    protected static final int ACTION_SHOW_ON_SCREEN = 0x01020036;
    protected static final int ACTION_SCROLL_UP = 0x01020038;
    protected static final int ACTION_SCROLL_DOWN = 0x0102003a;
    protected static final int ACTION_SCROLL_LEFT = 0x01020039;
    protected static final int ACTION_SCROLL_RIGHT = 0x0102003b;

    // Constant for no granularity selected.
    private static final int NO_GRANULARITY_SELECTED = 0;

    protected final AccessibilityManager mAccessibilityManager;
    private final Context mContext;
    private final RenderCoordinates mRenderCoordinates;
    private final WebContentsImpl mWebContents;
    protected long mNativeObj;
    private Rect mAccessibilityFocusRect;
    private boolean mIsHovering;
    private int mLastHoverId = View.NO_ID;
    protected int mCurrentRootId;
    private final int[] mTempLocation = new int[2];
    protected final ViewGroup mView;
    private boolean mUserHasTouchExplored;
    private boolean mPendingScrollToMakeNodeVisible;
    private boolean mNotifyFrameInfoInitializedCalled;
    private int mSelectionGranularity;
    private int mSelectionStartIndex;
    private int mSelectionEndIndex;
    protected int mAccessibilityFocusId;
    private Runnable mSendWindowContentChangedRunnable;
    private View mAutofillPopupView;
    private boolean mShouldFocusOnPageLoad;

    /**
     * Create a WebContentsAccessibility object.
     */
    public static WebContentsAccessibility create(Context context, ViewGroup containerView,
            WebContents webContents, boolean shouldFocusOnPageLoad) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new OWebContentsAccessibility(
                    context, containerView, webContents, shouldFocusOnPageLoad);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new LollipopWebContentsAccessibility(
                    context, containerView, webContents, shouldFocusOnPageLoad);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return new KitKatWebContentsAccessibility(
                    context, containerView, webContents, shouldFocusOnPageLoad);
        } else {
            return new WebContentsAccessibility(
                    context, containerView, webContents, shouldFocusOnPageLoad);
        }
    }

    protected WebContentsAccessibility(Context context, ViewGroup containerView,
            WebContents webContents, boolean shouldFocusOnPageLoad) {
        mContext = context;
        mWebContents = (WebContentsImpl) webContents;
        mAccessibilityFocusId = View.NO_ID;
        mIsHovering = false;
        mCurrentRootId = View.NO_ID;
        mView = containerView;
        mRenderCoordinates = mWebContents.getRenderCoordinates();
        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mShouldFocusOnPageLoad = shouldFocusOnPageLoad;
        mNativeObj = nativeInit(webContents);
    }

    @CalledByNative
    protected void onNativeObjectDestroyed() {
        mNativeObj = 0;
    }

    public boolean isEnabled() {
        return mNativeObj != 0 ? nativeIsEnabled(mNativeObj) : false;
    }

    public void enable() {
        if (mNativeObj != 0) nativeEnable(mNativeObj);
    }

    /**
     * @return An AccessibilityNodeProvider.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return this;
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (!mAccessibilityManager.isEnabled()) {
            return null;
        }
        int rootId = nativeGetRootId(mNativeObj);

        if (virtualViewId == View.NO_ID) {
            return createNodeForHost(rootId);
        }

        if (!isFrameInfoInitialized()) {
            return null;
        }

        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(mView);
        info.setPackageName(mContext.getPackageName());
        info.setSource(mView, virtualViewId);

        if (virtualViewId == rootId) {
            info.setParent(mView);
        }

        if (nativePopulateAccessibilityNodeInfo(mNativeObj, info, virtualViewId)) {
            return info;
        } else {
            info.recycle();
            return null;
        }
    }

    @Override
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(
            String text, int virtualViewId) {
        return new ArrayList<AccessibilityNodeInfo>();
    }

    private static boolean isValidMovementGranularity(int granularity) {
        switch (granularity) {
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD:
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                return true;
        }
        return false;
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        // We don't support any actions on the host view or nodes
        // that are not (any longer) in the tree.
        if (!mAccessibilityManager.isEnabled() || !nativeIsNodeValid(mNativeObj, virtualViewId)) {
            return false;
        }

        switch (action) {
            case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
                if (!moveAccessibilityFocusToId(virtualViewId)) return true;
                if (!mIsHovering) {
                    nativeScrollToMakeNodeVisible(mNativeObj, mAccessibilityFocusId);
                } else {
                    mPendingScrollToMakeNodeVisible = true;
                }
                return true;
            case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                // ALWAYS respond with TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED whether we thought
                // it had focus or not, so that the Android framework cache is correct.
                sendAccessibilityEvent(
                        virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                if (mAccessibilityFocusId == virtualViewId) {
                    mAccessibilityFocusId = View.NO_ID;
                    mAccessibilityFocusRect = null;
                }
                return true;
            case AccessibilityNodeInfo.ACTION_CLICK:
                nativeClick(mNativeObj, virtualViewId);
                return true;
            case AccessibilityNodeInfo.ACTION_FOCUS:
                nativeFocus(mNativeObj, virtualViewId);
                return true;
            case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:
                nativeBlur(mNativeObj);
                return true;
            case AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT: {
                if (arguments == null) return false;
                String elementType = arguments.getString(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING);
                if (elementType == null) return false;
                elementType = elementType.toUpperCase(Locale.US);
                return jumpToElementType(virtualViewId, elementType, true);
            }
            case AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT: {
                if (arguments == null) return false;
                String elementType = arguments.getString(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING);
                if (elementType == null) return false;
                elementType = elementType.toUpperCase(Locale.US);
                return jumpToElementType(virtualViewId, elementType, false);
            }
            case ACTION_SET_TEXT: {
                if (!nativeIsEditableText(mNativeObj, virtualViewId)) return false;
                if (arguments == null) return false;
                String newText = arguments.getString(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE);
                if (newText == null) return false;
                nativeSetTextFieldValue(mNativeObj, virtualViewId, newText);
                // Match Android framework and set the cursor to the end of the text field.
                nativeSetSelection(mNativeObj, virtualViewId, newText.length(), newText.length());
                return true;
            }
            case AccessibilityNodeInfo.ACTION_SET_SELECTION: {
                if (!nativeIsEditableText(mNativeObj, virtualViewId)) return false;
                int selectionStart = 0;
                int selectionEnd = 0;
                if (arguments != null) {
                    selectionStart = arguments.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT);
                    selectionEnd = arguments.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT);
                }
                nativeSetSelection(mNativeObj, virtualViewId, selectionStart, selectionEnd);
                return true;
            }
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY: {
                if (arguments == null) return false;
                int granularity = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                boolean extend = arguments.getBoolean(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN);
                if (!isValidMovementGranularity(granularity)) {
                    return false;
                }
                return nextAtGranularity(granularity, extend);
            }
            case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY: {
                if (arguments == null) return false;
                int granularity = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                boolean extend = arguments.getBoolean(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN);
                if (!isValidMovementGranularity(granularity)) {
                    return false;
                }
                return previousAtGranularity(granularity, extend);
            }
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
                return scrollForward(virtualViewId);
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                return scrollBackward(virtualViewId);
            case AccessibilityNodeInfo.ACTION_CUT:
                if (mWebContents != null) {
                    mWebContents.cut();
                    return true;
                }
                return false;
            case AccessibilityNodeInfo.ACTION_COPY:
                if (mWebContents != null) {
                    mWebContents.copy();
                    return true;
                }
                return false;
            case AccessibilityNodeInfo.ACTION_PASTE:
                if (mWebContents != null) {
                    mWebContents.paste();
                    return true;
                }
                return false;
            case AccessibilityNodeInfo.ACTION_COLLAPSE:
            case AccessibilityNodeInfo.ACTION_EXPAND:
                // If something is collapsible or expandable, just activate it to toggle.
                nativeClick(mNativeObj, virtualViewId);
                return true;
            case ACTION_SHOW_ON_SCREEN:
                nativeScrollToMakeNodeVisible(mNativeObj, virtualViewId);
                return true;
            case ACTION_CONTEXT_CLICK:
                nativeShowContextMenu(mNativeObj, virtualViewId);
                return true;
            case ACTION_SCROLL_UP:
                return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.UP);
            case ACTION_SCROLL_DOWN:
                return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.DOWN);
            case ACTION_SCROLL_LEFT:
                return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.LEFT);
            case ACTION_SCROLL_RIGHT:
                return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.RIGHT);
            default:
                break;
        }
        return false;
    }

    public void onAutofillPopupDisplayed(View autofillPopupView) {
        if (mAccessibilityManager.isEnabled()) {
            mAutofillPopupView = autofillPopupView;
            nativeOnAutofillPopupDisplayed(mNativeObj);
        }
    }

    public void onAutofillPopupDismissed() {
        if (mAccessibilityManager.isEnabled()) {
            nativeOnAutofillPopupDismissed(mNativeObj);
            mAutofillPopupView = null;
        }
    }

    public void onAutofillPopupAccessibilityFocusCleared() {
        if (mAccessibilityManager.isEnabled()) {
            int id = nativeGetIdForElementAfterElementHostingAutofillPopup(mNativeObj);
            if (id == 0) return;

            moveAccessibilityFocusToId(id);
            nativeScrollToMakeNodeVisible(mNativeObj, mAccessibilityFocusId);
        }
    }

    // Returns true if the hover event is to be consumed by accessibility feature.
    @CalledByNative
    private boolean onHoverEvent(int action) {
        if (!mAccessibilityManager.isEnabled()) {
            return false;
        }

        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            mIsHovering = false;
            if (mLastHoverId != View.NO_ID) {
                sendAccessibilityEvent(mLastHoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                mLastHoverId = View.NO_ID;
            }
            if (mPendingScrollToMakeNodeVisible) {
                nativeScrollToMakeNodeVisible(mNativeObj, mAccessibilityFocusId);
            }
            mPendingScrollToMakeNodeVisible = false;
            return true;
        }

        mIsHovering = true;
        mUserHasTouchExplored = true;
        return true;
    }

    /**
     * Notify us when the frame info is initialized,
     * the first time, since until that point, we can't use mRenderCoordinates to transform
     * web coordinates to screen coordinates.
     */
    @CalledByNative
    private void notifyFrameInfoInitialized() {
        if (mNotifyFrameInfoInitializedCalled) return;

        mNotifyFrameInfoInitializedCalled = true;

        // Invalidate the container view, since the chrome accessibility tree is now
        // ready and listed as the child of the container view.
        sendWindowContentChangedOnView();

        // (Re-) focus focused element, since we weren't able to create an
        // AccessibilityNodeInfo for this element before.
        if (!mShouldFocusOnPageLoad) return;
        if (mAccessibilityFocusId != View.NO_ID) {
            moveAccessibilityFocusToIdAndRefocusIfNeeded(mAccessibilityFocusId);
        }
    }

    private boolean jumpToElementType(int virtualViewId, String elementType, boolean forwards) {
        int id = nativeFindElementType(mNativeObj, virtualViewId, elementType, forwards);
        if (id == 0) return false;

        moveAccessibilityFocusToId(id);
        nativeScrollToMakeNodeVisible(mNativeObj, mAccessibilityFocusId);
        return true;
    }

    private void setGranularityAndUpdateSelection(int granularity) {
        mSelectionGranularity = granularity;
        if (mSelectionGranularity == NO_GRANULARITY_SELECTED) {
            mSelectionStartIndex = -1;
            mSelectionEndIndex = -1;
        }
        if (nativeIsEditableText(mNativeObj, mAccessibilityFocusId)
                && nativeIsFocused(mNativeObj, mAccessibilityFocusId)) {
            mSelectionStartIndex =
                    nativeGetEditableTextSelectionStart(mNativeObj, mAccessibilityFocusId);
            mSelectionEndIndex =
                    nativeGetEditableTextSelectionEnd(mNativeObj, mAccessibilityFocusId);
        }
    }

    private boolean nextAtGranularity(int granularity, boolean extendSelection) {
        setGranularityAndUpdateSelection(granularity);
        // This calls finishGranularityMove when it's done.
        return nativeNextAtGranularity(mNativeObj, mSelectionGranularity, extendSelection,
                mAccessibilityFocusId, mSelectionStartIndex);
    }

    private boolean previousAtGranularity(int granularity, boolean extendSelection) {
        setGranularityAndUpdateSelection(granularity);
        // This calls finishGranularityMove when it's done.
        return nativePreviousAtGranularity(mNativeObj, mSelectionGranularity, extendSelection,
                mAccessibilityFocusId, mSelectionEndIndex);
    }

    @CalledByNative
    private void finishGranularityMove(String text, boolean extendSelection, int itemStartIndex,
            int itemEndIndex, boolean forwards) {
        // Prepare to send both a selection and a traversal event in sequence.
        AccessibilityEvent selectionEvent = buildAccessibilityEvent(
                mAccessibilityFocusId, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
        if (selectionEvent == null) return;
        AccessibilityEvent traverseEvent = buildAccessibilityEvent(mAccessibilityFocusId,
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
        if (traverseEvent == null) {
            selectionEvent.recycle();
            return;
        }

        // Update the cursor or selection based on the traversal. If it's an editable
        // text node, set the real editing cursor too.
        if (forwards) {
            mSelectionEndIndex = itemEndIndex;
        } else {
            mSelectionEndIndex = itemStartIndex;
        }
        if (!extendSelection) {
            mSelectionStartIndex = mSelectionEndIndex;
        }
        if (nativeIsEditableText(mNativeObj, mAccessibilityFocusId)
                && nativeIsFocused(mNativeObj, mAccessibilityFocusId)) {
            nativeSetSelection(
                    mNativeObj, mAccessibilityFocusId, mSelectionStartIndex, mSelectionEndIndex);
        }

        // The selection event's "from" and "to" indices are just a cursor at the focus
        // end of the movement, or a selection if extendSelection is true.
        selectionEvent.setFromIndex(mSelectionStartIndex);
        selectionEvent.setToIndex(mSelectionStartIndex);
        selectionEvent.setItemCount(text.length());

        // The traverse event's "from" and "to" indices surround the item (e.g. the word,
        // etc.) with no whitespace.
        traverseEvent.setFromIndex(itemStartIndex);
        traverseEvent.setToIndex(itemEndIndex);
        traverseEvent.setItemCount(text.length());
        traverseEvent.setMovementGranularity(mSelectionGranularity);
        traverseEvent.setContentDescription(text);

        // The traverse event needs to set its associated action that triggered it.
        if (forwards) {
            traverseEvent.setAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        } else {
            traverseEvent.setAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        }

        mView.requestSendAccessibilityEvent(mView, selectionEvent);
        mView.requestSendAccessibilityEvent(mView, traverseEvent);
    }

    private boolean scrollForward(int virtualViewId) {
        if (nativeIsSlider(mNativeObj, virtualViewId)) {
            return nativeAdjustSlider(mNativeObj, virtualViewId, true);
        } else {
            return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.FORWARD);
        }
    }

    private boolean scrollBackward(int virtualViewId) {
        if (nativeIsSlider(mNativeObj, virtualViewId)) {
            return nativeAdjustSlider(mNativeObj, virtualViewId, false);
        } else {
            return nativeScroll(mNativeObj, virtualViewId, ScrollDirection.BACKWARD);
        }
    }

    private boolean moveAccessibilityFocusToId(int newAccessibilityFocusId) {
        if (newAccessibilityFocusId == mAccessibilityFocusId) return false;

        mAccessibilityFocusId = newAccessibilityFocusId;
        mAccessibilityFocusRect = null;
        mSelectionGranularity = NO_GRANULARITY_SELECTED;
        mSelectionStartIndex = -1;
        mSelectionEndIndex = nativeGetTextLength(mNativeObj, newAccessibilityFocusId);

        // Calling nativeSetAccessibilityFocus will asynchronously load inline text boxes for
        // this node and its subtree. If accessibility focus is on anything other than
        // the root, do it - otherwise set it to -1 so we don't load inline text boxes
        // for the whole subtree of the root.
        if (mAccessibilityFocusId == mCurrentRootId) {
            nativeSetAccessibilityFocus(mNativeObj, -1);
        } else if (nativeIsAutofillPopupNode(mNativeObj, mAccessibilityFocusId)) {
            mAutofillPopupView.requestFocus();
        } else {
            nativeSetAccessibilityFocus(mNativeObj, mAccessibilityFocusId);
        }

        sendAccessibilityEvent(
                mAccessibilityFocusId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        return true;
    }

    private void moveAccessibilityFocusToIdAndRefocusIfNeeded(int newAccessibilityFocusId) {
        // Work around a bug in the Android framework where it doesn't fully update the object
        // with accessibility focus even if you send it a WINDOW_CONTENT_CHANGED. To work around
        // this, clear focus and then set focus again.
        if (newAccessibilityFocusId == mAccessibilityFocusId) {
            sendAccessibilityEvent(newAccessibilityFocusId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            mAccessibilityFocusId = View.NO_ID;
        }
        moveAccessibilityFocusToId(newAccessibilityFocusId);
    }

    /**
     * Send a WINDOW_CONTENT_CHANGED event after a short delay. This helps throttle such
     * events from firing too quickly during animations, for example.
     */
    @CalledByNative
    private void sendDelayedWindowContentChangedEvent() {
        if (mSendWindowContentChangedRunnable != null) return;

        mSendWindowContentChangedRunnable = new Runnable() {
            @Override
            public void run() {
                sendWindowContentChangedOnView();
            }
        };

        mView.postDelayed(mSendWindowContentChangedRunnable, WINDOW_CONTENT_CHANGED_DELAY_MS);
    }

    private void sendWindowContentChangedOnView() {
        if (mSendWindowContentChangedRunnable != null) {
            mView.removeCallbacks(mSendWindowContentChangedRunnable);
            mSendWindowContentChangedRunnable = null;
        }
        mView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void sendWindowContentChangedOnVirtualView(int virtualViewId) {
        sendAccessibilityEvent(virtualViewId, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void sendAccessibilityEvent(int virtualViewId, int eventType) {
        // The container view is indicated by a virtualViewId of NO_ID; post these events directly
        // since there's no web-specific information to attach.
        if (virtualViewId == View.NO_ID) {
            mView.sendAccessibilityEvent(eventType);
            return;
        }

        AccessibilityEvent event = buildAccessibilityEvent(virtualViewId, eventType);
        if (event != null) {
            mView.requestSendAccessibilityEvent(mView, event);
        }
    }

    private AccessibilityEvent buildAccessibilityEvent(int virtualViewId, int eventType) {
        // If we don't have any frame info, then the virtual hierarchy
        // doesn't exist in the view of the Android framework, so should
        // never send any events.
        if (!mAccessibilityManager.isEnabled() || !isFrameInfoInitialized()) {
            return null;
        }

        // This is currently needed if we want Android to visually highlight
        // the item that has accessibility focus. In practice, this doesn't seem to slow
        // things down, because it's only called when the accessibility focus moves.
        // TODO(dmazzoni): remove this if/when Android framework fixes bug.
        mView.postInvalidate();

        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mContext.getPackageName());
        event.setSource(mView, virtualViewId);
        if (!nativePopulateAccessibilityEvent(mNativeObj, event, virtualViewId, eventType)) {
            event.recycle();
            return null;
        }
        return event;
    }

    private Bundle getOrCreateBundleForAccessibilityEvent(AccessibilityEvent event) {
        Bundle bundle = (Bundle) event.getParcelableData();
        if (bundle == null) {
            bundle = new Bundle();
            event.setParcelableData(bundle);
        }
        return bundle;
    }

    private AccessibilityNodeInfo createNodeForHost(int rootId) {
        // Since we don't want the parent to be focusable, but we can't remove
        // actions from a node, copy over the necessary fields.
        final AccessibilityNodeInfo result = AccessibilityNodeInfo.obtain(mView);
        final AccessibilityNodeInfo source = AccessibilityNodeInfo.obtain(mView);
        mView.onInitializeAccessibilityNodeInfo(source);

        // Copy over parent and screen bounds.
        Rect rect = new Rect();
        source.getBoundsInParent(rect);
        result.setBoundsInParent(rect);
        source.getBoundsInScreen(rect);
        result.setBoundsInScreen(rect);

        // Set up the parent view, if applicable.
        final ViewParent parent = mView.getParentForAccessibility();
        if (parent instanceof View) {
            result.setParent((View) parent);
        }

        // Populate the minimum required fields.
        result.setVisibleToUser(source.isVisibleToUser());
        result.setEnabled(source.isEnabled());
        result.setPackageName(source.getPackageName());
        result.setClassName(source.getClassName());

        // Add the Chrome root node.
        if (isFrameInfoInitialized()) {
            result.addChild(mView, rootId);
        }

        return result;
    }

    /**
     * Returns whether or not the frame info is initialized, meaning we can safely
     * convert web coordinates to screen coordinates. When this is first initialized,
     * notifyFrameInfoInitialized is called - but we shouldn't check whether or not
     * that method was called as a way to determine if frame info is valid because
     * notifyFrameInfoInitialized might not be called at all if mRenderCoordinates
     * gets initialized first.
     */
    private boolean isFrameInfoInitialized() {
        return mRenderCoordinates.getContentWidthCss() != 0.0
                || mRenderCoordinates.getContentHeightCss() != 0.0;
    }

    @CalledByNative
    private void handlePageLoaded(int id) {
        if (!mShouldFocusOnPageLoad) return;
        if (mUserHasTouchExplored) return;
        moveAccessibilityFocusToIdAndRefocusIfNeeded(id);
    }

    @CalledByNative
    private void handleFocusChanged(int id) {
        // If |mShouldFocusOnPageLoad| is false, that means this is a WebView and
        // we should avoid moving accessibility focus when the page loads, but more
        // generally we should avoid moving accessibility focus whenever it's not
        // already within this WebView.
        if (!mShouldFocusOnPageLoad && mAccessibilityFocusId == View.NO_ID) return;

        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_FOCUSED);
        moveAccessibilityFocusToId(id);
    }

    @CalledByNative
    private void handleCheckStateChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @CalledByNative
    private void handleClicked(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @CalledByNative
    private void handleTextSelectionChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    @CalledByNative
    private void handleEditableTextChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
    }

    @CalledByNative
    private void handleSliderChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    @CalledByNative
    private void handleContentChanged(int id) {
        int rootId = nativeGetRootId(mNativeObj);
        if (rootId != mCurrentRootId) {
            mCurrentRootId = rootId;
            sendWindowContentChangedOnView();
        } else {
            sendWindowContentChangedOnVirtualView(id);
        }
    }

    @CalledByNative
    private void handleNavigate() {
        mAccessibilityFocusId = View.NO_ID;
        mAccessibilityFocusRect = null;
        mUserHasTouchExplored = false;
        // Invalidate the host, since its child is now gone.
        sendWindowContentChangedOnView();
    }

    @CalledByNative
    private void handleScrollPositionChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    @CalledByNative
    private void handleScrolledToAnchor(int id) {
        moveAccessibilityFocusToId(id);
    }

    @CalledByNative
    private void handleHover(int id) {
        if (mLastHoverId == id) return;
        if (!mIsHovering) return;

        // Always send the ENTER and then the EXIT event, to match a standard Android View.
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        if (mLastHoverId != View.NO_ID) {
            sendAccessibilityEvent(mLastHoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        }
        mLastHoverId = id;
    }

    @CalledByNative
    private void announceLiveRegionText(String text) {
        mView.announceForAccessibility(text);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoParent(AccessibilityNodeInfo node, int parentId) {
        node.setParent(mView, parentId);
    }

    @CalledByNative
    private void addAccessibilityNodeInfoChild(AccessibilityNodeInfo node, int childId) {
        node.addChild(mView, childId);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoBooleanAttributes(AccessibilityNodeInfo node,
            int virtualViewId, boolean checkable, boolean checked, boolean clickable,
            boolean enabled, boolean focusable, boolean focused, boolean password,
            boolean scrollable, boolean selected, boolean visibleToUser) {
        node.setCheckable(checkable);
        node.setChecked(checked);
        node.setClickable(clickable);
        node.setEnabled(enabled);
        node.setFocusable(focusable);
        node.setFocused(focused);
        node.setPassword(password);
        node.setScrollable(scrollable);
        node.setSelected(selected);
        node.setVisibleToUser(visibleToUser);

        node.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);

        if (mAccessibilityFocusId == virtualViewId) {
            node.setAccessibilityFocused(true);
        } else {
            node.setAccessibilityFocused(false);
        }
    }

    // For anything lower than API level 21 (Lollipop), calls AccessibilityNodeInfo.addAction(int)
    // if it's a supported action, and does nothing otherwise.  For 21 and higher, this is
    // overridden in LollipopWebContentsAccessibility using the new non-deprecated API.
    @SuppressWarnings("deprecation")
    protected void addAction(AccessibilityNodeInfo node, int actionId) {
        // Before API level 21, it's not possible to expose actions other than the "legacy standard"
        // ones.
        if (actionId > AccessibilityNodeInfo.ACTION_SET_TEXT) return;

        node.addAction(actionId);
    }

    @CalledByNative
    private void addAccessibilityNodeInfoActions(AccessibilityNodeInfo node, int virtualViewId,
            boolean canScrollForward, boolean canScrollBackward, boolean canScrollUp,
            boolean canScrollDown, boolean canScrollLeft, boolean canScrollRight, boolean clickable,
            boolean editableText, boolean enabled, boolean focusable, boolean focused,
            boolean isCollapsed, boolean isExpanded, boolean hasNonEmptyValue) {
        addAction(node, AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
        addAction(node, AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT);
        addAction(node, AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        addAction(node, AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        addAction(node, ACTION_SHOW_ON_SCREEN);
        addAction(node, ACTION_CONTEXT_CLICK);

        if (editableText && enabled) {
            // TODO: don't support actions that modify it if it's read-only (but
            // SET_SELECTION and COPY are okay).
            addAction(node, ACTION_SET_TEXT);
            addAction(node, AccessibilityNodeInfo.ACTION_PASTE);

            if (hasNonEmptyValue) {
                addAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION);
                addAction(node, AccessibilityNodeInfo.ACTION_CUT);
                addAction(node, AccessibilityNodeInfo.ACTION_COPY);
            }
        }

        if (canScrollForward) {
            addAction(node, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }

        if (canScrollBackward) {
            addAction(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }

        if (canScrollUp) {
            addAction(node, ACTION_SCROLL_UP);
        }

        if (canScrollDown) {
            addAction(node, ACTION_SCROLL_DOWN);
        }

        if (canScrollLeft) {
            addAction(node, ACTION_SCROLL_LEFT);
        }

        if (canScrollRight) {
            addAction(node, ACTION_SCROLL_RIGHT);
        }

        if (focusable) {
            if (focused) {
                addAction(node, AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            } else {
                addAction(node, AccessibilityNodeInfo.ACTION_FOCUS);
            }
        }

        if (mAccessibilityFocusId == virtualViewId) {
            addAction(node, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            addAction(node, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }

        if (clickable) {
            addAction(node, AccessibilityNodeInfo.ACTION_CLICK);
        }

        if (isCollapsed) {
            addAction(node, ACTION_EXPAND);
        }

        if (isExpanded) {
            addAction(node, ACTION_COLLAPSE);
        }
    }

    @CalledByNative
    private void setAccessibilityNodeInfoClassName(AccessibilityNodeInfo node, String className) {
        node.setClassName(className);
    }

    @SuppressLint("NewApi")
    @CalledByNative
    private void setAccessibilityNodeInfoText(AccessibilityNodeInfo node, String text,
            boolean annotateAsLink, boolean isEditableText, String language) {
        CharSequence computedText = computeText(text, isEditableText, language);
        node.setText(computedText);
    }

    protected CharSequence computeText(String text, boolean annotateAsLink, String language) {
        if (annotateAsLink) {
            SpannableString spannable = new SpannableString(text);
            spannable.setSpan(new URLSpan(""), 0, spannable.length(), 0);
            return spannable;
        }
        return text;
    }

    protected void convertWebRectToAndroidCoordinates(Rect rect) {
        // Offset by the scroll position.
        rect.offset(-(int) mRenderCoordinates.getScrollX(), -(int) mRenderCoordinates.getScrollY());

        // Convert CSS (web) pixels to Android View pixels
        rect.left = (int) mRenderCoordinates.fromLocalCssToPix(rect.left);
        rect.top = (int) mRenderCoordinates.fromLocalCssToPix(rect.top);
        rect.bottom = (int) mRenderCoordinates.fromLocalCssToPix(rect.bottom);
        rect.right = (int) mRenderCoordinates.fromLocalCssToPix(rect.right);

        // Offset by the location of the web content within the view.
        rect.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());

        // Finally offset by the location of the view within the screen.
        final int[] viewLocation = new int[2];
        mView.getLocationOnScreen(viewLocation);
        rect.offset(viewLocation[0], viewLocation[1]);

        // Clip to the viewport bounds.
        int viewportRectTop = viewLocation[1] + (int) mRenderCoordinates.getContentOffsetYPix();
        int viewportRectBottom = viewportRectTop + mView.getHeight();
        if (rect.top < viewportRectTop) rect.top = viewportRectTop;
        if (rect.bottom > viewportRectBottom) rect.bottom = viewportRectBottom;
    }

    @CalledByNative
    private void setAccessibilityNodeInfoLocation(AccessibilityNodeInfo node,
            final int virtualViewId, int absoluteLeft, int absoluteTop, int parentRelativeLeft,
            int parentRelativeTop, int width, int height, boolean isRootNode) {
        // First set the bounds in parent.
        Rect boundsInParent = new Rect(parentRelativeLeft, parentRelativeTop,
                parentRelativeLeft + width, parentRelativeTop + height);
        if (isRootNode) {
            // Offset of the web content relative to the View.
            boundsInParent.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
        }
        node.setBoundsInParent(boundsInParent);

        Rect rect = new Rect(absoluteLeft, absoluteTop, absoluteLeft + width, absoluteTop + height);
        convertWebRectToAndroidCoordinates(rect);

        node.setBoundsInScreen(rect);

        // Work around a bug in the Android framework where if the object with accessibility
        // focus moves, the accessibility focus rect is not updated - both the visual highlight,
        // and the location on the screen that's clicked if you double-tap. To work around this,
        // when we know the object with accessibility focus moved, move focus away and then
        // move focus right back to it, which tricks Android into updating its bounds.
        if (virtualViewId == mAccessibilityFocusId && virtualViewId != mCurrentRootId) {
            if (mAccessibilityFocusRect == null) {
                mAccessibilityFocusRect = rect;
            } else if (!mAccessibilityFocusRect.equals(rect)) {
                mAccessibilityFocusRect = rect;
                moveAccessibilityFocusToIdAndRefocusIfNeeded(virtualViewId);
            }
        }
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoKitKatAttributes(AccessibilityNodeInfo node,
            boolean isRoot, boolean isEditableText, String role, String roleDescription,
            String hint, int selectionStartIndex, int selectionEndIndex) {
        // Requires KitKat or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoLollipopAttributes(AccessibilityNodeInfo node,
            boolean canOpenPopup, boolean contentInvalid, boolean dismissable, boolean multiLine,
            int inputType, int liveRegion) {
        // Requires Lollipop or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoCollectionInfo(
            AccessibilityNodeInfo node, int rowCount, int columnCount, boolean hierarchical) {
        // Requires Lollipop or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoCollectionItemInfo(AccessibilityNodeInfo node,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        // Requires Lollipop or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoRangeInfo(
            AccessibilityNodeInfo node, int rangeType, float min, float max, float current) {
        // Requires Lollipop or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoViewIdResourceName(
            AccessibilityNodeInfo node, String viewIdResourceName) {
        // Requires Lollipop or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoOAttributes(
            AccessibilityNodeInfo node, boolean hasCharacterLocations) {
        // Requires O or higher.
    }

    @CalledByNative
    private void setAccessibilityEventBooleanAttributes(AccessibilityEvent event, boolean checked,
            boolean enabled, boolean password, boolean scrollable) {
        event.setChecked(checked);
        event.setEnabled(enabled);
        event.setPassword(password);
        event.setScrollable(scrollable);
    }

    @CalledByNative
    private void setAccessibilityEventClassName(AccessibilityEvent event, String className) {
        event.setClassName(className);
    }

    @CalledByNative
    private void setAccessibilityEventListAttributes(
            AccessibilityEvent event, int currentItemIndex, int itemCount) {
        event.setCurrentItemIndex(currentItemIndex);
        event.setItemCount(itemCount);
    }

    @CalledByNative
    private void setAccessibilityEventScrollAttributes(
            AccessibilityEvent event, int scrollX, int scrollY, int maxScrollX, int maxScrollY) {
        event.setScrollX(scrollX);
        event.setScrollY(scrollY);
        event.setMaxScrollX(maxScrollX);
        event.setMaxScrollY(maxScrollY);
    }

    @CalledByNative
    private void setAccessibilityEventTextChangedAttrs(AccessibilityEvent event, int fromIndex,
            int addedCount, int removedCount, String beforeText, String text) {
        event.setFromIndex(fromIndex);
        event.setAddedCount(addedCount);
        event.setRemovedCount(removedCount);
        event.setBeforeText(beforeText);
        event.getText().add(text);
    }

    @CalledByNative
    private void setAccessibilityEventSelectionAttrs(
            AccessibilityEvent event, int fromIndex, int toIndex, int itemCount, String text) {
        event.setFromIndex(fromIndex);
        event.setToIndex(toIndex);
        event.setItemCount(itemCount);
        event.getText().add(text);
    }

    @CalledByNative
    protected void setAccessibilityEventLollipopAttributes(AccessibilityEvent event,
            boolean canOpenPopup, boolean contentInvalid, boolean dismissable, boolean multiLine,
            int inputType, int liveRegion) {
        // Backwards compatibility for Lollipop AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putBoolean("AccessibilityNodeInfo.canOpenPopup", canOpenPopup);
        bundle.putBoolean("AccessibilityNodeInfo.contentInvalid", contentInvalid);
        bundle.putBoolean("AccessibilityNodeInfo.dismissable", dismissable);
        bundle.putBoolean("AccessibilityNodeInfo.multiLine", multiLine);
        bundle.putInt("AccessibilityNodeInfo.inputType", inputType);
        bundle.putInt("AccessibilityNodeInfo.liveRegion", liveRegion);
    }

    @CalledByNative
    protected void setAccessibilityEventCollectionInfo(
            AccessibilityEvent event, int rowCount, int columnCount, boolean hierarchical) {
        // Backwards compatibility for Lollipop AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.CollectionInfo.rowCount", rowCount);
        bundle.putInt("AccessibilityNodeInfo.CollectionInfo.columnCount", columnCount);
        bundle.putBoolean("AccessibilityNodeInfo.CollectionInfo.hierarchical", hierarchical);
    }

    @CalledByNative
    protected void setAccessibilityEventHeadingFlag(AccessibilityEvent event, boolean heading) {
        // Backwards compatibility for Lollipop AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putBoolean("AccessibilityNodeInfo.CollectionItemInfo.heading", heading);
    }

    @CalledByNative
    protected void setAccessibilityEventCollectionItemInfo(
            AccessibilityEvent event, int rowIndex, int rowSpan, int columnIndex, int columnSpan) {
        // Backwards compatibility for Lollipop AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.rowIndex", rowIndex);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.rowSpan", rowSpan);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.columnIndex", columnIndex);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.columnSpan", columnSpan);
    }

    @CalledByNative
    protected void setAccessibilityEventRangeInfo(
            AccessibilityEvent event, int rangeType, float min, float max, float current) {
        // Backwards compatibility for Lollipop AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.RangeInfo.type", rangeType);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.min", min);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.max", max);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.current", current);
    }

    /**
     * On Android O and higher, we should respect whatever is displayed
     * in a password box and report that via accessibility APIs, whether
     * that's the unobscured password, or all dots.
     *
     * Previous to O, shouldExposePasswordText() returns a system setting
     * that determines whether we should return the unobscured password or all
     * dots, independent of what was displayed visually.
     */
    @CalledByNative
    boolean shouldRespectDisplayedPasswordText() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /**
     * Only relevant prior to Android O, see shouldRespectDisplayedPasswordText.
     */
    @CalledByNative
    boolean shouldExposePasswordText() {
        ContentResolver contentResolver = mContext.getContentResolver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return (Settings.System.getInt(contentResolver, Settings.System.TEXT_SHOW_PASSWORD, 1)
                    == 1);
        }

        return (Settings.Secure.getInt(
                        contentResolver, Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0)
                == 1);
    }

    /**
     * Iterate over all enabled accessibility services and return a bitmask containing the union
     * of all event types that they listen to.
     * @return
     */
    @CalledByNative
    private int getAccessibilityServiceEventTypeMask() {
        int eventTypeMask = 0;
        for (AccessibilityServiceInfo service :
                mAccessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            eventTypeMask |= service.eventTypes;
        }
        return eventTypeMask;
    }

    /**
     * Iterate over all enabled accessibility services and return a bitmask containing the union
     * of all feedback types that they provide.
     * @return
     */
    @CalledByNative
    private int getAccessibilityServiceFeedbackTypeMask() {
        int feedbackTypeMask = 0;
        for (AccessibilityServiceInfo service :
                mAccessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            feedbackTypeMask |= service.feedbackType;
        }
        return feedbackTypeMask;
    }

    /**
     * Iterate over all enabled accessibility services and return a bitmask containing the union
     * of all accessibility service flags from any of them.
     * @return
     */
    @CalledByNative
    private int getAccessibilityServiceFlagsMask() {
        int flagsMask = 0;
        for (AccessibilityServiceInfo service :
                mAccessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            flagsMask |= service.flags;
        }
        return flagsMask;
    }

    /**
     * Iterate over all enabled accessibility services and return a bitmask containing the union
     * of all service capabilities.
     * @return
     */
    @CalledByNative
    protected int getAccessibilityServiceCapabilitiesMask() {
        // Implemented in KitKatWebContentsAccessibility.
        return 0;
    }

    /**
     * @see View#onDetachedFromWindow()
     */
    public void onDetachedFromWindow() {}

    /**
     * @see View#onAttachedToWindow()
     * For versions before L, this method will not be called when container view is already
     * attached to a window and webContentsAccessibility gets created later as a check for L plus
     * versions is added in {@link ContentViewCore#getAccessibilityNodeProvider()}.
     */
    public void onAttachedToWindow() {}

    private native long nativeInit(WebContents webContents);
    private native void nativeOnAutofillPopupDisplayed(long nativeWebContentsAccessibilityAndroid);
    private native void nativeOnAutofillPopupDismissed(long nativeWebContentsAccessibilityAndroid);
    private native int nativeGetIdForElementAfterElementHostingAutofillPopup(
            long nativeWebContentsAccessibilityAndroid);
    private native int nativeGetRootId(long nativeWebContentsAccessibilityAndroid);
    private native boolean nativeIsNodeValid(long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeIsAutofillPopupNode(
            long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeIsEditableText(long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeIsFocused(long nativeWebContentsAccessibilityAndroid, int id);
    private native int nativeGetEditableTextSelectionStart(
            long nativeWebContentsAccessibilityAndroid, int id);
    private native int nativeGetEditableTextSelectionEnd(
            long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativePopulateAccessibilityNodeInfo(
            long nativeWebContentsAccessibilityAndroid, AccessibilityNodeInfo info, int id);
    private native boolean nativePopulateAccessibilityEvent(
            long nativeWebContentsAccessibilityAndroid, AccessibilityEvent event, int id,
            int eventType);
    private native void nativeClick(long nativeWebContentsAccessibilityAndroid, int id);
    private native void nativeFocus(long nativeWebContentsAccessibilityAndroid, int id);
    private native void nativeBlur(long nativeWebContentsAccessibilityAndroid);
    private native void nativeScrollToMakeNodeVisible(
            long nativeWebContentsAccessibilityAndroid, int id);
    private native int nativeFindElementType(long nativeWebContentsAccessibilityAndroid,
            int startId, String elementType, boolean forwards);
    private native void nativeSetTextFieldValue(
            long nativeWebContentsAccessibilityAndroid, int id, String newValue);
    private native void nativeSetSelection(
            long nativeWebContentsAccessibilityAndroid, int id, int start, int end);
    private native boolean nativeNextAtGranularity(long nativeWebContentsAccessibilityAndroid,
            int selectionGranularity, boolean extendSelection, int id, int cursorIndex);
    private native boolean nativePreviousAtGranularity(long nativeWebContentsAccessibilityAndroid,
            int selectionGranularity, boolean extendSelection, int id, int cursorIndex);
    private native boolean nativeAdjustSlider(
            long nativeWebContentsAccessibilityAndroid, int id, boolean increment);
    private native void nativeSetAccessibilityFocus(
            long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeIsSlider(long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeScroll(
            long nativeWebContentsAccessibilityAndroid, int id, int direction);
    protected native String nativeGetSupportedHtmlElementTypes(
            long nativeWebContentsAccessibilityAndroid);
    private native void nativeShowContextMenu(long nativeWebContentsAccessibilityAndroid, int id);
    private native boolean nativeIsEnabled(long nativeWebContentsAccessibilityAndroid);
    private native void nativeEnable(long nativeWebContentsAccessibilityAndroid);
    protected native boolean nativeAreInlineTextBoxesLoaded(
            long nativeWebContentsAccessibilityAndroid, int id);
    protected native void nativeLoadInlineTextBoxes(
            long nativeWebContentsAccessibilityAndroid, int id);
    protected native int[] nativeGetCharacterBoundingBoxes(
            long nativeWebContentsAccessibilityAndroid, int id, int start, int len);
    private native int nativeGetTextLength(long nativeWebContentsAccessibilityAndroid, int id);
}
