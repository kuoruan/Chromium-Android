// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.items;

import org.chromium.chrome.browser.download.DownloadInfo;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadNotifier;
import org.chromium.chrome.browser.download.DownloadServiceDelegate;
import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.OfflineContentProvider;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItemState;

import java.util.ArrayList;

/**
 * A glue class that bridges the Profile-attached OfflineContentProvider with the
 * download notification code (SystemDownloadNotifier and DownloadServiceDelegate).
 */
public class OfflineContentAggregatorNotificationBridgeUi
        implements DownloadServiceDelegate, OfflineContentProvider.Observer {
    private final OfflineContentProvider mProvider;

    /**
     * Creates a new OfflineContentAggregatorNotificationBridgeUi based on {@code provider}.
     */
    public OfflineContentAggregatorNotificationBridgeUi(OfflineContentProvider provider) {
        mProvider = provider;

        mProvider.addObserver(this);
    }

    /**
     * Destroys this class and detaches it from associated objects.
     */
    public void destroy() {
        mProvider.removeObserver(this);
        destroyServiceDelegate();
    }

    /** @see OfflineContentProvider#openItem(ContentId) */
    public void openItem(ContentId id) {
        mProvider.openItem(id);
    }

    // OfflineContentProvider.Observer implementation.
    @Override
    public void onItemsAvailable() {}

    @Override
    public void onItemsAdded(ArrayList<OfflineItem> items) {
        for (int i = 0; i < items.size(); i++) {
            OfflineItem item = items.get(i);

            // Only update the UI for new OfflineItems that are in progress or pending.
            if (item.state == OfflineItemState.IN_PROGRESS
                    || item.state == OfflineItemState.PENDING) {
                visuallyUpdateOfflineItem(item);
            }
        }
    }

    @Override
    public void onItemRemoved(ContentId id) {}

    @Override
    public void onItemUpdated(OfflineItem item) {
        visuallyUpdateOfflineItem(item);
    }

    // DownloadServiceDelegate implementation.
    @Override
    public void cancelDownload(ContentId id, boolean isOffTheRecord) {
        mProvider.cancelDownload(id);
    }

    @Override
    public void pauseDownload(ContentId id, boolean isOffTheRecord) {
        mProvider.pauseDownload(id);
    }

    @Override
    public void resumeDownload(ContentId id, DownloadItem item, boolean hasUserGesture) {
        mProvider.resumeDownload(id);
    }

    @Override
    public void destroyServiceDelegate() {}

    /**
     * Calls into the proper {@link DownloadNotifier} by converting an {@link OfflineItem} to a
     * {@link DownloadInfo}.
     * @param item The {@link OfflineItem} that needs a UI refresh.
     */
    private void visuallyUpdateOfflineItem(OfflineItem item) {
        DownloadInfo info = DownloadInfo.fromOfflineItem(item);
        DownloadNotifier notifier =
                DownloadManagerService.getDownloadManagerService().getDownloadNotifier();
        switch (item.state) {
            case OfflineItemState.IN_PROGRESS:
                notifier.notifyDownloadProgress(info, item.creationTimeMs, item.allowMetered);
                break;
            case OfflineItemState.COMPLETE:
                notifier.notifyDownloadSuccessful(info, -1L, false, false);
                break;
            case OfflineItemState.CANCELLED:
                notifier.notifyDownloadCanceled(item.id);
                break;
            case OfflineItemState.INTERRUPTED:
                // TODO(dtrainor): Push the correct value for auto resume.
                notifier.notifyDownloadInterrupted(info, true);
                break;
            case OfflineItemState.PAUSED:
                notifier.notifyDownloadPaused(info);
                break;
            default:
                assert false;
        }
    }
}
