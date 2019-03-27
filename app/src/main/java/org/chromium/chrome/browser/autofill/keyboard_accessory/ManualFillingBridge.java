// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.graphics.Bitmap;
import android.support.annotation.Px;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.AccessorySheetData;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Action;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.FooterCommand;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.UserInfo;
import org.chromium.ui.base.WindowAndroid;

class ManualFillingBridge {
    private final KeyboardAccessoryData.PropertyProvider<AccessorySheetData> mSheetDataProvider =
            new KeyboardAccessoryData.PropertyProvider<>();
    private final KeyboardAccessoryData.PropertyProvider<Action[]> mActionProvider =
            new KeyboardAccessoryData.PropertyProvider<>(
                    AccessoryAction.GENERATE_PASSWORD_AUTOMATIC);
    private final ManualFillingCoordinator mManualFillingCoordinator;
    private final ChromeActivity mActivity;
    private long mNativeView;

    private ManualFillingBridge(long nativeView, WindowAndroid windowAndroid) {
        mNativeView = nativeView;
        mActivity = (ChromeActivity) windowAndroid.getActivity().get();
        mManualFillingCoordinator = mActivity.getManualFillingController();
        mManualFillingCoordinator.registerPasswordProvider(mSheetDataProvider);
        mManualFillingCoordinator.registerActionProvider(mActionProvider);
    }

    @CalledByNative
    private static ManualFillingBridge create(long nativeView, WindowAndroid windowAndroid) {
        return new ManualFillingBridge(nativeView, windowAndroid);
    }

    @CalledByNative
    private void onItemsAvailable(Object objAccessorySheetData) {
        mSheetDataProvider.notifyObservers((AccessorySheetData) objAccessorySheetData);
    }

    @CalledByNative
    private void onAutomaticGenerationStatusChanged(boolean available) {
        final Action[] generationAction;
        if (available) {
            // This is meant to suppress the warning that the short string is not used.
            // TODO(crbug.com/855581): Switch between strings based on whether they fit on the
            // screen or not.
            boolean useLongString = true;
            String caption = useLongString
                    ? mActivity.getString(R.string.password_generation_accessory_button)
                    : mActivity.getString(R.string.password_generation_accessory_button_short);
            generationAction = new Action[] {
                    new Action(caption, AccessoryAction.GENERATE_PASSWORD_AUTOMATIC, (action) -> {
                        assert mNativeView
                                != 0
                            : "Controller has been destroyed but the bridge wasn't cleaned up!";
                        KeyboardAccessoryMetricsRecorder.recordActionSelected(
                                AccessoryAction.GENERATE_PASSWORD_AUTOMATIC);
                        nativeOnGenerationRequested(mNativeView);
                    })};
        } else {
            generationAction = new Action[0];
        }
        mActionProvider.notifyObservers(generationAction);
    }

    @CalledByNative
    void showWhenKeyboardIsVisible() {
        mManualFillingCoordinator.showWhenKeyboardIsVisible();
    }

    @CalledByNative
    void hide() {
        mManualFillingCoordinator.hide();
    }

    @CalledByNative
    private void closeAccessorySheet() {
        mManualFillingCoordinator.closeAccessorySheet();
    }

    @CalledByNative
    private void swapSheetWithKeyboard() {
        mManualFillingCoordinator.swapSheetWithKeyboard();
    }

    @CalledByNative
    private void destroy() {
        mSheetDataProvider.notifyObservers(null);
        mNativeView = 0;
    }

    @CalledByNative
    private static Object createAccessorySheetData(String title) {
        return new AccessorySheetData(title);
    }

    @CalledByNative
    private Object addUserInfoToAccessorySheetData(Object objAccessorySheetData) {
        UserInfo userInfo = new UserInfo(this::fetchFavicon);
        ((AccessorySheetData) objAccessorySheetData).getUserInfoList().add(userInfo);
        return userInfo;
    }

    @CalledByNative
    private void addFieldToUserInfo(Object objUserInfo, String displayText, String a11yDescription,
            boolean isObfuscated, boolean selectable) {
        Callback<UserInfo.Field> callback = null;
        if (selectable) {
            callback = (field) -> {
                assert mNativeView != 0 : "Controller was destroyed but the bridge wasn't!";
                KeyboardAccessoryMetricsRecorder.recordSuggestionSelected(
                        AccessoryTabType.PASSWORDS,
                        field.isObfuscated() ? AccessorySuggestionType.PASSWORD
                                             : AccessorySuggestionType.USERNAME);
                nativeOnFillingTriggered(mNativeView, field.isObfuscated(), field.getDisplayText());
            };
        }
        ((UserInfo) objUserInfo)
                .getFields()
                .add(new UserInfo.Field(displayText, a11yDescription, isObfuscated, callback));
    }

    @CalledByNative
    private void addFooterCommandToAccessorySheetData(
            Object objAccessorySheetData, String displayText) {
        ((AccessorySheetData) objAccessorySheetData)
                .getFooterCommands()
                .add(new FooterCommand(displayText, (footerCommand) -> {
                    assert mNativeView != 0 : "Controller was destroyed but the bridge wasn't!";
                    nativeOnOptionSelected(mNativeView, footerCommand.getDisplayText());
                }));
    }

    public void fetchFavicon(@Px int desiredSize, Callback<Bitmap> faviconCallback) {
        assert mNativeView != 0 : "Favicon was requested after the bridge was destroyed!";
        nativeOnFaviconRequested(mNativeView, desiredSize, faviconCallback);
    }

    private native void nativeOnFaviconRequested(long nativeManualFillingViewAndroid,
            int desiredSizeInPx, Callback<Bitmap> faviconCallback);
    private native void nativeOnFillingTriggered(
            long nativeManualFillingViewAndroid, boolean isObfuscated, String textToFill);
    private native void nativeOnOptionSelected(
            long nativeManualFillingViewAndroid, String selectedOption);
    private native void nativeOnGenerationRequested(long nativeManualFillingViewAndroid);
}