// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.cards.ActionItem;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.suggestions.DestructionObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsMetricsReporter;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to the snippets to display on the NTP using the C++ ContentSuggestionsService.
 */
public class SnippetsBridge
        implements SuggestionsSource, SuggestionsMetricsReporter, DestructionObserver {
    private static final String TAG = "SnippetsBridge";

    private long mNativeSnippetsBridge;
    private SuggestionsSource.Observer mObserver;
    private SuggestionsRanker mSuggestionsRanker;

    public static boolean isCategoryStatusAvailable(@CategoryStatusEnum int status) {
        // Note: This code is duplicated in content_suggestions_category_status.cc.
        return status == CategoryStatus.AVAILABLE_LOADING || status == CategoryStatus.AVAILABLE;
    }

    /** Returns whether the category is considered "enabled", and can show content suggestions. */
    public static boolean isCategoryEnabled(@CategoryStatusEnum int status) {
        switch (status) {
            case CategoryStatus.INITIALIZING:
            case CategoryStatus.AVAILABLE:
            case CategoryStatus.AVAILABLE_LOADING:
            case CategoryStatus.SIGNED_OUT:
                return true;
        }
        return false;
    }

    public static boolean isCategoryLoading(@CategoryStatusEnum int status) {
        return status == CategoryStatus.AVAILABLE_LOADING || status == CategoryStatus.INITIALIZING;
    }

    /**
     * Creates a SnippetsBridge for getting snippet data for the current user.
     *
     * @param profile Profile of the user that we will retrieve snippets for.
     */
    public SnippetsBridge(Profile profile) {
        mNativeSnippetsBridge = nativeInit(profile);
    }

    /**
     * Destroys the native bridge. This object can no longer be used to send native commands, and
     * any observer is nulled out and will stop receiving updates. This object should be discarded.
     */
    @Override
    public void onDestroy() {
        assert mNativeSnippetsBridge != 0;
        nativeDestroy(mNativeSnippetsBridge);
        mNativeSnippetsBridge = 0;
        mObserver = null;
    }

    /**
     * Reschedules the fetching of snippets.
     */
    public static void rescheduleFetching() {
        nativeRemoteSuggestionsSchedulerRescheduleFetching();
    }

    /**
     * Fetches remote suggestions in background.
     */
    public static void fetchRemoteSuggestionsFromBackground() {
        nativeRemoteSuggestionsSchedulerOnFetchDue();
    }

    @Override
    public void fetchRemoteSuggestions() {
        nativeReloadSuggestions(mNativeSnippetsBridge);
    }

    @Override
    public int[] getCategories() {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategories(mNativeSnippetsBridge);
    }

    @Override
    @CategoryStatusEnum
    public int getCategoryStatus(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategoryStatus(mNativeSnippetsBridge, category);
    }

    @Override
    public SuggestionsCategoryInfo getCategoryInfo(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategoryInfo(mNativeSnippetsBridge, category);
    }

    @Override
    public List<SnippetArticle> getSuggestionsForCategory(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetSuggestionsForCategory(mNativeSnippetsBridge, category);
    }

    @Override
    public void fetchSuggestionImage(SnippetArticle suggestion, Callback<Bitmap> callback) {
        assert mNativeSnippetsBridge != 0;
        nativeFetchSuggestionImage(mNativeSnippetsBridge, suggestion.mCategory,
                suggestion.mIdWithinCategory, callback);
    }

    @Override
    public void dismissSuggestion(SnippetArticle suggestion) {
        assert mNativeSnippetsBridge != 0;
        nativeDismissSuggestion(mNativeSnippetsBridge, suggestion.mUrl, suggestion.getGlobalRank(),
                suggestion.mCategory, suggestion.getPerSectionRank(), suggestion.mIdWithinCategory);
    }

    @Override
    public void dismissCategory(@CategoryInt int category) {
        assert mNativeSnippetsBridge != 0;
        nativeDismissCategory(mNativeSnippetsBridge, category);
    }

    @Override
    public void restoreDismissedCategories() {
        assert mNativeSnippetsBridge != 0;
        nativeRestoreDismissedCategories(mNativeSnippetsBridge);
    }

    @Override
    public void onPageShown(int[] categories, int[] suggestionsPerCategory) {
        assert mNativeSnippetsBridge != 0;
        nativeOnPageShown(mNativeSnippetsBridge, categories, suggestionsPerCategory);
    }

    @Override
    public void onSuggestionShown(SnippetArticle suggestion) {
        assert mNativeSnippetsBridge != 0;
        nativeOnSuggestionShown(mNativeSnippetsBridge, suggestion.getGlobalRank(),
                suggestion.mCategory, suggestion.getPerSectionRank(),
                suggestion.mPublishTimestampMilliseconds, suggestion.mScore,
                suggestion.mFetchTimestampMilliseconds);
    }

    @Override
    public void onSuggestionOpened(SnippetArticle suggestion, int windowOpenDisposition) {
        assert mNativeSnippetsBridge != 0;
        int categoryIndex = mSuggestionsRanker.getCategoryRank(suggestion.mCategory);
        nativeOnSuggestionOpened(mNativeSnippetsBridge, suggestion.getGlobalRank(),
                suggestion.mCategory, categoryIndex, suggestion.getPerSectionRank(),
                suggestion.mPublishTimestampMilliseconds, suggestion.mScore, windowOpenDisposition);
    }

    @Override
    public void onSuggestionMenuOpened(SnippetArticle suggestion) {
        assert mNativeSnippetsBridge != 0;
        nativeOnSuggestionMenuOpened(mNativeSnippetsBridge, suggestion.getGlobalRank(),
                suggestion.mCategory, suggestion.getPerSectionRank(),
                suggestion.mPublishTimestampMilliseconds, suggestion.mScore);
    }

    @Override
    public void onMoreButtonShown(ActionItem actionItem) {
        assert mNativeSnippetsBridge != 0;
        nativeOnMoreButtonShown(
                mNativeSnippetsBridge, actionItem.getCategory(), actionItem.getPerSectionRank());
    }

    @Override
    public void onMoreButtonClicked(ActionItem actionItem) {
        assert mNativeSnippetsBridge != 0;
        @CategoryInt
        int category = actionItem.getCategory();
        nativeOnMoreButtonClicked(mNativeSnippetsBridge, category, actionItem.getPerSectionRank());
        switch (category) {
            case KnownCategories.BOOKMARKS:
                NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_BOOKMARKS_MANAGER);
                break;
            // MORE button in both categories leads to the recent tabs manager
            case KnownCategories.FOREIGN_TABS:
            case KnownCategories.RECENT_TABS:
                NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_RECENT_TABS_MANAGER);
                break;
            case KnownCategories.DOWNLOADS:
                NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_DOWNLOADS_MANAGER);
                break;
            default:
                // No action associated
                break;
        }
    }

    /**
     * Notifies the scheduler to adjust the plan due to a newly opened NTP.
     */
    public void onNtpInitialized() {
        assert mNativeSnippetsBridge != 0;
        nativeOnNTPInitialized(mNativeSnippetsBridge);
    }

    public static void notifySchedulerAboutWarmResume() {
        SnippetsBridge snippetsBridge = new SnippetsBridge(Profile.getLastUsedProfile());
        snippetsBridge.onActivityWarmResumed();
    }

    public static void notifySchedulerAboutColdStart() {
        SnippetsBridge snippetsBridge = new SnippetsBridge(Profile.getLastUsedProfile());
        snippetsBridge.onColdStart();
    }

    public static void onSuggestionTargetVisited(int category, long visitTimeMs) {
        nativeOnSuggestionTargetVisited(category, visitTimeMs);
    }

    @Override
    public void setObserver(Observer observer) {
        assert observer != null;
        mObserver = observer;
    }

    @Override
    public void setRanker(SuggestionsRanker suggestionsRanker) {
        assert suggestionsRanker != null;
        mSuggestionsRanker = suggestionsRanker;
    }

    @Override
    public void fetchSuggestions(@CategoryInt int category, String[] displayedSuggestionIds) {
        nativeFetch(mNativeSnippetsBridge, category, displayedSuggestionIds);
    }

    private void onActivityWarmResumed() {
        assert mNativeSnippetsBridge != 0;
        nativeOnActivityWarmResumed(mNativeSnippetsBridge);
    }

    private void onColdStart() {
        assert mNativeSnippetsBridge != 0;
        nativeOnColdStart(mNativeSnippetsBridge);
    }

    @CalledByNative
    private static List<SnippetArticle> createSuggestionList() {
        return new ArrayList<>();
    }

    @CalledByNative
    private static SnippetArticle addSuggestion(List<SnippetArticle> suggestions, int category,
            String id, String title, String publisher, String previewText, String url,
            long timestamp, float score, long fetchTime) {
        int position = suggestions.size();
        suggestions.add(new SnippetArticle(
                category, id, title, publisher, previewText, url, timestamp, score, fetchTime));
        return suggestions.get(position);
    }

    @CalledByNative
    private static void setAssetDownloadDataForSuggestion(
            SnippetArticle suggestion, String filePath, String mimeType) {
        suggestion.setAssetDownloadData(filePath, mimeType);
    }

    @CalledByNative
    private static void setOfflinePageDownloadDataForSuggestion(
            SnippetArticle suggestion, long offlinePageId) {
        suggestion.setOfflinePageDownloadData(offlinePageId);
    }

    @CalledByNative
    private static void setRecentTabDataForSuggestion(
            SnippetArticle suggestion, int tabId, long offlinePageId) {
        suggestion.setRecentTabData(tabId, offlinePageId);
    }

    @CalledByNative
    private static SuggestionsCategoryInfo createSuggestionsCategoryInfo(int category, String title,
            int cardLayout, boolean hasFetchAction, boolean hasViewAllAction, boolean showIfEmpty,
            String noSuggestionsMessage) {
        return new SuggestionsCategoryInfo(category, title, cardLayout, hasFetchAction,
                hasViewAllAction, showIfEmpty, noSuggestionsMessage);
    }

    @CalledByNative
    private void onNewSuggestions(@CategoryInt int category) {
        if (mObserver != null) mObserver.onNewSuggestions(category);
    }

    @CalledByNative
    private void onMoreSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions) {
        if (mObserver != null) mObserver.onMoreSuggestions(category, suggestions);
    }

    @CalledByNative
    private void onCategoryStatusChanged(
            @CategoryInt int category, @CategoryStatusEnum int newStatus) {
        if (mObserver != null) mObserver.onCategoryStatusChanged(category, newStatus);
    }

    @CalledByNative
    private void onSuggestionInvalidated(@CategoryInt int category, String idWithinCategory) {
        if (mObserver != null) mObserver.onSuggestionInvalidated(category, idWithinCategory);
    }

    @CalledByNative
    private void onFullRefreshRequired() {
        if (mObserver != null) mObserver.onFullRefreshRequired();
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeNTPSnippetsBridge);
    private native void nativeReloadSuggestions(long nativeNTPSnippetsBridge);
    private static native void nativeRemoteSuggestionsSchedulerOnFetchDue();
    private static native void nativeRemoteSuggestionsSchedulerRescheduleFetching();
    private native int[] nativeGetCategories(long nativeNTPSnippetsBridge);
    private native int nativeGetCategoryStatus(long nativeNTPSnippetsBridge, int category);
    private native SuggestionsCategoryInfo nativeGetCategoryInfo(
            long nativeNTPSnippetsBridge, int category);
    private native List<SnippetArticle> nativeGetSuggestionsForCategory(
            long nativeNTPSnippetsBridge, int category);
    private native void nativeFetchSuggestionImage(long nativeNTPSnippetsBridge, int category,
            String idWithinCategory, Callback<Bitmap> callback);
    private native void nativeFetch(
            long nativeNTPSnippetsBridge, int category, String[] knownSuggestions);
    private native void nativeDismissSuggestion(long nativeNTPSnippetsBridge, String url,
            int globalPosition, int category, int positionInCategory, String idWithinCategory);
    private native void nativeDismissCategory(long nativeNTPSnippetsBridge, int category);
    private native void nativeRestoreDismissedCategories(long nativeNTPSnippetsBridge);
    private native void nativeOnPageShown(
            long nativeNTPSnippetsBridge, int[] categories, int[] suggestionsPerCategory);
    private native void nativeOnSuggestionShown(long nativeNTPSnippetsBridge, int globalPosition,
            int category, int positionInCategory, long publishTimestampMs, float score,
            long fetchTimestampMs);
    private native void nativeOnSuggestionOpened(long nativeNTPSnippetsBridge, int globalPosition,
            int category, int categoryIndex, int positionInCategory, long publishTimestampMs,
            float score, int windowOpenDisposition);
    private native void nativeOnSuggestionMenuOpened(long nativeNTPSnippetsBridge,
            int globalPosition, int category, int positionInCategory, long publishTimestampMs,
            float score);
    private native void nativeOnMoreButtonShown(
            long nativeNTPSnippetsBridge, int category, int position);
    private native void nativeOnMoreButtonClicked(
            long nativeNTPSnippetsBridge, int category, int position);
    private native void nativeOnActivityWarmResumed(long nativeNTPSnippetsBridge);
    private native void nativeOnColdStart(long nativeNTPSnippetsBridge);
    private static native void nativeOnSuggestionTargetVisited(int category, long visitTimeMs);
    private static native void nativeOnNTPInitialized(long nativeNTPSnippetsBridge);
}
