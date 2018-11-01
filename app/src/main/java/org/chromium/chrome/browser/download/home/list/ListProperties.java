// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.BooleanPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.ObjectPropertyKey;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;
import org.chromium.components.offline_items_collection.VisualsCallback;

/**
 * The properties required to build a {@link ListItem} which contain two types of properties for the
 * download manager: (1) A set of properties that act directly on the list view itself. (2) A set of
 * properties that are effectively shared across all list items like callbacks.
 */
public interface ListProperties {
    @FunctionalInterface
    interface VisualsProvider {
        /**
         * @param item         The {@link OfflineItem} to get the {@link OfflineItemVisuals} for.
         * @param iconWidthPx  The desired width of the icon in pixels (not guaranteed).
         * @param iconHeightPx The desired height of the icon in pixels (not guaranteed).
         * @param callback     A {@link Callback} that will be notified on completion.
         * @return             A {@link Runnable} that can be used to cancel the request.
         */
        Runnable getVisuals(
                OfflineItem item, int iconWidthPx, int iconHeightPx, VisualsCallback callback);
    }

    /** Whether or not item animations should be enabled. */
    BooleanPropertyKey ENABLE_ITEM_ANIMATIONS = new BooleanPropertyKey();

    /** The callback for when a UI action should open a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_OPEN = new ObjectPropertyKey<>();

    /** The callback for when a UI action should pause a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_PAUSE = new ObjectPropertyKey<>();

    /** The callback for when a UI action should resume a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_RESUME = new ObjectPropertyKey<>();

    /** The callback for when a UI action should cancel a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_CANCEL = new ObjectPropertyKey<>();

    /** The callback for when a UI action should share a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_SHARE = new ObjectPropertyKey<>();

    /** The callback for when a UI action should remove a {@link OfflineItem}. */
    ObjectPropertyKey<Callback<OfflineItem>> CALLBACK_REMOVE = new ObjectPropertyKey<>();

    /** The provider to retrieve expensive assets for a {@link OfflineItem}. */
    ObjectPropertyKey<VisualsProvider> PROVIDER_VISUALS = new ObjectPropertyKey<>();

    /** The callback to trigger when a UI action selects or deselects a {@link ListItem}. */
    ObjectPropertyKey<Callback<ListItem>> CALLBACK_SELECTION = new ObjectPropertyKey<>();

    /** Whether or not selection mode is currently active. */
    BooleanPropertyKey SELECTION_MODE_ACTIVE = new BooleanPropertyKey();

    PropertyKey[] ALL_KEYS = new PropertyKey[] {ENABLE_ITEM_ANIMATIONS, CALLBACK_OPEN,
            CALLBACK_PAUSE, CALLBACK_RESUME, CALLBACK_CANCEL, CALLBACK_SHARE, CALLBACK_REMOVE,
            PROVIDER_VISUALS, CALLBACK_SELECTION, SELECTION_MODE_ACTIVE};
}