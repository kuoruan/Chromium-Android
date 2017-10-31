// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.support.annotation.LayoutRes;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.metrics.ImpressionTracker;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.cards.CardViewHolder;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.SectionList;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.suggestions.SuggestionsBinder;
import org.chromium.chrome.browser.suggestions.SuggestionsMetrics;
import org.chromium.chrome.browser.suggestions.SuggestionsOfflineModelObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserverAdapter;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.ui.mojom.WindowOpenDisposition;

/**
 * A class that represents the view for a single card snippet.
 */
public class SnippetArticleViewHolder extends CardViewHolder implements ImpressionTracker.Listener {
    private final SuggestionsUiDelegate mUiDelegate;
    private final SuggestionsBinder mSuggestionsBinder;
    private final OfflinePageBridge mOfflinePageBridge;

    private SuggestionsCategoryInfo mCategoryInfo;
    private SnippetArticle mArticle;

    private final DisplayStyleObserverAdapter mDisplayStyleObserver;

    /**
     * Constructs a {@link SnippetArticleViewHolder} item used to display snippets.
     * @param parent The SuggestionsRecyclerView that is going to contain the newly created view.
     * @param contextMenuManager The manager responsible for the context menu.
     * @param uiDelegate The delegate object used to open an article, fetch thumbnails, etc.
     * @param uiConfig The NTP UI configuration object used to adjust the article UI.
     * @param offlinePageBridge used to determine if article is prefetched.
     */
    public SnippetArticleViewHolder(SuggestionsRecyclerView parent,
            ContextMenuManager contextMenuManager, SuggestionsUiDelegate uiDelegate,
            UiConfig uiConfig, OfflinePageBridge offlinePageBridge) {
        super(getLayout(), parent, uiConfig, contextMenuManager);

        mUiDelegate = uiDelegate;
        mSuggestionsBinder = new SuggestionsBinder(itemView, uiDelegate);
        mDisplayStyleObserver = new DisplayStyleObserverAdapter(
                itemView, uiConfig, newDisplayStyle -> updateLayout());

        mOfflinePageBridge = offlinePageBridge;

        new ImpressionTracker(itemView, this);
    }

    @Override
    public void onImpression() {
        if (mArticle != null && mArticle.trackImpression()) {
            if (SectionList.shouldReportPrefetchedSuggestionsMetrics(mArticle.mCategory)
                    && mOfflinePageBridge.isOfflinePageModelLoaded()) {
                // Before reporting prefetched suggestion impression, we ask Offline Page model
                // whether the page is actually prefetched to avoid race condition when suggestion
                // surface is opened.

                // TabId is relevant only for recent tab offline pages, which we do not handle here,
                // so we do not care about tab id.
                mOfflinePageBridge.selectPageForOnlineUrl(
                        mArticle.getUrl(), /* tabId = */ 0, item -> {
                            if (!SuggestionsOfflineModelObserver.isPrefetchedOfflinePage(item)) {
                                return;
                            }
                            NewTabPageUma.recordPrefetchedArticleSuggestionImpressionPosition(
                                    mArticle.getPerSectionRank());
                        });
            }

            mUiDelegate.getEventReporter().onSuggestionShown(mArticle);
            mRecyclerView.onSnippetImpression();
        }
    }

    @Override
    public void onCardTapped() {
        SuggestionsMetrics.recordCardTapped();
        int windowDisposition = WindowOpenDisposition.CURRENT_TAB;
        mUiDelegate.getEventReporter().onSuggestionOpened(
                mArticle, windowDisposition, mUiDelegate.getSuggestionsRanker());
        mUiDelegate.getNavigationDelegate().openSnippet(windowDisposition, mArticle);
    }

    @Override
    public void openItem(int windowDisposition) {
        mUiDelegate.getEventReporter().onSuggestionOpened(
                mArticle, windowDisposition, mUiDelegate.getSuggestionsRanker());
        mUiDelegate.getNavigationDelegate().openSnippet(windowDisposition, mArticle);
    }

    @Override
    public String getUrl() {
        return mArticle.mUrl;
    }

    @Override
    public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
        Boolean isSupported = mCategoryInfo.isContextMenuItemSupported(menuItemId);
        if (isSupported != null) return isSupported;

        return super.isItemSupported(menuItemId);
    }

    @Override
    public void onContextMenuCreated() {
        mUiDelegate.getEventReporter().onSuggestionMenuOpened(mArticle);
    }

    /**
     * Updates ViewHolder with data.
     * @param article The snippet to take the data from.
     * @param categoryInfo The info of the category which the snippet belongs to.
     */
    public void onBindViewHolder(
            final SnippetArticle article, SuggestionsCategoryInfo categoryInfo) {
        super.onBindViewHolder();

        mArticle = article;
        mCategoryInfo = categoryInfo;

        updateLayout();

        mDisplayStyleObserver.attach();
        mSuggestionsBinder.updateViewInformation(mArticle);

        refreshOfflineBadgeVisibility();
    }

    @Override
    public void recycle() {
        mDisplayStyleObserver.detach();
        mSuggestionsBinder.recycle();
        super.recycle();
    }

    /**
     * Triggers a refresh of the offline badge visibility. Intended to be used as
     * {@link NewTabPageViewHolder.PartialBindCallback}
     */
    public static void refreshOfflineBadgeVisibility(NewTabPageViewHolder holder) {
        ((SnippetArticleViewHolder) holder).refreshOfflineBadgeVisibility();
    }

    /**
     * Updates the layout taking into account screen dimensions and the type of snippet displayed.
     */
    private void updateLayout() {
        final int layout = mCategoryInfo.getCardLayout();

        boolean showHeadline = shouldShowHeadline();
        boolean showThumbnail = shouldShowThumbnail(layout);
        boolean showThumbnailVideoBadge = shouldShowThumbnailVideoBadge(showThumbnail);

        mSuggestionsBinder.updateFieldsVisibility(
                showHeadline, showThumbnail, showThumbnailVideoBadge);
    }

    /** If the title is empty (or contains only whitespace characters), we do not show it. */
    private boolean shouldShowHeadline() {
        return !mArticle.mTitle.trim().isEmpty();
    }

    private boolean shouldShowThumbnail(int layout) {
        // Minimal cards don't have a thumbnail
        if (layout == ContentSuggestionsCardLayout.MINIMAL_CARD) return false;

        return true;
    }

    private boolean shouldShowThumbnailVideoBadge(boolean showThumbnail) {
        if (!showThumbnail) return false;
        if (!mArticle.mIsVideoSuggestion) return false;
        return FeatureUtilities.isChromeHomeEnabled();
    }

    /** Updates the visibility of the card's offline badge by checking the bound article's info. */
    private void refreshOfflineBadgeVisibility() {
        boolean visible = mArticle.getOfflinePageOfflineId() != null || mArticle.isAssetDownload();
        mSuggestionsBinder.updateOfflineBadgeVisibility(visible);
    }

    /**
     * @return The layout resource reference for this card.
     */
    @LayoutRes
    private static int getLayout() {
        if (FeatureUtilities.isChromeHomeEnabled()) {
            return R.layout.content_suggestions_card_modern;
        }
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONTENT_SUGGESTIONS_LARGE_THUMBNAIL)) {
            return R.layout.new_tab_page_snippets_card_large_thumbnail;
        }
        return R.layout.new_tab_page_snippets_card;
    }
}
