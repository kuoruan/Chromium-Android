// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.host.network.HttpRequest;
import com.google.android.libraries.feed.host.network.HttpResponse;
import com.google.android.libraries.feed.host.network.NetworkClient;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Provides access to native implementations of NetworkClient.
 */
@JNINamespace("feed")
public class FeedNetworkBridge implements NetworkClient {
    private long mNativeBridge;

    /**
     * Creates a FeedNetworkBridge for accessing native network implementation for the current
     * user.
     *
     * @param profile Profile of the user we are rendering the Feed for.
     */
    public FeedNetworkBridge(Profile profile) {
        mNativeBridge = nativeInit(profile);
    }

    /*
     * Cleans up native half of this bridge.
     */
    public void destroy() {
        assert mNativeBridge != 0;
        nativeDestroy(mNativeBridge);
        mNativeBridge = 0;
    }

    @Override
    public void send(HttpRequest request, Consumer<HttpResponse> responseConsumer) {
        if (mNativeBridge == 0) {
            responseConsumer.accept(createHttpResponse(500, new byte[0]));
        } else {
            nativeSendNetworkRequest(mNativeBridge, request.getUri().toString(),
                    request.getMethod(), request.getBody(),
                    result -> responseConsumer.accept(result));
        }
    }

    @Override
    public void close() {
        // Bridge could have been destroyed for policy when this is called.
        // See https://crbug.com/901414.
        if (mNativeBridge == 0) return;

        nativeCancelRequests(mNativeBridge);
    }

    @CalledByNative
    private static HttpResponse createHttpResponse(int code, byte[] body) {
        return new HttpResponse(code, body);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeFeedNetworkBridge);
    private native void nativeSendNetworkRequest(long nativeFeedNetworkBridge, String url,
            String requestType, byte[] body, Callback<HttpResponse> resultCallback);
    private native void nativeCancelRequests(long nativeFeedNetworkBridge);
}
