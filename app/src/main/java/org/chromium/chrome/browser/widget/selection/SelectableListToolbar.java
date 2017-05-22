// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.annotation.CallSuper;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.NumberRollView;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserver;
import org.chromium.chrome.browser.widget.displaystyle.HorizontalDisplayStyle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;
import org.chromium.ui.UiUtils;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A toolbar that changes its view depending on whether a selection is established. The toolbar
 * also optionally shows a search view depending on whether {@link #initializeSearchView()} has
 * been called.
 *
 * @param <E> The type of the selectable items this toolbar interacts with.
 */
public class SelectableListToolbar<E> extends Toolbar implements SelectionObserver<E>,
        OnClickListener, OnEditorActionListener, DisplayStyleObserver {

    /**
     * A delegate that handles searching the list of selectable items associated with this toolbar.
     */
    public interface SearchDelegate {
        /**
         * Called when the text in the search EditText box has changed.
         * @param query The text in the search EditText box.
         */
        void onSearchTextChanged(String query);

        /**
         * Called when a search is ended.
         */
        void onEndSearch();
    }

    /** No navigation button is displayed. **/
    protected static final int NAVIGATION_BUTTON_NONE = 0;
    /** Button to open the DrawerLayout. Only valid if mDrawerLayout is set. **/
    protected static final int NAVIGATION_BUTTON_MENU = 1;
    /** Button to navigate back. This calls {@link #onNavigationBack()}. **/
    protected static final int NAVIGATION_BUTTON_BACK = 2;
    /** Button to clear the selection. **/
    protected static final int NAVIGATION_BUTTON_SELECTION_BACK = 3;

    protected boolean mIsSelectionEnabled;
    protected SelectionDelegate<E> mSelectionDelegate;

    private boolean mHasSearchView;
    private boolean mIsSearching;
    private LinearLayout mSearchView;
    private EditText mSearchEditText;
    private TintedImageButton mClearTextButton;
    private SearchDelegate mSearchDelegate;

    protected NumberRollView mNumberRollView;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private TintedDrawable mNormalMenuButton;
    private TintedDrawable mSelectionMenuButton;

    private int mNavigationButton;
    private int mTitleResId;
    private int mSearchMenuItemId;
    private int mNormalGroupResId;
    private int mSelectedGroupResId;

    private int mNormalBackgroundColor;
    private int mSelectionBackgroundColor;
    private int mSearchBackgroundColor;

    private UiConfig mUiConfig;
    private int mDefaultTitleMarginStartPx;
    private int mWideDisplayLateralOffsetPx;
    private int mWideDisplayEndOffsetPx;
    private int mWideDisplayNavButtonOffsetPx;
    private int mOriginalContentInsetStart;
    private int mOriginalContentInsetEnd;
    private int mOriginalContentInsetStartWithNavigation;
    private int mOriginalContentInsetEndWithActions;

    /**
     * Constructor for inflating from XML.
     */
    public SelectableListToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Destroys and cleans up itself.
     */
    public void destroy() {
        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
    }

    /**
     * Initializes the SelectionToolbar.
     *
     * @param delegate The SelectionDelegate that will inform the toolbar of selection changes.
     * @param titleResId The resource id of the title string. May be 0 if this class shouldn't set
     *                   set a title when the selection is cleared.
     * @param drawerLayout The DrawerLayout whose navigation icon is displayed in this toolbar.
     * @param normalGroupResId The resource id of the menu group to show when a selection isn't
     *                         established.
     * @param selectedGroupResId The resource id of the menu item to show when a selection is
     *                           established.
     * @param normalBackgroundColorResId The resource id of the color to use as the background color
     *                                   when selection is not enabled. If null the default appbar
     *                                   background color will be used.
     */
    public void initialize(SelectionDelegate<E> delegate, int titleResId,
            @Nullable DrawerLayout drawerLayout, int normalGroupResId, int selectedGroupResId,
            @Nullable Integer normalBackgroundColorResId) {
        mTitleResId = titleResId;
        mDrawerLayout = drawerLayout;
        mNormalGroupResId = normalGroupResId;
        mSelectedGroupResId = selectedGroupResId;

        mSelectionDelegate = delegate;
        mSelectionDelegate.addObserver(this);

        if (mDrawerLayout != null) initActionBarDrawerToggle();

        normalBackgroundColorResId = normalBackgroundColorResId != null ? normalBackgroundColorResId
                : R.color.appbar_background;
        mNormalBackgroundColor =
                ApiCompatibilityUtils.getColor(getResources(), normalBackgroundColorResId);
        setBackgroundColor(mNormalBackgroundColor);

        mSelectionBackgroundColor = ApiCompatibilityUtils.getColor(
                getResources(), R.color.light_active_color);

        if (mTitleResId != 0) setTitle(mTitleResId);

        // TODO(twellington): add the concept of normal & selected tint to apply to all toolbar
        //                    buttons.
        mNormalMenuButton = TintedDrawable.constructTintedDrawable(getResources(),
                R.drawable.btn_menu);
        mSelectionMenuButton = TintedDrawable.constructTintedDrawable(getResources(),
                R.drawable.btn_menu, android.R.color.white);
    }

    /**
     * Inflates and initializes the search view.
     * @param searchDelegate The delegate that will handle performing searches.
     * @param hintStringResId The hint text to show in the search view's EditText box.
     * @param searchMenuItemId The menu item used to activate the search view. This item will be
     *                         hidden when selection is enabled or if the list of selectable items
     *                         associated with this toolbar is empty.
     */
    public void initializeSearchView(SearchDelegate searchDelegate, int hintStringResId,
            int searchMenuItemId) {
        mHasSearchView = true;
        mSearchDelegate = searchDelegate;
        mSearchMenuItemId = searchMenuItemId;
        mSearchBackgroundColor = Color.WHITE;

        LayoutInflater.from(getContext()).inflate(R.layout.search_toolbar, this);

        mSearchView = (LinearLayout) findViewById(R.id.search_view);

        mSearchEditText = (EditText) findViewById(R.id.search_text);
        mSearchEditText.setHint(hintStringResId);
        mSearchEditText.setOnEditorActionListener(this);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mClearTextButton.setVisibility(
                        TextUtils.isEmpty(s) ? View.INVISIBLE : View.VISIBLE);
                if (mIsSearching) mSearchDelegate.onSearchTextChanged(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mClearTextButton = (TintedImageButton) findViewById(R.id.clear_text_button);
        mClearTextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchEditText.setText("");
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        LayoutInflater.from(getContext()).inflate(R.layout.number_roll_view, this);
        mNumberRollView = (NumberRollView) findViewById(R.id.selection_mode_number);
        mNumberRollView.setContentDescriptionString(R.plurals.accessibility_selected_items);

        mOriginalContentInsetStart = getContentInsetStart();
        mOriginalContentInsetEnd = getContentInsetEnd();
        mOriginalContentInsetStartWithNavigation = getContentInsetStartWithNavigation();
        mOriginalContentInsetEndWithActions = getContentInsetEndWithActions();
    }

    @Override
    @CallSuper
    public void onSelectionStateChange(List<E> selectedItems) {
        boolean wasSelectionEnabled = mIsSelectionEnabled;
        mIsSelectionEnabled = mSelectionDelegate.isSelectionEnabled();

        // If onSelectionStateChange() gets called before onFinishInflate(), mNumberRollView
        // will be uninitialized. See crbug.com/637948.
        if (mNumberRollView == null) {
            mNumberRollView = (NumberRollView) findViewById(R.id.selection_mode_number);
        }

        if (mIsSelectionEnabled) {
            showSelectionView(selectedItems, wasSelectionEnabled);
        } else if (mIsSearching) {
            showSearchViewInternal();
        } else {
            showNormalView();
        }

        if (mIsSelectionEnabled && !wasSelectionEnabled) {
            announceForAccessibility(
                    getResources().getString(R.string.accessibility_toolbar_screen_position));
        }
    }

    @Override
    public void onClick(View view) {
        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_MENU:
                // ActionBarDrawerToggle handles this.
                break;
            case NAVIGATION_BUTTON_BACK:
                onNavigationBack();
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                mSelectionDelegate.clearSelection();
                break;
            default:
                assert false : "Incorrect navigation button state";
        }
    }

    /**
     * Handle a click on the navigation back button. If this toolbar has a search view, the search
     * view will be hidden. Subclasses should override this method if navigation back is also a
     * valid toolbar action when not searching.
     */
    public void onNavigationBack() {
        if (!mHasSearchView || !mIsSearching) return;

        hideSearchView();
    }

    /**
     * Update the current navigation button (the top-left icon on LTR)
     * @param navigationButton one of NAVIGATION_BUTTON_* constants.
     */
    protected void setNavigationButton(int navigationButton) {
        int iconResId = 0;
        int contentDescriptionId = 0;

        if (navigationButton == NAVIGATION_BUTTON_MENU && mDrawerLayout == null) {
            mNavigationButton = NAVIGATION_BUTTON_NONE;
        } else {
            mNavigationButton = navigationButton;
        }

        if (mNavigationButton == NAVIGATION_BUTTON_MENU) {
            initActionBarDrawerToggle();
            // ActionBarDrawerToggle will take care of icon and content description, so just return.
            return;
        }

        if (mActionBarDrawerToggle != null) {
            mActionBarDrawerToggle.setDrawerIndicatorEnabled(false);
            mDrawerLayout.addDrawerListener(null);
        }

        setNavigationOnClickListener(this);

        switch (mNavigationButton) {
            case NAVIGATION_BUTTON_NONE:
                break;
            case NAVIGATION_BUTTON_BACK:
                // TODO(twellington): use ic_arrow_back_white_24dp and tint it.
                iconResId = R.drawable.back_normal;
                contentDescriptionId = R.string.accessibility_toolbar_btn_back;
                break;
            case NAVIGATION_BUTTON_SELECTION_BACK:
                iconResId = R.drawable.ic_arrow_back_white_24dp;
                contentDescriptionId = R.string.accessibility_cancel_selection;
                break;
            default:
                assert false : "Incorrect navigationButton argument";
        }

        if (iconResId == 0) {
            setNavigationIcon(null);
        } else {
            setNavigationIcon(iconResId);
        }
        setNavigationContentDescription(contentDescriptionId);
    }

    /**
     * Shows the search edit text box and related views.
     */
    public void showSearchView() {
        assert mHasSearchView;

        mIsSearching = true;
        mSelectionDelegate.clearSelection();

        showSearchViewInternal();

        mSearchEditText.requestFocus();
        UiUtils.showKeyboard(mSearchEditText);
        setTitle(null);
    }

    /**
     * Hides the search edit text box and related views.
     */
    public void hideSearchView() {
        assert mHasSearchView;

        mIsSearching = false;

        mSearchEditText.setText("");
        UiUtils.hideKeyboard(mSearchEditText);
        showNormalView();

        mSearchDelegate.onEndSearch();
    }

    /**
     * Called when the data in the selectable list this toolbar is associated with changes.
     * @param numItems The number of items in the selectable list.
     */
    protected void onDataChanged(int numItems) {
        if (mHasSearchView) {
            getMenu().findItem(mSearchMenuItemId).setVisible(
                    !mIsSelectionEnabled && !mIsSearching && numItems != 0);
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            UiUtils.hideKeyboard(v);
        }
        return false;
    }

    /**
     * When the toolbar has a wide display style, its contents will be width constrained to
     * {@link UiConfig#WIDE_DISPLAY_STYLE_MIN_WIDTH_DP}. If the current screen width is greater than
     * UiConfig#WIDE_DISPLAY_STYLE_MIN_WIDTH_DP, the toolbar contents will be visually centered by
     * adding padding to both sides.
     *
     * @param wideDisplayLateralOffsetPx The offset to use for the lateral padding when in
     *                                   {@link HorizontalDisplayStyle#WIDE}.
     */
    public void setHasWideDisplayStyle(int wideDisplayLateralOffsetPx, UiConfig uiConfig) {
        mWideDisplayLateralOffsetPx = wideDisplayLateralOffsetPx;
        mDefaultTitleMarginStartPx = getTitleMarginStart();
        mWideDisplayNavButtonOffsetPx =
                getResources().getDimensionPixelSize(R.dimen.toolbar_wide_display_nav_icon_offset);
        mWideDisplayEndOffsetPx = getResources().getDimensionPixelSize(
                R.dimen.toolbar_wide_display_end_offset);

        mUiConfig = uiConfig;
        mUiConfig.addObserver(this);

    }

    @Override
    public void onDisplayStyleChanged(UiConfig.DisplayStyle newDisplayStyle) {
        int padding =
                SelectableListLayout.getPaddingForDisplayStyle(newDisplayStyle, getResources());
        int paddingStartOffset = 0;
        int paddingEndOffset = 0;
        int contentInsetStart = mOriginalContentInsetStart;
        int contentInsetStartWithNavigation = mOriginalContentInsetStartWithNavigation;
        int contentInsetEnd = mOriginalContentInsetEnd;
        int contentInsetEndWithActions = mOriginalContentInsetEndWithActions;

        if (newDisplayStyle.horizontal == HorizontalDisplayStyle.WIDE) {
            paddingStartOffset = mWideDisplayLateralOffsetPx;

            // The title and nav buttons are inset in the normal display style. In the wide display
            // style they should be aligned with the starting edge of the list elements.
            if (mIsSearching || mIsSelectionEnabled) {
                paddingStartOffset += mWideDisplayNavButtonOffsetPx;
            } else {
                paddingStartOffset -= mDefaultTitleMarginStartPx;
            }

            // The end button is also inset in the normal display. In the wide display it should be
            // aligned with the ending edge of the list elements.
            paddingEndOffset = mWideDisplayLateralOffsetPx + mWideDisplayEndOffsetPx;

            contentInsetStart = 0;
            contentInsetStartWithNavigation = 0;
            contentInsetEnd = 0;
            contentInsetEndWithActions = 0;
        }

        ApiCompatibilityUtils.setPaddingRelative(this,
                padding + paddingStartOffset, this.getPaddingTop(),
                padding + paddingEndOffset, this.getPaddingBottom());
        setContentInsetsRelative(contentInsetStart, contentInsetEnd);
        setContentInsetStartWithNavigation(contentInsetStartWithNavigation);
        setContentInsetEndWithActions(contentInsetEndWithActions);
    }

    SelectionDelegate<E> getSelectionDelegate() {
        return mSelectionDelegate;
    }

    boolean isSearching() {
        return mIsSearching;
    }

    /**
     * Set up ActionBarDrawerToggle, a.k.a. hamburger button.
     */
    private void initActionBarDrawerToggle() {
        // Sadly, the only way to set correct toolbar button listener for ActionBarDrawerToggle
        // is constructing, so we will need to construct every time we re-show this button.
        mActionBarDrawerToggle = new ActionBarDrawerToggle((Activity) getContext(),
                mDrawerLayout, this,
                R.string.accessibility_drawer_toggle_btn_open,
                R.string.accessibility_drawer_toggle_btn_close);
        mDrawerLayout.addDrawerListener(mActionBarDrawerToggle);
        mActionBarDrawerToggle.syncState();
    }

    private void showNormalView() {
        getMenu().setGroupVisible(mNormalGroupResId, true);
        getMenu().setGroupVisible(mSelectedGroupResId, false);
        if (mHasSearchView) mSearchView.setVisibility(View.GONE);

        setNavigationButton(NAVIGATION_BUTTON_MENU);
        setBackgroundColor(mNormalBackgroundColor);
        setOverflowIcon(mNormalMenuButton);
        if (mTitleResId != 0) setTitle(mTitleResId);

        mNumberRollView.setVisibility(View.GONE);
        mNumberRollView.setNumber(0, false);

        updateDisplayStyleIfNecessary();
    }

    private void showSelectionView(List<E> selectedItems, boolean wasSelectionEnabled) {
        getMenu().setGroupVisible(mNormalGroupResId, false);
        getMenu().setGroupVisible(mSelectedGroupResId, true);
        if (mHasSearchView) mSearchView.setVisibility(View.GONE);

        setNavigationButton(NAVIGATION_BUTTON_SELECTION_BACK);
        setBackgroundColor(mSelectionBackgroundColor);
        setOverflowIcon(mSelectionMenuButton);
        setTitle(null);

        mNumberRollView.setVisibility(View.VISIBLE);
        if (!wasSelectionEnabled) mNumberRollView.setNumber(0, false);
        mNumberRollView.setNumber(selectedItems.size(), true);

        if (mIsSearching) UiUtils.hideKeyboard(mSearchEditText);

        updateDisplayStyleIfNecessary();
    }

    private void showSearchViewInternal() {
        getMenu().setGroupVisible(mNormalGroupResId, false);
        getMenu().setGroupVisible(mSelectedGroupResId, false);
        mSearchView.setVisibility(View.VISIBLE);

        setNavigationButton(NAVIGATION_BUTTON_BACK);
        setBackgroundColor(mSearchBackgroundColor);

        updateDisplayStyleIfNecessary();
    }

    private void updateDisplayStyleIfNecessary() {
        if (mUiConfig != null) onDisplayStyleChanged(mUiConfig.getCurrentDisplayStyle());
    }

    @VisibleForTesting
    public View getSearchViewForTests() {
        return mSearchView;
    }
}
