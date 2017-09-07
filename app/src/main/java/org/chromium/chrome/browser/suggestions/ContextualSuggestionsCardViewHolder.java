// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserver;
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
    private final UiConfig mUiConfig;
    private final DisplayStyleObserverAdapter mDisplayStyleObserver;
    private SnippetArticle mSuggestion;

    public ContextualSuggestionsCardViewHolder(
            ViewGroup recyclerView, UiConfig uiConfig, SuggestionsUiDelegate uiDelegate) {
        super(LayoutInflater.from(recyclerView.getContext())
                        .inflate(R.layout.contextual_suggestions_card, recyclerView, false));

        mUiConfig = uiConfig;
        mUiDelegate = uiDelegate;
        mSuggestionsBinder = new SuggestionsBinder(itemView, uiDelegate);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO(fhorschig): Add metrics to be recorded for the contextual suggestions.
                int windowDisposition = WindowOpenDisposition.CURRENT_TAB;
                mUiDelegate.getEventReporter().onSuggestionOpened(
                        mSuggestion, windowDisposition, mUiDelegate.getSuggestionsRanker());
                mUiDelegate.getNavigationDelegate().openSnippet(windowDisposition, mSuggestion);
            }
        });

        mDisplayStyleObserver =
                new DisplayStyleObserverAdapter(itemView, uiConfig, new DisplayStyleObserver() {
                    @Override
                    public void onDisplayStyleChanged(UiConfig.DisplayStyle newDisplayStyle) {
                        updateCardWidth(newDisplayStyle);
                    }
                });
        mSuggestionsBinder.updateFieldsVisibility(/* showHeadline = */ true,
                /* showDescription = */ false, /* showThumbnail = */ true,
                /* showThumbnailVideoOverlay = */ false, /* headerMaxLines = */ 3);
    }

    public void onBindViewHolder(SnippetArticle suggestion) {
        mSuggestion = suggestion;
        mDisplayStyleObserver.attach();
        mSuggestionsBinder.updateViewInformation(mSuggestion);
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

    @Override
    public void recycle() {
        mDisplayStyleObserver.detach();
        mSuggestionsBinder.recycle();
        super.recycle();
    }
}