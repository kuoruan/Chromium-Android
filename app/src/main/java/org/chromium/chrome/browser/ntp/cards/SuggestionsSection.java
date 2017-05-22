// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;
import org.chromium.chrome.browser.ntp.snippets.SectionHeader;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticleViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.suggestions.SuggestionsOfflineModelObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A group of suggestions, with a header, a status card, and a progress indicator. This is
 * responsible for tracking whether its suggestions have been saved offline.
 */
public class SuggestionsSection extends InnerNode {
    private static final String TAG = "NtpCards";

    private final Delegate mDelegate;
    private final SuggestionsCategoryInfo mCategoryInfo;
    private final OfflineModelObserver mOfflineModelObserver;

    // Children
    private final SectionHeader mHeader;
    private final SuggestionsList mSuggestionsList;
    private final StatusItem mStatus;
    private final ActionItem mMoreButton;
    private final ProgressItem mProgressIndicator;

    /**
     * Keeps track of how many suggestions have been seen by the user so that we replace only
     * suggestions that have not been seen, yet.
     */
    private int mNumberOfSuggestionsSeen;

    /**
     * Stores whether any suggestions have been appended to the list. In this case the list can
     * generally be longer than what is served by the Source. Thus, the list should never be
     * replaced again.
     */
    private boolean mHasAppended;

    /**
     * Delegate interface that allows dismissing this section without introducing
     * a circular dependency.
     */
    public interface Delegate {
        void dismissSection(SuggestionsSection section);
    }

    public SuggestionsSection(Delegate delegate, SuggestionsUiDelegate uiDelegate,
            SuggestionsRanker ranker, OfflinePageBridge offlinePageBridge,
            SuggestionsCategoryInfo info) {
        mDelegate = delegate;
        mCategoryInfo = info;

        mHeader = new SectionHeader(info.getTitle());
        mSuggestionsList = new SuggestionsList(uiDelegate, ranker, info);
        mStatus = StatusItem.createNoSuggestionsItem(info);
        mMoreButton = new ActionItem(this, ranker);
        mProgressIndicator = new ProgressItem();
        addChildren(mHeader, mSuggestionsList, mStatus, mMoreButton, mProgressIndicator);

        mOfflineModelObserver = new OfflineModelObserver(offlinePageBridge);
        uiDelegate.addDestructionObserver(mOfflineModelObserver);

        mStatus.setVisible(!hasSuggestions());
    }

    private static class SuggestionsList extends ChildNode implements Iterable<SnippetArticle> {
        private final List<SnippetArticle> mSuggestions = new ArrayList<>();

        // TODO(crbug.com/677672): Replace by SuggestionSource when it handles destruction.
        private final SuggestionsUiDelegate mUiDelegate;
        private final SuggestionsRanker mSuggestionsRanker;
        private final SuggestionsCategoryInfo mCategoryInfo;

        public SuggestionsList(SuggestionsUiDelegate uiDelegate, SuggestionsRanker ranker,
                SuggestionsCategoryInfo categoryInfo) {
            mUiDelegate = uiDelegate;
            mSuggestionsRanker = ranker;
            mCategoryInfo = categoryInfo;
        }

        @Override
        protected int getItemCountForDebugging() {
            return mSuggestions.size();
        }

        @Override
        @ItemViewType
        public int getItemViewType(int position) {
            checkIndex(position);
            return ItemViewType.SNIPPET;
        }

        @Override
        public void onBindViewHolder(NewTabPageViewHolder holder, int position) {
            checkIndex(position);
            assert holder instanceof SnippetArticleViewHolder;
            SnippetArticle suggestion = getSuggestionAt(position);
            mSuggestionsRanker.rankSuggestion(suggestion);
            ((SnippetArticleViewHolder) holder).onBindViewHolder(suggestion, mCategoryInfo);
        }

        @Override
        public SnippetArticle getSuggestionAt(int position) {
            return mSuggestions.get(position);
        }

        public void clear() {
            int itemCount = mSuggestions.size();
            if (itemCount == 0) return;

            mSuggestions.clear();
            notifyItemRangeRemoved(0, itemCount);
        }

        /**
         * Clears all suggestions except for the first {@code n} suggestions.
         */
        private void clearAllButFirstN(int n) {
            int itemCount = mSuggestions.size();
            if (itemCount > n) {
                mSuggestions.subList(n, itemCount).clear();
                notifyItemRangeRemoved(n, itemCount - n);
            }
        }

        public void addAll(List<SnippetArticle> suggestions) {
            if (suggestions.isEmpty()) return;

            int insertionPointIndex = mSuggestions.size();
            mSuggestions.addAll(suggestions);
            notifyItemRangeInserted(insertionPointIndex, suggestions.size());
        }

        public SnippetArticle remove(int position) {
            SnippetArticle suggestion = mSuggestions.remove(position);
            notifyItemRemoved(position);
            return suggestion;
        }

        @Override
        public Iterator<SnippetArticle> iterator() {
            return mSuggestions.iterator();
        }

        @Override
        public Set<Integer> getItemDismissalGroup(int position) {
            return Collections.singleton(position);
        }

        @Override
        public void dismissItem(int position, Callback<String> itemRemovedCallback) {
            checkIndex(position);
            SuggestionsSource suggestionsSource = mUiDelegate.getSuggestionsSource();
            if (suggestionsSource == null) {
                // It is possible for this method to be called after the NewTabPage has had
                // destroy() called. This can happen when
                // NewTabPageRecyclerView.dismissWithAnimation() is called and the animation ends
                // after the user has navigated away. In this case we cannot inform the native side
                // that the snippet has been dismissed (http://crbug.com/649299).
                return;
            }

            SnippetArticle suggestion = remove(position);
            suggestionsSource.dismissSuggestion(suggestion);
            itemRemovedCallback.onResult(suggestion.mTitle);
        }

        public void updateSuggestionOfflineId(SnippetArticle article, Long newId) {
            int index = mSuggestions.indexOf(article);
            // The suggestions could have been removed / replaced in the meantime.
            if (index == -1) return;

            Long oldId = article.getOfflinePageOfflineId();
            article.setOfflinePageOfflineId(newId);

            if ((oldId == null) == (newId == null)) return;
            notifyItemChanged(
                    index, SnippetArticleViewHolder.REFRESH_OFFLINE_BADGE_VISIBILITY_CALLBACK);
        }
    }

    @Override
    @CallSuper
    public void detach() {
        mOfflineModelObserver.onDestroy();
        super.detach();
    }

    private void onSuggestionsListCountChanged(int oldSuggestionsCount) {
        int newSuggestionsCount = getSuggestionsCount();
        if ((newSuggestionsCount == 0) == (oldSuggestionsCount == 0)) return;

        mStatus.setVisible(newSuggestionsCount == 0);

        // When the ActionItem stops being dismissable, it is possible that it was being interacted
        // with. We need to reset the view's related property changes.
        if (mMoreButton.isVisible()) {
            mMoreButton.notifyItemChanged(0, NewTabPageRecyclerView.RESET_FOR_DISMISS_CALLBACK);
        }
    }

    @Override
    public void dismissItem(int position, Callback<String> itemRemovedCallback) {
        if (getSectionDismissalRange().contains(position)) {
            mDelegate.dismissSection(this);
            itemRemovedCallback.onResult(getHeaderText());
            return;
        }

        super.dismissItem(position, itemRemovedCallback);
    }

    @Override
    public void onItemRangeRemoved(TreeNode child, int index, int count) {
        super.onItemRangeRemoved(child, index, count);
        if (child == mSuggestionsList) onSuggestionsListCountChanged(getSuggestionsCount() + count);
    }

    @Override
    public void onItemRangeInserted(TreeNode child, int index, int count) {
        super.onItemRangeInserted(child, index, count);
        if (child == mSuggestionsList) onSuggestionsListCountChanged(getSuggestionsCount() - count);
    }

    @Override
    protected void notifyItemRangeInserted(int index, int count) {
        super.notifyItemRangeInserted(index, count);
        notifyNeighboursModified(index - 1, index + count);
    }

    @Override
    protected void notifyItemRangeRemoved(int index, int count) {
        super.notifyItemRangeRemoved(index, count);
        notifyNeighboursModified(index - 1, index);
    }

    /** Sends a notification to the items at the provided indices to refresh their background. */
    private void notifyNeighboursModified(int aboveNeighbour, int belowNeighbour) {
        assert aboveNeighbour < belowNeighbour;

        if (aboveNeighbour >= 0) {
            notifyItemChanged(aboveNeighbour, NewTabPageViewHolder.UPDATE_LAYOUT_PARAMS_CALLBACK);
        }

        if (belowNeighbour < getItemCount()) {
            notifyItemChanged(belowNeighbour, NewTabPageViewHolder.UPDATE_LAYOUT_PARAMS_CALLBACK);
        }
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        childSeen(position);
    }

    /**
     * Sets the child at position {@code position} as being seen by the user.
     * @param position Position in the list being shown (the first suggestion being at index 1,
     * as at index 0, there is a non-suggestion).
     */
    private void childSeen(int position) {
        Log.d(TAG, "childSeen: position %d in category %d", position, mCategoryInfo.getCategory());

        if (getItemViewType(position) == ItemViewType.SNIPPET) {
            // We assume that all suggestions are clustered together, so the number seen can be
            // obtained from the index of the last suggestion seen.
            int firstSuggestionPosition = getStartingOffsetForChild(mSuggestionsList);
            mNumberOfSuggestionsSeen =
                    Math.max(mNumberOfSuggestionsSeen, position - firstSuggestionPosition + 1);
        }
    }

    /**
     * Removes a suggestion. Does nothing if the ID is unknown.
     * @param idWithinCategory The ID of the suggestion to remove.
     */
    public void removeSuggestionById(String idWithinCategory) {
        int i = 0;
        for (SnippetArticle suggestion : mSuggestionsList) {
            if (suggestion.mIdWithinCategory.equals(idWithinCategory)) {
                mSuggestionsList.remove(i);
                return;
            }
            i++;
        }
    }

    public boolean hasSuggestions() {
        return mSuggestionsList.getItemCount() != 0;
    }

    public int getSuggestionsCount() {
        return mSuggestionsList.getItemCount();
    }

    public String[] getDisplayedSuggestionIds() {
        String[] suggestionIds = new String[mSuggestionsList.getItemCount()];
        for (int i = 0; i < mSuggestionsList.getItemCount(); ++i) {
            suggestionIds[i] = mSuggestionsList.getSuggestionAt(i).mIdWithinCategory;
        }
        return suggestionIds;
    }

    /**
     * Puts {@code suggestions} into this section. It can either replace all existing suggestions
     * with the new ones or append the new suggestions at the end of the list. This call may have no
     * or only partial effect if changing the list of suggestions is not allowed (e.g. because the
     * user has already seen the suggestions).
     * @param suggestions The new list of suggestions for the given category.
     * @param status The new category status.
     * @param replaceExisting If true, {@code suggestions} replace the current list of suggestions.
     * If false, {@code suggestions} are appended to current list of suggestions.
     */
    public void setSuggestions(List<SnippetArticle> suggestions, @CategoryStatusEnum int status,
            boolean replaceExisting) {
        Log.d(TAG, "setSuggestions: previous number of suggestions: %d; replace existing: %b",
                mSuggestionsList.getItemCount(), replaceExisting);
        if (!SnippetsBridge.isCategoryStatusAvailable(status)) mSuggestionsList.clear();

        if (!replaceExisting) mHasAppended = true;

        // Remove suggestions to be replaced.
        if (replaceExisting && hasSuggestions()) {
            if (CardsVariationParameters.ignoreUpdatesForExistingSuggestions()) {
                Log.d(TAG, "setSuggestions: replacing existing suggestion disabled");
                NewTabPageUma.recordUIUpdateResult(NewTabPageUma.UI_UPDATE_FAIL_DISABLED);
                return;
            }

            if (mNumberOfSuggestionsSeen >= getSuggestionsCount() || mHasAppended) {
                Log.d(TAG, "setSuggestions: replacing existing suggestion not possible, all seen");
                NewTabPageUma.recordUIUpdateResult(NewTabPageUma.UI_UPDATE_FAIL_ALL_SEEN);
                return;
            }

            Log.d(TAG, "setSuggestions: keeping the first %d suggestion",
                        mNumberOfSuggestionsSeen);
            mSuggestionsList.clearAllButFirstN(mNumberOfSuggestionsSeen);

            if (mNumberOfSuggestionsSeen > 0) {
                // Make sure that mSuggestionsList will contain as many elements as newly provided
                // in suggestions. Remove the kept first element from the new collection, if it
                // repeats there. Otherwise, remove the last element of the new collection.
                int targetCountToAppend =
                        Math.max(0, suggestions.size() - mNumberOfSuggestionsSeen);
                for (SnippetArticle suggestion : mSuggestionsList) {
                    suggestions.remove(suggestion);
                }
                if (suggestions.size() > targetCountToAppend) {
                    Log.d(TAG, "setSuggestions: removing %d excess elements from the end",
                            suggestions.size() - targetCountToAppend);
                    suggestions.subList(targetCountToAppend, suggestions.size()).clear();
                }
            }
            NewTabPageUma.recordNumberOfSuggestionsSeenBeforeUIUpdateSuccess(
                    mNumberOfSuggestionsSeen);
            NewTabPageUma.recordUIUpdateResult(NewTabPageUma.UI_UPDATE_SUCCESS_REPLACED);
        } else {
            NewTabPageUma.recordUIUpdateResult(NewTabPageUma.UI_UPDATE_SUCCESS_APPENDED);
        }

        mProgressIndicator.setVisible(SnippetsBridge.isCategoryLoading(status));

        mSuggestionsList.addAll(suggestions);

        for (SnippetArticle article : suggestions) {
            if (!article.requiresExactOfflinePage()) {
                mOfflineModelObserver.updateOfflinableSuggestionAvailability(article);
            }
        }
    }



    /** Lets the {@link SuggestionsSection} know when a suggestion fetch has been started. */
    public void onFetchStarted() {
        mProgressIndicator.setVisible(true);
    }

    /** Sets the status for the section. Some statuses can cause the suggestions to be cleared. */
    public void setStatus(@CategoryStatusEnum int status) {
        if (!SnippetsBridge.isCategoryStatusAvailable(status)) mSuggestionsList.clear();
        mProgressIndicator.setVisible(SnippetsBridge.isCategoryLoading(status));
    }

    @CategoryInt
    public int getCategory() {
        return mCategoryInfo.getCategory();
    }

    @Override
    public Set<Integer> getItemDismissalGroup(int position) {
        // The section itself can be dismissed via any of the items in the dismissal group,
        // otherwise we fall back to the default implementation, which dispatches to our children.
        Set<Integer> sectionDismissalRange = getSectionDismissalRange();
        if (sectionDismissalRange.contains(position)) return sectionDismissalRange;

        return super.getItemDismissalGroup(position);
    }

    /** Sets the visibility of this section's header. */
    public void setHeaderVisibility(boolean headerVisibility) {
        mHeader.setVisible(headerVisibility);
    }

    /**
     * @return The set of indices corresponding to items that can dismiss this entire section
     * (as opposed to individual items in it).
     */
    private Set<Integer> getSectionDismissalRange() {
        if (hasSuggestions()) return Collections.emptySet();

        int statusCardIndex = getStartingOffsetForChild(mStatus);
        if (!mMoreButton.isVisible()) return Collections.singleton(statusCardIndex);

        assert statusCardIndex + 1 == getStartingOffsetForChild(mMoreButton);
        return new HashSet<>(Arrays.asList(statusCardIndex, statusCardIndex + 1));
    }

    public SuggestionsCategoryInfo getCategoryInfo() {
        return mCategoryInfo;
    }

    public String getHeaderText() {
        return mHeader.getHeaderText();
    }

    ProgressItem getProgressItemForTesting() {
        return mProgressIndicator;
    }

    ActionItem getActionItemForTesting() {
        return mMoreButton;
    }

    SectionHeader getHeaderItemForTesting() {
        return mHeader;
    }

    private class OfflineModelObserver extends SuggestionsOfflineModelObserver<SnippetArticle> {
        public OfflineModelObserver(OfflinePageBridge bridge) {
            super(bridge);
        }

        @Override
        public void onSuggestionOfflineIdChanged(SnippetArticle suggestion, @Nullable Long id) {
            mSuggestionsList.updateSuggestionOfflineId(suggestion, id);
        }

        @Override
        public Iterable<SnippetArticle> getOfflinableSuggestions() {
            return mSuggestionsList;
        }
    }
}
