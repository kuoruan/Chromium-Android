// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView;

import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.AccessorySheetData;
import org.chromium.ui.modelutil.ListModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class describes the {@link ListModel} used for keyboard accessory sheets like the
 * {@link PasswordAccessorySheetCoordinator}.
 */
class AccessorySheetTabModel extends ListModel<AccessorySheetTabModel.AccessorySheetDataPiece> {
    /**
     * The {@link AccessorySheetData} has to be mapped to single items in a {@link RecyclerView}.
     * This class allows wrapping the {@link AccessorySheetData} into small chunks that are
     * organized in a {@link ListModel}. A specific ViewHolder is defined for each piece.
     */
    static class AccessorySheetDataPiece {
        @IntDef({Type.TITLE, Type.PASSWORD_INFO, Type.FOOTER_COMMAND})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
            /**
             * An item in title style used to display text. Non-interactive.
             */
            int TITLE = 1;
            /**
             * A section with user credentials.
             */
            int PASSWORD_INFO = 2;
            /**
             * A command at the end of the accessory sheet tab.
             */
            int FOOTER_COMMAND = 3;
        }

        private Object mDataPiece;
        private @Type int mType;

        AccessorySheetDataPiece(Object dataPiece, @Type int type) {
            mDataPiece = dataPiece;
            mType = type;
        }

        public static int getType(AccessorySheetDataPiece accessorySheetDataPiece) {
            return accessorySheetDataPiece.mType;
        }

        public Object getDataPiece() {
            return mDataPiece;
        }
    }
}
