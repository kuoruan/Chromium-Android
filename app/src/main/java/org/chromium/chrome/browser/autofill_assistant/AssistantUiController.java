// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.content_public.browser.WebContents;

/**
 * Bridge to native side AssistantUiControllerAndroid. It allows native side to control Autofill
 * Assistant related UIs and forward UI events to native side.
 */
@JNINamespace("autofill_assistant")
public class AssistantUiController {
    private final long mAssistantUiControllerAndroid;

    /**
     * Construct Autofill Assistant UI controller.
     *
     * @param activity The CustomTabActivity of the controller associated with.
     */
    public AssistantUiController(CustomTabActivity activity) {
        // TODO(crbug.com/806868): Implement corresponding UI.
        Tab activityTab = activity.getActivityTab();
        mAssistantUiControllerAndroid = nativeInit(activityTab.getWebContents());

        // Stop Autofill Assistant when the tab is detached from the activity.
        activityTab.addObserver(new EmptyTabObserver() {
            @Override
            public void onActivityAttachmentChanged(Tab tab, boolean isAttached) {
                if (!isAttached) {
                    activityTab.removeObserver(this);
                    nativeDestroy(mAssistantUiControllerAndroid);
                }
            }
        });

        // Stop Autofill Assistant when the selected tab (foreground tab) is changed.
        TabModel currentTabModel = activity.getTabModelSelector().getCurrentModel();
        currentTabModel.addObserver(new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                currentTabModel.removeObserver(this);

                // Assume newly selected tab is always different from the last one.
                nativeDestroy(mAssistantUiControllerAndroid);
                // TODO(crbug.com/806868): May start a new Autofill Assistant instance for the newly
                // selected Tab.
            }
        });
    }

    @CalledByNative
    private void onShowStatusMessage(String message) {
        // TODO(crbug.com/806868): Implement corresponding UI.
    }

    @CalledByNative
    private void onShowOverlay() {
        // TODO(crbug.com/806868): Implement corresponding UI.
    }

    @CalledByNative
    private void onHideOverlay() {
        // TODO(crbug.com/806868): Implement corresponding UI.
    }

    // native methods.
    private native long nativeInit(WebContents webContents);
    private native void nativeDestroy(long nativeAssistantUiControllerAndroid);
}