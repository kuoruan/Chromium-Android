// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions;

import android.util.Pair;

import org.chromium.chrome.browser.omnibox.suggestions.OmniboxSuggestionsList.OmniboxSuggestionListEmbedder;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModel.WritableBooleanPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableObjectPropertyKey;

import java.util.List;

/**
 * The properties controlling the state of the list of suggestion items.
 */
public class SuggestionListProperties {
    /** Whether the suggestion list is visible. */
    public static final WritableBooleanPropertyKey VISIBLE = new WritableBooleanPropertyKey();

    /** The embedder for the suggestion list. */
    public static final WritableObjectPropertyKey<OmniboxSuggestionListEmbedder> EMBEDDER =
            new WritableObjectPropertyKey<>();

    /** The list of models controlling the state of the suggestion items. */
    public static final WritableObjectPropertyKey<List<Pair<Integer, PropertyModel>>>
            SUGGESTION_MODELS = new WritableObjectPropertyKey<>(true);

    /** Whether the suggestion list should have a dark background. */
    public static final WritableBooleanPropertyKey USE_DARK_BACKGROUND =
            new WritableBooleanPropertyKey();

    public static final PropertyKey[] ALL_KEYS =
            new PropertyKey[] {VISIBLE, EMBEDDER, SUGGESTION_MODELS, USE_DARK_BACKGROUND};
}
