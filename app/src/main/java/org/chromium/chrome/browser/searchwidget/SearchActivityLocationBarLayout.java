// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.searchwidget;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.omnibox.LocationBarLayout;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.UiUtils;

/** Implementation of the {@link LocationBarLayout} that is displayed for widget searches. */
public class SearchActivityLocationBarLayout extends LocationBarLayout {
    /** Delegates calls out to the containing Activity. */
    public static interface Delegate {
        /** Load a URL in the associated tab. */
        void loadUrl(String url);

        /** The user hit the back button. */
        void backKeyPressed();
    }

    private Delegate mDelegate;

    public SearchActivityLocationBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUrlBarFocusable(true);

        // TODO(dfalcantara): Get rid of any possibility of inflating the G in a layout.
        View gContainer = findViewById(R.id.google_g_container);
        if (gContainer != null) gContainer.setVisibility(View.GONE);

        // TODO(dfalcantara): Find the correct way to do this.
        int spacingLarge =
                getResources().getDimensionPixelSize(R.dimen.contextual_search_peek_promo_padding);
        setPadding(spacingLarge, 0, spacingLarge, 0);
    }

    /** Set the {@link Delegate}. */
    void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    @Override
    protected void loadUrl(String url, int transition) {
        mDelegate.loadUrl(url);
    }

    @Override
    protected void backKeyPressed() {
        mDelegate.backKeyPressed();
    }

    @Override
    public boolean mustQueryUrlBarLocationForSuggestions() {
        return true;
    }

    @Override
    public void setUrlToPageUrl() {
        // Explicitly do nothing.  The tab is invisible, so showing its URL would be confusing.
    }

    @Override
    public void onNativeLibraryReady() {
        super.onNativeLibraryReady();
        setAutocompleteProfile(Profile.getLastUsedProfile().getOriginalProfile());
        setShowCachedZeroSuggestResults(true);
    }

    /** Called when the SearchActivity has finished initialization. */
    void onDeferredStartup(boolean isVoiceSearchIntent) {
        SearchWidgetProvider.updateCachedVoiceSearchAvailability(isVoiceSearchEnabled());
        if (isVoiceSearchIntent && mUrlBar.isFocused()) onUrlFocusChange(true);
    }

    /** Begins a new query. */
    void beginQuery(boolean isVoiceSearchIntent) {
        if (isVoiceSearchEnabled() && isVoiceSearchIntent) {
            startVoiceRecognition();
        } else {
            focusTextBox();
        }
    }

    private void focusTextBox() {
        if (mNativeInitialized) onUrlFocusChange(true);

        mUrlBar.setIgnoreTextChangesForAutocomplete(true);
        mUrlBar.setUrl("", null);
        mUrlBar.setIgnoreTextChangesForAutocomplete(false);

        mUrlBar.setCursorVisible(true);
        mUrlBar.setSelection(0, mUrlBar.getText().length());
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                UiUtils.showKeyboard(mUrlBar);
            }
        });
    }
}
