// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.ObserverList;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.webcontents.WebContentsImpl;
import org.chromium.content.browser.webcontents.WebContentsUserData;
import org.chromium.content.browser.webcontents.WebContentsUserData.UserDataFactory;
import org.chromium.content_public.browser.GestureListenerManager;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.WebContents;

/**
 * Implementation of the interface {@link GestureListenerManager}. Manages
 * the {@link GestureStateListener} instances, and invokes them upon
 * notification of various events.
 * Instantiated object is held inside {@link WebContentsUserData} that is
 * managed by {@link WebContents}.
 */
@JNINamespace("content")
public class GestureListenerManagerImpl implements GestureListenerManager, WindowEventObserver {
    private static final class UserDataFactoryLazyHolder {
        private static final UserDataFactory<GestureListenerManagerImpl> INSTANCE =
                GestureListenerManagerImpl::new;
    }

    private final WebContentsImpl mWebContents;
    private final ObserverList<GestureStateListener> mListeners;
    private final RewindableIterator<GestureStateListener> mIterator;

    // The outstanding fling start events that hasn't got fling end yet. It may be > 1 because
    // onFlingEnd() is called asynchronously.
    private int mPotentiallyActiveFlingCount;

    private long mNativeGestureListenerManager;

    /**
     * @param webContents {@link WebContents} object.
     * @return {@link GestureListenerManager} object used for the give WebContents.
     *         Creates one if not present.
     */
    public static GestureListenerManagerImpl fromWebContents(WebContents webContents) {
        return WebContentsUserData.fromWebContents(
                webContents, GestureListenerManagerImpl.class, UserDataFactoryLazyHolder.INSTANCE);
    }

    public GestureListenerManagerImpl(WebContents webContents) {
        mWebContents = (WebContentsImpl) webContents;
        mListeners = new ObserverList<GestureStateListener>();
        mIterator = mListeners.rewindableIterator();
        mNativeGestureListenerManager = nativeInit(mWebContents);
    }

    /**
     * Reset the Java object in the native so this class stops receiving events.
     */
    public void reset() {
        if (mNativeGestureListenerManager != 0) nativeReset(mNativeGestureListenerManager);
    }

    @Override
    public void addListener(GestureStateListener listener) {
        mListeners.addObserver(listener);
    }

    @Override
    public void removeListener(GestureStateListener listener) {
        mListeners.removeObserver(listener);
    }

    /** Update all the listeners after touch down event occurred. */
    public void updateOnTouchDown() {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onTouchDown();
    }

    /** Checks if there's outstanding fling start events that hasn't got fling end yet. */
    public boolean hasPotentiallyActiveFling() {
        return mPotentiallyActiveFlingCount > 0;
    }

    // WindowEventObserver

    @Override
    public void onWindowFocusChanged(boolean gainFocus) {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onWindowFocusChanged(gainFocus);
        }
    }

    /**
     * Update all the listeners after vertical scroll offset/extent has changed.
     * @param offset New vertical scroll offset.
     * @param extent New vertical scroll extent, or viewport height.
     */
    public void updateOnScrollChanged(int offset, int extent) {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onScrollOffsetOrExtentChanged(offset, extent);
        }
    }

    /** Update all the listeners after scrolling end event occurred. */
    public void updateOnScrollEnd() {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onScrollEnded(verticalScrollOffset(), verticalScrollExtent());
        }
    }

    /**
     * Update all the listeners after min/max scale limit has changed.
     * @param minScale New minimum scale.
     * @param maxScale New maximum scale.
     */
    public void updateOnScaleLimitsChanged(float minScale, float maxScale) {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onScaleLimitsChanged(minScale, maxScale);
        }
    }

    /* Called when ongoing fling gesture needs to be reset. */
    public void resetFlingGesture() {
        if (mPotentiallyActiveFlingCount > 0) {
            onFlingEnd();
            mPotentiallyActiveFlingCount = 0;
        }
    }

    @CalledByNative
    private void onFlingEnd() {
        if (mPotentiallyActiveFlingCount > 0) mPotentiallyActiveFlingCount--;
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onFlingEndGesture(verticalScrollOffset(), verticalScrollExtent());
        }
    }

    @CalledByNative
    private void onFlingStartEventConsumed() {
        mPotentiallyActiveFlingCount++;
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onFlingStartGesture(verticalScrollOffset(), verticalScrollExtent());
        }
    }

    @CalledByNative
    private void onScrollBeginEventAck() {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onScrollStarted(verticalScrollOffset(), verticalScrollExtent());
        }
    }

    @CalledByNative
    private void onScrollEndEventAck() {
        updateOnScrollEnd();
    }

    @CalledByNative
    private void onScrollUpdateGestureConsumed() {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onScrollUpdateGestureConsumed();
        }
    }

    @CalledByNative
    private void onPinchBeginEventAck() {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onPinchStarted();
    }

    @CalledByNative
    private void onPinchEndEventAck() {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onPinchEnded();
    }

    @CalledByNative
    private void onSingleTapEventAck(boolean consumed) {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onSingleTap(consumed);
    }

    @CalledByNative
    private void onLongPressAck() {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onLongPress();
    }

    @CalledByNative
    private void onDestroy() {
        for (mIterator.rewind(); mIterator.hasNext();) mIterator.next().onDestroyed();
        mListeners.clear();
        mNativeGestureListenerManager = 0;
    }

    private int verticalScrollOffset() {
        return mWebContents.getRenderCoordinates().getScrollYPixInt();
    }

    private int verticalScrollExtent() {
        return mWebContents.getRenderCoordinates().getLastFrameViewportHeightPixInt();
    }

    private native long nativeInit(WebContentsImpl webContents);
    private native void nativeReset(long nativeGestureListenerManager);
}
