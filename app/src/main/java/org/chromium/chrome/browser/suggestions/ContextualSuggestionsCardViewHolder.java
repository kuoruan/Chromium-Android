// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserverAdapter;
import org.chromium.chrome.browser.widget.displaystyle.HorizontalDisplayStyle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.ui.mojom.WindowOpenDisposition;

/**
 * ViewHolder used to show contextual suggestions. The card will be used in the
 * {@link SuggestionsCarouselAdapter} to populate the {@link SuggestionsCarousel} in Chrome Home in
 * the bottom sheet.
 */
public class ContextualSuggestionsCardViewHolder extends NewTabPageViewHolder {
    private static final double CARD_WIDTH_TO_WINDOW_SIZE_RATIO = 0.9;
    private final SuggestionsBinder mSuggestionsBinder;
    private final SuggestionsUiDelegate mUiDelegate;
    private final DisplayStyleObserverAdapter mDisplayStyleObserver;
    private SnippetArticle mSuggestion;

    public ContextualSuggestionsCardViewHolder(ViewGroup recyclerView, UiConfig uiConfig,
            SuggestionsUiDelegate uiDelegate, ContextMenuManager contextMenuManager) {
        super(getCardView(recyclerView));

        mUiDelegate = uiDelegate;
        mSuggestionsBinder = new SuggestionsBinder(itemView, uiDelegate);
        InteractionsDelegate interactionsDelegate = new InteractionsDelegate(contextMenuManager);

        itemView.setOnClickListener(interactionsDelegate);
        itemView.setOnCreateContextMenuListener(interactionsDelegate);

        int startMargin = itemView.getResources().getDimensionPixelOffset(
                R.dimen.contextual_carousel_space_between_cards);
        ApiCompatibilityUtils.setMarginStart(
                (ViewGroup.MarginLayoutParams) itemView.getLayoutParams(), startMargin);

        mDisplayStyleObserver =
                new DisplayStyleObserverAdapter(itemView, uiConfig, this::updateCardWidth);
        mSuggestionsBinder.updateFieldsVisibility(/* showHeadline = */ true,
                /* showThumbnail = */ true, /* showThumbnailVideoBadge = */ false);
    }

    public void onBindViewHolder(SnippetArticle suggestion) {
        mSuggestion = suggestion;
        mDisplayStyleObserver.attach();
        mSuggestionsBinder.updateViewInformation(mSuggestion);
        refreshOfflineBadgeVisibility();
    }

    @Override
    public void recycle() {
        mDisplayStyleObserver.detach();
        mSuggestionsBinder.recycle();
        super.recycle();
    }

    private static View getCardView(ViewGroup recyclerView) {
        int res = SuggestionsConfig.useModernLayout() ? R.layout.content_suggestions_card_modern
                                                      : R.layout.contextual_suggestions_card;

        return LayoutInflater.from(recyclerView.getContext()).inflate(res, recyclerView, false);
    }

    private void updateCardWidth(UiConfig.DisplayStyle displayStyle) {
        @HorizontalDisplayStyle
        int horizontalStyle = displayStyle.horizontal;

        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        DisplayMetrics displayMetrics = itemView.getContext().getResources().getDisplayMetrics();

        // We choose the window side that we want to align the card width to. In portrait and split
        // screen we use the width of the window. In landscape, however, the width becomes
        // unnecessarily large. Therefore, we choose the height of the window, which allows to show
        // more suggestions in reasonably sized cards.
        int screenSizePx = horizontalStyle == HorizontalDisplayStyle.WIDE
                ? displayMetrics.heightPixels
                : displayMetrics.widthPixels;

        params.width = (int) (screenSizePx * CARD_WIDTH_TO_WINDOW_SIZE_RATIO);
        itemView.setLayoutParams(params);
    }

    public void refreshOfflineBadgeVisibility() {
        boolean visible = mSuggestion.getOfflinePageOfflineId() != null;
        mSuggestionsBinder.updateOfflineBadgeVisibility(visible);
    }

    private class InteractionsDelegate implements ContextMenuManager.Delegate, View.OnClickListener,
                                                  View.OnCreateContextMenuListener {
        private final ContextMenuManager mContextMenuManager;

        InteractionsDelegate(ContextMenuManager contextMenuManager) {
            mContextMenuManager = contextMenuManager;
        }

        @Override
        public void openItem(int windowDisposition) {
            mUiDelegate.getNavigationDelegate().navigateToSuggestionUrl(
                    windowDisposition, mSuggestion.getUrl());

            SuggestionsMetrics.recordContextualSuggestionOpened();
        }

        @Override
        public void removeItem() {
            // Unsupported.
            assert false;
        }

        @Override
        public String getUrl() {
            return mSuggestion.getUrl();
        }

        @Override
        public boolean isItemSupported(@ContextMenuManager.ContextMenuItemId int menuItemId) {
            return menuItemId != ContextMenuManager.ID_REMOVE
                    && menuItemId != ContextMenuManager.ID_LEARN_MORE;
        }

        @Override
        public void onContextMenuCreated() {}

        @Override
        public void onCreateContextMenu(
                ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
            mContextMenuManager.createContextMenu(contextMenu, view, this);
        }

        @Override
        public void onClick(View view) {
            openItem(WindowOpenDisposition.CURRENT_TAB);
        }
    }
}