// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.trustedwebactivityui;

import android.content.Intent;

import org.chromium.chrome.browser.browserservices.Origin;
import org.chromium.chrome.browser.dependency_injection.ActivityScope;
import org.chromium.ui.modelutil.PropertyModel;

import javax.inject.Inject;

/**
 * Model describing the state of a Trusted Web Activity.
 */
@ActivityScope
public class TrustedWebActivityModel extends PropertyModel {

    /** Whether toolbar should be hidden. */
    public static final WritableBooleanPropertyKey TOOLBAR_HIDDEN =
            new WritableBooleanPropertyKey();

    /** The state of Trusted Web Activity disclosure. Can be one of the constants below. */
    public static final WritableIntPropertyKey DISCLOSURE_STATE =
            new WritableIntPropertyKey();

    public static final int DISCLOSURE_STATE_NOT_SHOWN = 0;
    public static final int DISCLOSURE_STATE_SHOWN = 1;
    public static final int DISCLOSURE_STATE_DISMISSED_BY_USER = 2;

    /** Tag to use for showing and dismissing a persistent notification. */
    public static final WritableObjectPropertyKey<String>
            PERSISTENT_NOTIFICATION_TAG = new WritableObjectPropertyKey<>();

    /**
     * Data for building a persistent notification when it needs to be shown.
     * Null when it needs to be hidden.
     */
    public static final WritableObjectPropertyKey<PersistentNotificationData>
            PERSISTENT_NOTIFICATION = new WritableObjectPropertyKey<>();

    /** Callback for routing disclosure-related view events back to controller side. */
    public static final WritableObjectPropertyKey<DisclosureEventsCallback>
            DISCLOSURE_EVENTS_CALLBACK = new WritableObjectPropertyKey<>();


    public static class PersistentNotificationData {
        // Necessary for making a PendingIntent for sharing.
        public final Intent customTabActivityIntent;
        public final Origin origin;

        public PersistentNotificationData(Intent customTabActivityIntent, Origin origin) {
            this.customTabActivityIntent = customTabActivityIntent;
            this.origin = origin;
        }
    }

    public interface DisclosureEventsCallback {
        /** Called when user accepted the disclosure. */
        void onDisclosureAccepted();
    }

    @Inject
    public TrustedWebActivityModel() {
        super(TOOLBAR_HIDDEN, DISCLOSURE_STATE, PERSISTENT_NOTIFICATION,
                PERSISTENT_NOTIFICATION_TAG, DISCLOSURE_EVENTS_CALLBACK);
    }
}
