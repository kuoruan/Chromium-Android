// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.content.Context;
import android.net.MailTo;
import android.support.annotation.IntDef;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;

import org.chromium.base.CollectionUtil;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionProxyUma;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.util.UrlUtilities;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link ContextMenuPopulator} used for showing the default Chrome context menu.
 */
public class ChromeContextMenuPopulator implements ContextMenuPopulator {

    private static final String TAG = "CCMenuPopulator";

    /**
     * Defines the context menu modes
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        NORMAL_MODE, /* Default mode */
        CUSTOM_TAB_MODE, /* Custom tab mode */
        FULLSCREEN_TAB_MODE /* Full screen mode */
    })
    public @interface ContextMenuMode {}

    public static final int NORMAL_MODE = 0;
    public static final int CUSTOM_TAB_MODE = 1;
    public static final int FULLSCREEN_TAB_MODE = 2;

    // Items that are included in all context menus.
    private static final Set<Integer> BASE_WHITELIST = CollectionUtil.newHashSet(
            R.id.contextmenu_copy_link_address, R.id.contextmenu_call,
            R.id.contextmenu_send_message, R.id.contextmenu_add_to_contacts, R.id.contextmenu_copy,
            R.id.contextmenu_copy_link_text, R.id.contextmenu_load_original_image,
            R.id.contextmenu_save_link_as, R.id.contextmenu_save_image,
            R.id.contextmenu_share_image, R.id.contextmenu_save_video);

    // Items that are included for normal Chrome browser mode.
    private static final Set<Integer> NORMAL_MODE_WHITELIST = CollectionUtil.newHashSet(
            R.id.contextmenu_open_in_new_tab, R.id.contextmenu_open_in_other_window,
            R.id.contextmenu_open_in_incognito_tab, R.id.contextmenu_save_link_as,
            R.id.contextmenu_open_image_in_new_tab, R.id.contextmenu_search_by_image);

    // Additional items for custom tabs mode.
    private static final Set<Integer> CUSTOM_TAB_MODE_WHITELIST = CollectionUtil.newHashSet(
            R.id.contextmenu_open_image, R.id.contextmenu_search_by_image,
            R.id.contextmenu_open_in_new_chrome_tab, R.id.contextmenu_open_in_chrome_incognito_tab,
            R.id.contextmenu_open_in_browser_id);

    // Additional items for fullscreen tabs mode.
    private static final Set<Integer> FULLSCREEN_TAB_MODE_WHITELIST =
            CollectionUtil.newHashSet(R.id.menu_id_open_in_chrome);

    private final ContextMenuItemDelegate mDelegate;
    private MenuInflater mMenuInflater;
    private final int mMode;

    static class ContextMenuUma {
        // Note: these values must match the ContextMenuOption enum in histograms.xml.
        static final int ACTION_OPEN_IN_NEW_TAB = 0;
        static final int ACTION_OPEN_IN_INCOGNITO_TAB = 1;
        static final int ACTION_COPY_LINK_ADDRESS = 2;
        static final int ACTION_COPY_EMAIL_ADDRESS = 3;
        static final int ACTION_COPY_LINK_TEXT = 4;
        static final int ACTION_SAVE_LINK = 5;
        static final int ACTION_SAVE_IMAGE = 6;
        static final int ACTION_OPEN_IMAGE = 7;
        static final int ACTION_OPEN_IMAGE_IN_NEW_TAB = 8;
        static final int ACTION_SEARCH_BY_IMAGE = 11;
        static final int ACTION_LOAD_ORIGINAL_IMAGE = 13;
        static final int ACTION_SAVE_VIDEO = 14;
        static final int ACTION_SHARE_IMAGE = 19;
        static final int ACTION_OPEN_IN_OTHER_WINDOW = 20;
        static final int ACTION_SEND_EMAIL = 23;
        static final int ACTION_ADD_TO_CONTACTS = 24;
        static final int ACTION_CALL = 30;
        static final int ACTION_SEND_TEXT_MESSAGE = 31;
        static final int ACTION_COPY_PHONE_NUMBER = 32;
        static final int ACTION_OPEN_IN_NEW_CHROME_TAB = 33;
        static final int ACTION_OPEN_IN_CHROME_INCOGNITO_TAB = 34;
        static final int ACTION_OPEN_IN_BROWSER = 35;
        static final int ACTION_OPEN_IN_CHROME = 36;
        static final int NUM_ACTIONS = 37;

        // Note: these values must match the ContextMenuSaveLinkType enum in histograms.xml.
        // Only add new values at the end, right before NUM_TYPES. We depend on these specific
        // values in UMA histograms.
        static final int TYPE_UNKNOWN = 0;
        static final int TYPE_TEXT = 1;
        static final int TYPE_IMAGE = 2;
        static final int TYPE_AUDIO = 3;
        static final int TYPE_VIDEO = 4;
        static final int TYPE_PDF = 5;
        static final int NUM_TYPES = 6;

        /**
         * Records a histogram entry when the user selects an item from a context menu.
         * @param params The ContextMenuParams describing the current context menu.
         * @param action The action that the user selected (e.g. ACTION_SAVE_IMAGE).
         */
        static void record(ContextMenuParams params, int action) {
            assert action >= 0;
            assert action < NUM_ACTIONS;
            String histogramName;
            if (params.isVideo()) {
                histogramName = "ContextMenu.SelectedOption.Video";
            } else if (params.isImage()) {
                histogramName = params.isAnchor()
                        ? "ContextMenu.SelectedOption.ImageLink"
                        : "ContextMenu.SelectedOption.Image";
            } else {
                assert params.isAnchor();
                histogramName = "ContextMenu.SelectedOption.Link";
            }
            RecordHistogram.recordEnumeratedHistogram(histogramName, action, NUM_ACTIONS);
        }

        /**
         * Records the content types when user downloads the file by long pressing the
         * save link context menu option.
         */
        static void recordSaveLinkTypes(String url) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            int mimeType = TYPE_UNKNOWN;
            if (extension != null) {
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (type != null) {
                    if (type.startsWith("text")) {
                        mimeType = TYPE_TEXT;
                    } else if (type.startsWith("image")) {
                        mimeType = TYPE_IMAGE;
                    } else if (type.startsWith("audio")) {
                        mimeType = TYPE_AUDIO;
                    } else if (type.startsWith("video")) {
                        mimeType = TYPE_VIDEO;
                    } else if (type.equals("application/pdf")) {
                        mimeType = TYPE_PDF;
                    }
                }
            }
            RecordHistogram.recordEnumeratedHistogram(
                    "ContextMenu.SaveLinkType", mimeType, NUM_TYPES);
        }
    }

    /**
     * Builds a {@link ChromeContextMenuPopulator}.
     * @param delegate The {@link ContextMenuItemDelegate} that will be notified with actions
     *                 to perform when menu items are selected.
     * @param mode Defines the context menu mode
     */
    public ChromeContextMenuPopulator(ContextMenuItemDelegate delegate, @ContextMenuMode int mode) {
        mDelegate = delegate;
        mMode = mode;
    }

    @Override
    public void onDestroy() {
        mDelegate.onDestroy();
    }

    @Override
    public void buildContextMenu(ContextMenu menu, Context context, ContextMenuParams params) {
        if (!TextUtils.isEmpty(params.getLinkUrl())
                && !params.getLinkUrl().equals(UrlConstants.ABOUT_BLANK_DISPLAY_URL)) {
            setHeaderText(context, menu, params.getLinkUrl());
        } else if (!TextUtils.isEmpty(params.getTitleText())) {
            setHeaderText(context, menu, params.getTitleText());
        }

        if (params.isFile()) return;

        if (mMenuInflater == null) mMenuInflater = new MenuInflater(context);

        mMenuInflater.inflate(R.menu.chrome_context_menu, menu);

        menu.setGroupVisible(R.id.contextmenu_group_anchor, params.isAnchor());
        menu.setGroupVisible(R.id.contextmenu_group_image, params.isImage());
        menu.setGroupVisible(R.id.contextmenu_group_video, params.isVideo());
        menu.setGroupVisible(R.id.contextmenu_group_message,
                MailTo.isMailTo(params.getLinkUrl())
                        || UrlUtilities.isTelScheme(params.getLinkUrl()));

        Set<Integer> supportedOptions = new HashSet<>();
        supportedOptions.addAll(BASE_WHITELIST);
        if (mMode == FULLSCREEN_TAB_MODE) {
            supportedOptions.addAll(FULLSCREEN_TAB_MODE_WHITELIST);
        } else if (mMode == CUSTOM_TAB_MODE) {
            supportedOptions.addAll(CUSTOM_TAB_MODE_WHITELIST);
            if (!ChromePreferenceManager.getInstance().getCachedChromeDefaultBrowser()) {
                menu.findItem(R.id.contextmenu_open_in_browser_id)
                        .setTitle(mDelegate.getTitleForOpenTabInExternalApp());
            }
        } else {
            supportedOptions.addAll(NORMAL_MODE_WHITELIST);
        }

        Set<Integer> disabledOptions = getDisabledOptions(params);
        // Iterate through the entire menu list, if if doesn't exist in the map, don't show it.
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            item.setVisible(supportedOptions.contains(item.getItemId())
                    && !disabledOptions.contains(item.getItemId()));
        }

        // Special case for searching by image element.
        if (supportedOptions.contains(R.id.contextmenu_search_by_image)) {
            menu.findItem(R.id.contextmenu_search_by_image)
                    .setTitle(context.getString(R.string.contextmenu_search_web_for_image,
                            TemplateUrlService.getInstance()
                                    .getDefaultSearchEngineTemplateUrl()
                                    .getShortName()));
        }
    }

    /**
     * Given a set of params. It creates a list of items that should not be accessible in specific
     * instances. Since it doesn't have access to the menu groups, they need to be filtered outside
     * of this method.
     * @param params The parameters used to create a list of items that should not be allowed.
     */
    private Set<Integer> getDisabledOptions(ContextMenuParams params) {
        Set<Integer> disabledOptions = new HashSet<>();
        if (params.isAnchor() && !mDelegate.isOpenInOtherWindowSupported()) {
            disabledOptions.add(R.id.contextmenu_open_in_other_window);
        }

        if (mDelegate.isIncognito() || !mDelegate.isIncognitoSupported()) {
            disabledOptions.add(R.id.contextmenu_open_in_incognito_tab);
        }

        if (params.getLinkText().trim().isEmpty() || params.isImage()) {
            disabledOptions.add(R.id.contextmenu_copy_link_text);
        }

        if (params.isAnchor() && !UrlUtilities.isAcceptedScheme(params.getLinkUrl())) {
            disabledOptions.add(R.id.contextmenu_open_in_other_window);
            disabledOptions.add(R.id.contextmenu_open_in_new_tab);
            disabledOptions.add(R.id.contextmenu_open_in_incognito_tab);
        }

        if (MailTo.isMailTo(params.getLinkUrl())) {
            disabledOptions.add(R.id.contextmenu_copy_link_text);
            disabledOptions.add(R.id.contextmenu_copy_link_address);
            if (!mDelegate.supportsSendEmailMessage()) {
                disabledOptions.add(R.id.contextmenu_send_message);
            }
            if (TextUtils.isEmpty(MailTo.parse(params.getLinkUrl()).getTo())
                    || !mDelegate.supportsAddToContacts()) {
                disabledOptions.add(R.id.contextmenu_add_to_contacts);
            }
            disabledOptions.add(R.id.contextmenu_call);
        } else if (UrlUtilities.isTelScheme(params.getLinkUrl())) {
            disabledOptions.add(R.id.contextmenu_copy_link_text);
            disabledOptions.add(R.id.contextmenu_copy_link_address);
            if (!mDelegate.supportsCall()) {
                disabledOptions.add(R.id.contextmenu_call);
            }
            if (!mDelegate.supportsSendTextMessage()) {
                disabledOptions.add(R.id.contextmenu_send_message);
            }
            if (!mDelegate.supportsAddToContacts()) {
                disabledOptions.add(R.id.contextmenu_add_to_contacts);
            }
        }

        if (!UrlUtilities.isDownloadableScheme(params.getLinkUrl())) {
            disabledOptions.add(R.id.contextmenu_save_link_as);
        }

        boolean isSrcDownloadableScheme = UrlUtilities.isDownloadableScheme(params.getSrcUrl());
        if (params.isVideo()) {
            boolean saveableAndDownloadable = params.canSaveMedia() && isSrcDownloadableScheme;
            if (!saveableAndDownloadable) {
                disabledOptions.add(R.id.contextmenu_save_video);
            }
        } else if (params.isImage() && params.imageWasFetchedLoFi()) {
            DataReductionProxyUma.previewsLoFiContextMenuAction(
                    DataReductionProxyUma.ACTION_LOFI_LOAD_IMAGE_CONTEXT_MENU_SHOWN);
            // All image context menu items other than "Load image," "Open original image in
            // new tab," and "Copy image URL" should be disabled on Lo-Fi images.
            disabledOptions.add(R.id.contextmenu_save_image);
            disabledOptions.add(R.id.contextmenu_open_image);
            disabledOptions.add(R.id.contextmenu_search_by_image);
            disabledOptions.add(R.id.contextmenu_share_image);
        } else if (params.isImage() && !params.imageWasFetchedLoFi()) {
            disabledOptions.add(R.id.contextmenu_load_original_image);

            if (!isSrcDownloadableScheme) {
                disabledOptions.add(R.id.contextmenu_save_image);
            }

            // Avoid showing open image option for same image which is already opened.
            if (mDelegate.getPageUrl().equals(params.getSrcUrl())) {
                disabledOptions.add(R.id.contextmenu_open_image);
            }
            final TemplateUrlService templateUrlServiceInstance = TemplateUrlService.getInstance();
            final boolean isSearchByImageAvailable = isSrcDownloadableScheme
                    && templateUrlServiceInstance.isLoaded()
                    && templateUrlServiceInstance.isSearchByImageAvailable()
                    && templateUrlServiceInstance.getDefaultSearchEngineTemplateUrl() != null;

            if (!isSearchByImageAvailable) {
                disabledOptions.add(R.id.contextmenu_search_by_image);
            }
        }

        // Hide all items that could spawn additional tabs until FRE has been completed.
        if (!FirstRunStatus.getFirstRunFlowComplete()) {
            disabledOptions.add(R.id.contextmenu_open_image_in_new_tab);
            disabledOptions.add(R.id.contextmenu_open_in_other_window);
            disabledOptions.add(R.id.contextmenu_open_in_new_tab);
            disabledOptions.add(R.id.contextmenu_open_in_incognito_tab);
            disabledOptions.add(R.id.contextmenu_search_by_image);
            disabledOptions.add(R.id.menu_id_open_in_chrome);
        }

        if (mMode == CUSTOM_TAB_MODE) {
            try {
                URI uri = new URI(getUrl(params));
                if (UrlUtilities.isInternalScheme(uri)) {
                    disabledOptions.add(R.id.contextmenu_open_in_new_chrome_tab);
                    disabledOptions.add(R.id.contextmenu_open_in_chrome_incognito_tab);
                    disabledOptions.add(R.id.contextmenu_open_in_browser_id);
                } else if (ChromePreferenceManager.getInstance().getCachedChromeDefaultBrowser()) {
                    disabledOptions.add(R.id.contextmenu_open_in_browser_id);
                    if (!mDelegate.isIncognitoSupported()) {
                        disabledOptions.add(R.id.contextmenu_open_in_chrome_incognito_tab);
                    }
                } else {
                    disabledOptions.add(R.id.contextmenu_open_in_new_chrome_tab);
                    disabledOptions.add(R.id.contextmenu_open_in_chrome_incognito_tab);
                }
            } catch (URISyntaxException e) {
                return disabledOptions;
            }
        }

        return disabledOptions;
    }

    @Override
    public boolean onItemSelected(ContextMenuHelper helper, ContextMenuParams params, int itemId) {
        if (itemId == R.id.contextmenu_open_in_other_window) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_OTHER_WINDOW);
            mDelegate.onOpenInOtherWindow(params.getLinkUrl(), params.getReferrer());
        } else if (itemId == R.id.contextmenu_open_in_new_tab) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_NEW_TAB);
            mDelegate.onOpenInNewTab(params.getLinkUrl(), params.getReferrer());
        } else if (itemId == R.id.contextmenu_open_in_incognito_tab) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_INCOGNITO_TAB);
            mDelegate.onOpenInNewIncognitoTab(params.getLinkUrl());
        } else if (itemId == R.id.contextmenu_open_image) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IMAGE);
            mDelegate.onOpenImageUrl(params.getSrcUrl(), params.getReferrer());
        } else if (itemId == R.id.contextmenu_open_image_in_new_tab) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IMAGE_IN_NEW_TAB);
            mDelegate.onOpenImageInNewTab(params.getSrcUrl(), params.getReferrer());
        } else if (itemId == R.id.contextmenu_load_original_image) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_LOAD_ORIGINAL_IMAGE);
            DataReductionProxyUma.previewsLoFiContextMenuAction(
                    DataReductionProxyUma.ACTION_LOFI_LOAD_IMAGE_CONTEXT_MENU_CLICKED);
            if (!mDelegate.wasLoadOriginalImageRequestedForPageLoad()) {
                DataReductionProxyUma.previewsLoFiContextMenuAction(
                        DataReductionProxyUma.ACTION_LOFI_LOAD_IMAGE_CONTEXT_MENU_CLICKED_ON_PAGE);
            }
            mDelegate.onLoadOriginalImage();
        } else if (itemId == R.id.contextmenu_copy_link_address) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_COPY_LINK_ADDRESS);
            mDelegate.onSaveToClipboard(params.getUnfilteredLinkUrl(),
                    ContextMenuItemDelegate.CLIPBOARD_TYPE_LINK_URL);
        } else if (itemId == R.id.contextmenu_call) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_CALL);
            mDelegate.onCall(params.getLinkUrl());
        } else if (itemId == R.id.contextmenu_send_message) {
            if (MailTo.isMailTo(params.getLinkUrl())) {
                ContextMenuUma.record(params, ContextMenuUma.ACTION_SEND_EMAIL);
                mDelegate.onSendEmailMessage(params.getLinkUrl());
            } else if (UrlUtilities.isTelScheme(params.getLinkUrl())) {
                ContextMenuUma.record(params, ContextMenuUma.ACTION_SEND_TEXT_MESSAGE);
                mDelegate.onSendTextMessage(params.getLinkUrl());
            }
        } else if (itemId == R.id.contextmenu_add_to_contacts) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_ADD_TO_CONTACTS);
            mDelegate.onAddToContacts(params.getLinkUrl());
        } else if (itemId == R.id.contextmenu_copy) {
            if (MailTo.isMailTo(params.getLinkUrl())) {
                ContextMenuUma.record(params, ContextMenuUma.ACTION_COPY_EMAIL_ADDRESS);
                mDelegate.onSaveToClipboard(MailTo.parse(params.getLinkUrl()).getTo(),
                        ContextMenuItemDelegate.CLIPBOARD_TYPE_LINK_URL);
            } else if (UrlUtilities.isTelScheme(params.getLinkUrl())) {
                ContextMenuUma.record(params, ContextMenuUma.ACTION_COPY_PHONE_NUMBER);
                mDelegate.onSaveToClipboard(UrlUtilities.getTelNumber(params.getLinkUrl()),
                        ContextMenuItemDelegate.CLIPBOARD_TYPE_LINK_URL);
            }
        } else if (itemId == R.id.contextmenu_copy_link_text) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_COPY_LINK_TEXT);
            mDelegate.onSaveToClipboard(
                    params.getLinkText(), ContextMenuItemDelegate.CLIPBOARD_TYPE_LINK_TEXT);
        } else if (itemId == R.id.contextmenu_save_image) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_SAVE_IMAGE);
            if (mDelegate.startDownload(params.getSrcUrl(), false)) {
                helper.startContextMenuDownload(
                        false, mDelegate.isDataReductionProxyEnabledForURL(params.getSrcUrl()));
            }
        } else if (itemId == R.id.contextmenu_save_video) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_SAVE_VIDEO);
            if (mDelegate.startDownload(params.getSrcUrl(), false)) {
                helper.startContextMenuDownload(false, false);
            }
        } else if (itemId == R.id.contextmenu_save_link_as) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_SAVE_LINK);
            String url = params.getUnfilteredLinkUrl();
            if (mDelegate.startDownload(url, true)) {
                ContextMenuUma.recordSaveLinkTypes(url);
                helper.startContextMenuDownload(true, false);
            }
        } else if (itemId == R.id.contextmenu_search_by_image) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_SEARCH_BY_IMAGE);
            helper.searchForImage();
        } else if (itemId == R.id.contextmenu_share_image) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_SHARE_IMAGE);
            helper.shareImage();
        } else if (itemId == R.id.menu_id_open_in_chrome) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_CHROME);
            mDelegate.onOpenInChrome(params.getLinkUrl(), params.getPageUrl());
        } else if (itemId == R.id.contextmenu_open_in_new_chrome_tab) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_NEW_CHROME_TAB);
            mDelegate.onOpenInNewChromeTabFromCCT(getUrl(params), false);
        } else if (itemId == R.id.contextmenu_open_in_chrome_incognito_tab) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_CHROME_INCOGNITO_TAB);
            mDelegate.onOpenInNewChromeTabFromCCT(getUrl(params), true);
        } else if (itemId == R.id.contextmenu_open_in_browser_id) {
            ContextMenuUma.record(params, ContextMenuUma.ACTION_OPEN_IN_BROWSER);
            mDelegate.onOpenInDefaultBrowser(getUrl(params));
        } else {
            assert false;
        }

        return true;
    }

    /**
     * The valid url of a link is stored in the linkUrl of ContextMenuParams while the
     * valid url of a image or video is stored in the srcUrl of ContextMenuParams.
     * @param params The parameters used to decide the type of the content.
     */
    private String getUrl(ContextMenuParams params) {
        if (params.isImage() || params.isVideo()) {
            return params.getSrcUrl();
        } else {
            return params.getLinkUrl();
        }
    }

    private void setHeaderText(Context context, ContextMenu menu, String text) {
        ContextMenuTitleView title = new ContextMenuTitleView(context, text);
        menu.setHeaderView(title);
    }
}
