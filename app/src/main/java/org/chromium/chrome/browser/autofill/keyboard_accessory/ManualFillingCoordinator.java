// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewStub;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Provider;
import org.chromium.ui.DeferredViewStubInflationProvider;
import org.chromium.ui.DropdownPopupWindow;
import org.chromium.ui.ViewProvider;
import org.chromium.ui.base.WindowAndroid;

/**
 * Handles requests to the manual UI for filling passwords, payments and other user data. Ideally,
 * the caller has no access to Keyboard accessory or sheet and is only interacting with this
 * component.
 * For that, it facilitates the communication between {@link KeyboardAccessoryCoordinator} and
 * {@link AccessorySheetCoordinator} to add and trigger surfaces that may assist users while filling
 * fields.
 */
public class ManualFillingCoordinator {
    private final ManualFillingMediator mMediator = new ManualFillingMediator();

    /**
     * Initializes the manual filling component. Calls to this class are NoOps until this method is
     * called.
     * @param windowAndroid The window needed to listen to the keyboard and to connect to activity.
     * @param barStub The {@link ViewStub} used to inflate the keyboard accessory bar.
     * @param sheetStub The {@link ViewStub} used to inflate the keyboard accessory bottom sheet.
     */
    public void initialize(WindowAndroid windowAndroid, ViewStub barStub, ViewStub sheetStub) {
        if (barStub == null || sheetStub == null) return; // The manual filling isn't needed.
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.AUTOFILL_KEYBOARD_ACCESSORY)) {
            barStub.setLayoutResource(org.chromium.chrome.R.layout.keyboard_accessory_modern);
        }
        initialize(windowAndroid, new DeferredViewStubInflationProvider<>(barStub),
                new DeferredViewStubInflationProvider<>(sheetStub));
    }

    @VisibleForTesting
    void initialize(WindowAndroid windowAndroid, ViewProvider<KeyboardAccessoryView> barProvider,
            ViewProvider<AccessorySheetView> sheetProvider) {
        mMediator.initialize(new KeyboardAccessoryCoordinator(mMediator, barProvider),
                new AccessorySheetCoordinator(sheetProvider), windowAndroid);
    }

    /**
     * Cleans up the manual UI by destroying the accessory bar and its bottom sheet.
     */
    public void destroy() {
        mMediator.destroy();
    }

    /**
     * Handles tapping on the Android back button.
     * @return Whether tapping the back button dismissed the accessory sheet or not.
     */
    public boolean handleBackPress() {
        return mMediator.handleBackPress();
    }

    /**
     * Ensures that keyboard accessory and keyboard are hidden and reset.
     */
    public void dismiss() {
        mMediator.dismiss();
    }

    /**
     * Notifies the component that a popup window exists so it can be dismissed if necessary.
     * @param popup A {@link DropdownPopupWindow} that might be dismissed later.
     */
    public void notifyPopupAvailable(DropdownPopupWindow popup) {
        mMediator.notifyPopupOpened(popup);
    }

    /**
     * Requests to close the active tab in the keyboard accessory. If there is no active tab, this
     * is a NoOp.
     */
    public void closeAccessorySheet() {
        mMediator.onCloseAccessorySheet();
    }

    /**
     * Opens the keyboard which implicitly dismisses the sheet. Without open sheet, this is a NoOp.
     */
    public void swapSheetWithKeyboard() {
        mMediator.swapSheetWithKeyboard();
    }

    void registerActionProvider(
            KeyboardAccessoryData.PropertyProvider<KeyboardAccessoryData.Action[]> actionProvider) {
        mMediator.registerActionProvider(actionProvider);
    }

    void registerPasswordProvider(
            Provider<KeyboardAccessoryData.AccessorySheetData> sheetDataProvider) {
        mMediator.registerPasswordProvider(sheetDataProvider);
    }

    public void showWhenKeyboardIsVisible() {
        mMediator.showWhenKeyboardIsVisible();
    }

    public void hide() {
        mMediator.hide();
    }

    public void onResume() {
        mMediator.resume();
    }

    public void onPause() {
        mMediator.pause();
    }

    /**
     * Returns a size manager that allows to access the combined height of
     * {@link KeyboardAccessoryCoordinator} and {@link AccessorySheetCoordinator}, and to be
     * notified when it changes.
     * @return A {@link KeyboardExtensionSizeManager}.
     */
    public KeyboardExtensionSizeManager getKeyboardExtensionSizeManager() {
        return mMediator.getKeyboardExtensionSizeManager();
    }

    // TODO(fhorschig): Should be @VisibleForTesting.
    /**
     * Allows access to the keyboard accessory. This can be used to explicitly modify the the bar of
     * the keyboard accessory (e.g. by providing suggestions or actions).
     * @return The coordinator of the Keyboard accessory component.
     */
    public @Nullable KeyboardAccessoryCoordinator getKeyboardAccessory() {
        return mMediator.getKeyboardAccessory();
    }

    @VisibleForTesting
    ManualFillingMediator getMediatorForTesting() {
        return mMediator;
    }

    /**
     * Returns whether the Keyboard is replaced by an accessory sheet or is about to do so.
     * @return True if an accessory sheet is (being) opened and replacing the keyboard.
     * @param view A {@link View} that is used to find the window root.
     */
    public boolean isFillingViewShown(View view) {
        return mMediator.isFillingViewShown(view);
    }
}