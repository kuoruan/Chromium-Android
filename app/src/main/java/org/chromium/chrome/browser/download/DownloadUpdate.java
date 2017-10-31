// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.graphics.Bitmap;

import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.OfflineItem.Progress;

/**
 * Class representing information relating to an update in download status.
 * TODO(jming): Consolidate with other downloads-related objects (http://crbug.com/746692).
 */
public final class DownloadUpdate {
    private final ContentId mContentId;
    private final String mFileName;
    private final String mFilePath;
    private final Bitmap mIcon;
    private final int mIconId;
    private final boolean mIsDownloadPending;
    private final boolean mIsOffTheRecord;
    private final boolean mIsOpenable;
    private final boolean mIsSupportedMimeType;
    private final boolean mIsTransient;
    private final int mNotificationId;
    private final String mOriginalUrl;
    private final Progress mProgress;
    private final String mReferrer;
    private final long mStartTime;
    private final long mSystemDownloadId;
    private final long mTimeRemainingInMillis;

    private DownloadUpdate(Builder builder) {
        this.mContentId = builder.mContentId;
        this.mFileName = builder.mFileName;
        this.mFilePath = builder.mFilePath;
        this.mIcon = builder.mIcon;
        this.mIconId = builder.mIconId;
        this.mIsDownloadPending = builder.mIsDownloadPending;
        this.mIsOffTheRecord = builder.mIsOffTheRecord;
        this.mIsOpenable = builder.mIsOpenable;
        this.mIsSupportedMimeType = builder.mIsSupportedMimeType;
        this.mIsTransient = builder.mIsTransient;
        this.mNotificationId = builder.mNotificationId;
        this.mOriginalUrl = builder.mOriginalUrl;
        this.mProgress = builder.mProgress;
        this.mReferrer = builder.mReferrer;
        this.mStartTime = builder.mStartTime;
        this.mSystemDownloadId = builder.mSystemDownloadId;
        this.mTimeRemainingInMillis = builder.mTimeRemainingInMillis;
    }

    public ContentId getContentId() {
        return mContentId;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public int getIconId() {
        return mIconId;
    }

    public boolean getIsDownloadPending() {
        return mIsDownloadPending;
    }

    public boolean getIsOffTheRecord() {
        return mIsOffTheRecord;
    }

    public boolean getIsOpenable() {
        return mIsOpenable;
    }

    public boolean getIsSupportedMimeType() {
        return mIsSupportedMimeType;
    }

    public boolean getIsTransient() {
        return mIsTransient;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    public Progress getProgress() {
        return mProgress;
    }

    public String getReferrer() {
        return mReferrer;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public long getSystemDownloadId() {
        return mSystemDownloadId;
    }

    public long getTimeRemainingInMillis() {
        return mTimeRemainingInMillis;
    }

    /**
     * Helper class for building the DownloadUpdate object.
     */
    public static class Builder {
        private ContentId mContentId;
        private String mFileName;
        private String mFilePath;
        private Bitmap mIcon;
        private int mIconId = -1;
        private boolean mIsDownloadPending;
        private boolean mIsOffTheRecord;
        private boolean mIsOpenable;
        private boolean mIsSupportedMimeType;
        private boolean mIsTransient;
        private int mNotificationId = -1;
        private String mOriginalUrl;
        private Progress mProgress;
        private String mReferrer;
        private long mStartTime;
        private long mSystemDownloadId = -1;
        private long mTimeRemainingInMillis;

        public Builder setContentId(ContentId contentId) {
            this.mContentId = contentId;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.mFileName = fileName;
            return this;
        }

        public Builder setFilePath(String filePath) {
            this.mFilePath = filePath;
            return this;
        }

        public Builder setIcon(Bitmap icon) {
            this.mIcon = icon;
            return this;
        }

        public Builder setIconId(int iconId) {
            this.mIconId = iconId;
            return this;
        }

        public Builder setIsDownloadPending(boolean isDownloadPending) {
            this.mIsDownloadPending = isDownloadPending;
            return this;
        }

        public Builder setIsOffTheRecord(boolean isOffTheRecord) {
            this.mIsOffTheRecord = isOffTheRecord;
            return this;
        }

        public Builder setIsOpenable(boolean isOpenable) {
            this.mIsOpenable = isOpenable;
            return this;
        }

        public Builder setIsSupportedMimeType(boolean isSupportedMimeType) {
            this.mIsSupportedMimeType = isSupportedMimeType;
            return this;
        }

        public Builder setIsTransient(boolean isTransient) {
            this.mIsTransient = isTransient;
            return this;
        }

        public Builder setNotificationId(int notificationId) {
            this.mNotificationId = notificationId;
            return this;
        }

        public Builder setOriginalUrl(String originalUrl) {
            this.mOriginalUrl = originalUrl;
            return this;
        }

        public Builder setProgress(Progress progress) {
            this.mProgress = progress;
            return this;
        }

        public Builder setReferrer(String referrer) {
            this.mReferrer = referrer;
            return this;
        }

        public Builder setStartTime(long startTime) {
            this.mStartTime = startTime;
            return this;
        }

        public Builder setSystemDownload(long systemDownload) {
            this.mSystemDownloadId = systemDownload;
            return this;
        }

        public Builder setTimeRemainingInMillis(long timeRemainingInMillis) {
            this.mTimeRemainingInMillis = timeRemainingInMillis;
            return this;
        }

        public DownloadUpdate build() {
            return new DownloadUpdate(this);
        }
    }
}
