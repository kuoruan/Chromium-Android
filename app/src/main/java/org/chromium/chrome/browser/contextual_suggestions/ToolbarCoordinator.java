// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextual_suggestions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModel.PropertyKey;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

/**
 * Coordinator for the toolbar sub-component. Responsible for communication with the parent
 * {@link ContextualSuggestionsCoordinator} and lifecycle of sub-component objects.
 */
class ToolbarCoordinator {
    private final ToolbarView mToolbarView;
    private final PropertyModelChangeProcessor<ContextualSuggestionsModel, ToolbarView, PropertyKey>
            mModelChangeProcessor;

    /**
     * Construct a new {@link ToolbarCoordinator}.
     * @param context The {@link Context} used to retrieve resources.
     * @param parentView The parent {@link View} to which the content will eventually be attached.
     * @param model The {@link ContextualSuggestionsModel} for the component.
     */
    ToolbarCoordinator(Context context, ViewGroup parentView, ContextualSuggestionsModel model) {
        mToolbarView = (ToolbarView) LayoutInflater.from(context).inflate(
                R.layout.contextual_suggestions_toolbar, parentView, false);

        mModelChangeProcessor =
                PropertyModelChangeProcessor.create(model, mToolbarView, new ToolbarViewBinder());
    }

    /** @return The content {@link View}. */
    View getView() {
        return mToolbarView;
    }

    /** Destroy the toolbar component. */
    void destroy() {
        mModelChangeProcessor.destroy();
    }
}
