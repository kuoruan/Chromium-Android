// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.searchwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.LoadListener;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;

/**
 * Widget that lets the user search using their default search engine.
 *
 * Because this is a BroadcastReceiver, it dies immediately after it runs.  A new one is created
 * for each new broadcast.
 */
public class SearchWidgetProvider extends AppWidgetProvider {
    /** Monitors the TemplateUrlService for changes, updating the widget when necessary. */
    private static final class SearchWidgetTemplateUrlServiceObserver
            implements LoadListener, TemplateUrlServiceObserver {
        @Override
        public void onTemplateUrlServiceLoaded() {
            TemplateUrlService.getInstance().unregisterLoadListener(this);
            updateCachedEngineName();
        }

        @Override
        public void onTemplateURLServiceChanged() {
            updateCachedEngineName();
        }
    }

    private static final String ACTION_START_TEXT_QUERY =
            "org.chromium.chrome.browser.searchwidget.START_TEXT_QUERY";
    private static final String ACTION_START_VOICE_QUERY =
            "org.chromium.chrome.browser.searchwidget.START_VOICE_QUERY";
    private static final String ACTION_UPDATE_ALL_WIDGETS =
            "org.chromium.chrome.browser.searchwidget.UPDATE_ALL_WIDGETS";

    static final String EXTRA_START_VOICE_SEARCH =
            "org.chromium.chrome.browser.searchwidget.START_VOICE_SEARCH";

    private static final String PREF_IS_VOICE_SEARCH_AVAILABLE =
            "org.chromium.chrome.browser.searchwidget.IS_VOICE_SEARCH_AVAILABLE";
    private static final String PREF_SEARCH_ENGINE_SHORTNAME =
            "org.chromium.chrome.browser.searchwidget.SEARCH_ENGINE_SHORTNAME";

    private static final String TAG = "searchwidget";
    private static final Object OBSERVER_LOCK = new Object();

    private static SearchWidgetTemplateUrlServiceObserver sObserver;

    /**
     * Creates the singleton instance of the observer that will monitor for search engine changes.
     * The native library and the browser process must have been fully loaded before calling this.
     */
    public static void initialize() {
        ThreadUtils.assertOnUiThread();
        assert LibraryLoader.isInitialized();

        // Set up an observer to monitor for changes.
        synchronized (OBSERVER_LOCK) {
            if (sObserver != null) return;
            sObserver = new SearchWidgetTemplateUrlServiceObserver();

            TemplateUrlService service = TemplateUrlService.getInstance();
            service.registerLoadListener(sObserver);
            service.addObserver(sObserver);
            if (!service.isLoaded()) service.load();
        }
    }

    /** Nukes all cached information and forces all widgets to start with a blank slate. */
    public static void reset() {
        SharedPreferences.Editor editor = ContextUtils.getAppSharedPreferences().edit();
        editor.remove(PREF_IS_VOICE_SEARCH_AVAILABLE);
        editor.remove(PREF_SEARCH_ENGINE_SHORTNAME);
        editor.apply();
        updateAllWidgets();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (IntentHandler.isIntentChromeOrFirstParty(intent)) {
            if (ACTION_START_TEXT_QUERY.equals(intent.getAction())) {
                startSearchActivity(context, false);
            } else if (ACTION_START_VOICE_QUERY.equals(intent.getAction())) {
                startSearchActivity(context, true);
            } else if (ACTION_UPDATE_ALL_WIDGETS.equals(intent.getAction())) {
                performUpdate(context);
            }
            return;
        }
        super.onReceive(context, intent);
    }

    private void startSearchActivity(Context context, boolean startVoiceSearch) {
        Log.d(TAG, "Launching SearchActivity: VOICE=" + startVoiceSearch);

        // Launch the SearchActivity.
        Intent searchIntent = new Intent();
        searchIntent.setClass(context, SearchActivity.class);
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        searchIntent.putExtra(EXTRA_START_VOICE_SEARCH, startVoiceSearch);

        Bundle optionsBundle =
                ActivityOptionsCompat.makeCustomAnimation(context, R.anim.activity_open_enter, 0)
                        .toBundle();
        context.startActivity(searchIntent, optionsBundle);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        performUpdate(context, ids);
    }

    private static void performUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        performUpdate(context, getAllSearchWidgetIds(manager));
    }

    private static void performUpdate(Context context, int[] ids) {
        if (ids.length == 0) return;

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        boolean isVoiceSearchAvailable = prefs.getBoolean(PREF_IS_VOICE_SEARCH_AVAILABLE, true);
        String engineName = prefs.getString(PREF_SEARCH_ENGINE_SHORTNAME, null);

        for (int id : ids) {
            RemoteViews views = createWidgetViews(context, id, engineName, isVoiceSearchAvailable);
            manager.updateAppWidget(id, views);
        }
    }

    private static RemoteViews createWidgetViews(
            Context context, int id, String engineName, boolean isVoiceSearchAvailable) {
        RemoteViews views =
                new RemoteViews(context.getPackageName(), R.layout.search_widget_template);

        // Clicking on the widget fires an Intent back at this BroadcastReceiver, allowing control
        // over how the Activity is animated when it starts up.
        Intent textIntent = createStartQueryIntent(context, ACTION_START_TEXT_QUERY, id);
        views.setOnClickPendingIntent(R.id.text_container,
                PendingIntent.getBroadcast(
                        context, 0, textIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // If voice search is available, clicking on the microphone triggers a voice query.
        if (isVoiceSearchAvailable) {
            Intent voiceIntent = createStartQueryIntent(context, ACTION_START_VOICE_QUERY, id);
            views.setOnClickPendingIntent(R.id.microphone_icon,
                    PendingIntent.getBroadcast(
                            context, 0, voiceIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            views.setViewVisibility(R.id.microphone_icon, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.microphone_icon, View.GONE);
        }

        // Update what string is displayed by the widget.
        String text = TextUtils.isEmpty(engineName)
                ? context.getString(R.string.search_widget_default)
                : context.getString(R.string.search_with_product, engineName);
        views.setTextViewText(R.id.title, text);

        return views;
    }

    /** Caches whether or not a voice search is possible. */
    static void updateCachedVoiceSearchAvailability(boolean isVoiceSearchAvailable) {
        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        if (prefs.getBoolean(PREF_IS_VOICE_SEARCH_AVAILABLE, true) != isVoiceSearchAvailable) {
            prefs.edit().putBoolean(PREF_IS_VOICE_SEARCH_AVAILABLE, isVoiceSearchAvailable).apply();
            updateAllWidgets();
        }
    }

    /**
     * Updates the name of the user's default search engine that is cached in SharedPreferences.
     * Caching it in SharedPreferences prevents us from having to load the native library and the
     * TemplateUrlService whenever the widget is updated.
     */
    private static void updateCachedEngineName() {
        assert LibraryLoader.isInitialized();

        // Getting an instance of the TemplateUrlService requires that the native library be
        // loaded, but the TemplateUrlService itself needs to be initialized.
        TemplateUrlService service = TemplateUrlService.getInstance();
        assert service.isLoaded();
        String engineName = service.getDefaultSearchEngineTemplateUrl().getShortName();

        SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        if (!TextUtils.equals(prefs.getString(PREF_SEARCH_ENGINE_SHORTNAME, null), engineName)) {
            prefs.edit().putString(PREF_SEARCH_ENGINE_SHORTNAME, engineName).apply();
            updateAllWidgets();
        }
    }

    /** Get the IDs of all existing search widgets. */
    private static int[] getAllSearchWidgetIds(AppWidgetManager manager) {
        return manager.getAppWidgetIds(new ComponentName(
                ContextUtils.getApplicationContext(), SearchWidgetProvider.class.getName()));
    }

    /** Creates a trusted Intent that lets the user begin performing queries. */
    private static Intent createStartQueryIntent(Context context, String action, int widgetId) {
        Intent intent = new Intent(action, Uri.parse(String.valueOf(widgetId)));
        intent.setClass(context, SearchWidgetProvider.class);
        IntentHandler.addTrustedIntentExtras(intent);
        return intent;
    }

    /** Immediately updates all widgets. */
    private static void updateAllWidgets() {
        performUpdate(ContextUtils.getApplicationContext());
    }
}
