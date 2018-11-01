// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.view.ActionMode;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.modelutil.PropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.BooleanPropertyKey;
import org.chromium.chrome.browser.modelutil.PropertyModel.ObjectPropertyKey;
import org.chromium.chrome.browser.omnibox.UrlBar.ScrollType;
import org.chromium.chrome.browser.omnibox.UrlBar.UrlBarDelegate;
import org.chromium.chrome.browser.omnibox.UrlBar.UrlBarTextContextMenuDelegate;
import org.chromium.chrome.browser.omnibox.UrlBar.UrlDirectionListener;
import org.chromium.chrome.browser.omnibox.UrlBarCoordinator.SelectionState;

import java.util.Locale;

/**
 * The model properties for the URL bar text component.
 */
class UrlBarProperties {
    /**
     * Contains the necessary information to update the text shown in the UrlBar.
     */
    static class UrlBarTextState {
        /** Text to be shown. */
        public final CharSequence text;

        /** Specifies how the text should be scrolled in the unfocused state. */
        public final @ScrollType int scrollType;

        /** Specifies the index to scroll to if {@link UrlBar#SCROLL_TO_TLD} is specified. */
        public int scrollToIndex;

        /** Specifies how the text should be selected in the focused state. */
        public final @SelectionState int selectionState;

        public UrlBarTextState(CharSequence text, @ScrollType int scrollType, int scrollToIndex,
                @SelectionState int selectionState) {
            this.text = text;
            this.scrollType = scrollType;
            this.scrollToIndex = scrollToIndex;
            this.selectionState = selectionState;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s: text: %s; scrollType: %d; selectionState: %d",
                    getClass().getSimpleName(), text, scrollType, selectionState);
        }
    }

    /**
     * Contains the necessary information to display inline autocomplete text.
     */
    static class AutocompleteText {
        /** The text preceding the autocomplete text (typically entered by the user). */
        public final String userText;

        /** The inline autocomplete text to be appended to the end of the user text. */
        public final String autocompleteText;

        public AutocompleteText(String userText, String autocompleteText) {
            this.userText = userText;
            this.autocompleteText = autocompleteText;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s: user text: %s; autocomplete text: %s",
                    getClass().getSimpleName(), userText, autocompleteText);
        }
    }

    /** The callback for contextual action modes (cut, copy, etc...). */
    public static final ObjectPropertyKey<ActionMode.Callback> ACTION_MODE_CALLBACK =
            new ObjectPropertyKey<>();

    /** Whether focus should be allowed on the view. */
    public static final BooleanPropertyKey ALLOW_FOCUS = new BooleanPropertyKey();

    /** Specified the autocomplete text to be shown to the user. */
    public static final ObjectPropertyKey<AutocompleteText> AUTOCOMPLETE_TEXT =
            new ObjectPropertyKey<>();

    /** The main delegate that provides additional capabilities to the UrlBar. */
    public static final ObjectPropertyKey<UrlBarDelegate> DELEGATE = new ObjectPropertyKey<>();

    /** The callback to be notified on focus changes. */
    public static final ObjectPropertyKey<Callback<Boolean>> FOCUS_CHANGE_CALLBACK =
            new ObjectPropertyKey<>();

    /** Whether the cursor should be shown in the view. */
    public static final BooleanPropertyKey SHOW_CURSOR = new BooleanPropertyKey();

    /** Delegate that provides additional functionality to the textual context actions. */
    public static final ObjectPropertyKey<UrlBarTextContextMenuDelegate>
            TEXT_CONTEXT_MENU_DELEGATE = new ObjectPropertyKey<>();

    /** The primary text state for what is shown in the view. */
    public static final ObjectPropertyKey<UrlBarTextState> TEXT_STATE = new ObjectPropertyKey<>();

    /** The listener to be notified of URL direction changes. */
    public static final ObjectPropertyKey<UrlDirectionListener> URL_DIRECTION_LISTENER =
            new ObjectPropertyKey<>();

    /** Specifies whether dark text colors should be used in the view. */
    public static final BooleanPropertyKey USE_DARK_TEXT_COLORS = new BooleanPropertyKey();

    /** The delegate that provides Window capabilities to the view. */
    public static final ObjectPropertyKey<WindowDelegate> WINDOW_DELEGATE =
            new ObjectPropertyKey<>();

    public static final PropertyKey[] ALL_KEYS =
            new PropertyKey[] {ACTION_MODE_CALLBACK, ALLOW_FOCUS, AUTOCOMPLETE_TEXT, DELEGATE,
                    FOCUS_CHANGE_CALLBACK, SHOW_CURSOR, TEXT_CONTEXT_MENU_DELEGATE, TEXT_STATE,
                    URL_DIRECTION_LISTENER, USE_DARK_TEXT_COLORS, WINDOW_DELEGATE};
}
