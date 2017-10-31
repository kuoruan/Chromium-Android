// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.webcontents;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.Pair;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.AppWebMessagePort;
import org.chromium.content.browser.MediaSessionImpl;
import org.chromium.content.browser.RenderCoordinates;
import org.chromium.content.browser.framehost.RenderFrameHostDelegate;
import org.chromium.content.browser.framehost.RenderFrameHostImpl;
import org.chromium.content_public.browser.AccessibilitySnapshotCallback;
import org.chromium.content_public.browser.AccessibilitySnapshotNode;
import org.chromium.content_public.browser.ChildProcessImportance;
import org.chromium.content_public.browser.ContentBitmapCallback;
import org.chromium.content_public.browser.ImageDownloadCallback;
import org.chromium.content_public.browser.JavaScriptCallback;
import org.chromium.content_public.browser.MessagePort;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.content_public.browser.SmartClipCallback;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsInternals;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.OverscrollRefreshHandler;
import org.chromium.ui.base.EventForwarder;
import org.chromium.ui.base.WindowAndroid;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The WebContentsImpl Java wrapper to allow communicating with the native WebContentsImpl
 * object.
 */
@JNINamespace("content")
// TODO(tedchoc): Remove the package restriction once this class moves to a non-public content
//               package whose visibility will be enforced via DEPS.
/* package */ class WebContentsImpl implements WebContents, RenderFrameHostDelegate {
    private static final String PARCEL_VERSION_KEY = "version";
    private static final String PARCEL_WEBCONTENTS_KEY = "webcontents";
    private static final String PARCEL_PROCESS_GUARD_KEY = "processguard";

    private static final long PARCELABLE_VERSION_ID = 0;
    // Non-final for testing purposes, so resetting of the UUID can happen.
    private static UUID sParcelableUUID = UUID.randomUUID();

    /**
     * Used to reset the internal tracking for whether or not a serialized {@link WebContents}
     * was created in this process or not.
     */
    @VisibleForTesting
    public static void invalidateSerializedWebContentsForTesting() {
        sParcelableUUID = UUID.randomUUID();
    }

    /**
     * A {@link android.os.Parcelable.Creator} instance that is used to build
     * {@link WebContentsImpl} objects from a {@link Parcel}.
     */
    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("ParcelClassLoader")
    public static final Parcelable.Creator<WebContents> CREATOR =
            new Parcelable.Creator<WebContents>() {
                @Override
                public WebContents createFromParcel(Parcel source) {
                    Bundle bundle = source.readBundle();

                    // Check the version.
                    if (bundle.getLong(PARCEL_VERSION_KEY, -1) != 0) return null;

                    // Check that we're in the same process.
                    ParcelUuid parcelUuid = bundle.getParcelable(PARCEL_PROCESS_GUARD_KEY);
                    if (sParcelableUUID.compareTo(parcelUuid.getUuid()) != 0) return null;

                    // Attempt to retrieve the WebContents object from the native pointer.
                    return nativeFromNativePtr(bundle.getLong(PARCEL_WEBCONTENTS_KEY));
                }

                @Override
                public WebContents[] newArray(int size) {
                    return new WebContents[size];
                }
            };

    // Note this list may be incomplete. Frames that never had to initialize java side would
    // not have an entry here. This is here mainly to keep the java RenderFrameHosts alive, since
    // native side generally cannot safely hold strong references to them.
    private final List<RenderFrameHostImpl> mFrames = new ArrayList<>();

    private long mNativeWebContentsAndroid;
    private NavigationController mNavigationController;

    // Lazily created proxy observer for handling all Java-based WebContentsObservers.
    private WebContentsObserverProxy mObserverProxy;

    // The media session for this WebContents. It is constructed by the native MediaSession and has
    // the same life time as native MediaSession.
    private MediaSessionImpl mMediaSession;

    class SmartClipCallbackImpl implements SmartClipCallback {
        public SmartClipCallbackImpl(final Handler smartClipHandler) {
            mHandler = smartClipHandler;
        }
        public void storeRequestRect(Rect rect) {
            mRect = rect;
        }

        @Override
        public void onSmartClipDataExtracted(String text, String html) {
            Bundle bundle = new Bundle();
            bundle.putString("url", getVisibleUrl());
            bundle.putString("title", getTitle());
            bundle.putString("text", text);
            bundle.putString("html", html);
            bundle.putParcelable("rect", mRect);

            Message msg = Message.obtain(mHandler, 0);
            msg.setData(bundle);
            msg.sendToTarget();
        }

        Rect mRect;
        final Handler mHandler;
    }
    private SmartClipCallbackImpl mSmartClipCallback;

    private EventForwarder mEventForwarder;

    private static class DefaultInternalsHolder implements InternalsHolder {
        private WebContentsInternals mInternals;

        @Override
        public void set(WebContentsInternals internals) {
            mInternals = internals;
        }

        @Override
        public WebContentsInternals get() {
            return mInternals;
        }
    }

    private InternalsHolder mInternalsHolder;

    private static class WebContentsInternalsImpl implements WebContentsInternals {
        public HashSet<Object> retainedObjects;
        public HashMap<String, Pair<Object, Class>> injectedObjects;
    }

    private WebContentsImpl(
            long nativeWebContentsAndroid, NavigationController navigationController) {
        mNativeWebContentsAndroid = nativeWebContentsAndroid;
        mNavigationController = navigationController;

        // Initialize |mInternalsHolder| with a default one that keeps all the internals
        // inside WebContentsImpl. It holds a strong reference until an embedder invokes
        // |setInternalsHolder| to get the internals handed over to it.
        WebContentsInternalsImpl internals = new WebContentsInternalsImpl();
        internals.retainedObjects = new HashSet<Object>();
        internals.injectedObjects = new HashMap<String, Pair<Object, Class>>();
        nativeCreateJavaBridgeDispatcherHost(mNativeWebContentsAndroid, internals.retainedObjects);
        mInternalsHolder = new DefaultInternalsHolder();
        mInternalsHolder.set(internals);
    }

    @CalledByNative
    private static WebContentsImpl create(
            long nativeWebContentsAndroid, NavigationController navigationController) {
        return new WebContentsImpl(nativeWebContentsAndroid, navigationController);
    }

    @CalledByNative
    private void clearNativePtr() {
        mNativeWebContentsAndroid = 0;
        mNavigationController = null;
        if (mObserverProxy != null) {
            mObserverProxy.destroy();
            mObserverProxy = null;
        }
    }

    @Override
    public void setInternalsHolder(InternalsHolder internalsHolder) {
        // Ensure |setInternalsHolder()| is be called at most once.
        assert mInternalsHolder instanceof DefaultInternalsHolder;
        internalsHolder.set(mInternalsHolder.get());
        mInternalsHolder = internalsHolder;
    }

    // =================== RenderFrameHostDelegate overrides ===================
    @Override
    public void renderFrameCreated(RenderFrameHostImpl host) {
        assert !mFrames.contains(host);
        mFrames.add(host);
    }

    @Override
    public void renderFrameDeleted(RenderFrameHostImpl host) {
        assert mFrames.contains(host);
        mFrames.remove(host);
    }
    // ================= end RenderFrameHostDelegate overrides =================

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // This is wrapped in a Bundle so that failed deserialization attempts don't corrupt the
        // overall Parcel.  If we failed a UUID or Version check and didn't read the rest of the
        // fields it would corrupt the serialized stream.
        Bundle data = new Bundle();
        data.putLong(PARCEL_VERSION_KEY, PARCELABLE_VERSION_ID);
        data.putParcelable(PARCEL_PROCESS_GUARD_KEY, new ParcelUuid(sParcelableUUID));
        data.putLong(PARCEL_WEBCONTENTS_KEY, mNativeWebContentsAndroid);
        dest.writeBundle(data);
    }

    @CalledByNative
    private long getNativePointer() {
        return mNativeWebContentsAndroid;
    }

    @Override
    public WindowAndroid getTopLevelNativeWindow() {
        return nativeGetTopLevelNativeWindow(mNativeWebContentsAndroid);
    }

    @Override
    public void destroy() {
        if (!ThreadUtils.runningOnUiThread()) {
            throw new IllegalStateException("Attempting to destroy WebContents on non-UI thread");
        }
        if (mNativeWebContentsAndroid != 0) nativeDestroyWebContents(mNativeWebContentsAndroid);
    }

    @Override
    public boolean isDestroyed() {
        return mNativeWebContentsAndroid == 0;
    }

    @Override
    public NavigationController getNavigationController() {
        return mNavigationController;
    }

    @Override
    public RenderFrameHost getMainFrame() {
        return nativeGetMainFrame(mNativeWebContentsAndroid);
    }

    @Override
    public String getTitle() {
        return nativeGetTitle(mNativeWebContentsAndroid);
    }

    @Override
    public String getVisibleUrl() {
        return nativeGetVisibleURL(mNativeWebContentsAndroid);
    }

    @Override
    public String getEncoding() {
        return nativeGetEncoding(mNativeWebContentsAndroid);
    }

    @Override
    public boolean isLoading() {
        return nativeIsLoading(mNativeWebContentsAndroid);
    }

    @Override
    public boolean isLoadingToDifferentDocument() {
        return nativeIsLoadingToDifferentDocument(mNativeWebContentsAndroid);
    }

    @Override
    public void stop() {
        nativeStop(mNativeWebContentsAndroid);
    }

    @Override
    public void cut() {
        nativeCut(mNativeWebContentsAndroid);
    }

    @Override
    public void copy() {
        nativeCopy(mNativeWebContentsAndroid);
    }

    @Override
    public void paste() {
        nativePaste(mNativeWebContentsAndroid);
    }

    @Override
    public void pasteAsPlainText() {
        nativePasteAsPlainText(mNativeWebContentsAndroid);
    }

    @Override
    public void replace(String word) {
        nativeReplace(mNativeWebContentsAndroid, word);
    }

    @Override
    public void selectAll() {
        nativeSelectAll(mNativeWebContentsAndroid);
    }

    @Override
    public void collapseSelection() {
        // collapseSelection may get triggered when certain selection-related widgets
        // are destroyed. As the timing for such destruction is unpredictable,
        // safely guard against this case.
        if (isDestroyed()) return;
        nativeCollapseSelection(mNativeWebContentsAndroid);
    }

    @Override
    public void onHide() {
        nativeOnHide(mNativeWebContentsAndroid);
    }

    @Override
    public void onShow() {
        nativeOnShow(mNativeWebContentsAndroid);
    }

    @Override
    public void setImportance(@ChildProcessImportance int importance) {
        nativeSetImportance(mNativeWebContentsAndroid, importance);
    }

    @Override
    public void suspendAllMediaPlayers() {
        nativeSuspendAllMediaPlayers(mNativeWebContentsAndroid);
    }

    @Override
    public void setAudioMuted(boolean mute) {
        nativeSetAudioMuted(mNativeWebContentsAndroid, mute);
    }

    @Override
    public int getBackgroundColor() {
        return nativeGetBackgroundColor(mNativeWebContentsAndroid);
    }

    @Override
    public void showInterstitialPage(
            String url, long interstitialPageDelegateAndroid) {
        nativeShowInterstitialPage(mNativeWebContentsAndroid, url, interstitialPageDelegateAndroid);
    }

    @Override
    public boolean isShowingInterstitialPage() {
        return nativeIsShowingInterstitialPage(mNativeWebContentsAndroid);
    }

    @Override
    public boolean focusLocationBarByDefault() {
        return nativeFocusLocationBarByDefault(mNativeWebContentsAndroid);
    }

    @Override
    public boolean isReady() {
        return nativeIsRenderWidgetHostViewReady(mNativeWebContentsAndroid);
    }

    @Override
    public void exitFullscreen() {
        nativeExitFullscreen(mNativeWebContentsAndroid);
    }

    @Override
    public void updateBrowserControlsState(
            boolean enableHiding, boolean enableShowing, boolean animate) {
        nativeUpdateBrowserControlsState(
                mNativeWebContentsAndroid, enableHiding, enableShowing, animate);
    }

    @Override
    public void scrollFocusedEditableNodeIntoView() {
        // The native side keeps track of whether the zoom and scroll actually occurred. It is
        // more efficient to do it this way and sometimes fire an unnecessary message rather
        // than synchronize with the renderer and always have an additional message.
        nativeScrollFocusedEditableNodeIntoView(mNativeWebContentsAndroid);
    }

    @Override
    public void selectWordAroundCaret() {
        nativeSelectWordAroundCaret(mNativeWebContentsAndroid);
    }

    @Override
    public void adjustSelectionByCharacterOffset(
            int startAdjust, int endAdjust, boolean showSelectionMenu) {
        nativeAdjustSelectionByCharacterOffset(
                mNativeWebContentsAndroid, startAdjust, endAdjust, showSelectionMenu);
    }

    @Override
    public String getLastCommittedUrl() {
        return nativeGetLastCommittedURL(mNativeWebContentsAndroid);
    }

    @Override
    public boolean isIncognito() {
        return nativeIsIncognito(mNativeWebContentsAndroid);
    }

    @Override
    public void resumeLoadingCreatedWebContents() {
        nativeResumeLoadingCreatedWebContents(mNativeWebContentsAndroid);
    }

    @Override
    public void evaluateJavaScript(String script, JavaScriptCallback callback) {
        if (isDestroyed() || script == null) return;
        nativeEvaluateJavaScript(mNativeWebContentsAndroid, script, callback);
    }

    @Override
    @VisibleForTesting
    public void evaluateJavaScriptForTests(String script, JavaScriptCallback callback) {
        if (script == null) return;
        nativeEvaluateJavaScriptForTests(mNativeWebContentsAndroid, script, callback);
    }

    @Override
    public void addMessageToDevToolsConsole(int level, String message) {
        nativeAddMessageToDevToolsConsole(mNativeWebContentsAndroid, level, message);
    }

    @Override
    public void postMessageToFrame(String frameName, String message,
            String sourceOrigin, String targetOrigin, MessagePort[] ports) {
        if (ports != null) {
            for (MessagePort port : ports) {
                if (port.isClosed() || port.isTransferred()) {
                    throw new IllegalStateException("Port is already closed or transferred");
                }
                if (port.isStarted()) {
                    throw new IllegalStateException("Port is already started");
                }
            }
        }
        // Treat "*" as a wildcard. Internally, a wildcard is a empty string.
        if (targetOrigin.equals("*")) {
            targetOrigin = "";
        }
        nativePostMessageToFrame(
                mNativeWebContentsAndroid, frameName, message, sourceOrigin, targetOrigin, ports);
    }

    @Override
    public AppWebMessagePort[] createMessageChannel()
            throws IllegalStateException {
        return AppWebMessagePort.createPair();
    }

    @Override
    public boolean hasAccessedInitialDocument() {
        return nativeHasAccessedInitialDocument(mNativeWebContentsAndroid);
    }

    @CalledByNative
    private static void onEvaluateJavaScriptResult(
            String jsonResult, JavaScriptCallback callback) {
        callback.handleJavaScriptResult(jsonResult);
    }

    @Override
    public int getThemeColor() {
        return nativeGetThemeColor(mNativeWebContentsAndroid);
    }

    @Override
    public void requestSmartClipExtract(
            int x, int y, int width, int height, RenderCoordinates coordinateSpace) {
        if (mSmartClipCallback == null) return;
        mSmartClipCallback.storeRequestRect(new Rect(x, y, x + width, y + height));
        float dpi = coordinateSpace.getDeviceScaleFactor();
        y -= coordinateSpace.getContentOffsetYPix();
        nativeRequestSmartClipExtract(mNativeWebContentsAndroid, mSmartClipCallback,
                (int) (x / dpi), (int) (y / dpi), (int) (width / dpi), (int) (height / dpi));
    }

    @Override
    public void setSmartClipResultHandler(final Handler smartClipHandler) {
        if (smartClipHandler == null) {
            mSmartClipCallback = null;
            return;
        }
        mSmartClipCallback = new SmartClipCallbackImpl(smartClipHandler);
    }

    @CalledByNative
    private static void onSmartClipDataExtracted(
            String text, String html, SmartClipCallback callback) {
        callback.onSmartClipDataExtracted(text, html);
    }

    @Override
    public void requestAccessibilitySnapshot(AccessibilitySnapshotCallback callback) {
        nativeRequestAccessibilitySnapshot(mNativeWebContentsAndroid, callback);
    }

    @Override
    @VisibleForTesting
    public void simulateRendererKilledForTesting(boolean wasOomProtected) {
        if (mObserverProxy != null) {
            mObserverProxy.renderProcessGone(wasOomProtected);
        }
    }

    // root node can be null if parsing fails.
    @CalledByNative
    private static void onAccessibilitySnapshot(AccessibilitySnapshotNode root,
            AccessibilitySnapshotCallback callback) {
        callback.onAccessibilitySnapshot(root);
    }

    @CalledByNative
    private static void addAccessibilityNodeAsChild(AccessibilitySnapshotNode parent,
            AccessibilitySnapshotNode child) {
        parent.addChild(child);
    }

    @CalledByNative
    private static AccessibilitySnapshotNode createAccessibilitySnapshotNode(int parentRelativeLeft,
            int parentRelativeTop, int width, int height, boolean isRootNode, String text,
            int color, int bgcolor, float size, boolean bold, boolean italic, boolean underline,
            boolean lineThrough, String className) {
        AccessibilitySnapshotNode node = new AccessibilitySnapshotNode(text, className);

        // if size is smaller than 0, then style information does not exist.
        if (size >= 0.0) {
            node.setStyle(color, bgcolor, size, bold, italic, underline, lineThrough);
        }
        node.setLocationInfo(parentRelativeLeft, parentRelativeTop, width, height, isRootNode);
        return node;
    }

    @CalledByNative
    private static void setAccessibilitySnapshotSelection(
            AccessibilitySnapshotNode node, int start, int end) {
        node.setSelection(start, end);
    }

    @Override
    public EventForwarder getEventForwarder() {
        assert mNativeWebContentsAndroid != 0;
        if (mEventForwarder == null) {
            mEventForwarder = nativeGetOrCreateEventForwarder(mNativeWebContentsAndroid);
        }
        return mEventForwarder;
    }

    @Override
    public void addObserver(WebContentsObserver observer) {
        assert mNativeWebContentsAndroid != 0;
        if (mObserverProxy == null) mObserverProxy = new WebContentsObserverProxy(this);
        mObserverProxy.addObserver(observer);
    }

    @Override
    public void removeObserver(WebContentsObserver observer) {
        if (mObserverProxy == null) return;
        mObserverProxy.removeObserver(observer);
    }

    @Override
    public void setOverscrollRefreshHandler(OverscrollRefreshHandler handler) {
        nativeSetOverscrollRefreshHandler(mNativeWebContentsAndroid, handler);
    }

    @Override
    public void getContentBitmapAsync(int width, int height, ContentBitmapCallback callback) {
        nativeGetContentBitmap(mNativeWebContentsAndroid, width, height, callback);
    }

    @CalledByNative
    private void onGetContentBitmapFinished(ContentBitmapCallback callback, Bitmap bitmap,
            int response) {
        callback.onFinishGetBitmap(bitmap, response);
    }

    @Override
    public void reloadLoFiImages() {
        nativeReloadLoFiImages(mNativeWebContentsAndroid);
    }

    @Override
    public int downloadImage(String url, boolean isFavicon, int maxBitmapSize,
            boolean bypassCache, ImageDownloadCallback callback) {
        return nativeDownloadImage(mNativeWebContentsAndroid,
                url, isFavicon, maxBitmapSize, bypassCache, callback);
    }

    @CalledByNative
    private void onDownloadImageFinished(ImageDownloadCallback callback, int id, int httpStatusCode,
            String imageUrl, List<Bitmap> bitmaps, List<Rect> sizes) {
        callback.onFinishDownloadImage(id, httpStatusCode, imageUrl, bitmaps, sizes);
    }

    @Override
    public void dismissTextHandles() {
        nativeDismissTextHandles(mNativeWebContentsAndroid);
    }

    @Override
    public void showContextMenuAtTouchHandle(int x, int y) {
        nativeShowContextMenuAtTouchHandle(mNativeWebContentsAndroid, x, y);
    }

    @Override
    public void setHasPersistentVideo(boolean value) {
        nativeSetHasPersistentVideo(mNativeWebContentsAndroid, value);
    }

    @Override
    public boolean hasActiveEffectivelyFullscreenVideo() {
        return nativeHasActiveEffectivelyFullscreenVideo(mNativeWebContentsAndroid);
    }

    @Override
    public @Nullable Rect getFullscreenVideoSize() {
        return nativeGetFullscreenVideoSize(mNativeWebContentsAndroid);
    }

    @Override
    public void setSize(int width, int height) {
        nativeSetSize(mNativeWebContentsAndroid, width, height);
    }

    @CalledByNative
    private final void setMediaSession(MediaSessionImpl mediaSession) {
        mMediaSession = mediaSession;
    }

    @CalledByNative
    private static List<Bitmap> createBitmapList() {
        return new ArrayList<Bitmap>();
    }

    @CalledByNative
    private static void addToBitmapList(List<Bitmap> bitmaps, Bitmap bitmap) {
        bitmaps.add(bitmap);
    }

    @CalledByNative
    private static List<Rect> createSizeList() {
        return new ArrayList<Rect>();
    }

    @CalledByNative
    private static void createSizeAndAddToList(List<Rect> sizes, int width, int height) {
        sizes.add(new Rect(0, 0, width, height));
    }

    @CalledByNative
    private static Rect createSize(int width, int height) {
        return new Rect(0, 0, width, height);
    }

    @Override
    public Map<String, Pair<Object, Class>> getJavascriptInterfaces() {
        WebContentsInternals internals = mInternalsHolder.get();
        if (internals == null) return null;
        return ((WebContentsInternalsImpl) internals).injectedObjects;
    }

    @Override
    public void setAllowJavascriptInterfacesInspection(boolean allow) {
        nativeSetAllowJavascriptInterfacesInspection(mNativeWebContentsAndroid, allow);
    }

    @Override
    public void addPossiblyUnsafeJavascriptInterface(
            Object object, String name, Class<? extends Annotation> requiredAnnotation) {
        if (mNativeWebContentsAndroid != 0 && object != null) {
            Map<String, Pair<Object, Class>> jsInterface = getJavascriptInterfaces();
            // The interface map is available as long as the callsite is alive, which should
            // hold true since it is the callsite that is invoking this API.
            assert jsInterface != null;
            jsInterface.put(name, new Pair<Object, Class>(object, requiredAnnotation));
            nativeAddJavascriptInterface(
                    mNativeWebContentsAndroid, object, name, requiredAnnotation);
        }
    }

    @Override
    public void removeJavascriptInterface(String name) {
        Map<String, Pair<Object, Class>> jsInterface = getJavascriptInterfaces();
        assert jsInterface != null;
        jsInterface.remove(name);
        if (mNativeWebContentsAndroid != 0) {
            nativeRemoveJavascriptInterface(mNativeWebContentsAndroid, name);
        }
    }

    // This is static to avoid exposing a public destroy method on the native side of this class.
    private static native void nativeDestroyWebContents(long webContentsAndroidPtr);

    private static native WebContents nativeFromNativePtr(long webContentsAndroidPtr);

    private native WindowAndroid nativeGetTopLevelNativeWindow(long nativeWebContentsAndroid);
    private native void nativeCreateJavaBridgeDispatcherHost(
            long nativeWebContentsAndroid, Object retainedJavascriptObjects);
    private native RenderFrameHost nativeGetMainFrame(long nativeWebContentsAndroid);
    private native String nativeGetTitle(long nativeWebContentsAndroid);
    private native String nativeGetVisibleURL(long nativeWebContentsAndroid);
    private native String nativeGetEncoding(long nativeWebContentsAndroid);
    private native boolean nativeIsLoading(long nativeWebContentsAndroid);
    private native boolean nativeIsLoadingToDifferentDocument(long nativeWebContentsAndroid);
    private native void nativeStop(long nativeWebContentsAndroid);
    private native void nativeCut(long nativeWebContentsAndroid);
    private native void nativeCopy(long nativeWebContentsAndroid);
    private native void nativePaste(long nativeWebContentsAndroid);
    private native void nativePasteAsPlainText(long nativeWebContentsAndroid);
    private native void nativeReplace(long nativeWebContentsAndroid, String word);
    private native void nativeSelectAll(long nativeWebContentsAndroid);
    private native void nativeCollapseSelection(long nativeWebContentsAndroid);
    private native void nativeOnHide(long nativeWebContentsAndroid);
    private native void nativeOnShow(long nativeWebContentsAndroid);
    private native void nativeSetImportance(long nativeWebContentsAndroid, int importance);
    private native void nativeSuspendAllMediaPlayers(long nativeWebContentsAndroid);
    private native void nativeSetAudioMuted(long nativeWebContentsAndroid, boolean mute);
    private native int nativeGetBackgroundColor(long nativeWebContentsAndroid);
    private native void nativeShowInterstitialPage(long nativeWebContentsAndroid,
            String url, long nativeInterstitialPageDelegateAndroid);
    private native boolean nativeIsShowingInterstitialPage(long nativeWebContentsAndroid);
    private native boolean nativeFocusLocationBarByDefault(long nativeWebContentsAndroid);
    private native boolean nativeIsRenderWidgetHostViewReady(long nativeWebContentsAndroid);
    private native void nativeExitFullscreen(long nativeWebContentsAndroid);
    private native void nativeUpdateBrowserControlsState(long nativeWebContentsAndroid,
            boolean enableHiding, boolean enableShowing, boolean animate);
    private native void nativeScrollFocusedEditableNodeIntoView(long nativeWebContentsAndroid);
    private native void nativeSelectWordAroundCaret(long nativeWebContentsAndroid);
    private native void nativeAdjustSelectionByCharacterOffset(long nativeWebContentsAndroid,
            int startAdjust, int endAdjust, boolean showSelectionMenu);
    private native String nativeGetLastCommittedURL(long nativeWebContentsAndroid);
    private native boolean nativeIsIncognito(long nativeWebContentsAndroid);
    private native void nativeResumeLoadingCreatedWebContents(long nativeWebContentsAndroid);
    private native void nativeEvaluateJavaScript(long nativeWebContentsAndroid,
            String script, JavaScriptCallback callback);
    private native void nativeEvaluateJavaScriptForTests(long nativeWebContentsAndroid,
            String script, JavaScriptCallback callback);
    private native void nativeAddMessageToDevToolsConsole(
            long nativeWebContentsAndroid, int level, String message);
    private native void nativePostMessageToFrame(long nativeWebContentsAndroid, String frameName,
            String message, String sourceOrigin, String targetOrigin, MessagePort[] ports);
    private native boolean nativeHasAccessedInitialDocument(
            long nativeWebContentsAndroid);
    private native int nativeGetThemeColor(long nativeWebContentsAndroid);
    private native void nativeRequestSmartClipExtract(long nativeWebContentsAndroid,
            SmartClipCallback callback, int x, int y, int width, int height);
    private native void nativeRequestAccessibilitySnapshot(
            long nativeWebContentsAndroid, AccessibilitySnapshotCallback callback);
    private native void nativeSetOverscrollRefreshHandler(
            long nativeWebContentsAndroid, OverscrollRefreshHandler nativeOverscrollRefreshHandler);
    private native void nativeGetContentBitmap(
            long nativeWebContentsAndroid, int width, int height, ContentBitmapCallback callback);
    private native void nativeReloadLoFiImages(long nativeWebContentsAndroid);
    private native int nativeDownloadImage(long nativeWebContentsAndroid,
            String url, boolean isFavicon, int maxBitmapSize,
            boolean bypassCache, ImageDownloadCallback callback);
    private native void nativeDismissTextHandles(long nativeWebContentsAndroid);
    private native void nativeShowContextMenuAtTouchHandle(
            long nativeWebContentsAndroid, int x, int y);
    private native void nativeSetHasPersistentVideo(long nativeWebContentsAndroid, boolean value);
    private native boolean nativeHasActiveEffectivelyFullscreenVideo(long nativeWebContentsAndroid);
    private native Rect nativeGetFullscreenVideoSize(long nativeWebContentsAndroid);
    private native void nativeSetSize(long nativeWebContentsAndroid, int width, int height);
    private native EventForwarder nativeGetOrCreateEventForwarder(long nativeWebContentsAndroid);
    private native void nativeSetAllowJavascriptInterfacesInspection(
            long nativeWebContentsAndroid, boolean allow);
    private native void nativeAddJavascriptInterface(
            long nativeWebContentsAndroid, Object object, String name, Class requiredAnnotation);
    private native void nativeRemoveJavascriptInterface(long nativeWebContentsAndroid, String name);
}
