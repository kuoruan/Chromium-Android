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
public class GestureListenerManagerImpl implements GestureListenerManager {
    private static final class UserDataFactoryLazyHolder {
        private static final UserDataFactory<GestureListenerManagerImpl> INSTANCE =
                GestureListenerManagerImpl::new;
    }

    private final WebContentsImpl mWebContents;
    private final ObserverList<GestureStateListener> mListeners;
    private final RewindableIterator<GestureStateListener> mIterator;

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
        nativeInit(mWebContents);
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

    /**
     * Update all the listeners after window focus has changed.
     * @param hasFocus {@code true} if we're gaining focus.
     */
    public void updateOnWindowFocusChanged(boolean hasFocus) {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onWindowFocusChanged(hasFocus);
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

    /** Update all the listeners after fling end event occurred. */
    public void updateOnFlingEnd() {
        for (mIterator.rewind(); mIterator.hasNext();) {
            mIterator.next().onFlingEndGesture(verticalScrollOffset(), verticalScrollExtent());
        }
    }

    @CalledByNative
    private void onFlingStartEventConsumed() {
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
    }

    private int verticalScrollOffset() {
        return mWebContents.getRenderCoordinates().getScrollYPixInt();
    }

    private int verticalScrollExtent() {
        return mWebContents.getRenderCoordinates().getLastFrameViewportHeightPixInt();
    }

    private native void nativeInit(WebContentsImpl webContents);
}
