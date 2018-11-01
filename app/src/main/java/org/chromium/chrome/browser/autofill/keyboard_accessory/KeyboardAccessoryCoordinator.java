// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.support.annotation.Px;
import android.support.v4.view.ViewPager;
import android.view.ViewStub;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryViewBinder.ActionViewHolder;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryViewBinder.TabViewBinder;
import org.chromium.chrome.browser.modelutil.LazyViewBinderAdapter;
import org.chromium.chrome.browser.modelutil.ListModelChangeProcessor;
import org.chromium.chrome.browser.modelutil.PropertyModelChangeProcessor;
import org.chromium.chrome.browser.modelutil.RecyclerViewAdapter;
import org.chromium.chrome.browser.modelutil.SimpleRecyclerViewMcp;

/**
 * Creates and owns all elements which are part of the keyboard accessory component.
 * It's part of the controller but will mainly forward events (like adding a tab,
 * or showing the accessory) to the {@link KeyboardAccessoryMediator}.
 */
public class KeyboardAccessoryCoordinator {
    private final KeyboardAccessoryMediator mMediator;
    private LazyViewBinderAdapter.StubHolder<KeyboardAccessoryView> mViewHolder;

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
     * @param viewStub the stub that will become the accessory.
     */
    public KeyboardAccessoryCoordinator(ViewStub viewStub, VisibilityDelegate visibilityDelegate) {
        KeyboardAccessoryModel model = new KeyboardAccessoryModel();
        mMediator = new KeyboardAccessoryMediator(model, visibilityDelegate);
        mViewHolder = new LazyViewBinderAdapter.StubHolder<>(viewStub);

        model.addObserver(new PropertyModelChangeProcessor<>(model, mViewHolder,
                new LazyViewBinderAdapter<>(
                        new KeyboardAccessoryViewBinder(), this::onViewInflated)));
        KeyboardAccessoryMetricsRecorder.registerMetricsObserver(model);
    }

    /**
     * Creates an adapter to an {@link ActionViewHolder} that is wired
     * up to the model change processor which listens to the given {@link KeyboardAccessoryModel}.
     * @param model the {@link KeyboardAccessoryModel} the adapter gets its data from.
     * @return Returns a fully initialized and wired adapter to an ActionViewHolder.
     */
    static RecyclerViewAdapter<ActionViewHolder, Void> createActionsAdapter(
            KeyboardAccessoryModel model) {
        return new RecyclerViewAdapter<>(
                new SimpleRecyclerViewMcp<>(model.getActionList(),
                        KeyboardAccessoryData.Action::getActionType, ActionViewHolder::bind),
                ActionViewHolder::create);
    }

    /**
     * Creates the {@link TabViewBinder} that is linked to the {@link ListModelChangeProcessor} that
     * connects the given {@link KeyboardAccessoryView} to the given {@link KeyboardAccessoryModel}.
     * @param model the {@link KeyboardAccessoryModel} whose data is used by the TabViewBinder.
     * @param inflatedView the {@link KeyboardAccessoryView} to which the TabViewBinder binds data.
     * @return Returns a fully initialized and wired {@link TabViewBinder}.
     */
    static TabViewBinder createTabViewBinder(
            KeyboardAccessoryModel model, KeyboardAccessoryView inflatedView) {
        TabViewBinder tabViewBinder = new TabViewBinder();
        model.addTabListObserver(
                new ListModelChangeProcessor<>(model.getTabList(), inflatedView, tabViewBinder));
        return tabViewBinder;
    }

    public void closeActiveTab() {
        mMediator.closeActiveTab();
    }

    /**
     * Called by the {@link LazyViewBinderAdapter} as soon as the view is inflated so it can be
     * initialized. This call happens before the {@link KeyboardAccessoryViewBinder} is called for
     * the first time.
     * @param view The view that was inflated from the initially given {@link ViewStub}.
     */
    private void onViewInflated(KeyboardAccessoryView view) {
        view.setTabSelectionAdapter(mMediator);
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

    /**
     * Provides the PageChangeListener that is needed to wire up the TabLayout with the ViewPager.
     * @return Returns a {@link ViewPager.OnPageChangeListener}.
     */
    ViewPager.OnPageChangeListener getPageChangeListener() {
        assert mViewHolder.getView() != null : "Requested PageChangeListener before inflation.";
        return mViewHolder.getView().getPageChangeListener();
    }
}
