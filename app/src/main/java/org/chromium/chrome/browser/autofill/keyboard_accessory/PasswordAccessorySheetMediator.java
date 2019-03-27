// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece;
import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece.Type;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.AccessorySheetData;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.FooterCommand;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.UserInfo;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains all logic for the password accessory sheet component. Changes to its internal
 * {@link PropertyModel} are observed by a {@link PropertyModelChangeProcessor} and affect the
 * password accessory sheet tab view.
 */
class PasswordAccessorySheetMediator implements KeyboardAccessoryData.Observer<AccessorySheetData> {
    private final AccessorySheetTabModel mModel;

    @Override
    public void onItemAvailable(int typeId, AccessorySheetData accessorySheetData) {
        mModel.set(splitIntoDataPieces(accessorySheetData));
    }

    PasswordAccessorySheetMediator(AccessorySheetTabModel model) {
        mModel = model;
    }

    void onTabShown() {
        KeyboardAccessoryMetricsRecorder.recordActionImpression(AccessoryAction.MANAGE_PASSWORDS);
        KeyboardAccessoryMetricsRecorder.recordSheetSuggestions(AccessoryTabType.PASSWORDS, mModel);
    }

    private AccessorySheetDataPiece[] splitIntoDataPieces(AccessorySheetData accessorySheetData) {
        if (accessorySheetData == null) return new AccessorySheetDataPiece[0];

        List<AccessorySheetDataPiece> items = new ArrayList<>();
        items.add(new AccessorySheetDataPiece(accessorySheetData.getTitle(), Type.TITLE));
        for (UserInfo userInfo : accessorySheetData.getUserInfoList()) {
            items.add(new AccessorySheetDataPiece(userInfo, Type.PASSWORD_INFO));
        }
        for (FooterCommand command : accessorySheetData.getFooterCommands()) {
            items.add(new AccessorySheetDataPiece(command, Type.FOOTER_COMMAND));
        }

        return items.toArray(new AccessorySheetDataPiece[0]);
    }
}
