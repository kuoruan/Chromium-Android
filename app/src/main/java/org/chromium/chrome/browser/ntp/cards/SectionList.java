// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.suggestions.DestructionObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in the tree containing a list of all suggestions sections. It listens to changes in the
 * suggestions source and updates the corresponding sections.
 */
public class SectionList
        extends InnerNode implements SuggestionsSource.Observer, SuggestionsSection.Delegate {
    private static final String TAG = "Ntp";

    /** Maps suggestion categories to sections, with stable iteration ordering. */
    private final Map<Integer, SuggestionsSection> mSections = new LinkedHashMap<>();
    private final SuggestionsUiDelegate mUiDelegate;
    private final OfflinePageBridge mOfflinePageBridge;
    private final SuggestionsRanker mSuggestionsRanker;

    public SectionList(SuggestionsUiDelegate uiDelegate, OfflinePageBridge offlinePageBridge) {
        mSuggestionsRanker = new SuggestionsRanker();
        mUiDelegate = uiDelegate;
        mUiDelegate.getSuggestionsSource().setObserver(this);
        mUiDelegate.getMetricsReporter().setRanker(mSuggestionsRanker);
        mOfflinePageBridge = offlinePageBridge;

        mUiDelegate.addDestructionObserver(new DestructionObserver() {
            @Override
            public void onDestroy() {
                removeAllSections();
            }
        });
    }

    /**
     * Resets the sections, reloading the whole new tab page content.
     * @param alwaysAllowEmptySections Whether sections are always allowed to be displayed when
     *     they are empty, even when they are normally not.
     */
    private void resetSections(boolean alwaysAllowEmptySections) {
        removeAllSections();

        SuggestionsSource suggestionsSource = mUiDelegate.getSuggestionsSource();
        int[] categories = suggestionsSource.getCategories();
        int[] suggestionsPerCategory = new int[categories.length];
        int categoryIndex = 0;
        for (int category : categories) {
            int categoryStatus = suggestionsSource.getCategoryStatus(category);
            if (categoryStatus == CategoryStatus.LOADING_ERROR
                    || categoryStatus == CategoryStatus.NOT_PROVIDED
                    || categoryStatus == CategoryStatus.CATEGORY_EXPLICITLY_DISABLED)
                continue;

            suggestionsPerCategory[categoryIndex] =
                    resetSection(category, categoryStatus, alwaysAllowEmptySections);
            ++categoryIndex;
        }

        maybeHideArticlesHeader();
        mUiDelegate.getMetricsReporter().onPageShown(categories, suggestionsPerCategory);
    }

    /**
     * Resets the section for {@code category}. Removes the section if there are no suggestions for
     * it and it is not allowed to be empty. Otherwise, creates the section if it is not present
     * yet. Sets the available suggestions on the section.
     * @param category The category for which the section must be reset.
     * @param categoryStatus The category status.
     * @param alwaysAllowEmptySections Whether sections are always allowed to be displayed when
     *     they are empty, even when they are normally not.
     * @return The number of suggestions for the section.
     */
    private int resetSection(@CategoryInt int category, @CategoryStatusEnum int categoryStatus,
            boolean alwaysAllowEmptySections) {
        SuggestionsSource suggestionsSource = mUiDelegate.getSuggestionsSource();
        List<SnippetArticle> suggestions = suggestionsSource.getSuggestionsForCategory(category);
        SuggestionsCategoryInfo info = suggestionsSource.getCategoryInfo(category);

        SuggestionsSection section = mSections.get(category);

        // Do not show an empty section if not allowed.
        if (suggestions.isEmpty() && !info.showIfEmpty() && !alwaysAllowEmptySections) {
            if (section != null) removeSection(section);
            return 0;
        }

        // Create the section if needed.
        if (section == null) {
            section = new SuggestionsSection(
                    this, mUiDelegate, mSuggestionsRanker, mOfflinePageBridge, info);
            mSections.put(category, section);
            mSuggestionsRanker.registerCategory(category);
            addChild(section);
        }

        // Set the new suggestions.
        setSuggestions(category, suggestions, categoryStatus, /* replaceExisting = */ true);

        return suggestions.size();
    }

    @Override
    public void onNewSuggestions(@CategoryInt int category) {
        @CategoryStatusEnum
        int status = mUiDelegate.getSuggestionsSource().getCategoryStatus(category);

        if (!canLoadSuggestions(category, status)) return;

        List<SnippetArticle> suggestions =
                mUiDelegate.getSuggestionsSource().getSuggestionsForCategory(category);

        Log.d(TAG, "Received %d new suggestions for category %d.", suggestions.size(), category);

        // At first, there might be no suggestions available, we wait until they have been fetched.
        if (suggestions.isEmpty()) return;

        setSuggestions(category, suggestions, status, /* replaceExisting = */ true);
    }

    @Override
    public void onMoreSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions) {
        @CategoryStatusEnum
        int status = mUiDelegate.getSuggestionsSource().getCategoryStatus(category);
        if (!canLoadSuggestions(category, status)) return;

        setSuggestions(category, suggestions, status, /* replaceExisting = */ false);
    }

    @Override
    public void onCategoryStatusChanged(@CategoryInt int category, @CategoryStatusEnum int status) {
        // Observers should not be registered for this state.
        assert status != CategoryStatus.ALL_SUGGESTIONS_EXPLICITLY_DISABLED;

        // If there is no section for this category there is nothing to do.
        if (!mSections.containsKey(category)) return;

        switch (status) {
            case CategoryStatus.NOT_PROVIDED:
                // The section provider has gone away. Keep open UIs as they are.
                return;

            case CategoryStatus.CATEGORY_EXPLICITLY_DISABLED:
            case CategoryStatus.LOADING_ERROR:
                // Need to remove the entire section from the UI immediately.
                removeSection(mSections.get(category));
                return;

            case CategoryStatus.SIGNED_OUT:
                resetSection(category, status, /* alwaysAllowEmptySections = */ false);
                return;

            default:
                mSections.get(category).setStatus(status);
                return;
        }
    }

    @Override
    public void onSuggestionInvalidated(@CategoryInt int category, String idWithinCategory) {
        if (!mSections.containsKey(category)) return;
        mSections.get(category).removeSuggestionById(idWithinCategory);
    }

    @Override
    public void onFullRefreshRequired() {
        refreshSuggestions();
    }

    /**
     * Resets all the sections, getting the current list of categories and the associated
     * suggestions from the backend.
     */
    public void refreshSuggestions() {
        resetSections(/* alwaysAllowEmptySections = */false);
    }

    /**
     * Puts {@code suggestions} into given {@code category}. It can either replace all existing
     * suggestions with the new ones or append the new suggestions at the end of the list. This call
     * may have no or only partial effect if changing the list of suggestions is not allowed (e.g.
     * because the user has already seen the suggestions).
     * @param category The category for which the suggestions should be set.
     * @param suggestions The new list of suggestions for the given category.
     * @param status The new category status.
     * @param replaceExisting If true, {@code suggestions} replace the current list of suggestions.
     * If false, {@code suggestions} are appended to current list of suggestions.
     */
    private void setSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions,
            @CategoryStatusEnum int status, boolean replaceExisting) {
        mSections.get(category).setSuggestions(suggestions, status, replaceExisting);
    }

    private boolean canLoadSuggestions(@CategoryInt int category, @CategoryStatusEnum int status) {
        // We never want to add suggestions from unknown categories.
        if (!mSections.containsKey(category)) return false;

        // The status may have changed while the suggestions were loading, perhaps they should not
        // be displayed any more.
        if (!SnippetsBridge.isCategoryEnabled(status)) {
            Log.w(TAG, "Received suggestions for a disabled category (id=%d, status=%d)", category,
                    status);
            return false;
        }

        return true;
    }

    /**
     * Dismisses a section.
     * @param section The section to be dismissed.
     */
    @Override
    public void dismissSection(SuggestionsSection section) {
        mUiDelegate.getSuggestionsSource().dismissCategory(section.getCategory());
        removeSection(section);
    }

    @VisibleForTesting
    void removeSection(SuggestionsSection section) {
        mSections.remove(section.getCategory());
        removeChild(section);
    }

    private void removeAllSections() {
        mSections.clear();
        removeChildren();
    }

    /** Hides the header for the {@link KnownCategories#ARTICLES} section when necessary. */
    private void maybeHideArticlesHeader() {
        // If there is more than a section we want to show the headers for disambiguation purposes.
        if (mSections.size() != 1) return;

        SuggestionsSection articlesSection = mSections.get(KnownCategories.ARTICLES);
        if (articlesSection == null) return;

        articlesSection.setHeaderVisibility(false);
    }

    /**
     * Restores any sections that have been dismissed and triggers a new fetch.
     */
    public void restoreDismissedSections() {
        mUiDelegate.getSuggestionsSource().restoreDismissedCategories();
        resetSections(/* allowEmptySections = */ true);
        mUiDelegate.getSuggestionsSource().fetchRemoteSuggestions();
    }

    /**
     * @return Whether the list of sections is empty.
     */
    public boolean isEmpty() {
        return mSections.isEmpty();
    }

    SuggestionsSection getSectionForTesting(@CategoryInt int categoryId) {
        return mSections.get(categoryId);
    }
}
