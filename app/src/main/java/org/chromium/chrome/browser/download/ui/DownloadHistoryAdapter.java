// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadSharedPreferenceHelper;
import org.chromium.chrome.browser.download.ui.BackendProvider.DownloadDelegate;
import org.chromium.chrome.browser.download.ui.BackendProvider.OfflinePageDelegate;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.DownloadItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.OfflinePageItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.content_public.browser.DownloadState;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Bridges the user's download history and the UI used to display it. */
public class DownloadHistoryAdapter extends DateDividedAdapter
        implements DownloadUiObserver, DownloadSharedPreferenceHelper.Observer {

    /** Alerted about changes to internal state. */
    static interface TestObserver {
        abstract void onDownloadItemCreated(DownloadItem item);
        abstract void onDownloadItemUpdated(DownloadItem item);
    }

    private class BackendItemsImpl extends BackendItems {
        @Override
        public DownloadHistoryItemWrapper removeItem(String guid) {
            DownloadHistoryItemWrapper wrapper = super.removeItem(guid);

            if (wrapper != null) {
                mFilePathsToItemsMap.removeItem(wrapper);
                if (getSelectionDelegate().isItemSelected(wrapper)) {
                    getSelectionDelegate().toggleSelectionForItem(wrapper);
                }
            }

            return wrapper;
        }
    }

    /** Represents the subsection header of the suggested pages for a given date. */
    public class SubsectionHeader extends TimedItem {
        private final long mTimestamp;
        private final int mItemCount;
        private final long mTotalFileSize;
        private final Long mStableId;

        public SubsectionHeader(Date date, int itemCount, long totalFileSize) {
            mTimestamp = date.getTime();
            mItemCount = itemCount;
            mTotalFileSize = totalFileSize;

            // Generate a stable ID based on timestamp.
            mStableId = 0xFFFFFFFF00000000L + (getTimestamp() & 0x0FFFFFFFF);
        }

        @Override
        public long getTimestamp() {
            return mTimestamp;
        }

        public int getItemCount() {
            return mItemCount;
        }

        public long getTotalFileSize() {
            return mTotalFileSize;
        }

        @Override
        public long getStableId() {
            return mStableId;
        }
    }

    /**
     * Tracks externally deleted items that have been removed from downloads history.
     * Shared across instances.
     */
    private static final DeletedFileTracker sDeletedFileTracker = new DeletedFileTracker();

    private static final String EMPTY_QUERY = null;

    private final BackendItems mRegularDownloadItems = new BackendItemsImpl();
    private final BackendItems mIncognitoDownloadItems = new BackendItemsImpl();
    private final BackendItems mOfflinePageItems = new BackendItemsImpl();

    private final FilePathsToDownloadItemsMap mFilePathsToItemsMap =
            new FilePathsToDownloadItemsMap();

    private final Map<Date, Boolean> mSubsectionExpanded = new HashMap<>();
    private final ComponentName mParentComponent;
    private final boolean mShowOffTheRecord;
    private final LoadingStateDelegate mLoadingDelegate;
    private final ObserverList<TestObserver> mObservers = new ObserverList<>();
    private final List<DownloadItemView> mViews = new ArrayList<>();

    private BackendProvider mBackendProvider;
    private OfflinePageDownloadBridge.Observer mOfflinePageObserver;
    private int mFilter = DownloadFilter.FILTER_ALL;
    private String mSearchQuery = EMPTY_QUERY;

    DownloadHistoryAdapter(boolean showOffTheRecord, ComponentName parentComponent) {
        mShowOffTheRecord = showOffTheRecord;
        mParentComponent = parentComponent;
        mLoadingDelegate = new LoadingStateDelegate(mShowOffTheRecord);

        // Using stable IDs allows the RecyclerView to animate changes.
        setHasStableIds(true);
    }

    public void initialize(BackendProvider provider) {
        mBackendProvider = provider;

        // Get all regular and (if necessary) off the record downloads.
        DownloadDelegate downloadManager = getDownloadDelegate();
        downloadManager.addDownloadHistoryAdapter(this);
        downloadManager.getAllDownloads(false);
        if (mShowOffTheRecord) downloadManager.getAllDownloads(true);

        initializeOfflinePageBridge();

        sDeletedFileTracker.incrementInstanceCount();
    }

    /** Called when the user's regular or incognito download history has been loaded. */
    public void onAllDownloadsRetrieved(List<DownloadItem> result, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;

        BackendItems list = getDownloadItemList(isOffTheRecord);
        if (list.isInitialized()) return;
        assert list.size() == 0;

        int[] itemCounts = new int[DownloadFilter.FILTER_BOUNDARY];

        for (DownloadItem item : result) {
            DownloadItemWrapper wrapper = createDownloadItemWrapper(item);
            if (addDownloadHistoryItemWrapper(wrapper)
                    && wrapper.isVisibleToUser(DownloadFilter.FILTER_ALL)) {
                itemCounts[wrapper.getFilterType()]++;
                if (!isOffTheRecord && wrapper.getFilterType() == DownloadFilter.FILTER_OTHER) {
                    RecordHistogram.recordEnumeratedHistogram(
                            "Android.DownloadManager.OtherExtensions.InitialCount",
                            wrapper.getFileExtensionType(),
                            DownloadHistoryItemWrapper.FILE_EXTENSION_BOUNDARY);
                }
            }
        }

        if (!isOffTheRecord) recordDownloadCountHistograms(itemCounts);

        list.setIsInitialized();
        onItemsRetrieved(isOffTheRecord
                ? LoadingStateDelegate.INCOGNITO_DOWNLOADS
                : LoadingStateDelegate.REGULAR_DOWNLOADS);
    }

    /**
     * Checks if a wrapper corresponds to an item that was already deleted.
     * @return True if it does, false otherwise.
     */
    private boolean updateDeletedFileMap(DownloadHistoryItemWrapper wrapper) {
        // TODO(twellington): The native downloads service should remove externally deleted
        //                    downloads rather than passing them to Java.
        if (sDeletedFileTracker.contains(wrapper)) return true;

        if (wrapper.hasBeenExternallyRemoved()) {
            sDeletedFileTracker.add(wrapper);
            wrapper.remove();
            mFilePathsToItemsMap.removeItem(wrapper);
            RecordUserAction.record("Android.DownloadManager.Item.ExternallyDeleted");
            return true;
        }

        return false;
    }

    private boolean addDownloadHistoryItemWrapper(DownloadHistoryItemWrapper wrapper) {
        if (updateDeletedFileMap(wrapper)) return false;

        getListForItem(wrapper).add(wrapper);
        mFilePathsToItemsMap.addItem(wrapper);
        return true;
    }

    /** Called when the user's offline page history has been gathered. */
    private void onAllOfflinePagesRetrieved(List<OfflinePageDownloadItem> result) {
        if (mOfflinePageItems.isInitialized()) return;
        assert mOfflinePageItems.size() == 0;

        for (OfflinePageDownloadItem item : result) {
            addDownloadHistoryItemWrapper(createOfflinePageItemWrapper(item));
        }

        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.OfflinePage",
                result.size());

        mOfflinePageItems.setIsInitialized();
        onItemsRetrieved(LoadingStateDelegate.OFFLINE_PAGES);
    }

    /**
     * Should be called when download items or offline pages have been retrieved.
     */
    private void onItemsRetrieved(int type) {
        if (mLoadingDelegate.updateLoadingState(type)) {
            recordTotalDownloadCountHistogram();
            filter(mLoadingDelegate.getPendingFilter());
        }
    }

    /** Returns the total size of all non-deleted downloaded items. */
    public long getTotalDownloadSize() {
        long totalSize = 0;
        totalSize += mRegularDownloadItems.getTotalBytes();
        totalSize += mIncognitoDownloadItems.getTotalBytes();
        totalSize += mOfflinePageItems.getTotalBytes();
        return totalSize;
    }

    @Override
    protected int getTimedItemViewResId() {
        return R.layout.date_view;
    }

    @Override
    protected SubsectionHeaderViewHolder createSubsectionHeader(ViewGroup parent) {
        OfflineGroupHeaderView offlineHeader =
                (OfflineGroupHeaderView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.offline_download_header, parent, false);
        offlineHeader.setAdapter(this);
        return new SubsectionHeaderViewHolder(offlineHeader);
    }

    @Override
    protected void bindViewHolderForSubsectionHeader(
            SubsectionHeaderViewHolder holder, TimedItem timedItem) {
        SubsectionHeader headerItem = (SubsectionHeader) timedItem;
        Date date = new Date(headerItem.getTimestamp());
        OfflineGroupHeaderView headerView = (OfflineGroupHeaderView) holder.getView();
        headerView.update(date, isSubsectionExpanded(date), headerItem.getItemCount(),
                headerItem.getTotalFileSize());
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        DownloadItemView v = (DownloadItemView) LayoutInflater.from(parent.getContext()).inflate(
                R.layout.download_item_view, parent, false);
        v.setSelectionDelegate(getSelectionDelegate());
        mViews.add(v);
        return new DownloadHistoryItemViewHolder(v);
    }

    @Override
    public void bindViewHolderForTimedItem(ViewHolder current, TimedItem timedItem) {
        final DownloadHistoryItemWrapper item = (DownloadHistoryItemWrapper) timedItem;

        DownloadHistoryItemViewHolder holder = (DownloadHistoryItemViewHolder) current;
        holder.getItemView().displayItem(mBackendProvider, item);
    }

    @Override
    protected ItemGroup createGroup(long timeStamp) {
        return new DownloadItemGroup(timeStamp);
    }

    /** Called when a new DownloadItem has been created by the native DownloadManager. */
    public void onDownloadItemCreated(DownloadItem item) {
        boolean isOffTheRecord = item.getDownloadInfo().isOffTheRecord();
        if (isOffTheRecord && !mShowOffTheRecord) return;

        BackendItems list = getDownloadItemList(isOffTheRecord);
        assert list.findItemIndex(item.getId()) == BackendItems.INVALID_INDEX;

        DownloadItemWrapper wrapper = createDownloadItemWrapper(item);
        boolean wasAdded = addDownloadHistoryItemWrapper(wrapper);
        if (wasAdded && wrapper.isVisibleToUser(mFilter)) filter(mFilter);

        for (TestObserver observer : mObservers) observer.onDownloadItemCreated(item);
    }

    /** Updates the list when new information about a download comes in. */
    public void onDownloadItemUpdated(DownloadItem item) {
        DownloadItemWrapper newWrapper = createDownloadItemWrapper(item);
        if (newWrapper.isOffTheRecord() && !mShowOffTheRecord) return;

        // Check if the item has already been deleted.
        if (updateDeletedFileMap(newWrapper)) return;

        BackendItems list = getListForItem(newWrapper);
        int index = list.findItemIndex(item.getId());
        if (index == BackendItems.INVALID_INDEX) {
            assert false : "Tried to update DownloadItem that didn't exist.";
            return;
        }

        // Update the old one.
        DownloadHistoryItemWrapper existingWrapper = list.get(index);
        boolean isUpdated = existingWrapper.replaceItem(item);

        // Re-add the file mapping once it finishes downloading. This accounts for the backend
        // creating DownloadItems with a null file path, then updating it after the download starts.
        // Doing it once after completion instead of at every update is a compromise that prevents
        // us from rapidly and repeatedly updating the map with the same info.
        if (item.getDownloadInfo().state() == DownloadState.COMPLETE) {
            mFilePathsToItemsMap.addItem(existingWrapper);
        }

        if (item.getDownloadInfo().state() == DownloadState.CANCELLED) {
            // The old one is being removed.
            filter(mFilter);
        } else if (existingWrapper.isVisibleToUser(mFilter)) {
            if (existingWrapper.getPosition() == TimedItem.INVALID_POSITION) {
                filter(mFilter);
                for (TestObserver observer : mObservers) observer.onDownloadItemUpdated(item);
            } else if (isUpdated) {
                // Directly alert DownloadItemViews displaying information about the item that it
                // has changed instead of notifying the RecyclerView that a particular item has
                // changed.  This prevents the RecyclerView from detaching and immediately
                // reattaching the same view, causing janky animations.
                for (DownloadItemView view : mViews) {
                    if (TextUtils.equals(item.getId(), view.getItem().getId())) {
                        view.displayItem(mBackendProvider, existingWrapper);
                    }
                }

                for (TestObserver observer : mObservers) observer.onDownloadItemUpdated(item);
            }
        }
    }

    /**
     * Removes the DownloadItem with the given ID.
     * @param guid           ID of the DownloadItem that has been removed.
     * @param isOffTheRecord True if off the record, false otherwise.
     */
    public void onDownloadItemRemoved(String guid, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;
        if (getDownloadItemList(isOffTheRecord).removeItem(guid) != null) {
            filter(mFilter);
        }
    }

    @Override
    public void onFilterChanged(int filter) {
        if (mLoadingDelegate.isLoaded()) {
            filter(filter);
        } else {
            // Wait until all the backends are fully loaded before trying to show anything.
            mLoadingDelegate.setPendingFilter(filter);
        }
    }

    @Override
    public void onManagerDestroyed() {
        getDownloadDelegate().removeDownloadHistoryAdapter(this);
        getOfflinePageBridge().removeObserver(mOfflinePageObserver);
        sDeletedFileTracker.decrementInstanceCount();
    }

    @Override
    public void onAddOrReplaceDownloadSharedPreferenceEntry(final String guid) {
        // Alert DownloadItemViews displaying information about the item that it has changed.
        for (DownloadItemView view : mViews) {
            if (TextUtils.equals(guid, view.getItem().getId())) {
                view.displayItem(mBackendProvider, view.getItem());
            }
        }
    }

    /** Marks that certain items are about to be deleted. */
    void markItemsForDeletion(List<DownloadHistoryItemWrapper> items) {
        for (DownloadHistoryItemWrapper item : items) item.setIsDeletionPending(true);
        filter(mFilter);
    }

    /** Marks that items that were about to be deleted are not being deleted anymore. */
    void unmarkItemsForDeletion(List<DownloadHistoryItemWrapper> items) {
        for (DownloadHistoryItemWrapper item : items) item.setIsDeletionPending(false);
        filter(mFilter);
    }

    /**
     * Gets all DownloadHistoryItemWrappers that point to the same path in the user's storage.
     * @param filePath The file path used to retrieve items.
     * @return DownloadHistoryItemWrappers associated with filePath.
     */
    Set<DownloadHistoryItemWrapper> getItemsForFilePath(String filePath) {
        return mFilePathsToItemsMap.getItemsForFilePath(filePath);
    }

    /** Registers a {@link TestObserver} to monitor internal changes. */
    void registerObserverForTest(TestObserver observer) {
        mObservers.addObserver(observer);
    }

    /** Unregisters a {@link TestObserver} that was monitoring internal changes. */
    void unregisterObserverForTest(TestObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Called to perform a search. If the query is empty all items matching the current filter will
     * be displayed.
     * @param query The text to search for.
     */
    void search(String query) {
        mSearchQuery = query;
        filter(mFilter);
    }

    /**
     * Called when a search is ended.
     */
    void onEndSearch() {
        mSearchQuery = EMPTY_QUERY;
        filter(mFilter);
    }

    private DownloadDelegate getDownloadDelegate() {
        return mBackendProvider.getDownloadDelegate();
    }

    private OfflinePageDelegate getOfflinePageBridge() {
        return mBackendProvider.getOfflinePageBridge();
    }

    private SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate() {
        return mBackendProvider.getSelectionDelegate();
    }

    /** Filters the list of downloads to show only files of a specific type. */
    private void filter(int filterType) {
        mFilter = filterType;

        List<TimedItem> filteredTimedItems = new ArrayList<>();
        mRegularDownloadItems.filter(mFilter, mSearchQuery, filteredTimedItems);
        mIncognitoDownloadItems.filter(mFilter, mSearchQuery, filteredTimedItems);
        filterOfflinePageItems(filteredTimedItems);

        clear(false);
        loadItems(filteredTimedItems);
    }

    /**
     * Filters the offline pages based on the current filter and search text.
     * If there are suggested pages, they are filtered based on whether or not the subsection for
     * that date is expanded. Also a TimedItem is added to each subsection to represent the header
     * for the suggested pages.
     * @param filteredTimedItems List for appending items that match the filter.
     */
    private void filterOfflinePageItems(List<TimedItem> filteredTimedItems) {
        Map<Date, Integer> suggestedPageCountMap = new HashMap<>();
        Map<Date, Long> suggestedPageTotalSizeMap = new HashMap<>();

        List<TimedItem> filteredOfflinePageItems = new ArrayList<>();
        mOfflinePageItems.filter(mFilter, mSearchQuery, filteredOfflinePageItems);

        for (TimedItem item : filteredOfflinePageItems) {
            OfflinePageItemWrapper offlineItem = (OfflinePageItemWrapper) item;

            // Add the suggested pages to the adapter only if the section is expanded for that date.
            if (offlineItem.isSuggested()) {
                incrementSuggestedPageCount(
                        offlineItem, suggestedPageCountMap, suggestedPageTotalSizeMap);
                // TODO(shaktisahu): Check with UX if we need to skip this check and the subsection
                // headers when filtering for active search text.
                if (!isSubsectionExpanded(getDateWithoutTime(offlineItem.getTimestamp()))) continue;
            }
            filteredTimedItems.add(offlineItem);
        }

        generateSubsectionHeaders(
                filteredTimedItems, suggestedPageCountMap, suggestedPageTotalSizeMap);
    }

    // Updates the total number of suggested pages and file size grouped by date.
    private void incrementSuggestedPageCount(OfflinePageItemWrapper offlineItem,
            Map<Date, Integer> pageCountMap, Map<Date, Long> fileSizeMap) {
        Date date = getDateWithoutTime(offlineItem.getTimestamp());

        int count = pageCountMap.containsKey(date) ? pageCountMap.get(date) : 0;
        pageCountMap.put(date, count + 1);

        long fileSize = fileSizeMap.containsKey(date) ? fileSizeMap.get(date) : 0;
        fileSizeMap.put(date, fileSize + offlineItem.getFileSize());
    }

    // Creates subsection headers for each date and appends to |filteredTimedItems|.
    private void generateSubsectionHeaders(List<TimedItem> filteredTimedItems,
            Map<Date, Integer> pageCountMap, Map<Date, Long> fileSizeMap) {
        for (Map.Entry<Date, Integer> entry : pageCountMap.entrySet()) {
            Date date = entry.getKey();
            filteredTimedItems.add(
                    new SubsectionHeader(date, pageCountMap.get(date), fileSizeMap.get(date)));
        }

        // Remove entry from |mSubsectionExpanded| if there are no more suggested pages.
        Iterator<Entry<Date, Boolean>> iter = mSubsectionExpanded.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Date, Boolean> entry = iter.next();
            if (!pageCountMap.containsKey(entry.getKey())) {
                iter.remove();
            }
        }
    }

    /**
     * Whether the suggested pages section is expanded for a given date.
     * @param date The download date.
     * @return Whether the suggested pages section is expanded.
     */
    public boolean isSubsectionExpanded(Date date) {
        // Default state is collpased.
        if (mSubsectionExpanded.get(date) == null) {
            mSubsectionExpanded.put(date, false);
        }

        return mSubsectionExpanded.get(date);
    }

    /**
     * Sets the state of a subsection for a particular date and updates the adapter.
     * @param date The download date.
     * @param expanded Whether the suggested pages should be expanded.
     */
    public void setSubsectionExpanded(Date date, boolean expanded) {
        mSubsectionExpanded.put(date, expanded);
        clear(false);
        filter(mFilter);
    }

    @Override
    protected boolean isSubsectionHeader(TimedItem timedItem) {
        return timedItem instanceof SubsectionHeader;
    }

    private void initializeOfflinePageBridge() {
        mOfflinePageObserver = new OfflinePageDownloadBridge.Observer() {
            @Override
            public void onItemsLoaded() {
                onAllOfflinePagesRetrieved(getOfflinePageBridge().getAllItems());
            }

            @Override
            public void onItemAdded(OfflinePageDownloadItem item) {
                addDownloadHistoryItemWrapper(createOfflinePageItemWrapper(item));
                updateDisplayedItems();
            }

            @Override
            public void onItemDeleted(String guid) {
                if (mOfflinePageItems.removeItem(guid) != null) updateDisplayedItems();
            }

            @Override
            public void onItemUpdated(OfflinePageDownloadItem item) {
                int index = mOfflinePageItems.findItemIndex(item.getGuid());
                assert index != BackendItems.INVALID_INDEX;

                DownloadHistoryItemWrapper existingWrapper = mOfflinePageItems.get(index);
                existingWrapper.replaceItem(item);
                // Re-add the file mapping once it finishes downloading. This accounts for the
                // backend creating Offline Pages with a null file path, then updating it after the
                // download starts. Doing it once after completion instead of at every update
                // is a compromise that prevents us from rapidly and repeatedly updating the map
                // with the same info is progress is reported.
                if (item.getDownloadState()
                        == org.chromium.components.offlinepages.downloads.DownloadState.COMPLETE) {
                    mFilePathsToItemsMap.addItem(existingWrapper);
                }

                updateDisplayedItems();
            }

            /** Re-filter the items if needed. */
            private void updateDisplayedItems() {
                if (mFilter == DownloadFilter.FILTER_ALL || mFilter == DownloadFilter.FILTER_PAGE) {
                    filter(mFilter);
                }
            }
        };
        getOfflinePageBridge().addObserver(mOfflinePageObserver);
    }

    private BackendItems getDownloadItemList(boolean isOffTheRecord) {
        return isOffTheRecord ? mIncognitoDownloadItems : mRegularDownloadItems;
    }

    private BackendItems getListForItem(DownloadHistoryItemWrapper wrapper) {
        if (wrapper instanceof DownloadItemWrapper) {
            return getDownloadItemList(wrapper.isOffTheRecord());
        } else {
            return mOfflinePageItems;
        }
    }

    private DownloadItemWrapper createDownloadItemWrapper(DownloadItem item) {
        return new DownloadItemWrapper(item, mBackendProvider, mParentComponent);
    }

    private OfflinePageItemWrapper createOfflinePageItemWrapper(OfflinePageDownloadItem item) {
        return new OfflinePageItemWrapper(item, mBackendProvider, mParentComponent);
    }

    private void recordDownloadCountHistograms(int[] itemCounts) {
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Audio",
                itemCounts[DownloadFilter.FILTER_AUDIO]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Document",
                itemCounts[DownloadFilter.FILTER_DOCUMENT]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Image",
                itemCounts[DownloadFilter.FILTER_IMAGE]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Other",
                itemCounts[DownloadFilter.FILTER_OTHER]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Video",
                itemCounts[DownloadFilter.FILTER_VIDEO]);
    }

    private void recordTotalDownloadCountHistogram() {
        // The total count intentionally leaves out incognito downloads. This should be revisited
        // if/when incognito downloads are persistently available in downloads home.
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Total",
                mRegularDownloadItems.size() + mOfflinePageItems.size());
    }

    /**
     * Calculates the {@link Date} for midnight of the date represented by the timestamp.
     */
    private Date getDateWithoutTime(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
