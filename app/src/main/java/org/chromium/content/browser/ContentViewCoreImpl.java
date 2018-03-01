// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.assist.AssistStructure.ViewNode;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textclassifier.TextClassifier;

import org.chromium.base.ObserverList;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeClassQualifiedName;
import org.chromium.content.browser.ContentViewCore.InternalAccessDelegate;
import org.chromium.content.browser.accessibility.WebContentsAccessibility;
import org.chromium.content.browser.accessibility.captioning.CaptioningBridgeFactory;
import org.chromium.content.browser.accessibility.captioning.SystemCaptioningBridge;
import org.chromium.content.browser.accessibility.captioning.TextTrackSettings;
import org.chromium.content.browser.input.ImeAdapter;
import org.chromium.content.browser.input.InputMethodManagerWrapper;
import org.chromium.content.browser.input.SelectPopup;
import org.chromium.content.browser.input.SelectPopupDialog;
import org.chromium.content.browser.input.SelectPopupDropdown;
import org.chromium.content.browser.input.SelectPopupItem;
import org.chromium.content.browser.input.TextSuggestionHost;
import org.chromium.content.browser.webcontents.WebContentsImpl;
import org.chromium.content_public.browser.AccessibilitySnapshotCallback;
import org.chromium.content_public.browser.AccessibilitySnapshotNode;
import org.chromium.content_public.browser.ActionModeCallbackHelper;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.ImeEventObserver;
import org.chromium.content_public.browser.SelectionClient;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.device.gamepad.GamepadList;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.EventForwarder;
import org.chromium.ui.base.GestureEventType;
import org.chromium.ui.base.ViewAndroidDelegate;
import org.chromium.ui.base.ViewUtils;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.display.DisplayAndroid.DisplayAndroidObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the interface {@ContentViewCore}.
 */
@JNINamespace("content")
public class ContentViewCoreImpl
        implements ContentViewCore, AccessibilityStateChangeListener, DisplayAndroidObserver,
                   SystemCaptioningBridge.SystemCaptioningBridgeListener, WindowAndroidProvider,
                   ImeEventObserver {
    private static final String TAG = "cr_ContentViewCore";

    /**
     * A {@link WebContentsObserver} that listens to frame navigation events.
     */
    private static class ContentViewWebContentsObserver extends WebContentsObserver {
        // Using a weak reference avoids cycles that might prevent GC of WebView's WebContents.
        private final WeakReference<ContentViewCoreImpl> mWeakContentViewCore;

        ContentViewWebContentsObserver(ContentViewCoreImpl contentViewCore) {
            super(contentViewCore.getWebContents());
            mWeakContentViewCore = new WeakReference<ContentViewCoreImpl>(contentViewCore);
        }

        @Override
        public void didFinishNavigation(String url, boolean isInMainFrame, boolean isErrorPage,
                boolean hasCommitted, boolean isSameDocument, boolean isFragmentNavigation,
                Integer pageTransition, int errorCode, String errorDescription,
                int httpStatusCode) {
            if (hasCommitted && isInMainFrame && !isSameDocument) {
                resetPopupsAndInput();
            }
        }

        @Override
        public void renderProcessGone(boolean wasOomProtected) {
            resetPopupsAndInput();
            ContentViewCoreImpl contentViewCore = mWeakContentViewCore.get();
            if (contentViewCore == null) return;
            contentViewCore.mImeAdapter.resetAndHideKeyboard();
        }

        private void resetPopupsAndInput() {
            ContentViewCoreImpl contentViewCore = mWeakContentViewCore.get();
            if (contentViewCore == null) return;
            contentViewCore.mIsMobileOptimizedHint = false;
            contentViewCore.hidePopupsAndClearSelection();
            contentViewCore.resetScrollInProgress();
        }
    }

    private final Context mContext;
    private final String mProductVersion;
    private ViewGroup mContainerView;
    private InternalAccessDelegate mContainerViewInternals;
    private WebContentsImpl mWebContents;
    private WebContentsObserver mWebContentsObserver;

    // Native pointer to C++ ContentViewCore object which will be set by nativeInit().
    private long mNativeContentViewCore;

    private boolean mAttachedToWindow;
    private final ObserverList<GestureStateListener> mGestureStateListeners;
    private final RewindableIterator<GestureStateListener> mGestureStateListenersIterator;

    private PopupZoomer mPopupZoomer;
    private SelectPopup mSelectPopup;
    private long mNativeSelectPopupSourceFrame;

    // Only valid when focused on a text / password field.
    private ImeAdapter mImeAdapter;

    private TextSuggestionHost mTextSuggestionHost;

    // Cached copy of all positions and scales as reported by the renderer.
    private RenderCoordinates mRenderCoordinates;

    // Whether joystick scroll is enabled.  It's disabled when an editable field is focused.
    private boolean mJoystickScrollEnabled = true;

    private boolean mIsMobileOptimizedHint;

    private SelectionPopupController mSelectionPopupController;
    private boolean mPreserveSelectionOnNextLossOfFocus;

    // Whether native accessibility, i.e. without any script injection, is allowed.
    private boolean mNativeAccessibilityAllowed;

    // Handles native accessibility, i.e. without any script injection.
    private WebContentsAccessibility mWebContentsAccessibility;

    // System accessibility service.
    private final AccessibilityManager mAccessibilityManager;

    // If true, the web contents are obscured by another view and we shouldn't
    // return an AccessibilityNodeProvider or process touch exploration events.
    private boolean mIsObscuredByAnotherView;

    // Notifies the ContentViewCore when platform closed caption settings have changed
    // if they are supported. Otherwise does nothing.
    private final SystemCaptioningBridge mSystemCaptioningBridge;

    // Accessibility touch exploration state.
    private boolean mTouchExplorationEnabled;

    // Whether accessibility focus should be set to the page when it finishes loading.
    // This only applies if an accessibility service like TalkBack is running.
    // This is desirable behavior for a browser window, but not for an embedded
    // WebView.
    private boolean mShouldSetAccessibilityFocusOnPageLoad;

    // Whether a touch scroll sequence is active, used to hide text selection
    // handles. Note that a scroll sequence will *always* bound a pinch
    // sequence, so this will also be true for the duration of a pinch gesture.
    private boolean mTouchScrollInProgress;

    // The outstanding fling start events that hasn't got fling end yet. It may be > 1 because
    // onNativeFlingStopped() is called asynchronously.
    private int mPotentiallyActiveFlingCount;

    /**
     * PID used to indicate an invalid render process.
     */
    // Keep in sync with the value returned from ContentViewCore::GetCurrentRendererProcessId()
    // if there is no render process.
    public static final int INVALID_RENDER_PROCESS_PID = 0;

    // Offsets for the events that passes through this ContentViewCore.
    private float mCurrentTouchOffsetX;
    private float mCurrentTouchOffsetY;

    // True if we want to disable Android native event batching and use compositor event queue.
    private boolean mShouldRequestUnbufferedDispatch;

    // Whether the ContentViewCore requires the WebContents to be fullscreen in order to lock the
    // screen orientation.
    private boolean mFullscreenRequiredForOrientationLock = true;

    // A ViewAndroidDelegate that delegates to the current container view.
    private ViewAndroidDelegate mViewAndroidDelegate;

    private Boolean mHasViewFocus;

    // The list of observers that are notified when ContentViewCore changes its WindowAndroid.
    private final ObserverList<WindowAndroidChangedObserver> mWindowAndroidChangedObservers;

    /**
     * @param webContents The {@link WebContents} to find a {@link ContentViewCore} of.
     * @return            A {@link ContentViewCore} that is connected to {@code webContents} or
     *                    {@code null} if none exists.
     */
    public static ContentViewCore fromWebContents(WebContents webContents) {
        return nativeFromWebContentsAndroid(webContents);
    }

    /**
     * Constructs a new ContentViewCore. Embedders must call initialize() after constructing
     * a ContentViewCore and before using it.
     *
     * @param context The context used to create this.
     */
    public ContentViewCoreImpl(Context context, String productVersion) {
        mContext = context;
        mProductVersion = productVersion;
        mAccessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        mSystemCaptioningBridge = CaptioningBridgeFactory.getSystemCaptioningBridge(mContext);
        mGestureStateListeners = new ObserverList<GestureStateListener>();
        mGestureStateListenersIterator = mGestureStateListeners.rewindableIterator();

        mWindowAndroidChangedObservers = new ObserverList<WindowAndroidChangedObserver>();
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ViewGroup getContainerView() {
        return mContainerView;
    }

    @Override
    public WebContents getWebContents() {
        return mWebContents;
    }

    @Override
    public WindowAndroid getWindowAndroid() {
        if (mNativeContentViewCore == 0) return null;
        return nativeGetJavaWindowAndroid(mNativeContentViewCore);
    }

    @VisibleForTesting
    @Override
    public SelectionPopupController getSelectionPopupControllerForTesting() {
        return mSelectionPopupController;
    }

    @VisibleForTesting
    @Override
    public void setSelectionPopupControllerForTesting(SelectionPopupController actionMode) {
        mSelectionPopupController = actionMode;
    }

    @VisibleForTesting
    @Override
    public TextSuggestionHost getTextSuggestionHostForTesting() {
        return mTextSuggestionHost;
    }

    @VisibleForTesting
    @Override
    public void setTextSuggestionHostForTesting(TextSuggestionHost textSuggestionHost) {
        mTextSuggestionHost = textSuggestionHost;
    }

    @Override
    public void addWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {
        mWindowAndroidChangedObservers.addObserver(observer);
    }

    @Override
    public void removeWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {
        mWindowAndroidChangedObservers.removeObserver(observer);
    }

    @Override
    public void addImeEventObserver(ImeEventObserver imeEventObserver) {
        mImeAdapter.addEventObserver(imeEventObserver);
    }

    @VisibleForTesting
    @Override
    public void setImeAdapterForTest(ImeAdapter imeAdapter) {
        mImeAdapter = imeAdapter;
    }

    @VisibleForTesting
    @Override
    public ImeAdapter getImeAdapterForTest() {
        return mImeAdapter;
    }

    // Perform important post-construction set up of the ContentViewCore.
    // We do not require the containing view in the constructor to allow embedders to create a
    // ContentViewCore without having fully created its containing view. The containing view
    // is a vital component of the ContentViewCore, so embedders must exercise caution in what
    // they do with the ContentViewCore before calling initialize().
    // We supply the nativeWebContents pointer here rather than in the constructor to allow us
    // to set the private browsing mode at a later point for the WebView implementation.
    // Note that the caller remains the owner of the nativeWebContents and is responsible for
    // deleting it after destroying the ContentViewCore.
    @Override
    public void initialize(ViewAndroidDelegate viewDelegate,
            InternalAccessDelegate internalDispatcher, WebContents webContents,
            WindowAndroid windowAndroid) {
        mViewAndroidDelegate = viewDelegate;
        setContainerView(viewDelegate.getContainerView());
        long windowNativePointer = windowAndroid.getNativePointer();
        assert windowNativePointer != 0;

        final float dipScale = windowAndroid.getDisplay().getDipScale();

        mNativeContentViewCore =
                nativeInit(webContents, mViewAndroidDelegate, windowNativePointer, dipScale);
        mWebContents = (WebContentsImpl) nativeGetWebContentsAndroid(mNativeContentViewCore);
        mRenderCoordinates = mWebContents.getRenderCoordinates();
        mRenderCoordinates.setDeviceScaleFactor(dipScale, windowAndroid.getContext().get());

        setContainerViewInternals(internalDispatcher);

        mPopupZoomer = new PopupZoomer(mContext, mWebContents, mContainerView);
        mImeAdapter = new ImeAdapter(
                mWebContents, mContainerView, new InputMethodManagerWrapper(mContext));
        mImeAdapter.addEventObserver(this);
        mTextSuggestionHost = new TextSuggestionHost(this);

        mSelectionPopupController =
                new SelectionPopupController(mContext, windowAndroid, webContents, mContainerView);
        mSelectionPopupController.setCallback(ActionModeCallbackHelper.EMPTY_CALLBACK);
        mSelectionPopupController.setContainerView(mContainerView);

        mWebContentsObserver = new ContentViewWebContentsObserver(this);

        mShouldRequestUnbufferedDispatch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ContentFeatureList.isEnabled(ContentFeatureList.REQUEST_UNBUFFERED_DISPATCH)
                && !nativeUsingSynchronousCompositing(mNativeContentViewCore);
    }

    @Override
    public void updateWindowAndroid(WindowAndroid windowAndroid) {
        removeDisplayAndroidObserver();
        long windowNativePointer = windowAndroid == null ? 0 : windowAndroid.getNativePointer();
        nativeUpdateWindowAndroid(mNativeContentViewCore, windowNativePointer);

        // TODO(yusufo): Rename this call to be general for tab reparenting.
        // Clean up cached popups that may have been created with an old activity.
        mSelectPopup = null;
        destroyPastePopup();

        addDisplayAndroidObserverIfNeeded();

        for (WindowAndroidChangedObserver observer : mWindowAndroidChangedObservers) {
            observer.onWindowAndroidChanged(windowAndroid);
        }
    }

    private EventForwarder getEventForwarder() {
        return getWebContents().getEventForwarder();
    }

    @Override
    public void setActionModeCallback(ActionMode.Callback callback) {
        mSelectionPopupController.setCallback(callback);
    }

    @Override
    public void setNonSelectionActionModeCallback(ActionMode.Callback callback) {
        mSelectionPopupController.setNonSelectionCallback(callback);
    }

    @Override
    public SelectionClient.ResultCallback getPopupControllerResultCallback() {
        return mSelectionPopupController.getResultCallback();
    }

    private void addDisplayAndroidObserverIfNeeded() {
        if (!mAttachedToWindow) return;
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid != null) {
            DisplayAndroid display = windowAndroid.getDisplay();
            display.addObserver(this);
            onRotationChanged(display.getRotation());
            onDIPScaleChanged(display.getDipScale());
        }
    }

    private void removeDisplayAndroidObserver() {
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid != null) {
            windowAndroid.getDisplay().removeObserver(this);
        }
    }

    @Override
    public void setContainerView(ViewGroup containerView) {
        try {
            TraceEvent.begin("ContentViewCore.setContainerView");
            if (mContainerView != null) {
                hideSelectPopupWithCancelMessage();
                mPopupZoomer.hide(false);
                mImeAdapter.setContainerView(containerView);
            }

            mContainerView = containerView;
            mContainerView.setClickable(true);
            if (mSelectionPopupController != null) {
                mSelectionPopupController.setContainerView(containerView);
            }
        } finally {
            TraceEvent.end("ContentViewCore.setContainerView");
        }
    }

    @CalledByNative
    private void onNativeContentViewCoreDestroyed(long nativeContentViewCore) {
        assert nativeContentViewCore == mNativeContentViewCore;
        mNativeContentViewCore = 0;
    }

    @Override
    public void setContainerViewInternals(InternalAccessDelegate internalDispatcher) {
        mContainerViewInternals = internalDispatcher;
    }

    @VisibleForTesting
    @Override
    public void setPopupZoomerForTest(PopupZoomer popupZoomer) {
        mPopupZoomer = popupZoomer;
    }

    @Override
    public void destroy() {
        removeDisplayAndroidObserver();
        if (mNativeContentViewCore != 0) {
            nativeOnJavaContentViewCoreDestroyed(mNativeContentViewCore);
        }
        mWebContentsObserver.destroy();
        mWebContentsObserver = null;
        mImeAdapter.resetAndHideKeyboard();
        mWebContents = null;
        mNativeContentViewCore = 0;
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onDestroyed();
        }
        mGestureStateListeners.clear();
        hidePopupsAndPreserveSelection();
        destroyPastePopup();

        // See warning in javadoc before adding more clean up code here.
    }

    @Override
    public boolean isAlive() {
        return mNativeContentViewCore != 0;
    }

    @Override
    @CalledByNative
    public int getViewportWidthPix() {
        return mContainerView.getWidth();
    }

    @Override
    @CalledByNative
    public int getViewportHeightPix() {
        return mContainerView.getHeight();
    }

    @Override
    @CalledByNative
    public float getMouseWheelTickMultiplier() {
        return mRenderCoordinates.getWheelScrollFactor()
                / mRenderCoordinates.getDeviceScaleFactor();
    }

    @VisibleForTesting
    @Override
    public String getSelectedText() {
        return mSelectionPopupController.getSelectedText();
    }

    @VisibleForTesting
    @Override
    public int getTopControlsShrinkBlinkHeightForTesting() {
        // TODO(jinsukkim): Let callsites provide with its own top controls height to remove
        //                  the test-only method in content layer.
        if (mNativeContentViewCore == 0) return 0;
        return nativeGetTopControlsShrinkBlinkHeightPixForTesting(mNativeContentViewCore);
    }

    @Override
    public boolean isFocusedNodeEditable() {
        return mSelectionPopupController.isSelectionEditable();
    }

    @CalledByNative
    private void requestDisallowInterceptTouchEvent() {
        mContainerView.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean isScrollInProgress() {
        return mTouchScrollInProgress || mPotentiallyActiveFlingCount > 0;
    }

    private void setTouchScrollInProgress(boolean inProgress) {
        mTouchScrollInProgress = inProgress;
        mSelectionPopupController.setScrollInProgress(inProgress, isScrollInProgress());
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onFlingStartEventConsumed() {
        mPotentiallyActiveFlingCount++;
        setTouchScrollInProgress(false);
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onFlingStartGesture(
                    computeVerticalScrollOffset(), computeVerticalScrollExtent());
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onFlingCancelEventAck() {
        updateGestureStateListener(GestureEventType.FLING_CANCEL);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollBeginEventAck() {
        setTouchScrollInProgress(true);
        updateGestureStateListener(GestureEventType.SCROLL_START);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollUpdateGestureConsumed() {
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onScrollUpdateGestureConsumed();
        }
        destroyPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollEndEventAck() {
        setTouchScrollInProgress(false);
        updateGestureStateListener(GestureEventType.SCROLL_END);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onPinchBeginEventAck() {
        updateGestureStateListener(GestureEventType.PINCH_BEGIN);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onPinchEndEventAck() {
        updateGestureStateListener(GestureEventType.PINCH_END);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onSingleTapEventAck(boolean consumed) {
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onSingleTap(consumed);
        }
        destroyPastePopup();
    }

    /**
     * Called just prior to a tap or press gesture being forwarded to the renderer.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private boolean filterTapOrPressEvent(int type, int x, int y) {
        if (type == GestureEventType.LONG_PRESS && offerLongPressToEmbedder()) {
            return true;
        }

        if (!mPopupZoomer.isShowing()) mPopupZoomer.setLastTouch(x, y);

        return false;
    }

    @VisibleForTesting
    @Override
    public void sendDoubleTapForTest(long timeMs, int x, int y) {
        if (mNativeContentViewCore == 0) return;
        nativeDoubleTap(mNativeContentViewCore, timeMs, x, y);
    }

    @Override
    public void flingViewport(long timeMs, float velocityX, float velocityY, boolean fromGamepad) {
        if (mNativeContentViewCore == 0) return;
        nativeFlingCancel(mNativeContentViewCore, timeMs, fromGamepad);
        if (velocityX == 0 && velocityY == 0) return;
        nativeScrollBegin(
                mNativeContentViewCore, timeMs, 0f, 0f, velocityX, velocityY, true, fromGamepad);
        nativeFlingStart(
                mNativeContentViewCore, timeMs, 0, 0, velocityX, velocityY, true, fromGamepad);
    }

    @Override
    public void cancelFling(long timeMs) {
        if (mNativeContentViewCore == 0) return;
        nativeFlingCancel(mNativeContentViewCore, timeMs, false);
    }

    @Override
    public void addGestureStateListener(GestureStateListener listener) {
        mGestureStateListeners.addObserver(listener);
    }

    @Override
    public void removeGestureStateListener(GestureStateListener listener) {
        mGestureStateListeners.removeObserver(listener);
    }

    private void updateGestureStateListener(int gestureType) {
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            GestureStateListener listener = mGestureStateListenersIterator.next();
            switch (gestureType) {
                case GestureEventType.PINCH_BEGIN:
                    listener.onPinchStarted();
                    break;
                case GestureEventType.PINCH_END:
                    listener.onPinchEnded();
                    break;
                case GestureEventType.FLING_END:
                    listener.onFlingEndGesture(
                            computeVerticalScrollOffset(), computeVerticalScrollExtent());
                    break;
                case GestureEventType.SCROLL_START:
                    listener.onScrollStarted(
                            computeVerticalScrollOffset(), computeVerticalScrollExtent());
                    break;
                case GestureEventType.SCROLL_END:
                    listener.onScrollEnded(
                            computeVerticalScrollOffset(), computeVerticalScrollExtent());
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onShow() {
        assert mWebContents != null;
        mWebContents.onShow();
        setAccessibilityState(mAccessibilityManager.isEnabled());
        restoreSelectionPopupsIfNecessary();
    }

    @Override
    public int getCurrentRenderProcessId() {
        return nativeGetCurrentRenderProcessId(mNativeContentViewCore);
    }

    @Override
    public void onHide() {
        assert mWebContents != null;
        hidePopupsAndPreserveSelection();
        mWebContents.onHide();
    }

    private void hidePopupsAndClearSelection() {
        mSelectionPopupController.destroyActionModeAndUnselect();
        destroyPastePopup();
        hideSelectPopupWithCancelMessage();
        mPopupZoomer.hide(false);
        mTextSuggestionHost.hidePopups();
        if (mWebContents != null) mWebContents.dismissTextHandles();
    }

    @CalledByNative
    private void hidePopupsAndPreserveSelection() {
        mSelectionPopupController.destroyActionModeAndKeepSelection();
        destroyPastePopup();
        hideSelectPopupWithCancelMessage();
        mPopupZoomer.hide(false);
        mTextSuggestionHost.hidePopups();
    }

    private void restoreSelectionPopupsIfNecessary() {
        mSelectionPopupController.restoreSelectionPopupsIfNecessary();
    }

    @Override
    public void destroySelectActionMode() {
        mSelectionPopupController.finishActionMode();
    }

    @Override
    public boolean isSelectActionBarShowing() {
        return mSelectionPopupController.isActionModeValid();
    }

    private void resetGestureDetection() {
        if (mNativeContentViewCore == 0) return;
        nativeResetGestureDetection(mNativeContentViewCore);
    }

    @Override
    public boolean isAttachedToWindow() {
        return mAttachedToWindow;
    }

    @SuppressWarnings("javadoc")
    @Override
    public void onAttachedToWindow() {
        mAttachedToWindow = true;
        addDisplayAndroidObserverIfNeeded();
        setAccessibilityState(mAccessibilityManager.isEnabled());
        updateTextSelectionUI(true);
        GamepadList.onAttachedToWindow(mContext);
        mAccessibilityManager.addAccessibilityStateChangeListener(this);
        mSystemCaptioningBridge.addListener(this);
        mImeAdapter.onViewAttachedToWindow();
        if (mWebContentsAccessibility != null) {
            mWebContentsAccessibility.onAttachedToWindow();
        }
    }

    @Override
    public void updateTextSelectionUI(boolean focused) {
        setTextHandlesTemporarilyHidden(!focused);
        if (focused) {
            restoreSelectionPopupsIfNecessary();
        } else {
            hidePopupsAndPreserveSelection();
        }
    }

    @SuppressWarnings("javadoc")
    @SuppressLint("MissingSuperCall")
    @Override
    public void onDetachedFromWindow() {
        mAttachedToWindow = false;
        mImeAdapter.onViewDetachedFromWindow();
        removeDisplayAndroidObserver();
        GamepadList.onDetachedFromWindow();
        mAccessibilityManager.removeAccessibilityStateChangeListener(this);

        // WebView uses PopupWindows for handle rendering, which may remain
        // unintentionally visible even after the WebView has been detached.
        // Override the handle visibility explicitly to address this, but
        // preserve the underlying selection for detachment cases like screen
        // locking and app switching.
        updateTextSelectionUI(false);
        mSystemCaptioningBridge.removeListener(this);
        if (mWebContentsAccessibility != null) {
            mWebContentsAccessibility.onDetachedFromWindow();
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        boolean allowKeyboardLearning = getWebContents() != null && !getWebContents().isIncognito();
        return mImeAdapter.onCreateInputConnection(outAttrs, allowKeyboardLearning);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mImeAdapter.hasTextInputType();
    }

    @SuppressWarnings("javadoc")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        try {
            TraceEvent.begin("ContentViewCore.onConfigurationChanged");
            mImeAdapter.onKeyboardConfigurationChanged(newConfig);
            mContainerViewInternals.super_onConfigurationChanged(newConfig);
            // To request layout has side effect, but it seems OK as it only happen in
            // onConfigurationChange and layout has to be changed in most case.
            mContainerView.requestLayout();
        } finally {
            TraceEvent.end("ContentViewCore.onConfigurationChanged");
        }
    }

    @SuppressWarnings("javadoc")
    @Override
    public void onSizeChanged(int wPix, int hPix, int owPix, int ohPix) {
        updateAfterSizeChanged();
    }

    @CalledByNative
    private void onTouchDown(MotionEvent event) {
        if (mShouldRequestUnbufferedDispatch) requestUnbufferedDispatch(event);
        cancelRequestToScrollFocusedEditableNodeIntoView();
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onTouchDown();
        }
    }

    private void updateAfterSizeChanged() {
        mPopupZoomer.hide(false);

        // Execute a delayed form focus operation because the OSK was brought
        // up earlier.
        Rect focusPreOSKViewportRect = mImeAdapter.getFocusPreOSKViewportRect();
        if (!focusPreOSKViewportRect.isEmpty()) {
            Rect rect = new Rect();
            getContainerView().getWindowVisibleDisplayFrame(rect);
            if (!rect.equals(focusPreOSKViewportRect)) {
                // Only assume the OSK triggered the onSizeChanged if width was preserved.
                if (rect.width() == focusPreOSKViewportRect.width()) {
                    assert mWebContents != null;
                    mWebContents.scrollFocusedEditableNodeIntoView();
                }
                cancelRequestToScrollFocusedEditableNodeIntoView();
            }
        }
    }

    private void cancelRequestToScrollFocusedEditableNodeIntoView() {
        // Zero-ing the rect will prevent |updateAfterSizeChanged()| from
        // issuing the delayed form focus event.
        mImeAdapter.getFocusPreOSKViewportRect().setEmpty();
    }

    @Override
    public void onPause() {
        onFocusChanged(false, true);
    }

    @Override
    public void onResume() {
        onFocusChanged(ViewUtils.hasFocus(getContainerView()), true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mImeAdapter.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) resetGestureDetection();
        mSelectionPopupController.onWindowFocusChanged(hasWindowFocus);
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onWindowFocusChanged(hasWindowFocus);
        }
    }

    @Override
    public void onFocusChanged(boolean gainFocus, boolean hideKeyboardOnBlur) {
        if (mHasViewFocus != null && mHasViewFocus == gainFocus) return;
        mHasViewFocus = gainFocus;
        mImeAdapter.onViewFocusChanged(gainFocus, hideKeyboardOnBlur);

        mJoystickScrollEnabled = gainFocus && !isFocusedNodeEditable();

        if (gainFocus) {
            restoreSelectionPopupsIfNecessary();
        } else {
            cancelRequestToScrollFocusedEditableNodeIntoView();
            if (mPreserveSelectionOnNextLossOfFocus) {
                mPreserveSelectionOnNextLossOfFocus = false;
                hidePopupsAndPreserveSelection();
            } else {
                hidePopupsAndClearSelection();
                // Clear the selection. The selection is cleared on destroying IME
                // and also here since we may receive destroy first, for example
                // when focus is lost in webview.
                clearSelection();
            }
        }
        if (mNativeContentViewCore != 0) nativeSetFocus(mNativeContentViewCore, gainFocus);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mPopupZoomer.isShowing() && keyCode == KeyEvent.KEYCODE_BACK) {
            mPopupZoomer.backButtonPressed();
            return true;
        }
        return mContainerViewInternals.super_onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (GamepadList.dispatchKeyEvent(event)) return true;
        if (!shouldPropagateKeyEvent(event)) {
            return mContainerViewInternals.super_dispatchKeyEvent(event);
        }

        if (mImeAdapter.dispatchKeyEvent(event)) return true;

        return mContainerViewInternals.super_dispatchKeyEvent(event);
    }

    /**
     * Check whether a key should be propagated to the embedder or not.
     * We need to send almost every key to Blink. However:
     * 1. We don't want to block the device on the renderer for
     * some keys like menu, home, call.
     * 2. There are no WebKit equivalents for some of these keys
     * (see app/keyboard_codes_win.h)
     * Note that these are not the same set as KeyEvent.isSystemKey:
     * for instance, AKEYCODE_MEDIA_* will be dispatched to webkit*.
     */
    private static boolean shouldPropagateKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_CALL
                || keyCode == KeyEvent.KEYCODE_ENDCALL || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_CAMERA
                || keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return false;
        }
        return true;
    }

    /**
     * Removes noise from joystick motion events.
     */
    private static float getFilteredAxisValue(MotionEvent event, int axis) {
        final float kJoystickScrollDeadzone = 0.2f;
        float axisValWithNoise = event.getAxisValue(axis);
        if (Math.abs(axisValWithNoise) > kJoystickScrollDeadzone) return axisValWithNoise;
        return 0f;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (GamepadList.onGenericMotionEvent(event)) return true;
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_SCROLL:
                    getEventForwarder().onMouseWheelEvent(event.getEventTime(), event.getX(),
                            event.getY(), event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                            event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                            mRenderCoordinates.getWheelScrollFactor());
                    return true;
                case MotionEvent.ACTION_BUTTON_PRESS:
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    // TODO(mustaq): Should we include MotionEvent.TOOL_TYPE_STYLUS here?
                    // crbug.com/592082
                    if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                        return getEventForwarder().onMouseEvent(event);
                    }
            }
        } else if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (mJoystickScrollEnabled) {
                float velocityX = getFilteredAxisValue(event, MotionEvent.AXIS_X);
                float velocityY = getFilteredAxisValue(event, MotionEvent.AXIS_Y);
                if (velocityX != 0.f || velocityY != 0.f) {
                    flingViewport(event.getEventTime(), -velocityX, -velocityY, true);
                    return true;
                }
            }
        }
        return mContainerViewInternals.super_onGenericMotionEvent(event);
    }

    @Override
    public void setCurrentTouchEventOffsets(float dx, float dy) {
        mCurrentTouchOffsetX = dx;
        mCurrentTouchOffsetY = dy;
        getEventForwarder().setCurrentTouchEventOffsets(dx, dy);
    }

    @Override
    public void scrollBy(float dxPix, float dyPix) {
        if (mNativeContentViewCore == 0) return;
        if (dxPix == 0 && dyPix == 0) return;
        long time = SystemClock.uptimeMillis();
        // It's a very real (and valid) possibility that a fling may still
        // be active when programatically scrolling. Cancelling the fling in
        // such cases ensures a consistent gesture event stream.
        if (mPotentiallyActiveFlingCount > 0) {
            nativeFlingCancel(mNativeContentViewCore, time, false);
        }
        // x/y represents starting location of scroll.
        nativeScrollBegin(mNativeContentViewCore, time, 0f, 0f, -dxPix, -dyPix, true, false);
        nativeScrollBy(mNativeContentViewCore, time, 0f, 0f, dxPix, dyPix);
        nativeScrollEnd(mNativeContentViewCore, time);
    }

    @Override
    public void scrollTo(float xPix, float yPix) {
        if (mNativeContentViewCore == 0) return;
        final float xCurrentPix = mRenderCoordinates.getScrollXPix();
        final float yCurrentPix = mRenderCoordinates.getScrollYPix();
        final float dxPix = xPix - xCurrentPix;
        final float dyPix = yPix - yCurrentPix;
        scrollBy(dxPix, dyPix);
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeHorizontalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportWidthPixInt();
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeHorizontalScrollOffset() {
        return mRenderCoordinates.getScrollXPixInt();
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeHorizontalScrollRange() {
        return mRenderCoordinates.getContentWidthPixInt();
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeVerticalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportHeightPixInt();
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeVerticalScrollOffset() {
        return mRenderCoordinates.getScrollYPixInt();
    }

    @SuppressWarnings("javadoc")
    @Override
    public int computeVerticalScrollRange() {
        return mRenderCoordinates.getContentHeightPixInt();
    }

    // End FrameLayout overrides.

    @SuppressWarnings("javadoc")
    @Override
    public boolean awakenScrollBars(int startDelay, boolean invalidate) {
        // For the default implementation of ContentView which draws the scrollBars on the native
        // side, calling this function may get us into a bad state where we keep drawing the
        // scrollBars, so disable it by always returning false.
        if (mContainerView.getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            return false;
        } else {
            return mContainerViewInternals.super_awakenScrollBars(startDelay, invalidate);
        }
    }

    @Override
    public void updateMultiTouchZoomSupport(boolean supportsMultiTouchZoom) {
        if (mNativeContentViewCore == 0) return;
        nativeSetMultiTouchZoomSupportEnabled(mNativeContentViewCore, supportsMultiTouchZoom);
    }

    @Override
    public void updateDoubleTapSupport(boolean supportsDoubleTap) {
        if (mNativeContentViewCore == 0) return;
        nativeSetDoubleTapSupportEnabled(mNativeContentViewCore, supportsDoubleTap);
    }

    @Override
    public void selectPopupMenuItems(int[] indices) {
        if (mNativeContentViewCore != 0) {
            nativeSelectPopupMenuItems(
                    mNativeContentViewCore, mNativeSelectPopupSourceFrame, indices);
        }
        mNativeSelectPopupSourceFrame = 0;
        mSelectPopup = null;
    }

    /**
     * Send the screen orientation value to the renderer.
     */
    @VisibleForTesting
    private void sendOrientationChangeEvent(int orientation) {
        if (mNativeContentViewCore == 0) return;

        nativeSendOrientationChangeEvent(mNativeContentViewCore, orientation);
    }

    @Override
    public ActionModeCallbackHelper getActionModeCallbackHelper() {
        return mSelectionPopupController;
    }

    private void showSelectActionMode() {
        mSelectionPopupController.showActionModeOrClearOnFailure();
    }

    @Override
    public void clearSelection() {
        mSelectionPopupController.clearSelection();
    }

    @Override
    public void preserveSelectionOnNextLossOfFocus() {
        mPreserveSelectionOnNextLossOfFocus = true;
    }

    private void setTextHandlesTemporarilyHidden(boolean hide) {
        if (mNativeContentViewCore == 0) return;
        nativeSetTextHandlesTemporarilyHidden(mNativeContentViewCore, hide);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void updateFrameInfo(float scrollOffsetX, float scrollOffsetY, float pageScaleFactor,
            float minPageScaleFactor, float maxPageScaleFactor, float contentWidth,
            float contentHeight, float viewportWidth, float viewportHeight, float topBarShownPix,
            boolean topBarChanged, boolean isMobileOptimizedHint) {
        TraceEvent.begin("ContentViewCore:updateFrameInfo");
        mIsMobileOptimizedHint = isMobileOptimizedHint;
        final boolean contentSizeChanged = contentWidth != mRenderCoordinates.getContentWidthCss()
                || contentHeight != mRenderCoordinates.getContentHeightCss();
        final boolean scaleLimitsChanged =
                minPageScaleFactor != mRenderCoordinates.getMinPageScaleFactor()
                || maxPageScaleFactor != mRenderCoordinates.getMaxPageScaleFactor();
        final boolean pageScaleChanged = pageScaleFactor != mRenderCoordinates.getPageScaleFactor();
        final boolean scrollChanged = pageScaleChanged
                || scrollOffsetX != mRenderCoordinates.getScrollX()
                || scrollOffsetY != mRenderCoordinates.getScrollY();

        final boolean needHidePopupZoomer = contentSizeChanged || scrollChanged;

        if (needHidePopupZoomer) mPopupZoomer.hide(true);

        if (scrollChanged) {
            mContainerViewInternals.onScrollChanged(
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetX),
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetY),
                    (int) mRenderCoordinates.getScrollXPix(),
                    (int) mRenderCoordinates.getScrollYPix());
        }

        mRenderCoordinates.updateFrameInfo(scrollOffsetX, scrollOffsetY, contentWidth,
                contentHeight, viewportWidth, viewportHeight, pageScaleFactor, minPageScaleFactor,
                maxPageScaleFactor, topBarShownPix);

        if (scrollChanged || topBarChanged) {
            for (mGestureStateListenersIterator.rewind();
                    mGestureStateListenersIterator.hasNext();) {
                mGestureStateListenersIterator.next().onScrollOffsetOrExtentChanged(
                        computeVerticalScrollOffset(), computeVerticalScrollExtent());
            }
        }

        if (scaleLimitsChanged) {
            for (mGestureStateListenersIterator.rewind();
                    mGestureStateListenersIterator.hasNext();) {
                mGestureStateListenersIterator.next().onScaleLimitsChanged(
                        minPageScaleFactor, maxPageScaleFactor);
            }
        }

        TraceEvent.end("ContentViewCore:updateFrameInfo");
    }

    // ImeEventObserver

    @Override
    public void onImeEvent() {
        mPopupZoomer.hide(true);
    }

    @Override
    public void onNodeAttributeUpdated(boolean editable, boolean password) {
        mJoystickScrollEnabled = !editable;
        mSelectionPopupController.updateSelectionState(editable, password);
    }

    @Override
    public void onBeforeSendKeyEvent(KeyEvent event) {}

    /**
     * Called (from native) when the <select> popup needs to be shown.
     * @param anchorView View anchored for popup.
     * @param nativeSelectPopupSourceFrame The native RenderFrameHost that owns the popup.
     * @param items           Items to show.
     * @param enabled         POPUP_ITEM_TYPEs for items.
     * @param multiple        Whether the popup menu should support multi-select.
     * @param selectedIndices Indices of selected items.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void showSelectPopup(View anchorView, long nativeSelectPopupSourceFrame, String[] items,
            int[] enabled, boolean multiple, int[] selectedIndices, boolean rightAligned) {
        if (mContainerView.getParent() == null || mContainerView.getVisibility() != View.VISIBLE) {
            mNativeSelectPopupSourceFrame = nativeSelectPopupSourceFrame;
            selectPopupMenuItems(null);
            return;
        }

        hidePopupsAndClearSelection();
        assert mNativeSelectPopupSourceFrame == 0 : "Zombie popup did not clear the frame source";

        assert items.length == enabled.length;
        List<SelectPopupItem> popupItems = new ArrayList<SelectPopupItem>();
        for (int i = 0; i < items.length; i++) {
            popupItems.add(new SelectPopupItem(items[i], enabled[i]));
        }
        if (DeviceFormFactor.isTablet() && !multiple && !isTouchExplorationEnabled()) {
            mSelectPopup = new SelectPopupDropdown(
                    this, anchorView, popupItems, selectedIndices, rightAligned);
        } else {
            if (getWindowAndroid() == null) return;
            Context windowContext = getWindowAndroid().getContext().get();
            if (windowContext == null) return;
            mSelectPopup = new SelectPopupDialog(
                    this, windowContext, popupItems, multiple, selectedIndices);
        }
        mNativeSelectPopupSourceFrame = nativeSelectPopupSourceFrame;
        mSelectPopup.show();
    }

    /**
     * Called when the <select> popup needs to be hidden.
     */
    @CalledByNative
    private void hideSelectPopup() {
        if (mSelectPopup == null) return;
        mSelectPopup.hide(false);
        mSelectPopup = null;
        mNativeSelectPopupSourceFrame = 0;
    }

    /**
     * Called when the <select> popup needs to be hidden. This calls
     * nativeSelectPopupMenuItems() with null indices.
     */
    private void hideSelectPopupWithCancelMessage() {
        if (mSelectPopup != null) mSelectPopup.hide(true);
    }

    /**
     * @return The visible select popup being shown.
     */
    @VisibleForTesting
    @Override
    public SelectPopup getSelectPopupForTest() {
        return mSelectPopup;
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void performLongPressHapticFeedback() {
        mContainerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void destroyPastePopup() {
        mSelectionPopupController.destroyPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onRenderProcessChange() {
        // Immediately sync closed caption settings to the new render process.
        mSystemCaptioningBridge.syncToListener(this);
    }

    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        setAccessibilityState(enabled);
    }

    @Override
    public boolean supportsAccessibilityAction(int action) {
        // TODO(dmazzoni): implement this in WebContentsAccessibility.
        return false;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        // TODO(dmazzoni): implement this in WebContentsAccessibility.
        return false;
    }

    @Override
    public WebContentsAccessibility getWebContentsAccessibility() {
        return mWebContentsAccessibility;
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (mIsObscuredByAnotherView) return null;

        // If WebContentsAccessibility is null, create it if native a11y is allowed and WebContent
        // is not null. Else return null.
        if (mWebContentsAccessibility == null) {
            if (!mNativeAccessibilityAllowed || mWebContents == null) {
                return null;
            }
            mWebContentsAccessibility = WebContentsAccessibility.create(
                    mContext, mContainerView, mWebContents, mShouldSetAccessibilityFocusOnPageLoad);
            // For Lollipop devices, we need to register a broadcast receiver for locale change.
            if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
                    && mContainerView.isAttachedToWindow()) {
                mWebContentsAccessibility.onAttachedToWindow();
            }
        }
        if (!mWebContentsAccessibility.isEnabled()) {
            mWebContentsAccessibility.enable();
        }
        return mWebContentsAccessibility.getAccessibilityNodeProvider();
    }

    @Override
    public void setObscuredByAnotherView(boolean isObscured) {
        if (isObscured != mIsObscuredByAnotherView) {
            mIsObscuredByAnotherView = isObscured;
            getContainerView().sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onProvideVirtualStructure(
            final ViewStructure structure, final boolean ignoreScrollOffset) {
        // Do not collect accessibility tree in incognito mode
        if (getWebContents().isIncognito()) {
            structure.setChildCount(0);
            return;
        }
        structure.setChildCount(1);
        final ViewStructure viewRoot = structure.asyncNewChild(0);
        getWebContents().requestAccessibilitySnapshot(new AccessibilitySnapshotCallback() {
            @Override
            public void onAccessibilitySnapshot(AccessibilitySnapshotNode root) {
                viewRoot.setClassName("");
                viewRoot.setHint(mProductVersion);
                if (root == null) {
                    viewRoot.asyncCommit();
                    return;
                }
                createVirtualStructure(viewRoot, root, ignoreScrollOffset);
            }
        });
    }

    // When creating the View structure, the left and top are relative to the parent node.
    @TargetApi(Build.VERSION_CODES.M)
    private void createVirtualStructure(ViewStructure viewNode, AccessibilitySnapshotNode node,
            final boolean ignoreScrollOffset) {
        viewNode.setClassName(node.className);
        if (node.hasSelection) {
            viewNode.setText(node.text, node.startSelection, node.endSelection);
        } else {
            viewNode.setText(node.text);
        }
        int left = (int) mRenderCoordinates.fromLocalCssToPix(node.x);
        int top = (int) mRenderCoordinates.fromLocalCssToPix(node.y);
        int width = (int) mRenderCoordinates.fromLocalCssToPix(node.width);
        int height = (int) mRenderCoordinates.fromLocalCssToPix(node.height);

        Rect boundsInParent = new Rect(left, top, left + width, top + height);
        if (node.isRootNode) {
            // Offset of the web content relative to the View.
            boundsInParent.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
            if (!ignoreScrollOffset) {
                boundsInParent.offset(-(int) mRenderCoordinates.getScrollXPix(),
                        -(int) mRenderCoordinates.getScrollYPix());
            }
        }

        viewNode.setDimens(boundsInParent.left, boundsInParent.top, 0, 0, width, height);
        viewNode.setChildCount(node.children.size());
        if (node.hasStyle) {
            // The text size should be in physical pixels, not CSS pixels.
            float textSize = mRenderCoordinates.fromLocalCssToPix(node.textSize);

            int style = (node.bold ? ViewNode.TEXT_STYLE_BOLD : 0)
                    | (node.italic ? ViewNode.TEXT_STYLE_ITALIC : 0)
                    | (node.underline ? ViewNode.TEXT_STYLE_UNDERLINE : 0)
                    | (node.lineThrough ? ViewNode.TEXT_STYLE_STRIKE_THRU : 0);
            viewNode.setTextStyle(textSize, node.color, node.bgcolor, style);
        }
        for (int i = 0; i < node.children.size(); i++) {
            createVirtualStructure(viewNode.asyncNewChild(i), node.children.get(i), true);
        }
        viewNode.asyncCommit();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void requestUnbufferedDispatch(MotionEvent touchDownEvent) {
        mContainerView.requestUnbufferedDispatch(touchDownEvent);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onSystemCaptioningChanged(TextTrackSettings settings) {
        if (mNativeContentViewCore == 0) return;
        nativeSetTextTrackSettings(mNativeContentViewCore, settings.getTextTracksEnabled(),
                settings.getTextTrackBackgroundColor(), settings.getTextTrackFontFamily(),
                settings.getTextTrackFontStyle(), settings.getTextTrackFontVariant(),
                settings.getTextTrackTextColor(), settings.getTextTrackTextShadow(),
                settings.getTextTrackTextSize());
    }

    @Override
    public void onReceivedProcessTextResult(int resultCode, Intent data) {
        mSelectionPopupController.onReceivedProcessTextResult(resultCode, data);
    }

    @Override
    public boolean isTouchExplorationEnabled() {
        return mTouchExplorationEnabled;
    }

    @Override
    public void setAccessibilityState(boolean state) {
        if (!state) {
            mNativeAccessibilityAllowed = false;
            mTouchExplorationEnabled = false;
        } else {
            mNativeAccessibilityAllowed = true;
            mTouchExplorationEnabled = mAccessibilityManager.isTouchExplorationEnabled();
        }
    }

    @Override
    public void setShouldSetAccessibilityFocusOnPageLoad(boolean on) {
        mShouldSetAccessibilityFocusOnPageLoad = on;
    }

    @Override
    public boolean getIsMobileOptimizedHint() {
        return mIsMobileOptimizedHint;
    }

    @Override
    public void setBackgroundOpaque(boolean opaque) {
        if (mNativeContentViewCore != 0) {
            nativeSetBackgroundOpaque(mNativeContentViewCore, opaque);
        }
    }

    /**
     * Offer a long press gesture to the embedding View, primarily for WebView compatibility.
     *
     * @return true if the embedder handled the event.
     */
    private boolean offerLongPressToEmbedder() {
        return mContainerView.performLongClick();
    }

    /**
     * Reset scroll and fling accounting, notifying listeners as appropriate.
     * This is useful as a failsafe when the input stream may have been interruped.
     */
    private void resetScrollInProgress() {
        if (!isScrollInProgress()) return;

        final boolean touchScrollInProgress = mTouchScrollInProgress;
        final int potentiallyActiveFlingCount = mPotentiallyActiveFlingCount;

        mPotentiallyActiveFlingCount = 0;
        setTouchScrollInProgress(false);
        if (touchScrollInProgress) updateGestureStateListener(GestureEventType.SCROLL_END);
        if (potentiallyActiveFlingCount > 0) updateGestureStateListener(GestureEventType.FLING_END);
    }

    @CalledByNative
    private void onNativeFlingStopped() {
        if (mPotentiallyActiveFlingCount > 0) {
            mPotentiallyActiveFlingCount--;
            updateGestureStateListener(GestureEventType.FLING_END);
        }
        // Note that mTouchScrollInProgress should normally be false at this
        // point, but we reset it anyway as another failsafe.
        setTouchScrollInProgress(false);
    }

    // DisplayAndroidObserver method.
    @Override
    public void onRotationChanged(int rotation) {
        // ActionMode#invalidate() won't be able to re-layout the floating
        // action mode menu items according to the new rotation. So Chrome
        // has to re-create the action mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mSelectionPopupController.isActionModeValid()) {
            hidePopupsAndPreserveSelection();
            showSelectActionMode();
        }
        mTextSuggestionHost.hidePopups();

        int rotationDegrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDegrees = 0;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = -90;
                break;
            default:
                throw new IllegalStateException(
                        "Display.getRotation() shouldn't return that value");
        }

        sendOrientationChangeEvent(rotationDegrees);
    }

    // DisplayAndroidObserver method.
    @Override
    public void onDIPScaleChanged(float dipScale) {
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid == null || mNativeContentViewCore == 0) return;

        mRenderCoordinates.setDeviceScaleFactor(dipScale, getWindowAndroid().getContext().get());
        nativeSetDIPScale(mNativeContentViewCore, dipScale);
    }

    @Override
    public void setFullscreenRequiredForOrientationLock(boolean value) {
        mFullscreenRequiredForOrientationLock = value;
    }

    @CalledByNative
    private boolean isFullscreenRequiredForOrientationLock() {
        return mFullscreenRequiredForOrientationLock;
    }

    @Override
    public void setSelectionClient(SelectionClient selectionClient) {
        mSelectionPopupController.setSelectionClient(selectionClient);
    }

    @Override
    public void setTextClassifier(TextClassifier textClassifier) {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        SelectionClient client = mSelectionPopupController.getSelectionClient();
        if (client != null) client.setTextClassifier(textClassifier);
    }

    @Override
    public TextClassifier getTextClassifier() {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        SelectionClient client = mSelectionPopupController.getSelectionClient();
        return client == null ? null : client.getTextClassifier();
    }

    @Override
    public TextClassifier getCustomTextClassifier() {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        SelectionClient client = mSelectionPopupController.getSelectionClient();
        return client == null ? null : client.getCustomTextClassifier();
    }

    @NativeClassQualifiedName("ContentViewCore")
    private native long nativeInit(WebContents webContents, ViewAndroidDelegate viewAndroidDelegate,
            long windowAndroidPtr, float dipScale);
    @NativeClassQualifiedName("ContentViewCore")
    private static native ContentViewCore nativeFromWebContentsAndroid(WebContents webContents);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeUpdateWindowAndroid(
            long nativeContentViewCore, long windowAndroidPtr);
    @NativeClassQualifiedName("ContentViewCore")
    private native WebContents nativeGetWebContentsAndroid(long nativeContentViewCore);
    @NativeClassQualifiedName("ContentViewCore")
    private native WindowAndroid nativeGetJavaWindowAndroid(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeOnJavaContentViewCoreDestroyed(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetFocus(long nativeContentViewCore, boolean focused);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetDIPScale(long nativeContentViewCore, float dipScale);

    @NativeClassQualifiedName("ContentViewCore")
    private native int nativeGetTopControlsShrinkBlinkHeightPixForTesting(
            long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSendOrientationChangeEvent(
            long nativeContentViewCore, int orientation);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeScrollBegin(long nativeContentViewCore, long timeMs, float x, float y,
            float hintX, float hintY, boolean targetViewport, boolean fromGamepad);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeScrollEnd(long nativeContentViewCore, long timeMs);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeScrollBy(
            long nativeContentViewCore, long timeMs, float x, float y, float deltaX, float deltaY);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeFlingStart(long nativeContentViewCore, long timeMs, float x, float y,
            float vx, float vy, boolean targetViewport, boolean fromGamepad);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeFlingCancel(
            long nativeContentViewCore, long timeMs, boolean fromGamepad);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeDoubleTap(long nativeContentViewCore, long timeMs, float x, float y);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetTextHandlesTemporarilyHidden(
            long nativeContentViewCore, boolean hidden);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeResetGestureDetection(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetDoubleTapSupportEnabled(
            long nativeContentViewCore, boolean enabled);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetMultiTouchZoomSupportEnabled(
            long nativeContentViewCore, boolean enabled);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSelectPopupMenuItems(
            long nativeContentViewCore, long nativeSelectPopupSourceFrame, int[] indices);

    @NativeClassQualifiedName("ContentViewCore")
    private native int nativeGetCurrentRenderProcessId(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native boolean nativeUsingSynchronousCompositing(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeWasResized(long nativeContentViewCore);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetTextTrackSettings(long nativeContentViewCore,
            boolean textTracksEnabled, String textTrackBackgroundColor, String textTrackFontFamily,
            String textTrackFontStyle, String textTrackFontVariant, String textTrackTextColor,
            String textTrackTextShadow, String textTrackTextSize);

    @NativeClassQualifiedName("ContentViewCore")
    private native void nativeSetBackgroundOpaque(long nativeContentViewCore, boolean opaque);
}
