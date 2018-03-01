// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.selection.SelectableBottomSheetContent;

/**
 * A {@link BottomSheetContent} holding a {@link DownloadManagerUi} for display in the BottomSheet.
 */
public class DownloadSheetContent extends SelectableBottomSheetContent<DownloadHistoryItemWrapper> {
    private final ActivityStateListener mActivityStateListener;

    /**
     * @param activity The activity displaying the download manager UI.
     * @param isIncognito Whether the activity is currently displaying an incognito tab.
     * @param snackbarManager The {@link SnackbarManager} used to display snackbars.
     */
    public DownloadSheetContent(final ChromeActivity activity, final boolean isIncognito,
            SnackbarManager snackbarManager) {
        super();

        ThreadUtils.assertOnUiThread();

        DownloadManagerUi downloadManager = new DownloadManagerUi(
                activity, isIncognito, activity.getComponentName(), false, snackbarManager);
        initialize(activity, downloadManager);

        // #destroy() unregisters the ActivityStateListener to avoid checking for externally removed
        // downloads after the downloads UI is closed. This requires each download UI to have its
        // own ActivityStateListener. If multiple tabs are showing the downloads page, multiple
        // requests to check for externally removed downloads will be issued when the activity is
        // resumed.
        mActivityStateListener = (activity1, newState) -> {
            if (newState == ActivityState.RESUMED) {
                DownloadUtils.checkForExternallyRemovedDownloads(
                        downloadManager.getBackendProvider(), isIncognito);
            }
        };
        ApplicationStatus.registerStateListenerForActivity(mActivityStateListener, activity);
    }

    @Override
    public void destroy() {
        super.destroy();
        ApplicationStatus.unregisterActivityStateListener(mActivityStateListener);
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_DOWNLOADS;
    }
}
