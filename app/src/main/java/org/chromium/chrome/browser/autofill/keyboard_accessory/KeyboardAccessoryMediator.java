// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTrigger.MANUAL_CLOSE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BAR_ITEMS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.KEYBOARD_TOGGLE_VISIBLE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.SHOW_KEYBOARD_CALLBACK;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.support.annotation.Nullable;
import android.support.annotation.Px;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryCoordinator.TabSwitchingDelegate;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryCoordinator.VisibilityDelegate;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BarItem;
import org.chromium.ui.modelutil.ListObservable;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyObservable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This is the second part of the controller of the keyboard accessory component.
 * It is responsible for updating the model based on backend calls and notify the backend if the
 * model changes. From the backend, it receives all actions that the accessory can perform (most
 * prominently generating passwords) and lets the model know of these actions and which callback to
 * trigger when selecting them.
 */
class KeyboardAccessoryMediator
        implements ListObservable.ListObserver<Void>,
                   PropertyObservable.PropertyObserver<PropertyKey>,
                   KeyboardAccessoryData.Observer<KeyboardAccessoryData.Action[]>,
                   KeyboardAccessoryTabLayoutCoordinator.AccessoryTabObserver {
    private final PropertyModel mModel;
    private final VisibilityDelegate mVisibilityDelegate;
    private final TabSwitchingDelegate mTabSwitcher;

    private boolean mShowIfNotEmpty;

    KeyboardAccessoryMediator(PropertyModel model, VisibilityDelegate visibilityDelegate,
            TabSwitchingDelegate tabSwitcher) {
        mModel = model;
        mVisibilityDelegate = visibilityDelegate;
        mTabSwitcher = tabSwitcher;

        // Add mediator as observer so it can use model changes as signal for accessory visibility.
        mModel.set(SHOW_KEYBOARD_CALLBACK, this::closeSheet);
        mModel.get(BAR_ITEMS).addObserver(this);
        mModel.addObserver(this);
    }

    @Override
    public void onItemAvailable(@BarItem.Type int typeId, KeyboardAccessoryData.Action[] actions) {
        assert typeId != DEFAULT_TYPE : "Did not specify which Action type has been updated.";
        // If there is a new list, retain all actions that are of a different type than the provided
        // actions.
        List<BarItem> retainedItems = new ArrayList<>();
        for (BarItem item : mModel.get(BAR_ITEMS)) {
            if (item.getAction() == null) continue;
            if (item.getAction().getActionType() == typeId) continue;
            retainedItems.add(item);
        }
        // Append autofill suggestions to the end, right before the tab switcher.
        int insertPos = typeId == AccessoryAction.AUTOFILL_SUGGESTION ? retainedItems.size() : 0;
        retainedItems.addAll(insertPos, toBarItems(actions));
        mModel.get(BAR_ITEMS).set(retainedItems);
    }

    private Collection<BarItem> toBarItems(Action[] actions) {
        List<BarItem> barItems = new ArrayList<>(actions.length);
        for (Action action : actions) {
            barItems.add(new BarItem(toBarItemType(action.getActionType()), action));
        }
        return barItems;
    }

    private @BarItem.Type int toBarItemType(@AccessoryAction int accessoryAction) {
        switch (accessoryAction) {
            case AccessoryAction.AUTOFILL_SUGGESTION:
                return BarItem.Type.SUGGESTION;
            case AccessoryAction.GENERATE_PASSWORD_AUTOMATIC:
                return BarItem.Type.ACTION_BUTTON;
            case AccessoryAction.MANAGE_PASSWORDS: // Intentional fallthrough - no view defined.
            case AccessoryAction.COUNT:
                assert false : "No view defined for :" + accessoryAction;
                return BarItem.Type.COUNT;
        }
        assert false : "Unhandled action type:" + accessoryAction;
        return BarItem.Type.COUNT;
    }

    void requestShowing() {
        mShowIfNotEmpty = true;
        updateVisibility();
    }

    void close() {
        mShowIfNotEmpty = false;
        updateVisibility();
    }

    void dismiss() {
        mTabSwitcher.closeActiveTab();
        close();
    }

    @VisibleForTesting
    PropertyModel getModelForTesting() {
        return mModel;
    }

    @Override
    public void onItemRangeInserted(ListObservable source, int index, int count) {
        assert source == mModel.get(BAR_ITEMS);
        updateVisibility();
    }

    @Override
    public void onItemRangeRemoved(ListObservable source, int index, int count) {
        assert source == mModel.get(BAR_ITEMS);
        updateVisibility();
    }

    @Override
    public void onItemRangeChanged(
            ListObservable source, int index, int count, @Nullable Void payload) {
        assert source == mModel.get(BAR_ITEMS);
        assert payload == null;
        updateVisibility();
    }

    @Override
    public void onPropertyChanged(
            PropertyObservable<PropertyKey> source, @Nullable PropertyKey propertyKey) {
        // Update the visibility only if we haven't set it just now.
        if (propertyKey == VISIBLE) {
            // When the accessory just (dis)appeared, there should be no active tab.
            mTabSwitcher.closeActiveTab();
            mVisibilityDelegate.onBottomControlSpaceChanged();
            if (!mModel.get(VISIBLE)) {
                // TODO(fhorschig|ioanap): Maybe the generation bridge should take care of that.
                onItemAvailable(AccessoryAction.GENERATE_PASSWORD_AUTOMATIC, new Action[0]);
            }
            return;
        }
        if (propertyKey == BOTTOM_OFFSET_PX || propertyKey == SHOW_KEYBOARD_CALLBACK
                || propertyKey == KEYBOARD_TOGGLE_VISIBLE) {
            return;
        }
        assert false : "Every property update needs to be handled explicitly!";
    }

    @Override
    public void onActiveTabChanged(Integer activeTab) {
        mModel.set(KEYBOARD_TOGGLE_VISIBLE, activeTab != null);
        if (activeTab == null) {
            mVisibilityDelegate.onCloseAccessorySheet();
            updateVisibility();
            return;
        }
        mVisibilityDelegate.onChangeAccessorySheet(activeTab);
    }

    @Override
    public void onActiveTabReselected() {
        closeSheet();
    }

    @Override
    public void onTabsChanged() {
        updateVisibility();
    }

    private void closeSheet() {
        assert mTabSwitcher.getActiveTab() != null;
        KeyboardAccessoryMetricsRecorder.recordSheetTrigger(
                mTabSwitcher.getActiveTab().getRecordingType(), MANUAL_CLOSE);
        mModel.set(KEYBOARD_TOGGLE_VISIBLE, false);
        mVisibilityDelegate.onOpenKeyboard(); // This will close the active tab gently.
    }

    boolean hasContents() {
        return mModel.get(BAR_ITEMS).size() > 0 || mTabSwitcher.hasTabs();
    }

    private boolean shouldShowAccessory() {
        if (!mShowIfNotEmpty && mTabSwitcher.getActiveTab() == null) return false;
        return hasContents();
    }

    private void updateVisibility() {
        mModel.set(VISIBLE, shouldShowAccessory());
    }

    public void setBottomOffset(@Px int bottomOffset) {
        mModel.set(BOTTOM_OFFSET_PX, bottomOffset);
    }

    public boolean isShown() {
        return mModel.get(VISIBLE);
    }

    public boolean hasActiveTab() {
        return mModel.get(VISIBLE) && mTabSwitcher.getActiveTab() != null;
    }
}
