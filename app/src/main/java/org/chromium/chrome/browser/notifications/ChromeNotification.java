// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;

/**
 * A wrapper class of {@link Notification}, which also contains the notification id and tag, etc.
 */
public class ChromeNotification {
    private final Notification mNotification;
    private final NotificationMetadata mNotificationMetadata;

    ChromeNotification(Notification notification, NotificationMetadata metadata) {
        assert notification != null;
        assert metadata != null;
        mNotification = notification;
        mNotificationMetadata = metadata;
    }

    /**
     * Returns the {@link Notification}.
     */
    public Notification getNotification() {
        return mNotification;
    }

    /**
     * Gets the notification metadata.
     * @See {@link NotificationMetadata}.
     */
    public NotificationMetadata getMetadata() {
        return mNotificationMetadata;
    }
}
