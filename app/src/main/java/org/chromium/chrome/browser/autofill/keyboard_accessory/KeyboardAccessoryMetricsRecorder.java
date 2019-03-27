// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece.Type.PASSWORD_INFO;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece.getType;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTrigger.MANUAL_OPEN;
import static org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BAR_ITEMS;

import android.support.annotation.Nullable;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTabModel.AccessorySheetDataPiece;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.UserInfo;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryProperties.BarItem;
import org.chromium.ui.modelutil.ListModel;
import org.chromium.ui.modelutil.ListObservable;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyObservable;

import java.util.HashSet;
import java.util.Set;

/**
 * This class provides helpers to record metrics related to the keyboard accessory and its sheets.
 * It can set up observers to observe {@link KeyboardAccessoryProperties}-based models, {@link
 * AccessorySheetProperties}-based models or {@link ListObservable<>}s, and records metrics
 * accordingly.
 */
public class KeyboardAccessoryMetricsRecorder {
    static final String UMA_KEYBOARD_ACCESSORY_ACTION_IMPRESSION =
            "KeyboardAccessory.AccessoryActionImpression";
    public static final String UMA_KEYBOARD_ACCESSORY_ACTION_SELECTED =
            "KeyboardAccessory.AccessoryActionSelected";
    static final String UMA_KEYBOARD_ACCESSORY_BAR_SHOWN = "KeyboardAccessory.AccessoryBarShown";
    static final String UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTIONS =
            "KeyboardAccessory.AccessorySheetSuggestionCount";
    static final String UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTION_SELECTED =
            "KeyboardAccessory.AccessorySheetSuggestionsSelected";
    static final String UMA_KEYBOARD_ACCESSORY_SHEET_TRIGGERED =
            "KeyboardAccessory.AccessorySheetTriggered";
    static final String UMA_KEYBOARD_ACCESSORY_SHEET_TYPE_SUFFIX_PASSWORDS = "Passwords";

    /**
     * The Recorder itself should be stateless and have no need for an instance.
     */
    private KeyboardAccessoryMetricsRecorder() {}

    /**
     * This observer will react to changes of the {@link KeyboardAccessoryProperties} and store each
     * impression once per visibility change.
     */
    private static class AccessoryBarObserver
            implements ListObservable.ListObserver<Void>,
                       PropertyObservable.PropertyObserver<PropertyKey> {
        private final Set<Integer> mRecordedBarBuckets = new HashSet<>();
        private final Set<Integer> mRecordedActionImpressions = new HashSet<>();
        private final PropertyModel mModel;
        private final KeyboardAccessoryCoordinator.TabSwitchingDelegate mTabSwitcher;

        AccessoryBarObserver(PropertyModel keyboardAccessoryModel,
                KeyboardAccessoryCoordinator.TabSwitchingDelegate tabSwitcher) {
            mModel = keyboardAccessoryModel;
            mTabSwitcher = tabSwitcher;
        }

        @Override
        public void onPropertyChanged(
                PropertyObservable<PropertyKey> source, @Nullable PropertyKey propertyKey) {
            if (propertyKey == KeyboardAccessoryProperties.VISIBLE) {
                if (mModel.get(KeyboardAccessoryProperties.VISIBLE)) {
                    recordFirstImpression();
                    maybeRecordBarBucket(AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS);
                    maybeRecordBarBucket(AccessoryBarContents.WITH_TABS);
                    recordUnrecordedList(mModel.get(KeyboardAccessoryProperties.BAR_ITEMS), 0,
                            mModel.get(KeyboardAccessoryProperties.BAR_ITEMS).size());
                } else {
                    mRecordedBarBuckets.clear();
                    mRecordedActionImpressions.clear();
                }
                return;
            }
            if (propertyKey == KeyboardAccessoryProperties.BOTTOM_OFFSET_PX
                    || propertyKey == KeyboardAccessoryProperties.KEYBOARD_TOGGLE_VISIBLE
                    || propertyKey == KeyboardAccessoryProperties.SHOW_KEYBOARD_CALLBACK) {
                return;
            }
            assert false : "Every property update needs to be handled explicitly!";
        }

        /**
         * If not done yet, this records an impression for the general type of list that was added.
         * In addition, it records impressions for each new action type that changed in the list.
         * @param list A list of {@link BarItem}s.
         * @param first Index of the first element that changed.
         * @param count Number of elements starting with |first| that were added or changed.
         */
        private void recordUnrecordedList(ListObservable list, int first, int count) {
            if (!mModel.get(KeyboardAccessoryProperties.VISIBLE)) return;
            if (list != mModel.get(BAR_ITEMS)) return;
            // Remove all changed items, so changes are treated as new recordings.
            for (int index = first; index < first + count; ++index) {
                BarItem barItem = mModel.get(BAR_ITEMS).get(index);
                mRecordedActionImpressions.remove(barItem.getViewType());
            }
            // Record any unrecorded type, but not more than once (i.e. one set of suggestion).
            for (int index = first; index < first + count; ++index) {
                KeyboardAccessoryData.Action action = mModel.get(BAR_ITEMS).get(index).getAction();
                if (action == null) continue; // Ignore!
                maybeRecordBarBucket(action.getActionType() == AccessoryAction.AUTOFILL_SUGGESTION
                                ? AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS
                                : AccessoryBarContents.WITH_ACTIONS);
                if (mRecordedActionImpressions.add(action.getActionType())) {
                    recordActionImpression(action.getActionType());
                }
            }
        }

        private void recordGeneralActionTypes() {
            if (!mModel.get(KeyboardAccessoryProperties.VISIBLE)) return;
            // Record any unrecorded type, but not more than once (i.e. one set of suggestion).
            for (int index = 0; index < mModel.get(BAR_ITEMS).size(); ++index) {
                KeyboardAccessoryData.Action action = mModel.get(BAR_ITEMS).get(index).getAction();
                if (action == null) continue; // Item is no relevant action.
                maybeRecordBarBucket(action.getActionType() == AccessoryAction.AUTOFILL_SUGGESTION
                                ? AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS
                                : AccessoryBarContents.WITH_ACTIONS);
            }
        }

        /**
         * Records whether the first impression of the bar contained any contents (which it should).
         */
        private void recordFirstImpression() {
            if (!mRecordedBarBuckets.isEmpty()) return;
            @AccessoryBarContents
            int bucketToRecord = AccessoryBarContents.NO_CONTENTS;
            for (@AccessoryBarContents int bucket = 0; bucket < AccessoryBarContents.COUNT;
                    ++bucket) {
                if (shouldRecordAccessoryBarImpression(bucket)) {
                    bucketToRecord = AccessoryBarContents.ANY_CONTENTS;
                    break;
                }
            }
            maybeRecordBarBucket(bucketToRecord);
        }

        @Override
        public void onItemRangeInserted(ListObservable source, int index, int count) {
            recordUnrecordedList(source, index, count);
        }

        @Override
        public void onItemRangeRemoved(ListObservable source, int index, int count) {}

        @Override
        public void onItemRangeChanged(
                ListObservable<Void> source, int index, int count, @Nullable Void payload) {
            // Remove all actions that were changed, so changes are treated as new recordings.
            for (int i = index; i < index + count; ++i) {
                KeyboardAccessoryData.Action action = mModel.get(BAR_ITEMS).get(i).getAction();
                if (action == null) continue; // Item is no recordable action.
                mRecordedActionImpressions.remove(action.getActionType());
            }
            recordUnrecordedList(source, index, count);
        }

        /**
         * Returns an impression for the accessory bar if it hasn't occurred yet.
         * @param bucket The bucket to record.
         */
        private void maybeRecordBarBucket(@AccessoryBarContents int bucket) {
            if (!shouldRecordAccessoryBarImpression(bucket)) return;
            mRecordedBarBuckets.add(bucket);
            RecordHistogram.recordEnumeratedHistogram(
                    UMA_KEYBOARD_ACCESSORY_BAR_SHOWN, bucket, AccessoryBarContents.COUNT);
        }

        /**
         * If a checks whether the given bucket should be recorded (i.e. the property it observes is
         * not empty, the accessory is visible and it wasn't recorded yet).
         * @param bucket
         * @return
         */
        private boolean shouldRecordAccessoryBarImpression(int bucket) {
            if (!mModel.get(KeyboardAccessoryProperties.VISIBLE)) return false;
            if (mRecordedBarBuckets.contains(bucket)) return false;
            switch (bucket) {
                case AccessoryBarContents.WITH_ACTIONS:
                    return hasAtLeastOneActionOfType(mModel.get(BAR_ITEMS),
                            AccessoryAction.MANAGE_PASSWORDS,
                            AccessoryAction.GENERATE_PASSWORD_AUTOMATIC);
                case AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS:
                    return hasAtLeastOneActionOfType(
                            mModel.get(BAR_ITEMS), AccessoryAction.AUTOFILL_SUGGESTION);
                case AccessoryBarContents.WITH_TABS:
                    return mTabSwitcher.hasTabs();
                case AccessoryBarContents.ANY_CONTENTS: // Intentional fallthrough.
                case AccessoryBarContents.NO_CONTENTS:
                    return true; // Logged on first impression.
            }
            assert false : "Did not check whether to record an impression bucket " + bucket + ".";
            return false;
        }
    }

    /**
     * Registers an observer to the given model that records changes for all properties.
     * @param keyboardAccessoryModel The observable {@link KeyboardAccessoryProperties}.
     */
    static void registerKeyboardAccessoryModelMetricsObserver(PropertyModel keyboardAccessoryModel,
            KeyboardAccessoryCoordinator.TabSwitchingDelegate tabSwitcher) {
        AccessoryBarObserver observer =
                new AccessoryBarObserver(keyboardAccessoryModel, tabSwitcher);
        keyboardAccessoryModel.addObserver(observer);
        keyboardAccessoryModel.get(BAR_ITEMS).addObserver(observer);
    }

    /**
     * Registers an observer to the given model that records changes for all properties.
     * @param accessorySheetModel The observable {@link AccessorySheetProperties}.
     */
    static void registerAccessorySheetModelMetricsObserver(PropertyModel accessorySheetModel) {
        accessorySheetModel.addObserver((source, propertyKey) -> {
            if (propertyKey == AccessorySheetProperties.VISIBLE) {
                if (accessorySheetModel.get(AccessorySheetProperties.VISIBLE)) {
                    int activeTab =
                            accessorySheetModel.get(AccessorySheetProperties.ACTIVE_TAB_INDEX);
                    if (activeTab >= 0
                            && activeTab < accessorySheetModel.get(AccessorySheetProperties.TABS)
                                                   .size()) {
                        recordSheetTrigger(accessorySheetModel.get(AccessorySheetProperties.TABS)
                                                   .get(activeTab)
                                                   .getRecordingType(),
                                MANUAL_OPEN);
                    }
                } else {
                    recordSheetTrigger(AccessoryTabType.ALL, AccessorySheetTrigger.ANY_CLOSE);
                }
                return;
            }
            if (propertyKey == AccessorySheetProperties.ACTIVE_TAB_INDEX
                    || propertyKey == AccessorySheetProperties.HEIGHT
                    || propertyKey == AccessorySheetProperties.TOP_SHADOW_VISIBLE
                    || propertyKey == AccessorySheetProperties.PAGE_CHANGE_LISTENER) {
                return;
            }
            assert false : "Every property update needs to be handled explicitly!";
        });
    }

    /**
     * Gets the complete name of a histogram for the given tab type.
     * @param baseHistogram the base histogram.
     * @param tabType The tab type that determines the histogram's suffix.
     * @return The complete name of the histogram.
     */
    @VisibleForTesting
    static String getHistogramForType(String baseHistogram, @AccessoryTabType int tabType) {
        switch (tabType) {
            case AccessoryTabType.ALL:
                return baseHistogram;
            case AccessoryTabType.PASSWORDS:
                return baseHistogram + "." + UMA_KEYBOARD_ACCESSORY_SHEET_TYPE_SUFFIX_PASSWORDS;
        }
        assert false : "Undefined histogram for tab type " + tabType + " !";
        return "";
    }

    /**
     * Records why an accessory sheet was toggled.
     * @param tabType The tab that was selected to trigger the sheet.
     * @param bucket The {@link AccessorySheetTrigger} to record..
     */
    static void recordSheetTrigger(
            @AccessoryTabType int tabType, @AccessorySheetTrigger int bucket) {
        RecordHistogram.recordEnumeratedHistogram(
                getHistogramForType(UMA_KEYBOARD_ACCESSORY_SHEET_TRIGGERED, tabType), bucket,
                AccessorySheetTrigger.COUNT);
        if (tabType != AccessoryTabType.ALL) { // Record count for all tab types exactly once!
            RecordHistogram.recordEnumeratedHistogram(
                    getHistogramForType(
                            UMA_KEYBOARD_ACCESSORY_SHEET_TRIGGERED, AccessoryTabType.ALL),
                    bucket, AccessorySheetTrigger.COUNT);
        }
    }

    static void recordActionImpression(@AccessoryAction int bucket) {
        RecordHistogram.recordEnumeratedHistogram(
                UMA_KEYBOARD_ACCESSORY_ACTION_IMPRESSION, bucket, AccessoryAction.COUNT);
    }

    public static void recordActionSelected(@AccessoryAction int bucket) {
        RecordHistogram.recordEnumeratedHistogram(
                UMA_KEYBOARD_ACCESSORY_ACTION_SELECTED, bucket, AccessoryAction.COUNT);
    }

    static void recordSuggestionSelected(
            @AccessoryTabType int tabType, @AccessorySuggestionType int bucket) {
        RecordHistogram.recordEnumeratedHistogram(
                getHistogramForType(
                        UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTION_SELECTED, AccessoryTabType.ALL),
                bucket, AccessorySuggestionType.COUNT);
        if (tabType != AccessoryTabType.ALL) { // If recorded for all, don't record again.
            RecordHistogram.recordEnumeratedHistogram(
                    getHistogramForType(UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTION_SELECTED, tabType),
                    bucket, AccessorySuggestionType.COUNT);
        }
    }

    /**
     * Records the number of interactive suggestions in the given list.
     * @param tabType The tab that contained the list.
     * @param suggestionList The list containing all suggestions.
     */
    static void recordSheetSuggestions(
            @AccessoryTabType int tabType, ListModel<AccessorySheetDataPiece> suggestionList) {
        int interactiveSuggestions = 0;
        for (int i = 0; i < suggestionList.size(); ++i) {
            if (getType(suggestionList.get(i)) == PASSWORD_INFO) {
                UserInfo info = (UserInfo) suggestionList.get(i).getDataPiece();
                for (UserInfo.Field field : info.getFields()) {
                    if (field.isSelectable()) ++interactiveSuggestions;
                }
            }
        }
        RecordHistogram.recordCount100Histogram(
                getHistogramForType(UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTIONS, tabType),
                interactiveSuggestions);
        if (tabType != AccessoryTabType.ALL) { // Record count for all tab types exactly once!
            RecordHistogram.recordCount100Histogram(
                    getHistogramForType(
                            UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTIONS, AccessoryTabType.ALL),
                    interactiveSuggestions);
        }
    }

    private static boolean hasAtLeastOneActionOfType(
            ListModel<BarItem> itemList, @AccessoryAction int... types) {
        Set<Integer> typeList = new HashSet<>(types.length);
        for (@AccessoryAction int type : types) typeList.add(type);
        for (BarItem barItem : itemList) {
            if (barItem.getAction() == null) continue; // Item irrelevant for recording.
            if (typeList.contains(barItem.getAction().getActionType())) return true;
        }
        return false;
    }
}
