// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.os.SystemClock;
import android.util.LruCache;
import android.webkit.URLUtil;

import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.blink.mojom.document_metadata.CopylessPaste;
import org.chromium.blink.mojom.document_metadata.WebPage;
import org.chromium.chrome.browser.historyreport.AppIndexingReporter;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.content_public.browser.WebContents;
import org.chromium.services.service_manager.InterfaceProvider;

/**
 * This is the top-level CopylessPaste metadata extraction for AppIndexing.
 */
public class AppIndexingUtil {
    private static final int CACHE_SIZE = 100;
    private static final int CACHE_VISIT_CUTOFF_MS = 60 * 60 * 1000; // 1 hour
    // Cache of recently seen urls. If a url is among the CACHE_SIZE most recent pages visited, and
    // the parse was in the last CACHE_VISIT_CUTOFF_MS milliseconds, then we don't parse the page,
    // and instead just report the view (not the content) to App Indexing.
    private LruCache<String, CacheEntry> mPageCache;

    /**
     * Extracts entities from document metadata and reports it to on-device App Indexing.
     * This call can cache entities from recently parsed webpages, in which case, only the url and
     * title of the page is reported to App Indexing.
     */
    public void extractCopylessPasteMetadata(final Tab tab) {
        final String url = tab.getUrl();
        boolean isHttpOrHttps = URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url);
        if (!isEnabledForDevice() || tab.isIncognito() || !isHttpOrHttps) {
            return;
        }

        // There are three conditions that can occur with respect to the cache.
        // 1. Cache hit, and an entity was found previously. Report only the page view to App
        //    Indexing.
        // 2. Cache hit, but no entity was found. Ignore.
        // 3. Cache miss, we need to parse the page.
        if (wasPageVisitedRecently(url)) {
            if (lastPageVisitContainedEntity(url)) {
                // Condition 1
                getAppIndexingReporter().reportWebPageView(url, tab.getTitle());
            }
            // Condition 2
        } else {
            // Condition 3
            CopylessPaste copylessPaste = getCopylessPasteInterface(tab);
            if (copylessPaste == null) {
                return;
            }
            copylessPaste.getEntities(new CopylessPaste.GetEntitiesResponse() {
                @Override
                public void call(WebPage webpage) {
                    putCacheEntry(url, webpage != null);
                    if (webpage == null) return;
                    getAppIndexingReporter().reportWebPage(webpage);
                }
            });
        }
    }

    private boolean wasPageVisitedRecently(String url) {
        if (url == null) {
            return false;
        }
        CacheEntry entry = getPageCache().get(url);
        if (entry == null || (getElapsedTime() - entry.lastSeenTimeMs > CACHE_VISIT_CUTOFF_MS)) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the page is in the cache and it contained an entity the last time it was
     * parsed.
     */
    private boolean lastPageVisitContainedEntity(String url) {
        if (url == null) {
            return false;
        }
        CacheEntry entry = getPageCache().get(url);
        if (entry == null || !entry.containedEntity) {
            return false;
        }
        return true;
    }

    private void putCacheEntry(String url, boolean containedEntity) {
        CacheEntry entry = new CacheEntry();
        entry.lastSeenTimeMs = getElapsedTime();
        entry.containedEntity = containedEntity;
        getPageCache().put(url, entry);
    }

    @VisibleForTesting
    AppIndexingReporter getAppIndexingReporter() {
        return AppIndexingReporter.getInstance();
    }

    @VisibleForTesting
    CopylessPaste getCopylessPasteInterface(Tab tab) {
        WebContents webContents = tab.getWebContents();
        if (webContents == null) return null;

        RenderFrameHost mainFrame = webContents.getMainFrame();
        if (mainFrame == null) return null;

        InterfaceProvider interfaces = mainFrame.getRemoteInterfaces();
        if (interfaces == null) return null;

        return interfaces.getInterface(CopylessPaste.MANAGER);
    }

    @VisibleForTesting
    long getElapsedTime() {
        return SystemClock.elapsedRealtime();
    }

    boolean isEnabledForDevice() {
        return !SysUtils.isLowEndDevice()
                && ChromeFeatureList.isEnabled(ChromeFeatureList.COPYLESS_PASTE);
    }

    private LruCache<String, CacheEntry> getPageCache() {
        if (mPageCache == null) {
            mPageCache = new LruCache<String, CacheEntry>(CACHE_SIZE);
        }
        return mPageCache;
    }

    private static class CacheEntry {
        public long lastSeenTimeMs;
        public boolean containedEntity;
    }
}
