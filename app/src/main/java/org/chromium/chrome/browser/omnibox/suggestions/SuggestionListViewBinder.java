// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions;

import android.view.View;
import android.view.ViewGroup;

import org.chromium.ui.UiUtils;
import org.chromium.ui.modelutil.ModelListAdapter;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * Handles property updates to the suggestion list component.
 */
class SuggestionListViewBinder {
    /**
     * Holds the view components needed to renderer the suggestion list.
     */
    public static class SuggestionListViewHolder {
        public final ViewGroup container;
        public final OmniboxSuggestionsList listView;
        public final ModelListAdapter adapter;

        public SuggestionListViewHolder(
                ViewGroup container, OmniboxSuggestionsList list, ModelListAdapter adapter) {
            this.container = container;
            this.listView = list;
            this.adapter = adapter;
        }
    }

    /**
     * @see
     * PropertyModelChangeProcessor.ViewBinder#bind(Object,
     * Object, Object)
     */
    public static void bind(
            PropertyModel model, SuggestionListViewHolder view, PropertyKey propertyKey) {
        if (SuggestionListProperties.VISIBLE.equals(propertyKey)) {
            boolean visible = model.get(SuggestionListProperties.VISIBLE);
            if (visible) {
                view.container.setVisibility(View.VISIBLE);
                if (view.listView.getParent() == null) view.container.addView(view.listView);
                view.listView.show();
            } else {
                view.listView.setVisibility(View.GONE);
                UiUtils.removeViewFromParent(view.listView);
                view.container.setVisibility(View.INVISIBLE);
            }
        } else if (SuggestionListProperties.EMBEDDER.equals(propertyKey)) {
            view.listView.setEmbedder(model.get(SuggestionListProperties.EMBEDDER));
        } else if (SuggestionListProperties.SUGGESTION_MODELS.equals(propertyKey)) {
            view.adapter.updateSuggestions(model.get(SuggestionListProperties.SUGGESTION_MODELS));
            view.listView.setSelection(0);
        } else if (SuggestionListProperties.USE_DARK_BACKGROUND.equals(propertyKey)) {
            view.listView.refreshPopupBackground(
                    model.get(SuggestionListProperties.USE_DARK_BACKGROUND));
        }
    }
}
