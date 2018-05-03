// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityNodeProvider;

import org.chromium.base.VisibleForTesting;
import org.chromium.content.browser.accessibility.WebContentsAccessibility;
import org.chromium.content.browser.input.SelectPopup;
import org.chromium.content.browser.input.TextSuggestionHost;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.ViewAndroidDelegate;
import org.chromium.ui.base.WindowAndroid;

/**
 * Provides a Java-side 'wrapper' around a WebContent (native) instance.
 * Contains all the major functionality necessary to manage the lifecycle of a ContentView without
 * being tied to the view system.
 *
 * WARNING: ContentViewCore is in the process of being broken up. Please do not add new stuff.
 * See https://crbug.com/598880.
 */
public interface ContentViewCore {
    public static ContentViewCore create(Context context, String productVersion) {
        return new ContentViewCoreImpl(context, productVersion);
    }

    public static ContentViewCore fromWebContents(WebContents webContents) {
        return ContentViewCoreImpl.fromWebContents(webContents);
    }

    /**
     * Interface that consumers of {@link ContentViewCore} must implement to allow the proper
     * dispatching of view methods through the containing view.
     *
     * <p>
     * All methods with the "super_" prefix should be routed to the parent of the
     * implementing container view.
     */
    @SuppressWarnings("javadoc")
    public interface InternalAccessDelegate {
        /**
         * @see View#onKeyUp(keyCode, KeyEvent)
         */
        boolean super_onKeyUp(int keyCode, KeyEvent event);

        /**
         * @see View#dispatchKeyEvent(KeyEvent)
         */
        boolean super_dispatchKeyEvent(KeyEvent event);

        /**
         * @see View#onGenericMotionEvent(MotionEvent)
         */
        boolean super_onGenericMotionEvent(MotionEvent event);

        /**
         * @see View#onConfigurationChanged(Configuration)
         */
        void super_onConfigurationChanged(Configuration newConfig);

        /**
         * @see View#onScrollChanged(int, int, int, int)
         */
        void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix);

        /**
         * @see View#awakenScrollBars(int, boolean)
         */
        boolean super_awakenScrollBars(int startDelay, boolean invalidate);
    }

    /**
     * @return The context used for creating this ContentViewCore.
     */
    Context getContext();

    /**
     * @return The ViewGroup that all view actions of this ContentViewCore should interact with.
     */
    ViewGroup getContainerView();

    /**
     * @return The WebContents currently being rendered.
     */
    WebContents getWebContents();

    /**
     * @return The WindowAndroid associated with this ContentViewCore.
     */
    WindowAndroid getWindowAndroid();

    /**
     * Add {@link WindowAndroidChangeObserver} object.
     * @param observer Observer instance to add.
     */
    void addWindowAndroidChangedObserver(WindowAndroidChangedObserver observer);

    /**
     * Remove {@link WindowAndroidChangeObserver} object.
     * @param observer Observer instance to remove.
     */
    void removeWindowAndroidChangedObserver(WindowAndroidChangedObserver observer);

    /**
     * Initialize {@link ContentViewCore} object.
     * @param viewDelegate Delegate to add/remove anchor views.
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     * @param webContents A WebContents instance to connect to.
     * @param windowAndroid An instance of the WindowAndroid.
     */
    void initialize(ViewAndroidDelegate viewDelegate, InternalAccessDelegate internalDispatcher,
            WebContents webContents, WindowAndroid windowAndroid);

    /**
     * Updates the native {@link ContentViewCore} with a new window. This moves the NativeView and
     * attached it to the new NativeWindow linked with the given {@link WindowAndroid}.
     * @param windowAndroid The new {@link WindowAndroid} for this {@link ContentViewCore}.
     */
    void updateWindowAndroid(WindowAndroid windowAndroid);

    /**
     * Sets a new container view for this {@link ContentViewCore}.
     *
     * <p>WARNING: This method can also be used to replace the existing container view,
     * but you should only do it if you have a very good reason to. Replacing the
     * container view has been designed to support fullscreen in the Webview so it
     * might not be appropriate for other use cases.
     *
     * <p>This method only performs a small part of replacing the container view and
     * embedders are responsible for:
     * <ul>
     *     <li>Disconnecting the old container view from this ContentViewCore</li>
     *     <li>Updating the InternalAccessDelegate</li>
     *     <li>Reconciling the state of this ContentViewCore with the new container view</li>
     *     <li>Tearing down and recreating the native GL rendering where appropriate</li>
     *     <li>etc.</li>
     * </ul>
     */
    void setContainerView(ViewGroup containerView);

    /**
     * Set the Container view Internals.
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     */
    void setContainerViewInternals(InternalAccessDelegate internalDispatcher);

    /**
     * Destroy the internal state of the ContentView. This method may only be
     * called after the ContentView has been removed from the view system. No
     * other methods may be called on this ContentView after this method has
     * been called.
     * Warning: destroy() is not guranteed to be called in Android WebView.
     * Any object that relies solely on destroy() being called to be cleaned up
     * will leak in Android WebView. If appropriate, consider clean up in
     * onDetachedFromWindow() which is guaranteed to be called in Android WebView.
     */
    void destroy();

    /**
     * Returns true initially, false after destroy() has been called.
     * It is illegal to call any other method after destroy().
     */
    boolean isAlive();

    /**
     * @return Viewport width in physical pixels as set from onSizeChanged.
     */
    int getViewportWidthPix();

    /**
     * @return Viewport height in physical pixels as set from onSizeChanged.
     */
    int getViewportHeightPix();

    /**
     * @return Whether a scroll targeting web content is in progress.
     */
    boolean isScrollInProgress();

    /**
     * Flings the viewport with velocity vector (velocityX, velocityY).
     * @param timeMs the current time.
     * @param velocityX fling speed in x-axis.
     * @param velocityY fling speed in y-axis.
     * @param fromGamepad true if generated by gamepad (which will make this fixed-velocity fling)
     */
    void flingViewport(long timeMs, float velocityX, float velocityY, boolean fromGamepad);

    /**
     * Cancel any fling gestures active.
     * @param timeMs Current time (in milliseconds).
     */
    void cancelFling(long timeMs);

    /**
     * To be called when the ContentView is shown.
     */
    void onShow();

    /**
     * @return The ID of the renderer process that backs this tab or
     *         {@link #INVALID_RENDER_PROCESS_PID} if there is none.
     */
    int getCurrentRenderProcessId();

    /**
     * To be called when the ContentView is hidden.
     */
    void onHide();

    /**
     * Whether or not the associated ContentView is currently attached to a window.
     */
    boolean isAttachedToWindow();

    /**
     * @see View#onAttachedToWindow()
     */
    void onAttachedToWindow();

    /**
     * @see View#onDetachedFromWindow()
     */
    void onDetachedFromWindow();

    /**
     * @see View#onConfigurationChanged(Configuration)
     */
    void onConfigurationChanged(Configuration newConfig);

    /**
     * @see View#onGenericMotionEvent(MotionEvent)
     */
    boolean onGenericMotionEvent(MotionEvent event);

    /**
     * @see View#onKeyUp(int, KeyEvent)
     */
    boolean onKeyUp(int keyCode, KeyEvent event);

    /**
     * @see View#dispatchKeyEvent(KeyEvent)
     */
    boolean dispatchKeyEvent(KeyEvent event);

    /**
     * @see View#onWindowFocusChanged(boolean)
     */
    void onWindowFocusChanged(boolean hasWindowFocus);

    /**
     * @see View#scrollTo(int, int)
     */
    void scrollTo(float xPix, float yPix);

    /**
     * Update the text selection UI depending on the focus of the page. This will hide the selection
     * handles and selection popups if focus is lost.
     * TODO(mdjones): This was added as a temporary measure to hide text UI while Reader Mode or
     * Contextual Search are showing. This should be removed in favor of proper focusing of the
     * panel's ContentViewCore (which is currently not being added to the view hierarchy).
     * @param focused If the ContentViewCore currently has focus.
     */
    void updateTextSelectionUI(boolean focused);

    /**
     * When the activity pauses, the content should lose focus.
     * TODO(mthiesse): See crbug.com/686232 for context. Desktop platforms use keyboard focus to
     * trigger blur/focus, and the equivalent to this on Android is Window focus. However, we don't
     * use Window focus because of the complexity around popups stealing Window focus.
     */
    void onPause();

    /**
     * When the activity resumes, the View#onFocusChanged may not be called, so we should restore
     * the View focus state.
     */
    void onResume();

    /**
     * Called when keyboard/IME focus has changed.
     * @param gainFocus {@code true} if the focus is gained, otherwise {@code false}.
     * @param hideKeyboardOnBlur {@code true} if we should hide soft keyboard when losing focus.
     */
    void onFocusChanged(boolean gainFocus, boolean hideKeyboardOnBlur);

    /**
     * Sets the current amount to offset incoming touch events by (including MotionEvent and
     * DragEvent). This is used to handle content moving and not lining up properly with the
     * android input system.
     * @param dx The X offset in pixels to shift touch events.
     * @param dy The Y offset in pixels to shift touch events.
     */
    void setCurrentTouchEventOffsets(float dx, float dy);

    /**
     * @see View#scrollBy(int, int)
     * Currently the ContentView scrolling happens in the native side. In
     * the Java view system, it is always pinned at (0, 0). scrollBy() and scrollTo()
     * are overridden, so that View's mScrollX and mScrollY will be unchanged at
     * (0, 0). This is critical for drawing ContentView correctly.
     */
    void scrollBy(float dxPix, float dyPix);

    /**
     * @see View#computeHorizontalScrollOffset()
     */
    int computeHorizontalScrollOffset();

    /**
     * @see View#computeVerticalScrollOffset()
     */
    int computeVerticalScrollOffset();

    /**
     * @see View#computeHorizontalScrollRange()
     */
    int computeHorizontalScrollRange();

    /**
     * @see View#computeVerticalScrollExtent()
     */
    int computeVerticalScrollRange();

    /**
     * @see View#computeHorizontalScrollExtent()
     */
    int computeHorizontalScrollExtent();

    /**
     * @see View#computeVerticalScrollExtent()
     */
    int computeVerticalScrollExtent();

    /**
     * @see View#awakenScrollBars(int, boolean)
     */
    boolean awakenScrollBars(int startDelay, boolean invalidate);

    /**
     * Enable or disable multi-touch zoom support.
     * @param supportsMultiTouchZoom {@code true} if the feature is enabled.
     */
    void updateMultiTouchZoomSupport(boolean supportsMultiTouchZoom);

    /**
     * Enable or disable double tap support.
     * @param supportsDoubleTap {@code true} if the feature is enabled.
     */
    void updateDoubleTapSupport(boolean supportsDoubleTap);

    /**
     * Notifies that items were selected in the currently showing select popup.
     * @param indices Array of indices of the selected items.
     */
    void selectPopupMenuItems(int[] indices);

    /**
     * Ensure the selection is preserved the next time the view loses focus.
     */
    void preserveSelectionOnNextLossOfFocus();

    /**
     * Determines whether or not this ContentViewCore can handle this accessibility action.
     * @param action The action to perform.
     * @return Whether or not this action is supported.
     */
    boolean supportsAccessibilityAction(int action);

    /**
     * Attempts to perform an accessibility action on the web content.  If the accessibility action
     * cannot be processed, it returns {@code null}, allowing the caller to know to call the
     * super {@link View#performAccessibilityAction(int, Bundle)} method and use that return value.
     * Otherwise the return value from this method should be used.
     * @param action The action to perform.
     * @param arguments Optional action arguments.
     * @return Whether the action was performed or {@code null} if the call should be delegated to
     *         the super {@link View} class.
     */
    boolean performAccessibilityAction(int action, Bundle arguments);

    /**
     * Get the WebContentsAccessibility, used for native accessibility
     * (not script injection). This will return null when system accessibility
     * is not enabled.
     * @return This view's WebContentsAccessibility.
     */
    WebContentsAccessibility getWebContentsAccessibility();

    /**
     * If native accessibility is enabled and no other views are temporarily
     * obscuring this one, returns an AccessibilityNodeProvider that
     * implements native accessibility for this view. Returns null otherwise.
     * Lazily initializes native accessibility here if it's allowed.
     * @return The AccessibilityNodeProvider, if available, or null otherwise.
     */
    AccessibilityNodeProvider getAccessibilityNodeProvider();

    @TargetApi(Build.VERSION_CODES.M)
    void onProvideVirtualStructure(ViewStructure structure, boolean ignoreScrollOffset);

    /**
     * Set whether or not the web contents are obscured by another view.
     * If true, we won't return an accessibility node provider or respond
     * to touch exploration events.
     */
    void setObscuredByAnotherView(boolean isObscured);

    /**
     * Returns true if accessibility is on and touch exploration is enabled.
     */
    boolean isTouchExplorationEnabled();

    /**
     * Turns browser accessibility on or off.
     * If |state| is |false|, this turns off both native and injected accessibility.
     * Otherwise, if accessibility script injection is enabled, this will enable the injected
     * accessibility scripts. Native accessibility is enabled on demand.
     */
    void setAccessibilityState(boolean state);

    /**
     * Sets whether or not we should set accessibility focus on page load.
     * This only applies if an accessibility service like TalkBack is running.
     * This is desirable behavior for a browser window, but not for an embedded
     * WebView.
     */
    void setShouldSetAccessibilityFocusOnPageLoad(boolean on);

    /**
     * @return Whether the current page seems to be mobile-optimized. This hint is based upon
     *         rendered frames and may return different values when called multiple times for the
     *         same page (particularly during page load).
     */
    boolean getIsMobileOptimizedHint();

    /**
     * Set the background color mode.
     * @param opaque {@code true} if the background should be set to default opaque mode.
     */
    void setBackgroundOpaque(boolean opaque);

    /**
     * Set whether the ContentViewCore requires the WebContents to be fullscreen in order to lock
     * the screen orientation.
     */
    void setFullscreenRequiredForOrientationLock(boolean value);

    // Test-only methods

    /**
     * @return The TextSuggestionHost that handles displaying the text suggestion menu.
     */
    @VisibleForTesting
    TextSuggestionHost getTextSuggestionHostForTesting();

    @VisibleForTesting
    void setTextSuggestionHostForTesting(TextSuggestionHost textSuggestionHost);

    @VisibleForTesting
    void setPopupZoomerForTest(PopupZoomer popupZoomer);

    /**
     * @return The amount of the top controls height if controls are in the state
     *    of shrinking Blink's view size, otherwise 0.
     */
    @VisibleForTesting
    int getTopControlsShrinkBlinkHeightForTesting();

    @VisibleForTesting
    void sendDoubleTapForTest(long timeMs, int x, int y);

    /**
     * @return The visible select popup being shown.
     */
    @VisibleForTesting
    SelectPopup getSelectPopupForTest();
}
