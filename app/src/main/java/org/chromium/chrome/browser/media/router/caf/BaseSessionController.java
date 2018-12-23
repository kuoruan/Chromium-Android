// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.caf;

import android.support.annotation.Nullable;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.CastSessionUtil;
import org.chromium.chrome.browser.media.router.FlingingController;
import org.chromium.chrome.browser.media.router.MediaSink;
import org.chromium.chrome.browser.media.router.MediaSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A base wrapper for {@link CastSession}, extending its functionality for Chrome MediaRouter.
 *
 * Has persistent lifecycle and always attaches itself to the current {@link CastSession}.
 */
public class BaseSessionController {
    private static final String TAG = "BaseSessionCtrl";

    private CastSession mCastSession;
    private final CafBaseMediaRouteProvider mProvider;
    private final MediaRouter.Callback mMediaRouterCallbackForSessionLaunch;
    private CreateRouteRequestInfo mRouteCreationInfo;
    private final CafNotificationController mNotificationController;
    private final RemoteMediaClient.Callback mRemoteMediaClientCallback;

    public BaseSessionController(CafBaseMediaRouteProvider provider) {
        mProvider = provider;
        mMediaRouterCallbackForSessionLaunch = new MediaRouterCallbackForSessionLaunch();
        mNotificationController = new CafNotificationController(this);
        mRemoteMediaClientCallback = new RemoteMediaClientCallback();
    }

    public void requestSessionLaunch() {
        mRouteCreationInfo = mProvider.getPendingCreateRouteRequestInfo();
        CastUtils.getCastContext().setReceiverApplicationId(
                mRouteCreationInfo.source.getApplicationId());

        if (mRouteCreationInfo.routeInfo.isSelected()) {
            // If a route has just been selected, CAF might not be ready yet before setting the app
            // ID. So unselect and select the route will let CAF be aware that the route has been
            // selected thus it can start the session.
            //
            // An issue of this workaround is that if a route is unselected and selected in a very
            // short time, the selection might be ignored by MediaRouter, so put the reselection in
            // a callback.
            mProvider.getAndroidMediaRouter().addCallback(
                    mRouteCreationInfo.source.buildRouteSelector(),
                    mMediaRouterCallbackForSessionLaunch);
            mProvider.getAndroidMediaRouter().unselect(MediaRouter.UNSELECT_REASON_UNKNOWN);
        } else {
            mRouteCreationInfo.routeInfo.select();
        }
    }

    public MediaSource getSource() {
        return (mRouteCreationInfo != null) ? mRouteCreationInfo.source : null;
    }

    public MediaSink getSink() {
        return (mRouteCreationInfo != null) ? mRouteCreationInfo.sink : null;
    }

    public CreateRouteRequestInfo getRouteCreationInfo() {
        return mRouteCreationInfo;
    }

    public CastSession getSession() {
        return mCastSession;
    }

    public RemoteMediaClient getRemoteMediaClient() {
        return mCastSession.getRemoteMediaClient();
    }

    public CafNotificationController getNotificationController() {
        return mNotificationController;
    }

    public void endSession() {
        MediaRouter mediaRouter = mProvider.getAndroidMediaRouter();
        mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
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

    private void updateRemoteMediaClient(String message) {
        if (!isConnected()) return;

        mCastSession.getRemoteMediaClient().onMessageReceived(
                mCastSession.getCastDevice(), CastSessionUtil.MEDIA_NAMESPACE, message);
    }

    /** Attaches the controller to the current {@link CastSession}. */
    public void attachToCastSession(CastSession session) {
        mCastSession = session;
        getRemoteMediaClient().registerCallback(mRemoteMediaClientCallback);
    }

    /** Detaches the controller from any {@link CastSession}. */
    public void detachFromCastSession() {
        if (mCastSession == null) return;

        getRemoteMediaClient().unregisterCallback(mRemoteMediaClientCallback);
        mCastSession = null;
    }

    /** Called when session started. */
    public void onSessionStarted() {
        mNotificationController.onSessionStarted();
    }

    /** Called when session ended. */
    public void onSessionEnded() {
        mNotificationController.onSessionEnded();
        mRouteCreationInfo = null;
    }

    protected final CafBaseMediaRouteProvider getProvider() {
        return mProvider;
    }

    /**
     * All sub-classes need to register this method to listen to messages of the namespaces they are
     * interested in.
     */
    protected void onMessageReceived(CastDevice castDevice, String namespace, String message) {
        Log.d(TAG,
                "Received message from Cast device: namespace=\"" + namespace + "\" message=\""
                        + message + "\"");
        if (CastSessionUtil.MEDIA_NAMESPACE.equals(namespace)) {
            updateRemoteMediaClient(message);
        }
    }

    private class MediaRouterCallbackForSessionLaunch extends MediaRouter.Callback {
        @Override
        public void onRouteUnselected(MediaRouter mediaRouter, MediaRouter.RouteInfo routeInfo) {
            if (mProvider.getPendingCreateRouteRequestInfo() == null) return;

            if (routeInfo.getId().equals(
                        mProvider.getPendingCreateRouteRequestInfo().routeInfo.getId())) {
                routeInfo.select();
                mProvider.getAndroidMediaRouter().removeCallback(
                        mMediaRouterCallbackForSessionLaunch);
            }
        }
    }

    private class RemoteMediaClientCallback extends RemoteMediaClient.Callback {
        @Override
        public void onStatusUpdated() {
            BaseSessionController.this.onStatusUpdated();
        }

        @Override
        public void onMetadataUpdated() {
            BaseSessionController.this.onMetadataUpdated();
        }
    }

    protected void onStatusUpdated() {
        mNotificationController.onStatusUpdated();
    }

    protected void onMetadataUpdated() {
        mNotificationController.onMetadataUpdated();
    }

    @Nullable
    public FlingingController getFlingingController() {
        return null;
    }
}
