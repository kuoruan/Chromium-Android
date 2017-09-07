// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.cards.ItemViewType;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.NodeVisitor;
import org.chromium.chrome.browser.ntp.cards.OptionalLeaf;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

import java.util.Collections;
import java.util.List;

/**
 * This is an optional item in Chrome Home in the bottom sheet. The carousel is a {@link
 * RecyclerView} which is used to display contextual suggestions to the user according to the
 * website they are currently looking at. The carousel uses a {@link SuggestionsCarouselAdapter} to
 * populate information in {@link ContextualSuggestionsCardViewHolder} instances.
 *
 * When there is no context, i.e. the user is on a native page, the carousel will not be shown.
 */
public class SuggestionsCarousel extends OptionalLeaf {
    private final SuggestionsCarouselAdapter mAdapter;
    private String mCurrentContextUrl;

    public SuggestionsCarousel(UiConfig uiConfig, SuggestionsUiDelegate uiDelegate) {
        mAdapter = new SuggestionsCarouselAdapter(uiConfig, uiDelegate);
        setVisibilityInternal(true);
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder.itemView instanceof RecyclerView;

        ((RecyclerView) holder.itemView).setAdapter(mAdapter);
    }

    @Override
    protected int getItemViewType() {
        return ItemViewType.CAROUSEL;
    }

    @Override
    protected void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitCarouselItem(mAdapter);
    }

    /**
     * Set suggestions which will be displayed in the carousel.
     *
     * @param url The URL for which these suggestions were generated.
     * @param newSuggestions The contextual suggestions for the URL.
     */
    public void newContextualSuggestionsAvailable(String url, List<SnippetArticle> newSuggestions) {
        setVisibilityInternal(!newSuggestions.isEmpty());
        mCurrentContextUrl = url;
        mAdapter.setSuggestions(newSuggestions);
    }

    /**
     * @return The URL for which the carousel has suggestions.
     */
    public String getCurrentCarouselContextUrl() {
        return mCurrentContextUrl;
    }

    /**
     * Removes any suggestions that might be present in the carousel.
     */
    public void clearSuggestions() {
        mCurrentContextUrl = null;
        mAdapter.setSuggestions(Collections.<SnippetArticle>emptyList());
    }

    /**
     * Creates a view holder for a horizontal recycler view.
     *
     * @param parent The container for the recycler view.
     * @return The created recycler view.
     */
    public NewTabPageViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(parent);
    }

    /**
     * View holder for the {@link SuggestionsCarousel}.
     */
    private static class ViewHolder extends NewTabPageViewHolder {
        public ViewHolder(ViewGroup parentView) {
            super(new RecyclerView(parentView.getContext()));

            RecyclerView recyclerView = (RecyclerView) itemView;

            ViewGroup.LayoutParams params =
                    new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            recyclerView.setLayoutParams(params);

            // Make the recycler view scroll horizontally.
            LinearLayoutManager layoutManager = new LinearLayoutManager(parentView.getContext());
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setClipToPadding(false);

            float spaceBetweenCards = parentView.getContext().getResources().getDimension(
                    R.dimen.contextual_carousel_space_between_cards);
            int recyclerViewMarginEnd = (int) Math.floor(spaceBetweenCards);
            ApiCompatibilityUtils.setPaddingRelative(recyclerView, 0, 0, recyclerViewMarginEnd, 0);
        }

        @Override
        public void recycle() {
            ((RecyclerView) itemView).setAdapter(null);
            super.recycle();
        }
    }
}