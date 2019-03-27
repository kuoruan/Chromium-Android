// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.bottom;

import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.scene_layer.ScrollingBottomViewSceneLayer;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

/**
 * This class is responsible for pushing updates to both the Android view and the compositor
 * component of the browsing mode bottom toolbar. These updates are pulled from the
 * {@link BrowsingModeBottomToolbarModel} when a notification of an update is received.
 */
public class BrowsingModeBottomToolbarViewBinder
        implements PropertyModelChangeProcessor.ViewBinder<BrowsingModeBottomToolbarModel,
                BrowsingModeBottomToolbarViewBinder.ViewHolder, PropertyKey> {
    /**
     * A wrapper class that holds a {@link ViewGroup} (the toolbar view) and a composited layer to
     * be used with the {@link BrowsingModeBottomToolbarViewBinder}.
     */
    public static class ViewHolder {
        /** A handle to the Android View based version of the toolbar. */
        public final ScrollingBottomViewResourceFrameLayout toolbarRoot;

        /** A handle to the composited bottom toolbar layer. */
        public ScrollingBottomViewSceneLayer sceneLayer;

        /**
         * @param toolbarRootView The Android View based toolbar.
         */
        public ViewHolder(ScrollingBottomViewResourceFrameLayout toolbarRootView) {
            toolbarRoot = toolbarRootView;
        }
    }

    /**
     * Build a binder that handles interaction between the model and the views that make up the
     * browsing mode bottom toolbar.
     */
    public BrowsingModeBottomToolbarViewBinder() {}

    @Override
    public final void bind(
            BrowsingModeBottomToolbarModel model, ViewHolder view, PropertyKey propertyKey) {
        if (BrowsingModeBottomToolbarModel.Y_OFFSET == propertyKey) {
            // Native may not have completely initialized by the time this is set.
            if (view.sceneLayer == null) return;
            view.sceneLayer.setYOffset(model.get(BrowsingModeBottomToolbarModel.Y_OFFSET));
        } else if (BrowsingModeBottomToolbarModel.ANDROID_VIEW_VISIBLE == propertyKey) {
            view.toolbarRoot.setVisibility(
                    model.get(BrowsingModeBottomToolbarModel.ANDROID_VIEW_VISIBLE)
                            ? View.VISIBLE
                            : View.INVISIBLE);
        } else if (BrowsingModeBottomToolbarModel.COMPOSITED_VIEW_VISIBLE == propertyKey) {
            view.sceneLayer.setIsVisible(
                    model.get(BrowsingModeBottomToolbarModel.COMPOSITED_VIEW_VISIBLE));
            model.get(BrowsingModeBottomToolbarModel.LAYOUT_MANAGER).requestUpdate();
        } else if (BrowsingModeBottomToolbarModel.LAYOUT_MANAGER == propertyKey) {
            assert view.sceneLayer == null;
            view.sceneLayer = new ScrollingBottomViewSceneLayer(
                    view.toolbarRoot, view.toolbarRoot.getTopShadowHeight());
            model.get(BrowsingModeBottomToolbarModel.LAYOUT_MANAGER)
                    .addSceneOverlayToBack(view.sceneLayer);
        } else if (BrowsingModeBottomToolbarModel.TOOLBAR_SWIPE_LAYOUT == propertyKey) {
            assert view.sceneLayer != null;
            model.get(BrowsingModeBottomToolbarModel.TOOLBAR_SWIPE_LAYOUT)
                    .setBottomToolbarSceneLayers(new ScrollingBottomViewSceneLayer(view.sceneLayer),
                            new ScrollingBottomViewSceneLayer(view.sceneLayer));
        } else if (BrowsingModeBottomToolbarModel.RESOURCE_MANAGER == propertyKey) {
            model.get(BrowsingModeBottomToolbarModel.RESOURCE_MANAGER)
                    .getDynamicResourceLoader()
                    .registerResource(
                            view.toolbarRoot.getId(), view.toolbarRoot.getResourceAdapter());
        } else if (BrowsingModeBottomToolbarModel.TOOLBAR_SWIPE_HANDLER == propertyKey) {
            view.toolbarRoot.setSwipeDetector(
                    model.get(BrowsingModeBottomToolbarModel.TOOLBAR_SWIPE_HANDLER));
        } else if (BrowsingModeBottomToolbarModel.PRIMARY_COLOR == propertyKey) {
            view.toolbarRoot.findViewById(R.id.bottom_toolbar_buttons)
                    .setBackgroundColor(model.get(BrowsingModeBottomToolbarModel.PRIMARY_COLOR));
        } else if (BrowsingModeBottomToolbarModel.IS_VISIBLE == propertyKey) {
            final boolean isVisible = model.get(BrowsingModeBottomToolbarModel.IS_VISIBLE);
            view.toolbarRoot.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        } else {
            assert false : "Unhandled property detected in BrowsingModeBottomToolbarViewBinder!";
        }
    }
}
