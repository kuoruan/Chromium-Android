// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions.editurl;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

/** A mechanism for binding the {@link EditUrlSuggestionProperties} to its view. */
public class EditUrlSuggestionViewBinder {
    public static void bind(PropertyModel model, ViewGroup view, PropertyKey propertyKey) {
        if (EditUrlSuggestionProperties.COPY_ICON_VISIBLE == propertyKey) {
            view.findViewById(R.id.url_copy_icon)
                    .setVisibility(model.get(EditUrlSuggestionProperties.COPY_ICON_VISIBLE)
                                    ? View.VISIBLE
                                    : View.GONE);
        } else if (EditUrlSuggestionProperties.SHARE_ICON_VISIBLE == propertyKey) {
            view.findViewById(R.id.url_share_icon)
                    .setVisibility(model.get(EditUrlSuggestionProperties.SHARE_ICON_VISIBLE)
                                    ? View.VISIBLE
                                    : View.GONE);
        } else if (EditUrlSuggestionProperties.TITLE_TEXT == propertyKey) {
            TextView titleView = view.findViewById(R.id.title_text_view);
            titleView.setText(model.get(EditUrlSuggestionProperties.TITLE_TEXT));
        } else if (EditUrlSuggestionProperties.URL_TEXT == propertyKey) {
            TextView urlView = view.findViewById(R.id.full_url_text_view);
            urlView.setText(model.get(EditUrlSuggestionProperties.URL_TEXT));
        } else if (EditUrlSuggestionProperties.BUTTON_CLICK_LISTENER == propertyKey) {
            view.findViewById(R.id.url_edit_icon)
                    .setOnClickListener(
                            model.get(EditUrlSuggestionProperties.BUTTON_CLICK_LISTENER));
            view.findViewById(R.id.url_copy_icon)
                    .setOnClickListener(
                            model.get(EditUrlSuggestionProperties.BUTTON_CLICK_LISTENER));
            view.findViewById(R.id.url_share_icon)
                    .setOnClickListener(
                            model.get(EditUrlSuggestionProperties.BUTTON_CLICK_LISTENER));
        } else if (EditUrlSuggestionProperties.TEXT_CLICK_LISTENER == propertyKey) {
            view.setOnClickListener(model.get(EditUrlSuggestionProperties.TEXT_CLICK_LISTENER));
        }
        // TODO(mdjones): Support SuggestionCommonProperties.*
    }
}
