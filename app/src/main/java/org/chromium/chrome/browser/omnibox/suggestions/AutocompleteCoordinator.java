// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ListView;

import org.chromium.base.Callback;
import org.chromium.base.StrictModeContext;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.omnibox.LocationBarVoiceRecognitionHandler;
import org.chromium.chrome.browser.omnibox.UrlBar.UrlTextChangeListener;
import org.chromium.chrome.browser.omnibox.UrlBarEditingTextStateProvider;
import org.chromium.chrome.browser.omnibox.UrlFocusChangeListener;
import org.chromium.chrome.browser.omnibox.suggestions.AutocompleteController.OnSuggestionsReceivedListener;
import org.chromium.chrome.browser.omnibox.suggestions.OmniboxSuggestionsList.OmniboxSuggestionListEmbedder;
import org.chromium.chrome.browser.omnibox.suggestions.SuggestionListViewBinder.SuggestionListViewHolder;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionView;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionViewViewBinder;
import org.chromium.chrome.browser.omnibox.suggestions.editurl.EditUrlSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.editurl.EditUrlSuggestionViewBinder;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;
import org.chromium.chrome.browser.util.KeyNavigationUtil;
import org.chromium.ui.ViewProvider;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.modelutil.LazyConstructionPropertyMcp;
import org.chromium.ui.modelutil.ModelListAdapter;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinator that handles the interactions with the autocomplete system.
 */
public class AutocompleteCoordinator implements UrlFocusChangeListener, UrlTextChangeListener {
    private final ViewGroup mParent;
    private final AutocompleteMediator mMediator;

    private ListView mListView;

    /**
     * A processor of omnibox suggestions. Implementers are provided the opportunity to analyze a
     * suggestion and create a custom model.
     */
    public interface SuggestionProcessor {
        /**
         * @param suggestion The suggestion to process.
         * @return Whether this suggestion processor handles this type of suggestion.
         */
        boolean doesProcessSuggestion(OmniboxSuggestion suggestion);

        /**
         * @return The type of view the models created by this processor represent.
         */
        int getViewTypeId();

        /**
         * @see org.chromium.chrome.browser.omnibox.UrlFocusChangeListener#onUrlFocusChange(boolean)
         */
        void onUrlFocusChange(boolean hasFocus);

        /**
         * Signals that native initialization has completed.
         */
        void onNativeInitialized();

        /**
         * Create a model for a suggestion at the specified position.
         * @param suggestion The suggestion to create the model for.
         * @return A model for the suggestion.
         */
        PropertyModel createModelForSuggestion(OmniboxSuggestion suggestion);

        /**
         * Populate a model for the given suggestion.
         * @param suggestion The suggestion to populate the model for.
         * @param model The model to populate.
         * @param position The position of the suggestion in the list.
         */
        void populateModel(OmniboxSuggestion suggestion, PropertyModel model, int position);
    }

    /**
     * Provides the additional functionality to trigger and interact with autocomplete suggestions.
     */
    public interface AutocompleteDelegate extends EditUrlSuggestionProcessor.LocationBarDelegate {
        /**
         * Notified that the URL text has changed.
         */
        void onUrlTextChanged();

        /**
         * Notified that suggestions have changed.
         * @param autocompleteText The inline autocomplete text that can be appended to the
         *                         currently entered user text.
         */
        void onSuggestionsChanged(String autocompleteText);

        /**
         * Notified that the suggestions have been hidden.
         */
        void onSuggestionsHidden();

        /**
         * Requests the keyboard be hidden.
         */
        void hideKeyboard();

        /**
         * Requests that the given URL be loaded in the current tab.
         *
         * @param url The URL to be loaded.
         * @param transition The transition type associated with the url load.
         * @param inputStart The time the input started for the load request.
         */
        void loadUrl(String url, @PageTransition int transition, long inputStart);

        /**
         * @return Whether the omnibox was focused via the NTP fakebox.
         */
        boolean didFocusUrlFromFakebox();

        /**
         * @return Whether the URL currently has focus.
         */
        boolean isUrlBarFocused();
    }

    /**
     * Constructs a coordinator for the autocomplete system.
     *
     * @param parent The UI parent component for the autocomplete UI.
     * @param delegate The delegate to fulfill additional autocomplete requirements.
     * @param listEmbedder The embedder for controlling the display constraints of the suggestions
     *                     list.
     * @param urlBarEditingTextProvider Provider of editing text state from the UrlBar.
     */
    public AutocompleteCoordinator(ViewGroup parent, AutocompleteDelegate delegate,
            OmniboxSuggestionListEmbedder listEmbedder,
            UrlBarEditingTextStateProvider urlBarEditingTextProvider) {
        mParent = parent;
        Context context = parent.getContext();

        PropertyModel listModel = new PropertyModel(SuggestionListProperties.ALL_KEYS);
        listModel.set(SuggestionListProperties.EMBEDDER, listEmbedder);
        listModel.set(SuggestionListProperties.VISIBLE, false);

        ViewProvider<SuggestionListViewHolder> viewProvider = createViewProvider(context);
        viewProvider.whenLoaded((holder) -> { mListView = holder.listView; });
        LazyConstructionPropertyMcp.create(listModel, SuggestionListProperties.VISIBLE,
                viewProvider, SuggestionListViewBinder::bind);

        mMediator =
                new AutocompleteMediator(context, delegate, urlBarEditingTextProvider, listModel);
    }

    private ViewProvider<SuggestionListViewHolder> createViewProvider(Context context) {
        return new ViewProvider<SuggestionListViewHolder>() {
            private List<Callback<SuggestionListViewHolder>> mCallbacks = new ArrayList<>();
            private SuggestionListViewHolder mHolder;

            @Override
            public void inflate() {
                ViewGroup container = (ViewGroup) ((ViewStub) mParent.getRootView().findViewById(
                                                           R.id.omnibox_results_container_stub))
                                              .inflate();
                OmniboxSuggestionsList list;
                try (StrictModeContext unused = StrictModeContext.allowDiskReads()) {
                    list = new OmniboxSuggestionsList(context);
                }

                // Start with visibility GONE to ensure that show() is called.
                // http://crbug.com/517438
                list.setVisibility(View.GONE);
                ModelListAdapter adapter = new ModelListAdapter(context);
                list.setAdapter(adapter);
                list.setClipToPadding(false);

                // Register a view type for a default omnibox suggestion.
                // Note: clang-format does a bad job formatting lambdas so we turn it off here.
                // clang-format off
                adapter.registerType(
                        OmniboxSuggestionUiType.DEFAULT,
                        () -> new SuggestionView(mListView.getContext()),
                        SuggestionViewViewBinder::bind);

                adapter.registerType(
                        OmniboxSuggestionUiType.EDIT_URL_SUGGESTION,
                        () -> EditUrlSuggestionProcessor.createView(mListView.getContext()),
                        EditUrlSuggestionViewBinder::bind);
                // clang-format on

                mHolder = new SuggestionListViewHolder(container, list, adapter);

                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onResult(mHolder);
                }
                mCallbacks = null;
            }

            @Override
            public void whenLoaded(Callback<SuggestionListViewHolder> callback) {
                if (mHolder != null) {
                    callback.onResult(mHolder);
                    return;
                }
                mCallbacks.add(callback);
            }
        };
    }

    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        mMediator.onUrlFocusChange(hasFocus);
    }

    @Override
    public void onUrlAnimationFinished(boolean hasFocus) {
        mMediator.onUrlAnimationFinished(hasFocus);
    }

    /**
     * Provides data and state for the toolbar component.
     * @param toolbarDataProvider The data provider.
     */
    public void setToolbarDataProvider(ToolbarDataProvider toolbarDataProvider) {
        mMediator.setToolbarDataProvider(toolbarDataProvider);
    }

    /**
     * Updates the profile used for generating autocomplete suggestions.
     * @param profile The profile to be used.
     */
    public void setAutocompleteProfile(Profile profile) {
        mMediator.setAutocompleteProfile(profile);
    }

    /**
     * Set the WindowAndroid instance associated with the containing Activity.
     */
    public void setWindowAndroid(WindowAndroid windowAndroid) {
        mMediator.setWindowAndroid(windowAndroid);
    }

    /**
     * @param provider A means of accessing the activity's tab.
     */
    public void setActivityTabProvider(ActivityTabProvider provider) {
        mMediator.setActivityTabProvider(provider);
    }

    /**
     * Whether omnibox autocomplete should currently be prevented from generating suggestions.
     */
    public void setShouldPreventOmniboxAutocomplete(boolean prevent) {
        mMediator.setShouldPreventOmniboxAutocomplete(prevent);
    }

    /**
     * @return The number of current autocomplete suggestions.
     */
    public int getSuggestionCount() {
        return mMediator.getSuggestionCount();
    }

    /**
     * Retrieve the omnibox suggestion at the specified index.  The index represents the ordering
     * in the underlying model.  The index does not represent visibility due to the current scroll
     * position of the list.
     *
     * @param index The index of the suggestion to fetch.
     * @return The suggestion at the given index.
     */
    public OmniboxSuggestion getSuggestionAt(int index) {
        return mMediator.getSuggestionAt(index);
    }

    /**
     * Signals that native initialization has completed.
     */
    public void onNativeInitialized() {
        mMediator.onNativeInitialized();
    }

    /**
     * @return The suggestion list popup containing the omnibox results (or null if it has not yet
     *         been created).
     */
    @VisibleForTesting
    public ListView getSuggestionList() {
        return mListView;
    }

    /**
     * @see AutocompleteController#onVoiceResults(List)
     */
    public void onVoiceResults(
            @Nullable List<LocationBarVoiceRecognitionHandler.VoiceResult> results) {
        mMediator.onVoiceResults(results);
    }

    /**
     * @return The current native pointer to the autocomplete results.
     */
    // TODO(tedchoc): Figure out how to remove this.
    public long getCurrentNativeAutocompleteResult() {
        return mMediator.getCurrentNativeAutocompleteResult();
    }

    /**
     * Update the layout direction of the suggestion list based on the parent layout direction.
     */
    public void updateSuggestionListLayoutDirection() {
        mMediator.setLayoutDirection(ViewCompat.getLayoutDirection(mParent));
    }

    /**
     * Update the visuals of the autocomplete UI.
     * @param useDarkColors Whether dark colors should be applied to the UI.
     */
    public void updateVisualsForState(boolean useDarkColors) {
        mMediator.setUseDarkColors(useDarkColors);
    }

    /**
     * Sets to show cached zero suggest results. This will start both caching zero suggest results
     * in shared preferences and also attempt to show them when appropriate without needing native
     * initialization.
     * @param showCachedZeroSuggestResults Whether cached zero suggest should be shown.
     */
    public void setShowCachedZeroSuggestResults(boolean showCachedZeroSuggestResults) {
        mMediator.setShowCachedZeroSuggestResults(showCachedZeroSuggestResults);
    }

    /**
     * Handle the key events associated with the suggestion list.
     *
     * @param keyCode The keycode representing what key was interacted with.
     * @param event The key event containing all meta-data associated with the event.
     * @return Whether the key event was handled.
     */
    public boolean handleKeyEvent(int keyCode, KeyEvent event) {
        boolean isShowingList = mListView != null && mListView.isShown();

        boolean isUpOrDown = KeyNavigationUtil.isGoUpOrDown(event);
        if (isShowingList && mMediator.getSuggestionCount() > 0 && isUpOrDown) {
            mMediator.allowPendingItemSelection();
        }
        boolean isValidListKey = isUpOrDown || KeyNavigationUtil.isGoRight(event)
                || KeyNavigationUtil.isEnter(event);
        if (isShowingList && isValidListKey && mListView.onKeyDown(keyCode, event)) return true;
        if (KeyNavigationUtil.isEnter(event) && mParent.getVisibility() == View.VISIBLE) {
            mMediator.loadTypedOmniboxText(event.getEventTime());
            return true;
        }
        return false;
    }

    /**
     * Notifies the autocomplete system that the text has changed that drives autocomplete and the
     * autocomplete suggestions should be updated.
     */
    @Override
    public void onTextChangedForAutocomplete() {
        mMediator.onTextChangedForAutocomplete();
    }

    /**
     * Cancels the queued task to start the autocomplete controller, if any.
     */
    @VisibleForTesting
    public void cancelPendingAutocompleteStart() {
        mMediator.cancelPendingAutocompleteStart();
    }

    /**
     * Trigger autocomplete for the given query.
     */
    public void startAutocompleteForQuery(String query) {
        mMediator.startAutocompleteForQuery(query);
    }

    /**
     * Sets the autocomplete controller for the location bar.
     *
     * @param controller The controller that will handle autocomplete/omnibox suggestions.
     * @note Only used for testing.
     */
    @VisibleForTesting
    public void setAutocompleteController(AutocompleteController controller) {
        mMediator.setAutocompleteController(controller);
    }

    /** Allows injecting autocomplete suggestions for testing. */
    @VisibleForTesting
    public OnSuggestionsReceivedListener getSuggestionsReceivedListenerForTest() {
        return mMediator;
    }
}
