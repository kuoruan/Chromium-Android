// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.usage_stats;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory store of {@link org.chromium.chrome.browser.usage_stats.WebsiteEvent} objects.
 * Allows for addition of events and querying for all events in a time interval.
 */
public class EventTracker {
    private final List<WebsiteEvent> mWebsiteList;

    public EventTracker() {
        mWebsiteList = new ArrayList<>();
    }

    /** Query all events in the half-open range [start, end) */
    public List<WebsiteEvent> queryWebsiteEvents(long start, long end) {
        List<WebsiteEvent> sublist = sublistFromTimeRange(start, end);
        List<WebsiteEvent> sublistCopy = new ArrayList<>(sublist.size());
        sublistCopy.addAll(sublist);
        return sublistCopy;
    }

    /**
     * Adds an event to the end of the list of events. Adding an event whose timestamp precedes the
     * last event in the list is illegal.
     */
    public void addWebsiteEvent(WebsiteEvent event) {
        if (mWebsiteList.size() > 0) {
            assert event.getTimestamp() >= mWebsiteList.get(mWebsiteList.size() - 1).getTimestamp();
        }

        mWebsiteList.add(event);
    }

    private List<WebsiteEvent> sublistFromTimeRange(long start, long end) {
        return mWebsiteList.subList(indexOf(start), indexOf(end));
    }

    private int indexOf(long time) {
        for (int i = 0; i < mWebsiteList.size(); i++) {
            boolean nextElementGreater =
                    (i + 1 < mWebsiteList.size()) && mWebsiteList.get(i + 1).getTimestamp() > time;
            if (mWebsiteList.get(i).getTimestamp() == time || nextElementGreater) {
                return i;
            }
        }

        return mWebsiteList.size();
    }
}