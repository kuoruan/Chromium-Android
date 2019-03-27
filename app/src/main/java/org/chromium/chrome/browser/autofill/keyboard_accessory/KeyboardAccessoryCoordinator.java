// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BAR_ITEMS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.KEYBOARD_TOGGLE_VISIBLE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.SHOW_KEYBOARD_CALLBACK;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.v4.view.ViewPager;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BarItem;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryViewBinder.BarItemViewHolder;
import org.chromium.ui.ViewProvider;
import org.chromium.ui.modelutil.LazyConstructionPropertyMcp;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;
import org.chromium.ui.modelutil.RecyclerViewAdapter;

/**
 * Creates and owns all elements which are part of the keyboard accessory component.
 * It's part of the controller but will mainly forward events (like adding a tab,
 * or showing the accessory) to the {@link KeyboardAccessoryMediator}.
 */
public class KeyboardAccessoryCoordinator {
    private final KeyboardAccessoryMediator mMediator;
    private final KeyboardAccessoryTabLayoutCoordinator mTabLayout =
            new KeyboardAccessoryTabLayoutCoordinator();

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
     * Describes a delegate manages all known tabs and is responsible to determine the active tab.
     */
    public interface TabSwitchingDelegate {
        /**
         * A {@link KeyboardAccessoryData.Tab} passed into this function will be represented as item
         * at the start of the tab layout. It is meant to trigger various bottom sheets.
         * @param tab The tab which contains representation data of a bottom sheet.
         */
        void addTab(KeyboardAccessoryData.Tab tab);

        /**
         * The {@link KeyboardAccessoryData.Tab} passed into this function will be completely
         * removed from the tab layout.
         * @param tab The tab to be removed.
         */
        void removeTab(KeyboardAccessoryData.Tab tab);

        /**
         * Clears all currently known tabs and adds the given tabs as replacement.
         * @param tabs An array of {@link KeyboardAccessoryData.Tab}s.
         */
        void setTabs(KeyboardAccessoryData.Tab[] tabs);

        /**
         * Closes any active tab so that {@link #getActiveTab} returns null again.
         */
        void closeActiveTab();

        /**
         * Returns whether active tab or null if no tab is currently active. The returned property
         * reflects the latest change while the view might still be in progress of being updated.
         * @return The active {@link KeyboardAccessoryData.Tab}, null otherwise.
         */
        @Nullable
        KeyboardAccessoryData.Tab getActiveTab();

        /**
         * Returns whether the model holds any tabs.
         * @return True if there is at least one tab, false otherwise.
         */
        boolean hasTabs();
    }

    /**
     * Initializes the component as soon as the native library is loaded by e.g. starting to listen
     * to keyboard visibility events.
     * @param viewProvider A provider for the accessory.
     */
    public KeyboardAccessoryCoordinator(VisibilityDelegate visibilityDelegate,
            ViewProvider<KeyboardAccessoryView> viewProvider) {
        PropertyModel model = new PropertyModel
                                      .Builder(BAR_ITEMS, VISIBLE, BOTTOM_OFFSET_PX,
                                              KEYBOARD_TOGGLE_VISIBLE, SHOW_KEYBOARD_CALLBACK)
                                      .with(BAR_ITEMS, new ListModel<>())
                                      .with(VISIBLE, false)
                                      .with(KEYBOARD_TOGGLE_VISIBLE, false)
                                      .build();
        mMediator = new KeyboardAccessoryMediator(
                model, visibilityDelegate, mTabLayout.getTabSwitchingDelegate());
        viewProvider.whenLoaded(barView -> mTabLayout.assignNewView(barView.getTabLayout()));

        mTabLayout.setTabObserver(mMediator);
        PropertyModelChangeProcessor
                .ViewBinder<PropertyModel, KeyboardAccessoryView, PropertyKey> viewBinder =
                KeyboardAccessoryViewBinder::bind;
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.AUTOFILL_KEYBOARD_ACCESSORY)) {
            viewBinder = KeyboardAccessoryModernViewBinder::bind;
        }
        LazyConstructionPropertyMcp.create(model, VISIBLE, viewProvider, viewBinder);
        KeyboardAccessoryMetricsRecorder.registerKeyboardAccessoryModelMetricsObserver(
                model, mTabLayout.getTabSwitchingDelegate());
    }

    /**
     * Creates an adapter to an {@link BarItemViewHolder} that is wired
     * up to the model change processor which listens to the given item list.
     * @param barItems The list of shown items represented by the adapter.
     * @return Returns a fully initialized and wired adapter to an BarItemViewHolder.
     */
    static RecyclerViewAdapter<BarItemViewHolder, Void> createBarItemsAdapter(
            ListModel<BarItem> barItems) {
        RecyclerViewAdapter.ViewHolderFactory<BarItemViewHolder> factory =
                KeyboardAccessoryViewBinder::create;
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.AUTOFILL_KEYBOARD_ACCESSORY)) {
            factory = KeyboardAccessoryModernViewBinder::create;
        }
        return new RecyclerViewAdapter<>(
                new KeyboardAccessoryRecyclerViewMcp<>(barItems, BarItem::getViewType,
                        BarItemViewHolder::bind, BarItemViewHolder::recycle),
                factory);
    }

    public void closeActiveTab() {
        mTabLayout.getTabSwitchingDelegate().closeActiveTab();
    }

    /**
     * A {@link KeyboardAccessoryData.Tab} passed into this function will be represented as item at
     * the start of the accessory. It is meant to trigger various bottom sheets.
     * @param tab The tab which contains representation data and links back to a bottom sheet.
     */
    void addTab(KeyboardAccessoryData.Tab tab) {
        mTabLayout.getTabSwitchingDelegate().addTab(tab);
    }

    /**
     * The {@link KeyboardAccessoryData.Tab} passed into this function will be completely removed
     * from the accessory.
     * @param tab The tab to be removed.
     */
    void removeTab(KeyboardAccessoryData.Tab tab) {
        mTabLayout.getTabSwitchingDelegate().removeTab(tab);
    }

    void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mTabLayout.getTabSwitchingDelegate().setTabs(tabs);
    }

    /**
     * Allows any {@link KeyboardAccessoryData.Provider} to communicate with the
     * {@link KeyboardAccessoryMediator} of this component.
     *
     * Note that the provided actions are removed when the accessory is hidden.
     *
     * @param provider The object providing action lists to observers in this component.
     */
    public void registerActionProvider(
            KeyboardAccessoryData.Provider<KeyboardAccessoryData.Action[]> provider) {
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
     * @return True if the accessory is visible and has an active tab, false otherwise.
     */
    public boolean hasActiveTab() {
        return mMediator.hasActiveTab();
    }

    ViewPager.OnPageChangeListener getOnPageChangeListener() {
        return mTabLayout.getStablePageChangeListener();
    }

    @VisibleForTesting
    KeyboardAccessoryMediator getMediatorForTesting() {
        return mMediator;
    }

    @VisibleForTesting
    KeyboardAccessoryTabLayoutCoordinator getTabLayoutForTesting() {
        return mTabLayout;
    }
}
