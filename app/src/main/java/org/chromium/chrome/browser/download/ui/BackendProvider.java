// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.widget.ThumbnailProvider;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.components.offline_items_collection.OfflineContentProvider;

/**
 * Provides classes that need to be interacted with by the {@link DownloadHistoryAdapter}.
 */
public interface BackendProvider {

    /** Interacts with the Downloads backend. */
    public static interface DownloadDelegate {
        /** See {@link DownloadManagerService#addDownloadHistoryAdapter}. */
        void addDownloadHistoryAdapter(DownloadHistoryAdapter adapter);

        /** See {@link DownloadManagerService#removeDownloadHistoryAdapter}. */
        void removeDownloadHistoryAdapter(DownloadHistoryAdapter adapter);

        /** See {@link DownloadManagerService#getAllDownloads}. */
        void getAllDownloads(boolean isOffTheRecord);

        /** See {@link DownloadManagerService#broadcastDownloadAction}. */
        void broadcastDownloadAction(DownloadItem downloadItem, String action);

        /** See {@link DownloadManagerService#checkForExternallyRemovedDownloads}. */
        void checkForExternallyRemovedDownloads(boolean isOffTheRecord);

        /** See {@link DownloadManagerService#removeDownload}. */
        void removeDownload(String guid, boolean isOffTheRecord);

        /** See {@link DownloadManagerService#isDownloadOpenableInBrowser}. */
        boolean isDownloadOpenableInBrowser(boolean isOffTheRecord, String mimeType);

        /** See {@link DownloadManagerService#updateLastAccessTime}. */
        void updateLastAccessTime(String downloadGuid, boolean isOffTheRecord);
    }

    /** Returns the {@link DownloadDelegate} that works with the Downloads backend. */
    DownloadDelegate getDownloadDelegate();

    /** Returns the associated {@link OfflineContentProvider}. */
    OfflineContentProvider getOfflineContentProvider();

    /** Returns the {@link ThumbnailProvider} that gets thumbnails for files. */
    ThumbnailProvider getThumbnailProvider();

    /** Returns the {@link SelectionDelegate} that tracks selected items. */
    SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate();

    /** Destroys the BackendProvider. */
    void destroy();
}
