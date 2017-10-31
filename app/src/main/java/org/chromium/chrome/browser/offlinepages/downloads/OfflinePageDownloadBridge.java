// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.downloads;

import android.app.Activity;
import android.content.ComponentName;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.offlinepages.OfflinePageOrigin;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Serves as an interface between Download Home UI and offline page related items that are to be
 * displayed in the downloads UI.
 */
@JNINamespace("offline_pages::android")
public class OfflinePageDownloadBridge {
    private static OfflinePageDownloadBridge sInstance;
    private static boolean sIsTesting;
    private long mNativeOfflinePageDownloadBridge;
    private boolean mIsLoaded;

    /**
     * @return An {@link OfflinePageDownloadBridge} instance singleton.  If one
     *         is not available this will create a new one.
     */
    public static OfflinePageDownloadBridge getInstance() {
        if (sInstance == null) {
            sInstance = new OfflinePageDownloadBridge(
                    Profile.getLastUsedProfile().getOriginalProfile());
        }
        return sInstance;
    }

    private OfflinePageDownloadBridge(Profile profile) {
        // If |profile| is incognito profile, switch to the regular one since
        // downloads are shared between them.
        mNativeOfflinePageDownloadBridge =
                sIsTesting ? 0L : nativeInit(profile.getOriginalProfile());
    }

    /** Destroys the native portion of the bridge. */
    public void destroy() {
        if (mNativeOfflinePageDownloadBridge != 0) {
            nativeDestroy(mNativeOfflinePageDownloadBridge);
            mNativeOfflinePageDownloadBridge = 0;
            mIsLoaded = false;
        }
    }

    /**
     * 'Opens' the offline page identified by the given URL and offlineId.
     * This is done by creating a new tab and navigating it to the saved local snapshot.
     * No automatic redirection is happening based on the connection status.
     * If the item with specified GUID is not found or can't be opened, nothing happens.
     */
    @CalledByNative
    private static void openItem(String url, long offlineId) {
        LoadUrlParams params =
                OfflinePageUtils.getLoadUrlParamsForOpeningOfflineVersion(url, offlineId);
        ComponentName componentName = getComponentName();
        AsyncTabCreationParams asyncParams = componentName == null
                ? new AsyncTabCreationParams(params)
                : new AsyncTabCreationParams(params, componentName);
        final TabDelegate tabDelegate = new TabDelegate(false);
        tabDelegate.createNewTab(asyncParams, TabLaunchType.FROM_CHROME_UI, Tab.INVALID_TAB_ID);
    }

    /**
     * Starts download of the page currently open in the specified Tab.
     * If tab's contents are not yet loaded completely, we'll wait for it
     * to load enough for snapshot to be reasonable. If the Chrome is made
     * background and killed, the background request remains that will
     * eventually load the page in background and obtain its offline
     * snapshot.
     *
     * @param tab a tab contents of which will be saved locally.
     * @param origin the object encapsulating application origin of the request.
     */
    public static void startDownload(Tab tab, OfflinePageOrigin origin) {
        nativeStartDownload(tab, origin.encodeAsJsonString());
    }

    /**
     * Method to ensure that the bridge is created for tests without calling the native portion of
     * initialization.
     * @param isTesting flag indicating whether the constructor will initialize native code.
     */
    static void setIsTesting(boolean isTesting) {
        sIsTesting = isTesting;
    }

    private static ComponentName getComponentName() {
        if (!ApplicationStatus.hasVisibleActivities()) return null;

        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (activity instanceof ChromeTabbedActivity) {
            return activity.getComponentName();
        }

        return null;
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeOfflinePageDownloadBridge);
    private static native void nativeStartDownload(Tab tab, String origin);
}
