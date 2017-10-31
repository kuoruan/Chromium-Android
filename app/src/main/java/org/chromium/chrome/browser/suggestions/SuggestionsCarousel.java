// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.URLUtil;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.metrics.ImpressionTracker;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.ItemViewType;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.NodeVisitor;
import org.chromium.chrome.browser.ntp.cards.OptionalLeaf;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.ui.widget.Toast;

import java.util.Collections;
import java.util.Locale;

/**
 * This is an optional item in Chrome Home in the bottom sheet. The carousel is a {@link
 * RecyclerView} which is used to display contextual suggestions to the user according to the
 * website they are currently looking at. The carousel uses a {@link SuggestionsCarouselAdapter} to
 * populate information in {@link ContextualSuggestionsCardViewHolder} instances.
 *
 * When there is no context, i.e. the user is on a native page, the carousel will not be shown.
 */
public class SuggestionsCarousel extends OptionalLeaf implements ImpressionTracker.Listener {
    private final SuggestionsCarouselAdapter mAdapter;
    private final SuggestionsUiDelegate mUiDelegate;
    private final ImpressionTracker mImpressionTracker;
    @Nullable
    private String mCurrentContextUrl;

    /**
     * Necessary flag to record correct UMA metrics and avoid possible inconsistencies caused by the
     * {@link ViewHolder}. We record that the carousel was scrolled in the view holder, but want to
     * make sure that this is recorded only once per carousel shown. */
    private boolean mWasScrolledSinceShown;

    public SuggestionsCarousel(UiConfig uiConfig, SuggestionsUiDelegate uiDelegate,
            ContextMenuManager contextMenuManager, OfflinePageBridge offlinePageBridge) {
        mAdapter = new SuggestionsCarouselAdapter(
                uiConfig, uiDelegate, contextMenuManager, offlinePageBridge);
        mUiDelegate = uiDelegate;

        // The impression tracker will record metrics only once per bottom sheet opened.
        mImpressionTracker = new ImpressionTracker(this);

        // TODO(dgn): Handle this case properly. Also enable test in ContextualSuggestionsTest.
        // We need to keep the carousel always internally visible because it is the first item in
        // the SuggestionsRecyclerView. Otherwise, if we setVisibilityInternal() as we receive
        // suggestions in the ContextualFetchCallback, there is a problem. When the suggestions
        // arrive late, the SuggestionsRecyclerView has already laid out its children and will not
        // automatically scroll up to show the carousel. (See crbug.com/758179).
        // This way the carousel is always a child in the NewTabPageAdapter data structure but is
        // only shown when there are suggestions.
        setVisibilityInternal(true);
    }

    /**
     * Fetches new suggestions if the context URL was changed from the last time that the carousel
     * was shown.
     */
    public void refresh(final Context context, @Nullable final String newUrl) {
        if (!URLUtil.isNetworkUrl(newUrl)) {
            clearSuggestions();
            return;
        }

        // Reset the impression tracker to record if the carousel is shown. We do this once per
        // bottom sheet opened.
        mImpressionTracker.clearTriggered();
        // Reset that the carousel has not been scrolled in this impression yet.
        mWasScrolledSinceShown = false;

        // Do nothing if there are already suggestions in the carousel for the new context.
        if (isContextTheSame(newUrl)) return;

        String text = "Fetching contextual suggestions...";
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();

        // Context has changed, so we want to remove any old suggestions from the carousel.
        clearSuggestions();
        mCurrentContextUrl = newUrl;

        mUiDelegate.getSuggestionsSource().fetchContextualSuggestions(newUrl, (suggestions) -> {
            // Avoiding double fetches causing suggestions for incorrect context.
            if (!TextUtils.equals(newUrl, mCurrentContextUrl)) return;

            mAdapter.setSuggestions(suggestions);

            String toastText = String.format(
                    Locale.US, "Fetched %d contextual suggestions.", suggestions.size());
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onImpression() {
        SuggestionsMetrics.recordContextualSuggestionsCarouselShown();
        mImpressionTracker.reset(null);
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        RecyclerView carouselRecyclerView = (RecyclerView) holder.itemView;

        carouselRecyclerView.setAdapter(mAdapter);

        // We want to record only once that the carousel was shown after the bottom sheet was
        // opened. The first time that the carousel is shown, the mImpressionTracker will be
        // triggered and we don't want it to track any views anymore.
        mImpressionTracker.reset(mImpressionTracker.wasTriggered() ? null : holder.itemView);

        // If we have recorded a scroll since the carousel was shown, we don't want to register a
        // scroll listener again.
        if (mWasScrolledSinceShown) return;

        carouselRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                // The dragging state guarantees that the scroll event was caused by the user.
                if (newState != RecyclerView.SCROLL_STATE_DRAGGING) return;

                SuggestionsMetrics.recordContextualSuggestionsCarouselScrolled();
                mWasScrolledSinceShown = true;

                // We are not interested in scrolls after the first one, so we can safely remove
                // the listener.
                recyclerView.removeOnScrollListener(this);
            }
        });
    }

    @Override
    protected int getItemViewType() {
        return ItemViewType.CAROUSEL;
    }

    @Override
    protected void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitCarouselItem(mAdapter);
    }

    @VisibleForTesting
    boolean isContextTheSame(String newUrl) {
        // The call to UrlUtilities is wrapped to be able to mock it and skip the native call in
        // unit tests.
        return UrlUtilities.urlsMatchIgnoringFragments(newUrl, mCurrentContextUrl);
    }

    /**
     * Removes any suggestions that might be present in the carousel.
     */
    private void clearSuggestions() {
        mCurrentContextUrl = null;
        mAdapter.setSuggestions(Collections.<SnippetArticle>emptyList());
    }

    /**
     * View holder for the {@link SuggestionsCarousel}.
     */
    public static class ViewHolder extends NewTabPageViewHolder {
        private final RecyclerView mRecyclerView;

        public ViewHolder(ViewGroup parentView) {
            super(new RecyclerView(parentView.getContext()));

            mRecyclerView = (RecyclerView) itemView;

            setUpRecyclerView();
        }

        private void setUpRecyclerView() {
            ViewGroup.LayoutParams params =
                    new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mRecyclerView.setLayoutParams(params);

            // Make the recycler view scroll horizontally.
            LinearLayoutManager layoutManager = new LinearLayoutManager(mRecyclerView.getContext());
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            mRecyclerView.setLayoutManager(layoutManager);
            mRecyclerView.setClipToPadding(false);

            float spaceBetweenCards = mRecyclerView.getContext().getResources().getDimension(
                    R.dimen.contextual_carousel_space_between_cards);
            int recyclerViewMarginEnd = (int) Math.floor(spaceBetweenCards);
            ApiCompatibilityUtils.setPaddingRelative(mRecyclerView, 0, 0, recyclerViewMarginEnd, 0);
        }

        @Override
        public void recycle() {
            mRecyclerView.setAdapter(null);
            mRecyclerView.clearOnScrollListeners();
            super.recycle();
        }
    }
}