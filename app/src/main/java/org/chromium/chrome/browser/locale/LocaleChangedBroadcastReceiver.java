// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.task.AsyncTask;
import org.chromium.chrome.browser.notifications.channels.ChannelsUpdater;

/**
 * Triggered when Android's locale changes.
 */
public class LocaleChangedBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) return;
        updateChannels();
    }

    /**
     * Updates notification channels to reflect the new locale.
     */
    private void updateChannels() {
        final PendingResult result = goAsync();
        new AsyncTask<Void>() {
            @Override
            protected Void doInBackground() {
                ChannelsUpdater.getInstance().updateLocale();
                result.finish();
                return null;
            }
        }
                .executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }
}
