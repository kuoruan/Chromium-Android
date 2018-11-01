// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.scene_layer.ScrollingBottomViewSceneLayer;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.toolbar.ToolbarButtonSlotData.ToolbarButtonData;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.widget.TintedImageButton;

/**
 * This class is responsible for pushing updates to both the Android view and the compositor
 * component of the bottom toolbar. These updates are pulled from the {@link BottomToolbarModel}
 * when a notification of an update is received.
 */
public class BottomToolbarViewBinder
        implements PropertyModelChangeProcessor.ViewBinder<BottomToolbarModel,
                BottomToolbarViewBinder.ViewHolder, PropertyKey> {
    /**
     * A wrapper class that holds a {@link ViewGroup} (the toolbar view) and a composited layer to
     * be used with the {@link BottomToolbarViewBinder}.
     */
    public static class ViewHolder {
        /** A handle to the Android View based version of the toolbar. */
        public final ScrollingBottomViewResourceFrameLayout toolbarRoot;

        /** A handle to the composited bottom toolbar layer. */
        public ScrollingBottomViewSceneLayer sceneLayer;

        /** Cached {@link TintedImageButton} of the first button. */
        public final TintedImageButton firstTintedImageButton;

        /** Cached {@link TintedImageButton} of the second button. */
        public final TintedImageButton secondTintedImageButton;

        /**
         * @param toolbarRootView The Android View based toolbar.
         */
        public ViewHolder(ScrollingBottomViewResourceFrameLayout toolbarRootView) {
            toolbarRoot = toolbarRootView;
            firstTintedImageButton = toolbarRoot.findViewById(R.id.first_button);
            secondTintedImageButton = toolbarRoot.findViewById(R.id.second_button);
        }
    }

    /**
     * Build a binder that handles interaction between the model and the views that make up the
     * bottom toolbar.
     */
    public BottomToolbarViewBinder() {}

    @Override
    public final void bind(BottomToolbarModel model, ViewHolder view, PropertyKey propertyKey) {
        if (BottomToolbarModel.Y_OFFSET == propertyKey) {
            assert view.sceneLayer != null;
            view.sceneLayer.setYOffset(model.getValue(BottomToolbarModel.Y_OFFSET));
        } else if (BottomToolbarModel.ANDROID_VIEW_VISIBLE == propertyKey) {
            view.toolbarRoot.setVisibility(model.getValue(BottomToolbarModel.ANDROID_VIEW_VISIBLE)
                            ? View.VISIBLE
                            : View.INVISIBLE);
        } else if (BottomToolbarModel.COMPOSITED_VIEW_VISIBLE == propertyKey) {
            view.sceneLayer.setIsVisible(
                    model.getValue(BottomToolbarModel.COMPOSITED_VIEW_VISIBLE));
            model.getValue(BottomToolbarModel.LAYOUT_MANAGER).requestUpdate();
        } else if (BottomToolbarModel.LAYOUT_MANAGER == propertyKey) {
            assert view.sceneLayer == null;
            view.sceneLayer = new ScrollingBottomViewSceneLayer(
                    view.toolbarRoot, view.toolbarRoot.getTopShadowHeight());
            model.getValue(BottomToolbarModel.LAYOUT_MANAGER)
                    .addSceneOverlayToBack(view.sceneLayer);
        } else if (BottomToolbarModel.TOOLBAR_SWIPE_LAYOUT == propertyKey) {
            assert view.sceneLayer != null;
            model.getValue(BottomToolbarModel.TOOLBAR_SWIPE_LAYOUT)
                    .setBottomToolbarSceneLayers(new ScrollingBottomViewSceneLayer(view.sceneLayer),
                            new ScrollingBottomViewSceneLayer(view.sceneLayer));
        } else if (BottomToolbarModel.RESOURCE_MANAGER == propertyKey) {
            model.getValue(BottomToolbarModel.RESOURCE_MANAGER)
                    .getDynamicResourceLoader()
                    .registerResource(
                            view.toolbarRoot.getId(), view.toolbarRoot.getResourceAdapter());
        } else if (BottomToolbarModel.TOOLBAR_SWIPE_HANDLER == propertyKey) {
            view.toolbarRoot.setSwipeDetector(
                    model.getValue(BottomToolbarModel.TOOLBAR_SWIPE_HANDLER));
        } else if (BottomToolbarModel.FIRST_BUTTON_DATA == propertyKey) {
            updateButton(view.firstTintedImageButton,
                    model.getValue(BottomToolbarModel.FIRST_BUTTON_DATA), useLightIcons(model));
        } else if (BottomToolbarModel.SECOND_BUTTON_DATA == propertyKey) {
            updateButton(view.secondTintedImageButton,
                    model.getValue(BottomToolbarModel.SECOND_BUTTON_DATA), useLightIcons(model));
        } else if (BottomToolbarModel.PRIMARY_COLOR == propertyKey) {
            final boolean useLightIcons = useLightIcons(model);
            view.toolbarRoot.findViewById(R.id.bottom_sheet_toolbar)
                    .setBackgroundColor(model.getValue(BottomToolbarModel.PRIMARY_COLOR));
            updateButtonDrawable(view.firstTintedImageButton,
                    model.getValue(BottomToolbarModel.FIRST_BUTTON_DATA), useLightIcons);
            updateButtonDrawable(view.secondTintedImageButton,
                    model.getValue(BottomToolbarModel.SECOND_BUTTON_DATA), useLightIcons);
        } else {
            assert false : "Unhandled property detected in BottomToolbarViewBinder!";
        }
    }

    private static boolean useLightIcons(BottomToolbarModel model) {
        return ColorUtils.shouldUseLightForegroundOnBackground(
                model.getValue(BottomToolbarModel.PRIMARY_COLOR));
    }

    private static void updateButton(
            TintedImageButton button, ToolbarButtonData buttonData, boolean useLightIcons) {
        if (buttonData == null) {
            ToolbarButtonData.clearButton(button);
        } else {
            buttonData.updateButton(button, useLightIcons);
        }
    }

    private static void updateButtonDrawable(
            TintedImageButton button, ToolbarButtonData buttonData, boolean useLightIcons) {
        if (buttonData != null) buttonData.updateButtonDrawable(button, useLightIcons);
    }
}
