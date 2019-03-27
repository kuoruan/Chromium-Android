// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.AccessorySheetData;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Provider;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.RecyclerViewAdapter;
import org.chromium.ui.modelutil.SimpleRecyclerViewMcp;

/**
 * This component is a tab that can be added to the {@link ManualFillingCoordinator} which shows it
 * as bottom sheet below the keyboard accessory.
 */
public class PasswordAccessorySheetCoordinator extends AccessorySheetTabCoordinator {
    private final AccessorySheetTabModel mModel = new AccessorySheetTabModel();
    private final PasswordAccessorySheetMediator mMediator;

    @VisibleForTesting
    static class IconProvider {
        private final static IconProvider sInstance = new IconProvider();
        private Drawable mIcon;
        private IconProvider() {}

        public static IconProvider getInstance() {
            return sInstance;
        }

        /**
         * Loads and remembers the icon used for this class. Used to mock icons in unit tests.
         * @param context The context containing the icon resources.
         * @return The icon as {@link Drawable}.
         */
        public Drawable getIcon(Context context) {
            if (mIcon != null) return mIcon;
            mIcon = AppCompatResources.getDrawable(context, R.drawable.ic_vpn_key_grey);
            return mIcon;
        }

        @VisibleForTesting
        void setIconForTesting(Drawable icon) {
            mIcon = icon;
        }
    }

    /**
     * Creates the passwords tab.
     * @param context The {@link Context} containing resources like icons and layouts for this tab.
     * @param scrollListener An optional listener that will be bound to the inflated recycler view.
     */
    public PasswordAccessorySheetCoordinator(
            Context context, @Nullable RecyclerView.OnScrollListener scrollListener) {
        super(IconProvider.getInstance().getIcon(context),
                context.getString(R.string.password_accessory_sheet_toggle),
                context.getString(R.string.password_accessory_sheet_opened),
                R.layout.password_accessory_sheet, AccessoryTabType.PASSWORDS, scrollListener);
        mMediator = new PasswordAccessorySheetMediator(mModel);
    }

    @Override
    public void onTabCreated(ViewGroup view) {
        super.onTabCreated(view);
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.AUTOFILL_KEYBOARD_ACCESSORY)) {
            PasswordAccessorySheetModernViewBinder.initializeView((RecyclerView) view, mModel);
        } else {
            PasswordAccessorySheetViewBinder.initializeView((RecyclerView) view, mModel);
        }
    }

    @Override
    public void onTabShown() {
        mMediator.onTabShown();
    }

    /**
     * Registers the provider pushing a complete new instance of {@link AccessorySheetData} that
     * should be displayed as sheet for this tab.
     * @param accessorySheetDataProvider A {@link Provider<AccessorySheetData>}.
     */
    void registerDataProvider(Provider<AccessorySheetData> accessorySheetDataProvider) {
        accessorySheetDataProvider.addObserver(mMediator);
    }

    /**
     * Creates an adapter to an {@link PasswordAccessorySheetViewBinder} that is wired
     * up to a model change processor listening to the {@link AccessorySheetTabModel}.
     * @param model the {@link AccessorySheetTabModel} the adapter gets its data from.
     * @return Returns a fully initialized and wired adapter to a PasswordAccessorySheetViewBinder.
     */
    static RecyclerViewAdapter<AccessorySheetTabViewBinder.ElementViewHolder, Void> createAdapter(
            AccessorySheetTabModel model) {
        return new RecyclerViewAdapter<>(
                new SimpleRecyclerViewMcp<>(model, AccessorySheetDataPiece::getType,
                        AccessorySheetTabViewBinder.ElementViewHolder::bind),
                PasswordAccessorySheetViewBinder::create);
    }

    /**
     * Creates an adapter to an {@link PasswordAccessorySheetModernViewBinder} that is wired up to
     * the model change processor which listens to the {@link AccessorySheetTabModel}.
     * @param model the {@link AccessorySheetTabModel} the adapter gets its data from.
     * @return Returns an {@link PasswordAccessorySheetModernViewBinder} wired to a MCP.
     */
    static RecyclerViewAdapter<AccessorySheetTabViewBinder.ElementViewHolder, Void>
    createModernAdapter(ListModel<AccessorySheetDataPiece> model) {
        return new RecyclerViewAdapter<>(
                new SimpleRecyclerViewMcp<>(model, AccessorySheetDataPiece::getType,
                        AccessorySheetTabViewBinder.ElementViewHolder::bind),
                PasswordAccessorySheetModernViewBinder::create);
    }

    @VisibleForTesting
    AccessorySheetTabModel getSheetDataPiecesForTesting() {
        return mModel;
    }
}
