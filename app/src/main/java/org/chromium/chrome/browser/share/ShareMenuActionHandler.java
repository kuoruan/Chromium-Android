// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.share;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.physicalweb.PhysicalWebShareActivity;
import org.chromium.chrome.browser.printing.PrintShareActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ChromeFileProvider;
import org.chromium.content_public.browser.ContentBitmapCallback;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.readback_types.ReadbackResponse;
import org.chromium.net.GURLUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the action of selecting the share item in the menu.
 */
public class ShareMenuActionHandler {
    private static boolean sScreenshotCaptureSkippedForTesting;
    private static ShareMenuActionHandler sInstance;

    private final ShareMenuActionDelegate mDelegate;

    /**
     * @return The singleton share menu handler.
     */
    public static ShareMenuActionHandler getInstance() {
        if (sInstance == null) {
            sInstance = new ShareMenuActionHandler(new ShareMenuActionDelegate());
        }
        return sInstance;
    }

    @VisibleForTesting
    ShareMenuActionHandler(ShareMenuActionDelegate delegate) {
        mDelegate = delegate;
    }

    @VisibleForTesting
    public static void setScreenshotCaptureSkippedForTesting(boolean value) {
        sScreenshotCaptureSkippedForTesting = value;
    }

    /**
     * Triggered when the share menu item is selected.
     * This creates and shows a share intent picker dialog or starts a share intent directly.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param isIncognito Whether currentTab is incognito.
     */
    public void onShareMenuItemSelected(
            Activity activity, Tab currentTab, boolean shareDirectly, boolean isIncognito) {
        if (currentTab == null) return;

        List<Class<? extends ShareActivity>> classesToEnable = new ArrayList<>(2);

        if (PrintShareActivity.featureIsAvailable(currentTab)) {
            classesToEnable.add(PrintShareActivity.class);
        }
        if (PhysicalWebShareActivity.featureIsAvailable()) {
            classesToEnable.add(PhysicalWebShareActivity.class);
        }

        if (!classesToEnable.isEmpty()) {
            OptionalShareTargetsManager.enableOptionalShareActivities(activity, classesToEnable,
                    () -> triggerShare(activity, currentTab, shareDirectly, isIncognito));
            return;
        }

        triggerShare(activity, currentTab, shareDirectly, isIncognito);
    }

    @VisibleForTesting
    static boolean shouldFetchCanonicalUrl(final Tab currentTab) {
        WebContents webContents = currentTab.getWebContents();
        if (webContents == null) return false;
        if (webContents.getMainFrame() == null) return false;
        String url = currentTab.getUrl();
        if (TextUtils.isEmpty(url)) return false;
        // TODO(tedchoc): Can we replace GURLUtils.getScheme with Uri.parse(...).getScheme()
        //                crbug.com/783819
        if (!UrlConstants.HTTPS_SCHEME.equals(GURLUtils.getScheme(url))) {
            return false;
        }
        if (currentTab.isShowingErrorPage() || currentTab.isShowingInterstitialPage()
                || currentTab.isShowingSadTab()) {
            return false;
        }
        return true;
    }

    @VisibleForTesting
    static String getUrlToShare(String visibleUrl, String canonicalUrl) {
        if (TextUtils.isEmpty(canonicalUrl)) return visibleUrl;
        String canonicalScheme = GURLUtils.getScheme(canonicalUrl);
        if (!UrlConstants.HTTP_SCHEME.equals(canonicalScheme)
                && !UrlConstants.HTTPS_SCHEME.equals(canonicalScheme)) {
            return visibleUrl;
        }
        if (!UrlConstants.HTTPS_SCHEME.equals(GURLUtils.getScheme(visibleUrl))) {
            return visibleUrl;
        }
        return canonicalUrl;
    }

    private void triggerShare(final Activity activity, final Tab currentTab,
            final boolean shareDirectly, boolean isIncognito) {
        boolean isOffline = OfflinePageUtils.isOfflinePage(currentTab);
        RecordHistogram.recordBooleanHistogram("OfflinePages.SharedPageWasOffline", isOffline);

        boolean canShareOfflinePage = OfflinePageBridge.isPageSharingEnabled();
        if (canShareOfflinePage && isOffline) {
            ShareParams params = OfflinePageUtils.buildShareParams(activity, currentTab);
            if (params == null) return;
            mDelegate.share(params);
            return;
        }

        if (shouldFetchCanonicalUrl(currentTab)) {
            WebContents webContents = currentTab.getWebContents();
            webContents.getMainFrame().getCanonicalUrlForSharing(new Callback<String>() {
                @Override
                public void onResult(String result) {
                    triggerShareWithCanonicalUrlResolved(
                            activity, currentTab, result, shareDirectly, isIncognito);
                }
            });
        } else {
            triggerShareWithCanonicalUrlResolved(
                    activity, currentTab, null, shareDirectly, isIncognito);
        }
    }

    private void triggerShareWithCanonicalUrlResolved(final Activity mainActivity,
            final Tab currentTab, final String canonicalUrl, final boolean shareDirectly,
            boolean isIncognito) {
        WebContents webContents = currentTab.getWebContents();

        // Share an empty blockingUri in place of screenshot file. The file ready notification is
        // sent by onScreenshotReady call below when the file is written.
        final Uri blockingUri = (isIncognito || webContents == null)
                ? null
                : ChromeFileProvider.generateUriAndBlockAccess(mainActivity);
        ShareParams.Builder builder =
                new ShareParams
                        .Builder(mainActivity, currentTab.getTitle(),
                                getUrlToShare(currentTab.getUrl(), canonicalUrl))
                        .setShareDirectly(shareDirectly)
                        .setSaveLastUsed(!shareDirectly)
                        .setScreenshotUri(blockingUri);
        mDelegate.share(builder.build());
        if (shareDirectly) {
            RecordUserAction.record("MobileMenuDirectShare");
        } else {
            RecordUserAction.record("MobileMenuShare");
        }

        if (blockingUri == null) return;

        // Start screenshot capture and notify the provider when it is ready.
        ContentBitmapCallback callback = (bitmap, response) -> ShareHelper.saveScreenshotToDisk(
                bitmap, mainActivity,
                result -> {
                    // Unblock the file once it is saved to disk.
                    ChromeFileProvider.notifyFileReady(blockingUri, result);
                });
        if (sScreenshotCaptureSkippedForTesting) {
            callback.onFinishGetBitmap(null, ReadbackResponse.SURFACE_UNAVAILABLE);
        } else {
            webContents.getContentBitmapAsync(0, 0, callback);
        }
    }

    /**
     * Delegate for share handling.
     */
    static class ShareMenuActionDelegate {
        /**
         * Trigger the share action for the specified params.
         */
        void share(ShareParams params) {
            ShareHelper.share(params);
        }
    }
}
