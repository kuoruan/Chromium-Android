// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.caf;

import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastSession;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.CastSessionUtil;
import org.chromium.chrome.browser.media.router.MediaSink;
import org.chromium.chrome.browser.media.router.MediaSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A wrapper for {@link CastSession}, extending its functionality for Chrome MediaRouter.
 *
 * Has the same lifecycle with CastSession.
 */
public class CastSessionController {
    private static final String TAG = "CafSessionCtrl";

    private CastSession mCastSession;
    private final CafBaseMediaRouteProvider mProvider;
    private MediaSink mSink;
    private MediaSource mSource;
    private List<String> mNamespaces;
    private final CastListener mCastListener;

    public CastSessionController(CafBaseMediaRouteProvider provider) {
        mProvider = provider;
        mCastListener = new CastListener();
    }

    public void requestSessionLaunch(CreateRouteRequestInfo request) {
        mSource = request.source;
        mSink = request.sink;
        CastUtils.getCastContext().setReceiverApplicationId(request.source.getApplicationId());

        for (MediaRouter.RouteInfo routeInfo : mProvider.getAndroidMediaRouter().getRoutes()) {
            if (routeInfo.getId().equals(request.sink.getId())) {
                // Unselect and then select so that CAF will get notified of the selection.
                mProvider.getAndroidMediaRouter().unselect(MediaRouter.UNSELECT_REASON_UNKNOWN);
                routeInfo.select();
                break;
            }
        }
    }

    public MediaSource getSource() {
        return mSource;
    }

    public MediaSink getSink() {
        return mSink;
    }

    public CastSession getSession() {
        return mCastSession;
    }

    public void endSession() {
        MediaRouter mediaRouter = mProvider.getAndroidMediaRouter();
        mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
    }

    public List<String> getNamespaces() {
        // Not implemented.
        return new ArrayList<>();
    }

    public List<String> getCapabilities() {
        List<String> capabilities = new ArrayList<>();
        if (mCastSession == null || !mCastSession.isConnected()) return capabilities;
        CastDevice device = mCastSession.getCastDevice();
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_IN)) {
            capabilities.add("audio_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)) {
            capabilities.add("audio_out");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_IN)) {
            capabilities.add("video_in");
        }
        if (device.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)) {
            capabilities.add("video_out");
        }
        return capabilities;
    }

    public boolean isConnected() {
        return mCastSession != null && mCastSession.isConnected();
    }

    public void updateRemoteMediaClient(String message) {
        if (!isConnected()) return;

        mCastSession.getRemoteMediaClient().onMessageReceived(
                mCastSession.getCastDevice(), CastSessionUtil.MEDIA_NAMESPACE, message);
    }

    public void attachToCastSession(CastSession session) {
        mCastSession = session;
        mCastSession.addCastListener(mCastListener);
    }

    public void detachFromCastSession(CastSession session) {
        mCastSession.removeCastListener(mCastListener);
        mCastSession = null;
    }

    private class CastListener extends Cast.Listener {
        @Override
        public void onApplicationStatusChanged() {
            CastSessionController.this.onApplicationStatusChanged();
        }

        @Override
        public void onApplicationMetadataChanged(ApplicationMetadata metadata) {
            onApplicationStatusChanged();
        }

        @Override
        public void onVolumeChanged() {
            CafMessageHandler messageHandler = mProvider.getMessageHandler();
            if (messageHandler == null) return;
            messageHandler.onVolumeChanged();
        }
    }

    private void onApplicationStatusChanged() {
        updateNamespaces();

        CafMessageHandler messageHandler = mProvider.getMessageHandler();
        if (messageHandler != null) {
            messageHandler.broadcastClientMessage(
                    "update_sesssion", messageHandler.buildSessionMessage());
        }
    }

    private void updateNamespaces() {
        if (!isConnected()) return;

        Set<String> namespacesToAdd =
                new HashSet<>(mCastSession.getApplicationMetadata().getSupportedNamespaces());
        Set<String> namespacesToRemove = new HashSet<String>(mNamespaces);

        namespacesToRemove.removeAll(namespacesToAdd);
        namespacesToAdd.removeAll(mNamespaces);

        for (String namespace : namespacesToRemove) unregisterNamespace(namespace);
        for (String namespace : namespacesToAdd) registerNamespace(namespace);
    }

    private void registerNamespace(String namespace) {
        assert !mNamespaces.contains(namespace);

        if (!isConnected()) return;

        try {
            mCastSession.setMessageReceivedCallbacks(namespace, this ::onMessageReceived);
            mNamespaces.add(namespace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register namespace listener for %s", namespace, e);
        }
    }

    private void unregisterNamespace(String namespace) {
        assert mNamespaces.contains(namespace);

        if (!isConnected()) return;

        try {
            mCastSession.removeMessageReceivedCallbacks(namespace);
            mNamespaces.remove(namespace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove the namespace listener for %s", namespace, e);
        }
    }

    private void onMessageReceived(CastDevice castDevice, String namespace, String message) {
        Log.d(TAG,
                "Received message from Cast device: namespace=\"" + namespace + "\" message=\""
                        + message + "\"");
        CafMessageHandler messageHandler = mProvider.getMessageHandler();
        messageHandler.onMessageReceived(namespace, message);
    }
}
