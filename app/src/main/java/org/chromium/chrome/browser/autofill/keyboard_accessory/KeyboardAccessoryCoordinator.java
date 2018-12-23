// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIONS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TAB_SELECTION_CALLBACKS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.support.annotation.Px;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryViewBinder.ActionViewHolder;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryViewBinder.TabViewBinder;
import org.chromium.chrome.browser.modelutil.LazyConstructionPropertyMcp;
import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.ListModelChangeProcessor;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.modelutil.RecyclerViewAdapter;
import org.chromium.chrome.browser.modelutil.SimpleRecyclerViewMcp;
import org.chromium.ui.ViewProvider;

/**
 * Creates and owns all elements which are part of the keyboard accessory component.
 * It's part of the controller but will mainly forward events (like adding a tab,
 * or showing the accessory) to the {@link KeyboardAccessoryMediator}.
 */
public class KeyboardAccessoryCoordinator {
    private final KeyboardAccessoryMediator mMediator;

    /**
     * The keyboard accessory provides signals when to show or change the accessory sheet below it.
     * The actual implementation isn't relevant for this component. Therefore, a class implementing
     * this interface takes that responsibility, i.e. {@link ManualFillingCoordinator}.
     */
    public interface VisibilityDelegate {
        /**
         * Is triggered when a tab in the accessory was selected and the sheet needs to change.
         * @param tabIndex The index of the selected tab in the tab bar.
         */
        void onChangeAccessorySheet(int tabIndex);

        /**
         * Called when the sheet needs to be hidden.
         */
        void onCloseAccessorySheet();

        /**
         * Called when the normal keyboard should be brought back (e.g. the user dismissed a bottom
         * sheet manually because they didn't find a suitable suggestion).
         */
        void onOpenKeyboard();

        /**
         * Called when the keyboard accessory or a sheet changes visibility or size.
         */
        void onBottomControlSpaceChanged();
    }

    /**
     * Initializes the component as soon as the native library is loaded by e.g. starting to listen
     * to keyboard visibility events.
     * @param viewProvider A provider for the accessory.
     */
    public KeyboardAccessoryCoordinator(VisibilityDelegate visibilityDelegate,
            ViewProvider<KeyboardAccessoryView> viewProvider) {
        PropertyModel model = new PropertyModel
                                      .Builder(ACTIONS, TABS, VISIBLE, BOTTOM_OFFSET_PX, ACTIVE_TAB,
                                              TAB_SELECTION_CALLBACKS)
                                      .with(TABS, new ListModel<>())
                                      .with(ACTIONS, new ListModel<>())
                                      .with(ACTIVE_TAB, null)
                                      .with(VISIBLE, false)
                                      .build();
        mMediator = new KeyboardAccessoryMediator(model, visibilityDelegate);
        viewProvider.whenLoaded(view -> view.setTabSelectionAdapter(mMediator));

        LazyConstructionPropertyMcp.create(
                model, VISIBLE, viewProvider, KeyboardAccessoryViewBinder::bind);
        KeyboardAccessoryMetricsRecorder.registerKeyboardAccessoryModelMetricsObserver(model);
    }

    /**
     * Creates an adapter to an {@link ActionViewHolder} that is wired
     * up to the model change processor which listens to the given action list.
     * @param actions The list of actions shown represented by the adapter.
     * @return Returns a fully initialized and wired adapter to an ActionViewHolder.
     */
    static RecyclerViewAdapter<ActionViewHolder, Void> createActionsAdapter(
            ListModel<KeyboardAccessoryData.Action> actions) {
        return new RecyclerViewAdapter<>(
                new SimpleRecyclerViewMcp<>(actions, KeyboardAccessoryData.Action::getActionType,
                        ActionViewHolder::bind),
                ActionViewHolder::create);
    }

    /**
     * Creates the {@link TabViewBinder} that is linked to the {@link ListModelChangeProcessor} that
     * connects the given {@link KeyboardAccessoryView} to the given action list.
     * @param model the {@link KeyboardAccessoryProperties} whose data is used by the TabViewBinder.
     * @param inflatedView the {@link KeyboardAccessoryView} to which the TabViewBinder binds data.
     * @return Returns a fully initialized and wired {@link TabViewBinder}.
     */
    static TabViewBinder createTabViewBinder(
            PropertyModel model, KeyboardAccessoryView inflatedView) {
        TabViewBinder tabViewBinder = new TabViewBinder();
        model.get(TABS).addObserver(
                new ListModelChangeProcessor<>(model.get(TABS), inflatedView, tabViewBinder));
        return tabViewBinder;
    }

    public void closeActiveTab() {
        mMediator.closeActiveTab();
    }

    /**
     * A {@link KeyboardAccessoryData.Tab} passed into this function will be represented as item at
     * the start of the accessory. It is meant to trigger various bottom sheets.
     * @param tab The tab which contains representation data and links back to a bottom sheet.
     */
    void addTab(KeyboardAccessoryData.Tab tab) {
        mMediator.addTab(tab);
    }

    /**
     * The {@link KeyboardAccessoryData.Tab} passed into this function will be completely removed
     * from the accessory.
     * @param tab The tab to be removed.
     */
    void removeTab(KeyboardAccessoryData.Tab tab) {
        mMediator.removeTab(tab);
    }

    void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mMediator.setTabs(tabs);
    }

    /**
     * Allows any {@link KeyboardAccessoryData.Provider} to communicate with the
     * {@link KeyboardAccessoryMediator} of this component.
     *
     * Note that the provided actions are removed when the accessory is hidden.
     *
     * @param provider The object providing action lists to observers in this component.
     */
    public void registerActionListProvider(
            KeyboardAccessoryData.Provider<KeyboardAccessoryData.Action> provider) {
        provider.addObserver(mMediator);
    }

    /**
     * Dismisses the accessory by hiding it's view, clearing potentially left over suggestions and
     * hiding the keyboard.
     */
    public void dismiss() {
        mMediator.dismiss();
    }

    /**
     * Sets the offset to the end of the activity - which is usually 0, the height of the keyboard
     * or the height of a bottom sheet.
     * @param bottomOffset The offset in pixels.
     */
    public void setBottomOffset(@Px int bottomOffset) {
        mMediator.setBottomOffset(bottomOffset);
    }

    /**
     * Closes the accessory bar. This sends signals to close the active tab and recalculate bottom
     * offsets.
     */
    public void close() {
        mMediator.close();
    }

    /**
     * Triggers the accessory to be shown if there are contents to be shown. Effect might therefore
     * be delayed until contents (e.g. tabs, actions, chips) arrive.
     */
    public void requestShowing() {
        mMediator.requestShowing();
    }

    /**
     * Returns the visibility of the the accessory. The returned property reflects the latest change
     * while the view might still be in progress of being updated accordingly.
     * @return True if the accessory should be visible, false otherwise.
     */
    public boolean isShown() {
        return mMediator.isShown();
    }

    /**
     * This method returns whether the accessory has any contents that justify showing it. A single
     * tab, action or suggestion chip would already allow that.
     * @return True if there is any content to be shown. False otherwise.
     */
    public boolean hasContents() {
        return mMediator.hasContents();
    }

    /**
     * Returns whether the active tab is non-null. The returned property reflects the latest change
     * while the view might still be in progress of being updated accordingly.
     * @return True if the accessory has an active tab, false otherwise.
     */
    public boolean hasActiveTab() {
        return mMediator.hasActiveTab();
    }

    @VisibleForTesting
    KeyboardAccessoryMediator getMediatorForTesting() {
        return mMediator;
    }
}
