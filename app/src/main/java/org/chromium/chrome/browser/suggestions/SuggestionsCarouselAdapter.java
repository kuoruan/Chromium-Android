// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a horizontal list of suggestions. The individual suggestions are held
 * in {@link ContextualSuggestionsCardViewHolder} instances.
 */
public class SuggestionsCarouselAdapter
        extends RecyclerView.Adapter<ContextualSuggestionsCardViewHolder> {
    private final SuggestionsUiDelegate mUiDelegate;
    private final UiConfig mUiConfig;
    private final List<SnippetArticle> mSuggestions;

    public SuggestionsCarouselAdapter(UiConfig uiConfig, SuggestionsUiDelegate uiDelegate) {
        mUiDelegate = uiDelegate;
        mUiConfig = uiConfig;
        mSuggestions = new ArrayList<>();
    }

    @Override
    public ContextualSuggestionsCardViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new ContextualSuggestionsCardViewHolder(viewGroup, mUiConfig, mUiDelegate);
    }

    @Override
    public void onBindViewHolder(ContextualSuggestionsCardViewHolder holder, int i) {
        holder.onBindViewHolder(mSuggestions.get(i));
    }

    @Override
    public void onViewRecycled(ContextualSuggestionsCardViewHolder holder) {
        holder.recycle();
    }

    @Override
    public int getItemCount() {
        return mSuggestions.size();
    }

    /**
     * Set the new contextual suggestions to be shown in the suggestions carousel and update the UI.
     *
     * @param suggestions The new suggestions to be shown in the suggestions carousel.
     */
    public void setSuggestions(List<SnippetArticle> suggestions) {
        mSuggestions.clear();
        mSuggestions.addAll(suggestions);

        notifyDataSetChanged();
    }
}