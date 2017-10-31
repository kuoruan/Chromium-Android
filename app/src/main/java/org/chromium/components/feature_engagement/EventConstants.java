// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement;

/**
 * EventConstants contains the String name of all in-product help events.
 */
public final class EventConstants {
    /**
     * The page load has failed and user has landed on an offline dino page.
     */
    public static final String USER_HAS_SEEN_DINO = "user_has_seen_dino";

    /**
     * The user has started downloading a page.
     */
    public static final String DOWNLOAD_PAGE_STARTED = "download_page_started";

    /**
     * The download has completed successfully.
     */
    public static final String DOWNLOAD_COMPLETED = "download_completed";

    /**
     * The download home was opened by the user (from toolbar menu or notifications).
     */
    public static final String DOWNLOAD_HOME_OPENED = "download_home_opened";

    /**
     * The bottom sheet was expanded by the user.
     */
    public static final String BOTTOM_SHEET_EXPANDED = "bottom_sheet_expanded";

    /**
     * The user clicked the Chrome Home menu header.
     */
    public static final String CHROME_HOME_MENU_HEADER_CLICKED = "chrome_home_menu_header_clicked";

    /**
     * Screenshot is taken with Chrome in the foreground.
     */
    public static final String SCREENSHOT_TAKEN_CHROME_IN_FOREGROUND =
            "screenshot_taken_chrome_in_foreground";

    /**
     * The data saver preview infobar was shown.
     */
    public static final String DATA_SAVER_PREVIEW_INFOBAR_SHOWN = "data_saver_preview_opened";

    /**
     * Data was saved when page loaded.
     */
    public static final String DATA_SAVED_ON_PAGE_LOAD = "data_saved_page_load";

    /**
     * The overflow menu was opened.
     */
    public static final String OVERFLOW_OPENED_WITH_DATA_SAVER_SHOWN =
            "overflow_opened_data_saver_shown";

    /**
     * The data saver footer was used (tapped).
     */
    public static final String DATA_SAVER_DETAIL_OPENED = "data_saver_overview_opened";

    /**
     * The download button for a media element was displayed.
     */
    public static final String MEDIA_DOWNLOAD_BUTTON_DISPLAYED = "media_download_button_displayed";

    /**
     * Contextual Search panel was opened.
     */
    public static final String CONTEXTUAL_SEARCH_PANEL_OPENED = "contextual_search_panel_opened";

    /**
     * Contextual Search showed an entity result for the searched query.
     */
    public static final String CONTEXTUAL_SEARCH_ENTITY_RESULT = "contextual_search_entity_result";

    /**
     * Contextual Search was triggered by a tap.
     */
    public static final String CONTEXTUAL_SEARCH_TRIGGERED_BY_TAP =
            "contextual_search_triggered_by_tap";

    /**
     * Contextual Search was triggered by longpressing.
     */
    public static final String CONTEXTUAL_SEARCH_TRIGGERED_BY_LONGPRESS =
            "contextual_search_triggered_by_longpress";

    /**
     * Do not instantiate.
     */
    private EventConstants() {}
}
