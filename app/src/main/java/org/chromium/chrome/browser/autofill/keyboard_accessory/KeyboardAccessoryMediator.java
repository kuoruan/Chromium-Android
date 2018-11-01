// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTrigger.MANUAL_CLOSE;

import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.design.widget.TabLayout;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryCoordinator.VisibilityDelegate;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.modelutil.ListObservable;
import org.chromium.chrome.browser.modelutil.PropertyObservable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is the second part of the controller of the keyboard accessory component.
 * It is responsible to update the {@link KeyboardAccessoryModel} based on Backend calls and notify
 * the Backend if the {@link KeyboardAccessoryModel} changes.
 * From the backend, it receives all actions that the accessory can perform (most prominently
 * generating passwords) and lets the {@link KeyboardAccessoryModel} know of these actions and which
 * callback to trigger when selecting them.
 */
class KeyboardAccessoryMediator
        implements ListObservable.ListObserver<Void>,
                   PropertyObservable.PropertyObserver<KeyboardAccessoryModel.PropertyKey>,
                   KeyboardAccessoryData.Observer<KeyboardAccessoryData.Action>,
                   TabLayout.OnTabSelectedListener {
    private final KeyboardAccessoryModel mModel;
    private final VisibilityDelegate mVisibilityDelegate;

    private boolean mShowIfNotEmpty;

    KeyboardAccessoryMediator(KeyboardAccessoryModel model, VisibilityDelegate visibilityDelegate) {
        mModel = model;
        mVisibilityDelegate = visibilityDelegate;

        // Add mediator as observer so it can use model changes as signal for accessory visibility.
        mModel.addObserver(this);
        mModel.getTabList().addObserver(this);
        mModel.getActionList().addObserver(this);
        mModel.setTabSelectionCallbacks(this);
    }

    @Override
    public void onItemsAvailable(int typeId, KeyboardAccessoryData.Action[] actions) {
        assert typeId != DEFAULT_TYPE : "Did not specify which Action type has been updated.";
        // If there is a new list, retain all actions that are of a different type than the provided
        // actions.
        List<Action> retainedActions = new ArrayList<>();
        for (Action a : mModel.getActionList()) {
            if (a.getActionType() == typeId) continue;
            retainedActions.add(a);
        }
        // Always append autofill suggestions to the very end.
        int insertPos = typeId == AccessoryAction.AUTOFILL_SUGGESTION ? retainedActions.size() : 0;
        retainedActions.addAll(insertPos, Arrays.asList(actions));
        mModel.setActions(retainedActions.toArray(new Action[retainedActions.size()]));
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
        mModel.addTab(tab);
    }

    void removeTab(KeyboardAccessoryData.Tab tab) {
        mModel.removeTab(tab);
    }

    void setTabs(KeyboardAccessoryData.Tab[] tabs) {
        mModel.getTabList().set(tabs);
    }

    void dismiss() {
        closeActiveTab();
        close();
    }

    void closeActiveTab() {
        mModel.setActiveTab(null);
    }

    @VisibleForTesting
    KeyboardAccessoryModel getModelForTesting() {
        return mModel;
    }

    @Override
    public void onItemRangeInserted(ListObservable source, int index, int count) {
        assert source == mModel.getActionList() || source == mModel.getTabList();
        updateVisibility();
    }

    @Override
    public void onItemRangeRemoved(ListObservable source, int index, int count) {
        assert source == mModel.getActionList() || source == mModel.getTabList();
        updateVisibility();
    }

    @Override
    public void onItemRangeChanged(
            ListObservable source, int index, int count, @Nullable Void payload) {
        assert source == mModel.getActionList() || source == mModel.getTabList();
        assert payload == null;
        updateVisibility();
    }

    @Override
    public void onPropertyChanged(PropertyObservable<KeyboardAccessoryModel.PropertyKey> source,
            @Nullable KeyboardAccessoryModel.PropertyKey propertyKey) {
        // Update the visibility only if we haven't set it just now.
        if (propertyKey == KeyboardAccessoryModel.PropertyKey.VISIBLE) {
            // When the accessory just (dis)appeared, there should be no active tab.
            closeActiveTab();
            mVisibilityDelegate.onBottomControlSpaceChanged();
            if (!mModel.isVisible()) {
                // TODO(fhorschig|ioanap): Maybe the generation bridge should take care of that.
                onItemsAvailable(AccessoryAction.GENERATE_PASSWORD_AUTOMATIC, new Action[0]);
            }
            return;
        }
        if (propertyKey == KeyboardAccessoryModel.PropertyKey.ACTIVE_TAB) {
            Integer activeTab = mModel.activeTab();
            if (activeTab == null) {
                mVisibilityDelegate.onCloseAccessorySheet();
                updateVisibility();
                return;
            }
            mVisibilityDelegate.onChangeAccessorySheet(activeTab);
            return;
        }
        if (propertyKey == KeyboardAccessoryModel.PropertyKey.BOTTOM_OFFSET
                || propertyKey == KeyboardAccessoryModel.PropertyKey.TAB_SELECTION_CALLBACKS) {
            return;
        }
        assert false : "Every property update needs to be handled explicitly!";
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mModel.setActiveTab(tab.getPosition());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        if (mModel.activeTab() == null) {
            mModel.setActiveTab(tab.getPosition());
        } else {
            KeyboardAccessoryMetricsRecorder.recordSheetTrigger(
                    mModel.getTabList().get(mModel.activeTab()).getRecordingType(), MANUAL_CLOSE);
            mVisibilityDelegate.onOpenKeyboard(); // This will close the active tab gently.
        }
    }

    boolean hasContents() {
        return mModel.getActionList().size() > 0 || mModel.getTabList().size() > 0;
    }

    private boolean shouldShowAccessory() {
        if (!mShowIfNotEmpty && mModel.activeTab() == null) return false;
        return hasContents();
    }

    private void updateVisibility() {
        mModel.setVisible(shouldShowAccessory());
    }

    public void setBottomOffset(@Px int bottomOffset) {
        mModel.setBottomOffset(bottomOffset);
    }

    public boolean isShown() {
        return mModel.isVisible();
    }

    public boolean hasActiveTab() {
        return mModel.isVisible() && mModel.activeTab() != null;
    }
}
