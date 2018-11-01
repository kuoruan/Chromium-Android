// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.home.list;

import org.chromium.base.CollectionUtil;
import org.chromium.chrome.browser.download.home.filter.Filters;
import org.chromium.chrome.browser.download.home.filter.OfflineItemFilterObserver;
import org.chromium.chrome.browser.download.home.filter.OfflineItemFilterSource;
import org.chromium.chrome.browser.download.home.list.ListItem.DateListItem;
import org.chromium.chrome.browser.download.home.list.ListItem.OfflineItemListItem;
import org.chromium.chrome.browser.download.home.list.ListItem.SectionHeaderListItem;
import org.chromium.chrome.browser.download.home.list.ListItem.SeparatorViewListItem;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItemFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class responsible for turning a {@link Collection} of {@link OfflineItem}s into a list meant
 * to be displayed in the download home UI.  This list has the following properties:
 * - Sorted.
 * - Separated by date headers for each individual day.
 * - Converts changes in the form of {@link Collection}s to delta changes on the list.
 *
 * TODO(dtrainor): This should be optimized in the near future.  There are a few key things that can
 * be changed:
 * - Do a single iterating across each list to merge/unmerge them.  This requires sorting and
 *   tracking the current position across both as iterating (see {@link #onItemsRemoved(Collection)}
 *   for an example since that is close to doing what we want - minus the contains() call).
 */
class DateOrderedListMutator implements OfflineItemFilterObserver {
    private final ListItemModel mModel;

    private final Map<Date, DateGroup> mDateGroups =
            new TreeMap<>((lhs, rhs) -> { return rhs.compareTo(lhs); });

    private boolean mHideAllHeaders;
    private boolean mHideSectionHeaders;

    /**
     * Creates an DateOrderedList instance that will reflect {@code source}.
     * @param source The source of data for this list.
     * @param model  The model that will be the storage for the updated list.
     */
    public DateOrderedListMutator(OfflineItemFilterSource source, ListItemModel model) {
        mModel = model;
        source.addObserver(this);
        onItemsAdded(source.getItems());
    }

    /**
     * Called when the selected tab or chip has changed.
     * @param filter The currently selected filter type.
     */
    public void onFilterTypeSelected(@Filters.FilterType int filter) {
        mHideAllHeaders = filter == Filters.FilterType.PREFETCHED;
        mHideSectionHeaders = filter != Filters.FilterType.NONE;
    }

    // OfflineItemFilterObserver implementation.
    @Override
    public void onItemsAdded(Collection<OfflineItem> items) {
        for (OfflineItem item : items) {
            Date date = getDateFromOfflineItem(item);
            DateGroup dateGroup = mDateGroups.get(date);
            if (dateGroup == null) {
                dateGroup = new DateGroup();
                mDateGroups.put(date, dateGroup);
            }
            dateGroup.addItem(item);
        }

        pushItemsToModel();
    }

    @Override
    public void onItemsRemoved(Collection<OfflineItem> items) {
        for (OfflineItem item : items) {
            Date date = getDateFromOfflineItem(item);
            DateGroup dateGroup = mDateGroups.get(date);
            if (dateGroup == null) continue;

            dateGroup.removeItem(item);
            if (dateGroup.sections.isEmpty()) {
                mDateGroups.remove(date);
            }
        }

        pushItemsToModel();
    }

    @Override
    public void onItemUpdated(OfflineItem oldItem, OfflineItem item) {
        assert oldItem.id.equals(item.id);

        // If the update changed the creation time or filter type, remove and add the element to get
        // it positioned.
        if (oldItem.creationTimeMs != item.creationTimeMs || oldItem.filter != item.filter) {
            // TODO(shaktisahu): Collect UMA when this happens.
            onItemsRemoved(CollectionUtil.newArrayList(oldItem));
            onItemsAdded(CollectionUtil.newArrayList(item));
        } else {
            for (int i = 0; i < mModel.size(); i++) {
                ListItem listItem = mModel.get(i);
                if (!(listItem instanceof OfflineItemListItem)) continue;

                OfflineItem offlineListItem = ((OfflineItemListItem) listItem).item;
                if (item.id.equals(offlineListItem.id)) {
                    mModel.update(i, new OfflineItemListItem(item));
                }
            }
        }

        mModel.dispatchLastEvent();
    }

    // Flattens out the hierarchical data and adds items to the model in the order they should be
    // displayed. Date header, section header, date separator and section separators are added
    // wherever necessary. The existing items in the model are replaced by the new set of items
    // computed.
    // TODO(shaktisahu): Write a version having no headers for the prefetch tab.
    private void pushItemsToModel() {
        List<ListItem> listItems = new ArrayList<>();
        int dateIndex = 0;
        for (Date date : mDateGroups.keySet()) {
            DateGroup dateGroup = mDateGroups.get(date);
            int sectionIndex = 0;

            // Add an item for the date header.
            if (!mHideAllHeaders) {
                listItems.add(new DateListItem(CalendarUtils.getStartOfDay(date.getTime())));
            }

            // For each section.
            for (Integer filter : dateGroup.sections.keySet()) {
                Section section = dateGroup.sections.get(filter);

                // Add a section header.
                if (!mHideSectionHeaders && !mHideAllHeaders) {
                    SectionHeaderListItem sectionHeaderItem =
                            new SectionHeaderListItem(filter, date.getTime());
                    sectionHeaderItem.isFirstSectionOfDay = sectionIndex == 0;
                    listItems.add(sectionHeaderItem);
                }

                // Add the items in the section.
                for (OfflineItem offlineItem : section.items.values()) {
                    OfflineItemListItem item = new OfflineItemListItem(offlineItem);
                    if (section.items.size() == 1
                            && offlineItem.filter == OfflineItemFilter.FILTER_IMAGE) {
                        item.spanFullWidth = true;
                    }
                    listItems.add(item);
                }

                // Add a section separator if needed.
                if (!mHideAllHeaders && sectionIndex < dateGroup.sections.size() - 1) {
                    listItems.add(new SeparatorViewListItem(date.getTime(), filter));
                }
                sectionIndex++;
            }

            // Add a date separator if needed.
            if (!mHideAllHeaders && dateIndex < mDateGroups.size() - 1) {
                listItems.add(new SeparatorViewListItem(date.getTime()));
            }
            dateIndex++;
        }

        mModel.set(listItems);
        mModel.dispatchLastEvent();
    }

    private Date getDateFromOfflineItem(OfflineItem offlineItem) {
        return CalendarUtils.getStartOfDay(offlineItem.creationTimeMs).getTime();
    }

    /** Represents a group of items which were downloaded on the same day. */
    private static class DateGroup {
        /**
         * The list of sections for the day. The ordering is done in the same order as {@code
         * Filters.FilterType}.
         */
        public Map<Integer, Section> sections = new TreeMap<>((lhs, rhs) -> {
            return Filters.fromOfflineItem(lhs).compareTo(Filters.fromOfflineItem(rhs));
        });

        public void addItem(OfflineItem item) {
            Section section = sections.get(item.filter);
            if (section == null) {
                section = new Section();
                sections.put(item.filter, section);
            }
            section.addItem(item);
        }

        public void removeItem(OfflineItem item) {
            Section section = sections.get(item.filter);
            if (section == null) return;

            section.removeItem(item);
            if (section.items.isEmpty()) {
                sections.remove(item.filter);
            }
        }
    }

    /** Represents a group of items having the same filter type. */
    private static class Section {
        public Map<Date, OfflineItem> items =
                new TreeMap<>((lhs, rhs) -> { return rhs.compareTo(lhs); });

        public void addItem(OfflineItem item) {
            items.put(new Date(item.creationTimeMs), item);
        }

        public void removeItem(OfflineItem item) {
            items.remove(new Date(item.creationTimeMs));
        }
    }
}
