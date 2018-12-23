// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTrigger.MANUAL_CLOSE;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIONS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.ACTIVE_TAB;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BOTTOM_OFFSET_PX;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TABS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.TAB_SELECTION_CALLBACKS;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.VISIBLE;

import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.design.widget.TabLayout;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryCoordinator.VisibilityDelegate;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.modelutil.ListObservable;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel;
import org.chromium.chrome.browser.modelutil.PropertyObservable;

import java.util.ArrayList;
import java.util.Arrays;
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
                   KeyboardAccessoryData.Observer<KeyboardAccessoryData.Action>,
                   TabLayout.OnTabSelectedListener {
    private final PropertyModel mModel;
    private final VisibilityDelegate mVisibilityDelegate;

    private boolean mShowIfNotEmpty;

    KeyboardAccessoryMediator(PropertyModel model, VisibilityDelegate visibilityDelegate) {
        mModel = model;
        mVisibilityDelegate = visibilityDelegate;

        // Add mediator as observer so it can use model changes as signal for accessory visibility.
        mModel.addObserver(this);
        mModel.get(TABS).addObserver(this);
        mModel.get(ACTIONS).addObserver(this);
        mModel.set(TAB_SELECTION_CALLBACKS, this);
    }

    @Override
    public void onItemsAvailable(int typeId, KeyboardAccessoryData.Action[] actions) {
        assert typeId != DEFAULT_TYPE : "Did not specify which Action type has been updated.";
        // If there is a new list, retain all actions that are of a different type than the provided
        // actions.
        List<Action> retainedActions = new ArrayList<>();
        for (Action a : mModel.get(ACTIONS)) {
            if (a.getActionType() == typeId) continue;
            retainedActions.add(a);
        }
        // Always append autofill suggestions to the very end.
        int insertPos = typeId == AccessoryAction.AUTOFILL_SUGGESTION ? retainedActions.size() : 0;
        retainedActions.addAll(insertPos, Arrays.asList(actions));
        mModel.get(ACTIONS).set(retainedActions);
    }

    void requestShowing() {
        mShowIfNotEmpty = true;
        updateVisibility();
    }

    void close() {
        mShowIfNotEmpty = false;
        updateVisibility();
    }

    void addTab(KeyboardAccessoryData.Tab tab) {
        mModel.get(TABS).add(tab);
    }

    void removeTab(KeyboardAccessoryData.Tab tab) {
        mModel.get(TABS).remove(tab);
    }

    void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mModel.get(TABS).set(tabs);
    }

    void dismiss() {
        closeActiveTab();
        close();
    }

    void closeActiveTab() {
        mModel.set(ACTIVE_TAB, null);
    }

    @VisibleForTesting
    PropertyModel getModelForTesting() {
        return mModel;
    }

    @Override
    public void onItemRangeInserted(ListObservable source, int index, int count) {
        assert source == mModel.get(ACTIONS) || source == mModel.get(TABS);
        updateVisibility();
    }

    @Override
    public void onItemRangeRemoved(ListObservable source, int index, int count) {
        assert source == mModel.get(ACTIONS) || source == mModel.get(TABS);
        updateVisibility();
    }

    @Override
    public void onItemRangeChanged(
            ListObservable source, int index, int count, @Nullable Void payload) {
        assert source == mModel.get(ACTIONS) || source == mModel.get(TABS);
        assert payload == null;
        updateVisibility();
    }

    @Override
    public void onPropertyChanged(
            PropertyObservable<PropertyKey> source, @Nullable PropertyKey propertyKey) {
        // Update the visibility only if we haven't set it just now.
        if (propertyKey == VISIBLE) {
            // When the accessory just (dis)appeared, there should be no active tab.
            closeActiveTab();
            mVisibilityDelegate.onBottomControlSpaceChanged();
            if (!mModel.get(VISIBLE)) {
                // TODO(fhorschig|ioanap): Maybe the generation bridge should take care of that.
                onItemsAvailable(AccessoryAction.GENERATE_PASSWORD_AUTOMATIC, new Action[0]);
            }
            return;
        }
        if (propertyKey == ACTIVE_TAB) {
            Integer activeTab = mModel.get(ACTIVE_TAB);
            if (activeTab == null) {
                mVisibilityDelegate.onCloseAccessorySheet();
                updateVisibility();
                return;
            }
            mVisibilityDelegate.onChangeAccessorySheet(activeTab);
            return;
        }
        if (propertyKey == BOTTOM_OFFSET_PX || propertyKey == TAB_SELECTION_CALLBACKS) {
            return;
        }
        assert false : "Every property update needs to be handled explicitly!";
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mModel.set(ACTIVE_TAB, tab.getPosition());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        if (mModel.get(ACTIVE_TAB) == null) {
            mModel.set(ACTIVE_TAB, tab.getPosition());
        } else {
            KeyboardAccessoryMetricsRecorder.recordSheetTrigger(
                    mModel.get(TABS).get(mModel.get(ACTIVE_TAB)).getRecordingType(), MANUAL_CLOSE);
            mVisibilityDelegate.onOpenKeyboard(); // This will close the active tab gently.
        }
    }

    boolean hasContents() {
        return mModel.get(ACTIONS).size() > 0 || mModel.get(TABS).size() > 0;
    }

    private boolean shouldShowAccessory() {
        if (!mShowIfNotEmpty && mModel.get(ACTIVE_TAB) == null) return false;
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
        return mModel.get(VISIBLE) && mModel.get(ACTIVE_TAB) != null;
    }
}
