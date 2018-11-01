// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.ToolbarSwipeLayout;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.toolbar.ToolbarButtonSlotData.ToolbarButtonData;
import org.chromium.ui.resources.ResourceManager;

/**
 * All of the state for the bottom toolbar, updated by the {@link BottomToolbarCoordinator}.
 */
public class BottomToolbarModel extends PropertyModel {
    /** The Y offset of the view in px. */
    public static final IntPropertyKey Y_OFFSET = new IntPropertyKey();

    /** Whether the Android view version of the toolbar is visible. */
    public static final BooleanPropertyKey ANDROID_VIEW_VISIBLE = new BooleanPropertyKey();

    /** Whether the composited version of the toolbar is visible. */
    public static final BooleanPropertyKey COMPOSITED_VIEW_VISIBLE = new BooleanPropertyKey();

    /** A {@link LayoutManager} to attach overlays to. */
    public static final ObjectPropertyKey<LayoutManager> LAYOUT_MANAGER = new ObjectPropertyKey<>();

    /** The browser's {@link ToolbarSwipeLayout}. */
    public static final ObjectPropertyKey<ToolbarSwipeLayout> TOOLBAR_SWIPE_LAYOUT =
            new ObjectPropertyKey<>();

    /** A {@link ResourceManager} for loading textures into the compositor. */
    public static final ObjectPropertyKey<ResourceManager> RESOURCE_MANAGER =
            new ObjectPropertyKey<>();

    /** A handler for swipe events on the toolbar. */
    public static final ObjectPropertyKey<EdgeSwipeHandler> TOOLBAR_SWIPE_HANDLER =
            new ObjectPropertyKey<>();

    /** Data used to show the first button. */
    public static final ObjectPropertyKey<ToolbarButtonData> FIRST_BUTTON_DATA =
            new ObjectPropertyKey<>();

    /** Data used to show the second button. */
    public static final ObjectPropertyKey<ToolbarButtonData> SECOND_BUTTON_DATA =
            new ObjectPropertyKey<>();

    /** Primary color of bottom toolbar. */
    public static final IntPropertyKey PRIMARY_COLOR = new IntPropertyKey();

    /** Default constructor. */
    public BottomToolbarModel() {
        super(Y_OFFSET, ANDROID_VIEW_VISIBLE, COMPOSITED_VIEW_VISIBLE, LAYOUT_MANAGER,
                TOOLBAR_SWIPE_LAYOUT, RESOURCE_MANAGER, TOOLBAR_SWIPE_HANDLER, FIRST_BUTTON_DATA,
                SECOND_BUTTON_DATA, PRIMARY_COLOR);
    }
}
