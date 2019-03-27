// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.caf.remoting;

import com.google.android.gms.cast.framework.CastSession;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.CastSessionUtil;
import org.chromium.chrome.browser.media.router.caf.BaseNotificationController;
import org.chromium.chrome.browser.media.router.caf.BaseSessionController;
import org.chromium.chrome.browser.media.router.caf.CafBaseMediaRouteProvider;
import org.chromium.chrome.browser.media.router.cast.remoting.RemotingMediaSource;

import java.lang.ref.WeakReference;

/** Wrapper for {@link CastSession} for remoting. */
public class RemotingSessionController extends BaseSessionController {
    private static final String TAG = "RmtSessionCtrl";

    private static WeakReference<RemotingSessionController> sInstance;

    public static RemotingSessionController getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    private FlingingControllerAdapter mFlingingControllerAdapter;
    private RemotingNotificationController mNotificationController;

    RemotingSessionController(CafBaseMediaRouteProvider provider) {
        super(provider);
        mNotificationController = new RemotingNotificationController(this);
        sInstance = new WeakReference<>(this);
    }

    @Override
    public void attachToCastSession(CastSession session) {
        super.attachToCastSession(session);

        try {
            getSession().setMessageReceivedCallbacks(
                    CastSessionUtil.MEDIA_NAMESPACE, this ::onMessageReceived);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register namespace listener for %s",
                    CastSessionUtil.MEDIA_NAMESPACE, e);
        }
    }

    @Override
    public void onSessionStarted() {
        super.onSessionStarted();
        RemotingMediaSource source = (RemotingMediaSource) getSource();
        mFlingingControllerAdapter = new FlingingControllerAdapter(this, source.getMediaUrl());
    }

    @Override
    protected void onStatusUpdated() {
        mFlingingControllerAdapter.onStatusUpdated();
        super.onStatusUpdated();
    }

    @Override
    public FlingingControllerAdapter getFlingingController() {
        return mFlingingControllerAdapter;
    }

    @Override
    public BaseNotificationController getNotificationController() {
        return mNotificationController;
    }
}
