// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.LaunchOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.ChromeMediaRouter;
import org.chromium.chrome.browser.media.router.MediaRoute;
import org.chromium.chrome.browser.media.router.MediaSink;
import org.chromium.chrome.browser.media.router.MediaSource;
import org.chromium.chrome.browser.media.router.cast.remoting.RemotingCastSession;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Establishes a {@link MediaRoute} by starting a Cast application represented by the given
 * presentation URL. Reports success or failure to {@link ChromeMediaRouter}.
 * Since there're numerous asynchronous calls involved in getting the application to launch
 * the class is implemented as a state machine.
 */
// Migrated to CafMediaRouteProvider. See https://crbug.com/711860.
public class CreateRouteRequest implements GoogleApiClient.ConnectionCallbacks,
                                           GoogleApiClient.OnConnectionFailedListener,
                                           ResultCallback<Cast.ApplicationConnectionResult>,
                                           ChromeCastSessionManager.CastSessionLaunchRequest {
    private static final String TAG = "MediaRouter";

    @IntDef({State.IDLE, State.CONNECTING_TO_API, State.API_CONNECTION_SUSPENDED,
            State.LAUNCHING_APPLICATION, State.LAUNCH_SUCCEEDED, State.TERMINATED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {
        int IDLE = 0;
        int CONNECTING_TO_API = 1;
        int API_CONNECTION_SUSPENDED = 2;
        int LAUNCHING_APPLICATION = 3;
        int LAUNCH_SUCCEEDED = 4;
        int TERMINATED = 5;
    }

    private final MediaSource mSource;
    private final MediaSink mSink;
    private final String mPresentationId;
    private final String mOrigin;
    private final int mTabId;
    private final boolean mIsIncognito;
    private final int mRequestId;
    private final CastMessageHandler mMessageHandler;
    private final ChromeCastSessionManager.CastSessionManagerListener mSessionListener;
    private final @RequestedCastSessionType int mSessionType;

    private GoogleApiClient mApiClient;
    private @State int mState = State.IDLE;

    // Used to identify whether the request should launch a CastSessionImpl or a RemotingCastSession
    // (based off of wheter the route creation was requested by a RemotingMediaRouteProvider or a
    // CastMediaRouteProvider).
    @IntDef({RequestedCastSessionType.CAST, RequestedCastSessionType.REMOTE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestedCastSessionType {
        int CAST = 0;
        int REMOTE = 1;
    }

    /**
     * Initializes the request.
     * @param source The {@link MediaSource} defining the application to launch on the Cast device.
     * @param sink The {@link MediaSink} identifying the selected Cast device.
     * @param presentationId The presentation id assigned to the route by {@link ChromeMediaRouter}.
     * @param origin The origin of the frame requesting the route.
     * @param tabId The id of the tab containing the frame requesting the route.
     * @param isIncognito Whether the route is being requested from an Incognito profile.
     * @param requestId The id of the route creation request for tracking by
     * {@link ChromeMediaRouter}.
     * @param listener The listener that should be notified of session/route creation changes.
     * @param messageHandler A message handler (used to create CastSessionImpl instances).
     */
    public CreateRouteRequest(MediaSource source, MediaSink sink, String presentationId,
            String origin, int tabId, boolean isIncognito, int requestId,
            ChromeCastSessionManager.CastSessionManagerListener listener,
            @RequestedCastSessionType int sessionType,
            @Nullable CastMessageHandler messageHandler) {
        assert source != null;
        assert sink != null;

        mSource = source;
        mSink = sink;
        mPresentationId = presentationId;
        mOrigin = origin;
        mTabId = tabId;
        mIsIncognito = isIncognito;
        mRequestId = requestId;
        mSessionListener = listener;
        mSessionType = sessionType;
        mMessageHandler = messageHandler;
    }

    public MediaSource getSource() {
        return mSource;
    }

    public MediaSink getSink() {
        return mSink;
    }

    public String getPresentationId() {
        return mPresentationId;
    }

    public String getOrigin() {
        return mOrigin;
    }

    public int getTabId() {
        return mTabId;
    }

    public boolean isIncognito() {
        return mIsIncognito;
    }

    public int getNativeRequestId() {
        return mRequestId;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // ChromeCastSessionManager.CastSessionLaunchRequest implementation.

    /**
     * Returns the object that should be notified of session changes that result
     * from this route creation request.
     */
    @Override
    public ChromeCastSessionManager.CastSessionManagerListener getSessionListener() {
        return mSessionListener;
    }

    /**
     * Starts the process of launching the application on the Cast device.
     */
    @Override
    public void start(Cast.Listener castListener) {
        if (mState != State.IDLE) throwInvalidState();

        mApiClient = createApiClient(castListener);
        mApiClient.connect();
        mState = State.CONNECTING_TO_API;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // GoogleApiClient.* implementations.

    @Override
    public void onConnected(Bundle connectionHint) {
        if (mState != State.CONNECTING_TO_API && mState != State.API_CONNECTION_SUSPENDED) {
            throwInvalidState();
        }

        if (mState == State.API_CONNECTION_SUSPENDED) return;

        try {
            launchApplication(mApiClient, mSource.getApplicationId(), true)
                    .setResultCallback(this);
            mState = State.LAUNCHING_APPLICATION;
        } catch (Exception e) {
            Log.e(TAG, "Launch application failed: %s", mSource.getApplicationId(), e);
            reportError();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mState = State.API_CONNECTION_SUSPENDED;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mState != State.CONNECTING_TO_API) throwInvalidState();

        Log.e(TAG, "GoogleApiClient connection failed: %d, %b", result.getErrorCode(),
                result.hasResolution());
        reportError();
    }

    /**
     * ResultCallback<Cast.ApplicationConnectionResult> implementation.
     */
    @Override
    public void onResult(Cast.ApplicationConnectionResult result) {
        if (mState != State.LAUNCHING_APPLICATION && mState != State.API_CONNECTION_SUSPENDED) {
            throwInvalidState();
        }

        Status status = result.getStatus();
        if (!status.isSuccess()) {
            Log.e(TAG, "Launch application failed with status: %s, %d, %s",
                    mSource.getApplicationId(), status.getStatusCode(), status.getStatusMessage());
            reportError();
            return;
        }

        mState = State.LAUNCH_SUCCEEDED;
        reportSuccess(result);
    }

    private GoogleApiClient createApiClient(Cast.Listener listener) {
        Cast.CastOptions.Builder apiOptionsBuilder =
                new Cast.CastOptions.Builder(mSink.getDevice(), listener)
                         // TODO(avayvod): hide this behind the flag or remove
                         .setVerboseLoggingEnabled(true);

        return new GoogleApiClient.Builder(ContextUtils.getApplicationContext())
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private PendingResult<Cast.ApplicationConnectionResult> launchApplication(
            GoogleApiClient apiClient,
            String appId,
            boolean relaunchIfRunning) {
        LaunchOptions.Builder builder = new LaunchOptions.Builder();
        return Cast.CastApi.launchApplication(apiClient, appId,
                builder.setRelaunchIfRunning(relaunchIfRunning)
                        .build());
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private void throwInvalidState() {
        throw new RuntimeException(String.format("Invalid state: %d", mState));
    }

    private void reportSuccess(Cast.ApplicationConnectionResult result) {
        if (mState != State.LAUNCH_SUCCEEDED) throwInvalidState();

        CastSession session = null;

        switch (mSessionType) {
            case RequestedCastSessionType.CAST:
                session = new CastSessionImpl(mApiClient, result.getSessionId(),
                        result.getApplicationMetadata(), result.getApplicationStatus(),
                        mSink.getDevice(), mOrigin, mTabId, mIsIncognito, mSource, mMessageHandler);
                break;
            case RequestedCastSessionType.REMOTE:
                session = new RemotingCastSession(mApiClient, result.getSessionId(),
                        result.getApplicationMetadata(), result.getApplicationStatus(),
                        mSink.getDevice(), mOrigin, mTabId, mIsIncognito, mSource);
                break;
        }

        ChromeCastSessionManager.get().onSessionStarted(session);

        terminate();
    }

    private void reportError() {
        if (mState == State.TERMINATED) throwInvalidState();

        ChromeCastSessionManager.get().onSessionStartFailed();

        terminate();
    }

    private void terminate() {
        mApiClient.unregisterConnectionCallbacks(this);
        mApiClient.unregisterConnectionFailedListener(this);
        mState = State.TERMINATED;
    }
}
