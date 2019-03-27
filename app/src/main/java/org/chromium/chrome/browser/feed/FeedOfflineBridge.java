// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import com.google.android.libraries.feed.api.knowncontent.ContentMetadata;
import com.google.android.libraries.feed.api.knowncontent.ContentRemoval;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi;
import com.google.android.libraries.feed.common.functional.Consumer;

import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Provides access to native implementations of OfflineIndicatorApi. */
@JNINamespace("feed")
public class FeedOfflineBridge
        implements FeedOfflineIndicator, KnownContentApi.KnownContentListener {
    private long mNativeBridge;
    private KnownContentApi mKnownContentApi;

    /**
     * Hold onto listeners in Java. It is difficult to offload this completely to native, because we
     * need to remove with object reference equality in removeOfflineStatusListener().
     */
    private Set<OfflineStatusListener> mListeners = new HashSet<>();

    /**
     * Creates a FeedOfflineBridge for accessing native offlining logic.
     *
     * @param profile Profile of the user we are rendering the Feed for.
     * @param knownContentApi Interface to access information about the Feed's articles.
     */
    public FeedOfflineBridge(Profile profile, KnownContentApi knownContentApi) {
        mNativeBridge = nativeInit(profile);
        mKnownContentApi = knownContentApi;
        mKnownContentApi.addListener(this);
    }

    @Override
    public void destroy() {
        assert mNativeBridge != 0;
        nativeDestroy(mNativeBridge);
        mNativeBridge = 0;
        mKnownContentApi.removeListener(this);
    }

    @Override
    public Long getOfflineIdIfPageIsOfflined(String url) {
        if (mNativeBridge == 0) {
            return 0L;
        } else {
            return (Long) nativeGetOfflineId(mNativeBridge, url);
        }
    }

    @Override
    public void getOfflineStatus(
            List<String> urlsToRetrieve, Consumer<List<String>> urlListConsumer) {
        if (mNativeBridge == 0) {
            urlListConsumer.accept(Collections.emptyList());
        } else {
            String[] urlsArray = urlsToRetrieve.toArray(new String[urlsToRetrieve.size()]);
            nativeGetOfflineStatus(mNativeBridge, urlsArray,
                    (String[] urlsAsArray) -> urlListConsumer.accept(Arrays.asList(urlsAsArray)));
        }
    }

    @Override
    public void addOfflineStatusListener(OfflineStatusListener offlineStatusListener) {
        if (mNativeBridge != 0) {
            mListeners.add(offlineStatusListener);
        }
    }

    @Override
    public void removeOfflineStatusListener(OfflineStatusListener offlineStatusListener) {
        if (mNativeBridge != 0) {
            mListeners.remove(offlineStatusListener);
            if (mListeners.isEmpty()) {
                nativeOnNoListeners(mNativeBridge);
            }
        }
    }

    @Override
    public void onContentRemoved(List<ContentRemoval> contentRemoved) {
        if (mNativeBridge != 0) {
            List<String> userDrivenRemovals = takeUserDriveRemovalsOnly(contentRemoved);
            if (!userDrivenRemovals.isEmpty()) {
                nativeOnContentRemoved(mNativeBridge,
                        userDrivenRemovals.toArray(new String[userDrivenRemovals.size()]));
            }
        }
    }

    @Override
    public void onNewContentReceived(boolean isNewRefresh, long contentCreationDateTimeMs) {
        if (mNativeBridge != 0) {
            nativeOnNewContentReceived(mNativeBridge);
        }
    }

    /**
     * Filters out any {@link ContentRemoval} that was not user driven, such as old articles being
     * garbage collected.
     *
     * @param contentRemoved The articles being removed, may or may not be user driven.
     * @return All and only the user driven removals.
     */
    @VisibleForTesting
    static List<String> takeUserDriveRemovalsOnly(List<ContentRemoval> contentRemoved) {
        List<String> urlsRemovedByUser = new ArrayList<>();
        for (ContentRemoval removal : contentRemoved) {
            if (removal.isRequestedByUser()) {
                urlsRemovedByUser.add(removal.getUrl());
            }
        }
        return urlsRemovedByUser;
    }

    @CalledByNative
    private static Long createLong(long id) {
        return Long.valueOf(id);
    }

    @CalledByNative
    private void getKnownContent() {
        mKnownContentApi.getKnownContent((List<ContentMetadata> metadataList) -> {
            if (mNativeBridge == 0) return;

            for (ContentMetadata metadata : metadataList) {
                long time_published_ms = TimeUnit.SECONDS.toMillis(metadata.getTimePublished());
                nativeAppendContentMetadata(mNativeBridge, metadata.getUrl(), metadata.getTitle(),
                        time_published_ms, metadata.getImageUrl(), metadata.getPublisher(),
                        metadata.getFaviconUrl(), metadata.getSnippet());
            }
            nativeOnGetKnownContentDone(mNativeBridge);
        });
    }

    @CalledByNative
    private void notifyStatusChange(String url, boolean availableOffline) {
        for (OfflineStatusListener listener : mListeners) {
            listener.updateOfflineStatus(url, availableOffline);
        }
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeFeedOfflineBridge);
    private native Object nativeGetOfflineId(long nativeFeedOfflineBridge, String url);
    private native void nativeGetOfflineStatus(long nativeFeedOfflineBridge,
            String[] urlsToRetrieve, Callback<String[]> urlListConsumer);
    private native void nativeOnContentRemoved(long nativeFeedOfflineBridge, String[] urlsRemoved);
    private native void nativeOnNewContentReceived(long nativeFeedOfflineBridge);
    private native void nativeOnNoListeners(long nativeFeedOfflineBridge);
    private native void nativeAppendContentMetadata(long nativeFeedOfflineBridge, String url,
            String title, long timePublishedMs, String imageUrl, String publisher,
            String faviconUrl, String snippet);
    private native void nativeOnGetKnownContentDone(long nativeFeedOfflineBridge);
}
