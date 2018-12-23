// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for providing data and views to the omnibox results list.
 */
@VisibleForTesting
public class OmniboxResultsAdapter extends BaseAdapter {

    private final List<OmniboxResultItem> mSuggestionItems;
    private final Context mContext;
    private ToolbarDataProvider mDataProvider;
    private OmniboxSuggestionDelegate mSuggestionDelegate;
    private boolean mUseDarkColors = true;
    private Set<String> mPendingAnswerRequestUrls = new HashSet<>();
    private int mLayoutDirection;

    public OmniboxResultsAdapter(
            Context context,
            List<OmniboxResultItem> suggestionItems) {
        mContext = context;
        mSuggestionItems = suggestionItems;
    }

    public void notifySuggestionsChanged() {
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mSuggestionItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mSuggestionItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SuggestionView suggestionView;
        if (convertView instanceof SuggestionView) {
            suggestionView = (SuggestionView) convertView;
        } else {
            suggestionView = new SuggestionView(mContext);
        }
        OmniboxResultItem item = mSuggestionItems.get(position);
        maybeFetchAnswerIcon(item);

        suggestionView.init(item, mSuggestionDelegate, position, mUseDarkColors);
        ViewCompat.setLayoutDirection(suggestionView, mLayoutDirection);

        return suggestionView;
    }

    private void maybeFetchAnswerIcon(OmniboxResultItem item) {
        ThreadUtils.assertOnUiThread();

        // Attempting to fetch answer data before we have a profile to request it for.
        if (mDataProvider == null) return;

        // Do not refetch an answer image if it already exists.
        if (item.getAnswerImage() != null) return;
        OmniboxSuggestion suggestion = item.getSuggestion();
        final String url = getAnswerImageRequestUrl(suggestion);
        if (url == null) return;

        // Do not make duplicate answer image requests for the same URL (to avoid generating
        // duplicate bitmaps for the same image).
        if (mPendingAnswerRequestUrls.contains(url)) return;

        mPendingAnswerRequestUrls.add(url);
        AnswersImage.requestAnswersImage(
                mDataProvider.getProfile(), url, new AnswersImage.AnswersImageObserver() {
                    @Override
                    public void onAnswersImageChanged(Bitmap bitmap) {
                        ThreadUtils.assertOnUiThread();

                        onAnswerImageReceived(url, bitmap);
                        boolean retVal = mPendingAnswerRequestUrls.remove(url);
                        assert retVal : "Pending answer URL should exist";
                    }
                });
    }

    private String getAnswerImageRequestUrl(OmniboxSuggestion suggestion) {
        if (!suggestion.hasAnswer()) return null;
        return suggestion.getAnswer().getSecondLine().getImage();
    }

    private void onAnswerImageReceived(String url, Bitmap bitmap) {
        boolean didUpdateImage = false;
        for (int i = 0; i < mSuggestionItems.size(); i++) {
            String answerUrl = getAnswerImageRequestUrl(mSuggestionItems.get(i).getSuggestion());
            if (TextUtils.equals(answerUrl, url)) {
                mSuggestionItems.get(i).setAnswerImage(bitmap);
                didUpdateImage = true;
            }
        }
        if (didUpdateImage) {
            notifyDataSetChanged();
        }
    }

    /**
     * Sets the data provider for the toolbar.
     */
    public void setToolbarDataProvider(ToolbarDataProvider provider) {
        mDataProvider = provider;
    }

    /**
     * Set the selection delegate for suggestion entries in the adapter.
     *
     * @param delegate The delegate for suggestion selections.
     */
    public void setSuggestionDelegate(OmniboxSuggestionDelegate delegate) {
        mSuggestionDelegate = delegate;
    }

    /**
     * Sets the layout direction to be used for any new suggestion views.
     * @see View#setLayoutDirection(int)
     */
    public void setLayoutDirection(int layoutDirection) {
        mLayoutDirection = layoutDirection;
    }

    /**
     * @return The selection delegate for suggestion entries in the adapter.
     */
    @VisibleForTesting
    public OmniboxSuggestionDelegate getSuggestionDelegate() {
        return mSuggestionDelegate;
    }

    /**
     * Specifies the visual state to be used by the suggestions.
     * @param useDarkColors Whether dark colors should be used for fonts and icons.
     */
    public void setUseDarkColors(boolean useDarkColors) {
        mUseDarkColors = useDarkColors;
    }

    /**
     * Handler for actions that happen on suggestion view.
     */
    @VisibleForTesting
    public static interface OmniboxSuggestionDelegate {
        /**
         * Triggered when the user selects one of the omnibox suggestions to navigate to.
         * @param suggestion The OmniboxSuggestion which was selected.
         * @param position Position of the suggestion in the drop down view.
         */
        public void onSelection(OmniboxSuggestion suggestion, int position);

        /**
         * Triggered when the user selects to refine one of the omnibox suggestions.
         * @param suggestion The suggestion selected.
         */
        public void onRefineSuggestion(OmniboxSuggestion suggestion);

        /**
         * Triggered when the user long presses the omnibox suggestion.
         * @param suggestion The suggestion selected.
         * @param position The position of the suggestion.
         */
        public void onLongPress(OmniboxSuggestion suggestion, int position);

        /**
         * Triggered when the user navigates to one of the suggestions without clicking on it.
         * @param suggestion The suggestion that was selected.
         */
        public void onSetUrlToSuggestion(OmniboxSuggestion suggestion);

        /**
         * Triggered when the user touches the suggestion view.
         */
        public void onGestureDown();

        /**
         * Triggered when the user touch on the suggestion view finishes.
         * @param ev the event for the ACTION_UP.
         */
        public void onGestureUp(long timetamp);

        /**
         * Triggered when text width information is updated.
         * These values should be used to calculate max text widths.
         * @param requiredWidth a new required width.
         * @param matchContentsWidth a new match contents width.
         */
        public void onTextWidthsUpdated(float requiredWidth, float matchContentsWidth);

        /**
         * @return max required width for the suggestion.
         */
        public float getMaxRequiredWidth();

        /**
         * @return max match contents width for the suggestion.
         */
        public float getMaxMatchContentsWidth();
    }

    /**
     * Simple wrapper around the omnibox suggestions provided in the backend and the query that
     * matched it.
     */
    @VisibleForTesting
    public static class OmniboxResultItem {
        private final OmniboxSuggestion mSuggestion;
        private final String mMatchedQuery;
        private Bitmap mAnswerImage;

        public OmniboxResultItem(OmniboxSuggestion suggestion, String matchedQuery) {
            mSuggestion = suggestion;
            mMatchedQuery = matchedQuery;
        }

        /**
         * @return The omnibox suggestion for this item.
         */
        public OmniboxSuggestion getSuggestion() {
            return mSuggestion;
        }

        /**
         * @return The user query that triggered this suggestion to be shown.
         */
        public String getMatchedQuery() {
            return mMatchedQuery;
        }

        /**
         * @return The image associated with the answer for this suggestion (if applicable).
         */
        @Nullable
        public Bitmap getAnswerImage() {
            return mAnswerImage;
        }

        private void setAnswerImage(Bitmap bitmap) {
            mAnswerImage = bitmap;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof OmniboxResultItem)) {
                return false;
            }

            OmniboxResultItem item = (OmniboxResultItem) o;
            return mMatchedQuery.equals(item.mMatchedQuery) && mSuggestion.equals(item.mSuggestion);
        }

        @Override
        public int hashCode() {
            return 53 * mMatchedQuery.hashCode() ^ mSuggestion.hashCode();
        }
    }
}
