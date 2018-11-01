// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill.keyboard_accessory;

import static org.chromium.chrome.browser.autofill.keyboard_accessory.AccessorySheetTrigger.MANUAL_OPEN;

import android.support.annotation.Nullable;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.autofill.keyboard_accessory.KeyboardAccessoryData.Item;
import org.chromium.chrome.browser.modelutil.ListModel;
import org.chromium.chrome.browser.modelutil.ListObservable;
import org.chromium.chrome.browser.modelutil.PropertyObservable;

import java.util.HashSet;
import java.util.Set;

/**
 * This class provides helpers to record metrics related to the keyboard accessory and its sheets.
 * It can set up observers to observe {@link KeyboardAccessoryModel}s, {@link AccessorySheetModel}s
 * or {@link ListObservable<Item>}s changes and records metrics accordingly.
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
     * This observer will react to changes of the {@link KeyboardAccessoryModel} and store each
     * impression once per visibility change.
     */
    private static class AccessoryBarObserver
            implements ListObservable.ListObserver<Void>,
                       PropertyObservable.PropertyObserver<KeyboardAccessoryModel.PropertyKey> {
        private final Set<Integer> mRecordedBarBuckets = new HashSet<>();
        private final Set<Integer> mRecordedActionImpressions = new HashSet<>();
        private final KeyboardAccessoryModel mModel;

        AccessoryBarObserver(KeyboardAccessoryModel keyboardAccessoryModel) {
            mModel = keyboardAccessoryModel;
        }

        @Override
        public void onPropertyChanged(PropertyObservable<KeyboardAccessoryModel.PropertyKey> source,
                @Nullable KeyboardAccessoryModel.PropertyKey propertyKey) {
            if (propertyKey == KeyboardAccessoryModel.PropertyKey.VISIBLE) {
                if (mModel.isVisible()) {
                    recordFirstImpression();
                    maybeRecordBarBucket(AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS);
                    recordUnrecordedList(mModel.getTabList(), 0, mModel.getTabList().size());
                    recordUnrecordedList(mModel.getActionList(), 0, mModel.getActionList().size());
                } else {
                    mRecordedBarBuckets.clear();
                    mRecordedActionImpressions.clear();
                }
                return;
            }
            if (propertyKey == KeyboardAccessoryModel.PropertyKey.ACTIVE_TAB
                    || propertyKey == KeyboardAccessoryModel.PropertyKey.BOTTOM_OFFSET
                    || propertyKey == KeyboardAccessoryModel.PropertyKey.TAB_SELECTION_CALLBACKS) {
                return;
            }
            assert false : "Every property update needs to be handled explicitly!";
        }

        /**
         * If not done yet, this records an impression for the general type of list that was added.
         * In addition, it records impressions for each new action type that changed in the list.
         * @param list A generic list with {@link KeyboardAccessoryData.Tab}s or
         *             {@link KeyboardAccessoryData.Action}s.
         * @param first Index of the first element that changed.
         * @param count Number of elements starting with |first| that were added or changed.
         */
        private void recordUnrecordedList(ListObservable list, int first, int count) {
            if (!mModel.isVisible()) return;
            if (list == mModel.getTabList()) {
                maybeRecordBarBucket(AccessoryBarContents.WITH_TABS);
                return;
            }
            if (list == mModel.getActionList()) {
                // Remove all actions that were changed, so changes are treated as new recordings.
                for (int index = first; index < first + count; ++index) {
                    KeyboardAccessoryData.Action action = mModel.getActionList().get(index);
                    mRecordedActionImpressions.remove(action.getActionType());
                }
                // Record any unrecorded type, but not more than once (i.e. one set of suggestion).
                for (int index = first; index < first + count; ++index) {
                    KeyboardAccessoryData.Action action = mModel.getActionList().get(index);
                    maybeRecordBarBucket(
                            action.getActionType() == AccessoryAction.AUTOFILL_SUGGESTION
                                    ? AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS
                                    : AccessoryBarContents.WITH_ACTIONS);
                    if (mRecordedActionImpressions.add(action.getActionType())) {
                        recordActionImpression(action.getActionType());
                    }
                }
                return;
            }
            assert false : "Tried to record metrics for unknown list " + list;
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
            if (!mModel.isVisible()) return false;
            if (mRecordedBarBuckets.contains(bucket)) return false;
            switch (bucket) {
                case AccessoryBarContents.WITH_ACTIONS:
                    return hasAtLeastOneActionOfType(mModel.getActionList(),
                            AccessoryAction.MANAGE_PASSWORDS,
                            AccessoryAction.GENERATE_PASSWORD_AUTOMATIC);
                case AccessoryBarContents.WITH_AUTOFILL_SUGGESTIONS:
                    return hasAtLeastOneActionOfType(
                            mModel.getActionList(), AccessoryAction.AUTOFILL_SUGGESTION);
                case AccessoryBarContents.WITH_TABS:
                    return mModel.getTabList().size() > 0;
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
     * @param keyboardAccessoryModel The observable {@link KeyboardAccessoryModel}.
     */
    static void registerMetricsObserver(KeyboardAccessoryModel keyboardAccessoryModel) {
        AccessoryBarObserver observer = new AccessoryBarObserver(keyboardAccessoryModel);
        keyboardAccessoryModel.addObserver(observer);
        keyboardAccessoryModel.addTabListObserver(observer);
        keyboardAccessoryModel.addActionListObserver(observer);
    }

    /**
     * Registers an observer to the given model that records changes for all properties.
     * @param accessorySheetModel The observable {@link AccessorySheetModel}.
     */
    static void registerMetricsObserver(AccessorySheetModel accessorySheetModel) {
        accessorySheetModel.addObserver((source, propertyKey) -> {
            if (propertyKey == AccessorySheetModel.PropertyKey.VISIBLE) {
                if (accessorySheetModel.isVisible()) {
                    int activeTab = accessorySheetModel.getActiveTabIndex();
                    if (activeTab >= 0 && activeTab < accessorySheetModel.getTabList().size()) {
                        recordSheetTrigger(
                                accessorySheetModel.getTabList().get(activeTab).getRecordingType(),
                                MANUAL_OPEN);
                    }
                } else {
                    recordSheetTrigger(AccessoryTabType.ALL, AccessorySheetTrigger.ANY_CLOSE);
                }
                return;
            }
            if (propertyKey == AccessorySheetModel.PropertyKey.ACTIVE_TAB_INDEX
                    || propertyKey == AccessorySheetModel.PropertyKey.HEIGHT) {
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

    static void recordSuggestionSelected(@AccessorySuggestionType int bucket) {
        RecordHistogram.recordEnumeratedHistogram(UMA_KEYBOARD_ACCESSORY_SHEET_SUGGESTION_SELECTED,
                bucket, AccessorySuggestionType.COUNT);
    }

    /**
     * Records the number of interactive suggestions in the given list.
     * @param tabType The tab that contained the list.
     * @param suggestionList The list containing all suggestions.
     */
    static void recordSheetSuggestions(
            @AccessoryTabType int tabType, ListModel<Item> suggestionList) {
        int interactiveSuggestions = 0;
        for (int i = 0; i < suggestionList.size(); ++i) {
            if (suggestionList.get(i).getType() == ItemType.SUGGESTION) ++interactiveSuggestions;
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
            ListModel<KeyboardAccessoryData.Action> actionList, @AccessoryAction int... types) {
        Set<Integer> typeList = new HashSet<>(types.length);
        for (@AccessoryAction int type : types) typeList.add(type);
        for (KeyboardAccessoryData.Action action : actionList) {
            if (typeList.contains(action.getActionType())) return true;
        }
        return false;
    }
}
