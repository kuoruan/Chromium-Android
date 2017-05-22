// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

/**
 * Subclass of BrowserAccessibilityManager for Lollipop.
 */
@JNINamespace("content")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopBrowserAccessibilityManager extends KitKatBrowserAccessibilityManager {
    private static SparseArray<AccessibilityAction> sAccessibilityActionMap =
            new SparseArray<AccessibilityAction>();

    LollipopBrowserAccessibilityManager(long nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        super(nativeBrowserAccessibilityManagerAndroid, contentViewCore);
    }

    @Override
    protected void setAccessibilityNodeInfoLollipopAttributes(AccessibilityNodeInfo node,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        node.setCanOpenPopup(canOpenPopup);
        node.setContentInvalid(contentInvalid);
        node.setDismissable(contentInvalid);
        node.setMultiLine(multiLine);
        node.setInputType(inputType);
        node.setLiveRegion(liveRegion);
    }

    @Override
    protected void setAccessibilityNodeInfoCollectionInfo(AccessibilityNodeInfo node,
            int rowCount, int columnCount, boolean hierarchical) {
        node.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(
                rowCount, columnCount, hierarchical));
    }

    @Override
    protected void setAccessibilityNodeInfoCollectionItemInfo(AccessibilityNodeInfo node,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        node.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(
                rowIndex, rowSpan, columnIndex, columnSpan, heading));
    }

    @Override
    protected void setAccessibilityNodeInfoRangeInfo(AccessibilityNodeInfo node,
            int rangeType, float min, float max, float current) {
        node.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                rangeType, min, max, current));
    }

    @Override
    protected void setAccessibilityNodeInfoViewIdResourceName(
            AccessibilityNodeInfo node, String viewIdResourceName) {
        node.setViewIdResourceName(viewIdResourceName);
    }

    @Override
    protected void setAccessibilityEventLollipopAttributes(AccessibilityEvent event,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        // This is just a fallback for pre-Lollipop systems.
        // Do nothing on Lollipop and higher.
    }

    @Override
    protected void setAccessibilityEventCollectionInfo(AccessibilityEvent event,
            int rowCount, int columnCount, boolean hierarchical) {
        // This is just a fallback for pre-Lollipop systems.
        // Do nothing on Lollipop and higher.
    }

    @Override
    protected void setAccessibilityEventHeadingFlag(AccessibilityEvent event,
            boolean heading) {
        // This is just a fallback for pre-Lollipop systems.
        // Do nothing on Lollipop and higher.
    }

    @Override
    protected void setAccessibilityEventCollectionItemInfo(AccessibilityEvent event,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan) {
        // This is just a fallback for pre-Lollipop systems.
        // Do nothing on Lollipop and higher.
    }

    @Override
    protected void setAccessibilityEventRangeInfo(AccessibilityEvent event,
            int rangeType, float min, float max, float current) {
        // This is just a fallback for pre-Lollipop systems.
        // Do nothing on Lollipop and higher.
    }

    @Override
    protected void addAction(AccessibilityNodeInfo node, int actionId) {
        // The Lollipop SDK requires us to call AccessibilityNodeInfo.addAction with an
        // AccessibilityAction argument, but to simplify things and share more code with
        // the pre-L SDK, we just cache a set of AccessibilityActions mapped by their ID.
        AccessibilityAction action = sAccessibilityActionMap.get(actionId);
        if (action == null) {
            action = new AccessibilityAction(actionId, null);
            sAccessibilityActionMap.put(actionId, action);
        }
        node.addAction(action);
    }
}
