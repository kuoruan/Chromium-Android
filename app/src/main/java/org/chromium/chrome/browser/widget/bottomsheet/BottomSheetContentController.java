// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.bookmarks.BookmarkSheetContent;
import org.chromium.chrome.browser.download.DownloadSheetContent;
import org.chromium.chrome.browser.history.HistorySheetContent;
import org.chromium.chrome.browser.ntp.IncognitoBottomSheetContent;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.suggestions.SuggestionsBottomSheetContent;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.ViewHighlighter;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.StateChangeReason;
import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationItemView;
import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationMenuView;
import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationView;
import org.chromium.chrome.browser.widget.bottomsheet.base.BottomNavigationView.OnNavigationItemSelectedListener;
import org.chromium.ui.UiUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Displays and controls a {@link BottomNavigationView} fixed to the bottom of the
 * {@link BottomSheet}. Also manages {@link BottomSheetContent} displayed in the BottomSheet.
 */
public class BottomSheetContentController extends BottomNavigationView
        implements OnNavigationItemSelectedListener {
    /** The different types of content that may be displayed in the bottom sheet. */
    @IntDef({TYPE_SUGGESTIONS, TYPE_DOWNLOADS, TYPE_BOOKMARKS, TYPE_HISTORY, TYPE_INCOGNITO_HOME,
            TYPE_AUXILIARY_CONTENT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContentType {}
    public static final int TYPE_SUGGESTIONS = 0;
    public static final int TYPE_DOWNLOADS = 1;
    public static final int TYPE_BOOKMARKS = 2;
    public static final int TYPE_HISTORY = 3;
    public static final int TYPE_INCOGNITO_HOME = 4;
    // This type should be used for non-primary sheet content. If the sheet is opened because of
    // content with this type, the back button will close the sheet.
    public static final int TYPE_AUXILIARY_CONTENT = 5;

    // Used if there is no bottom sheet content.
    private static final int NO_CONTENT_ID = 0;

    // R.id.action_home is overloaded, so an invalid ID is used to reference the incognito version
    // of the home content.
    private static final int INCOGNITO_HOME_ID = -1;

    // Since the placeholder content cannot be triggered by a navigation item like the others, this
    // value must also be an invalid ID.
    private static final int PLACEHOLDER_ID = -2;

    private final Map<Integer, BottomSheetContent> mBottomSheetContents = new HashMap<>();

    private final BottomSheetObserver mBottomSheetObserver = new EmptyBottomSheetObserver() {
        @Override
        public void onSheetOffsetChanged(float heightFraction) {
            // If the omnibox is not focused, allow the navigation bar to set its Y translation.
            if (!mOmniboxHasFocus) {
                float offsetY = mBottomSheet.getSheetHeightForState(mBottomSheet.isSmallScreen()
                                                ? BottomSheet.SHEET_STATE_FULL
                                                : BottomSheet.SHEET_STATE_HALF)
                        - mBottomSheet.getSheetOffsetFromBottom();
                setTranslationY(Math.max(offsetY, 0f));

                if (mBottomSheet.getTargetSheetState() != BottomSheet.SHEET_STATE_PEEK
                        && mSelectedItemId == PLACEHOLDER_ID) {
                    showBottomSheetContent(R.id.action_home);
                }
            }
            setVisibility(MathUtils.areFloatsEqual(heightFraction, 0f) ? View.GONE : View.VISIBLE);

            mSnackbarManager.dismissAllSnackbars();
        }

        @Override
        public void onSheetOpened(@StateChangeReason int reason) {
            setIcons();

            if (reason == StateChangeReason.OMNIBOX_FOCUS) {
                // If the omnibox is being focused, show the placeholder.
                mBottomSheet.showContent(mPlaceholderContent);
                mBottomSheet.endTransitionAnimations();
                if (mSelectedItemId > 0) getMenu().findItem(mSelectedItemId).setChecked(false);
                mSelectedItemId = PLACEHOLDER_ID;
                return;
            }

            if (mSelectedItemId == NO_CONTENT_ID) showBottomSheetContent(R.id.action_home);

            if (mHighlightItemId != null) {
                mHighlightedView = mActivity.findViewById(mHighlightItemId);
                ViewHighlighter.turnOnHighlight(mHighlightedView, true);
            }
        }

        @Override
        public void onSheetClosed(@StateChangeReason int reason) {
            removeIcons();

            if (ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME_DESTROY_SUGGESTIONS)) {
                // TODO(bauerb): Implement support for destroying the home sheet after a delay.
                mSelectedItemId = NO_CONTENT_ID;
                mBottomSheet.showContent(null);
            } else {
                if (mSelectedItemId != NO_CONTENT_ID && mSelectedItemId != R.id.action_home) {
                    showBottomSheetContent(R.id.action_home);
                } else {
                    clearBottomSheetContents(false);
                }
            }

            // The keyboard should be hidden when the sheet is closed in case it was made visible by
            // sheet content.
            UiUtils.hideKeyboard(BottomSheetContentController.this);

            ViewHighlighter.turnOffHighlight(mHighlightedView);
            mHighlightedView = null;
            mHighlightItemId = null;
        }

        @Override
        public void onSheetContentChanged(BottomSheetContent newContent) {
            if (mBottomSheet.isSheetOpen()) announceBottomSheetContentSelected();

            if (mShouldOpenSheetOnNextContentChange) {
                mShouldOpenSheetOnNextContentChange = false;
                if (mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_FULL) {
                    mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_FULL, true);
                }
                return;
            }

            if (mBottomSheet.getSheetState() == BottomSheet.SHEET_STATE_PEEK) {
                clearBottomSheetContents(mBottomSheet.getCurrentSheetContent() == null);
            }
        }

        @Override
        public void onSheetLayout(int windowHeight, int containerHeight) {
            setTranslationY(containerHeight - windowHeight);
        }
    };

    private BottomSheet mBottomSheet;
    private TabModelSelector mTabModelSelector;
    private SnackbarManager mSnackbarManager;
    private int mSelectedItemId;
    private ChromeActivity mActivity;
    private boolean mShouldOpenSheetOnNextContentChange;
    private PlaceholderSheetContent mPlaceholderContent;
    private boolean mOmniboxHasFocus;
    private TabModelSelectorObserver mTabModelSelectorObserver;
    private Integer mHighlightItemId;
    private View mHighlightedView;
    private boolean mNavItemSelectedWhileOmniboxFocused;
    private int mConsecutiveBookmarkTaps;

    public BottomSheetContentController(Context context, AttributeSet atts) {
        super(context, atts);

        mPlaceholderContent = new PlaceholderSheetContent(context);
    }

    public void setHighlightItemId(@Nullable Integer highlightItemId) {
        mHighlightItemId = highlightItemId;
    }

    /** Called when the activity containing the bottom sheet is destroyed. */
    public void destroy() {
        clearBottomSheetContents(true);
        if (mPlaceholderContent != null) {
            mPlaceholderContent.destroy();
            mPlaceholderContent = null;
        }
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mTabModelSelectorObserver);
            mTabModelSelector = null;
        }
    }

    /**
     * Initializes the {@link BottomSheetContentController}.
     * @param bottomSheet The {@link BottomSheet} associated with this bottom nav.
     * @param tabModelSelector The {@link TabModelSelector} for the application.
     * @param activity The {@link ChromeActivity} that owns the BottomSheet.
     */
    public void init(
            BottomSheet bottomSheet, TabModelSelector tabModelSelector, ChromeActivity activity) {
        mBottomSheet = bottomSheet;
        mBottomSheet.addObserver(mBottomSheetObserver);
        mActivity = activity;
        mTabModelSelector = tabModelSelector;

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                updateVisuals(newModel.isIncognito(), true);
                showBottomSheetContent(R.id.action_home);
                mPlaceholderContent.setIsIncognito(newModel.isIncognito());

                // Release incognito bottom sheet content so that it can be garbage collected.
                if (!newModel.isIncognito()
                        && mBottomSheetContents.containsKey(INCOGNITO_HOME_ID)) {
                    mBottomSheetContents.get(INCOGNITO_HOME_ID).destroy();
                    mBottomSheetContents.remove(INCOGNITO_HOME_ID);
                }
            }
        };
        mTabModelSelector.addObserver(mTabModelSelectorObserver);

        setOnNavigationItemSelectedListener(this);
        hideMenuLabels();

        ViewGroup snackbarContainer =
                (ViewGroup) activity.findViewById(R.id.bottom_sheet_snackbar_container);

        if (mBottomSheet.useTallBottomNav()) {
            getLayoutParams().height = (int) mBottomSheet.getBottomNavHeight();
            ((MarginLayoutParams) snackbarContainer.getLayoutParams()).bottomMargin =
                    (int) mBottomSheet.getBottomNavHeight();
        }

        mSnackbarManager = new SnackbarManager(mActivity, snackbarContainer);
        mSnackbarManager.onStart();

        ApplicationStatus.registerStateListenerForActivity(new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.STARTED) mSnackbarManager.onStart();
                if (newState == ActivityState.STOPPED) mSnackbarManager.onStop();
            }
        }, mActivity);

        // We use a global layout listener here to ensure we update menu item spacing after the
        // menu icons have their full width.
        mBottomSheet.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateMenuItemSpacing();
            }
        });
        updateVisuals(mTabModelSelector.isIncognitoSelected(), true);
        BottomSheetPaddingUtils.applyPaddingToContent(mPlaceholderContent, mBottomSheet);
    }

    /**
     * Whenever this is triggered by a global layout change, we ensure that our bottom navigation
     * menu items are spaced apart appropriately.
     */
    private void updateMenuItemSpacing() {
        getMenuView().updateMenuItemSpacingForMinWidth(
                mBottomSheet.getWidth(), mBottomSheet.getHeight());
    }

    /**
     * @param itemId The id of the MenuItem to select.
     */
    public void selectItem(int itemId) {
        // TODO(twellington): A #setSelectedItemId() method was added to the support library
        //                    recently. Replace this custom implementation with that method after
        //                    the support library is rolled.
        onNavigationItemSelected(getMenu().findItem(itemId));
    }

    /**
     * Shows the specified {@link BottomSheetContent} and opens the {@link BottomSheet}.
     * @param itemId The menu item id of the {@link BottomSheetContent} to show.
     */
    public void showContentAndOpenSheet(int itemId) {
        if (mActivity.isInOverviewMode() && !mBottomSheet.isShowingNewTab()) {
            // Open a new tab to show the content if currently in tab switcher and a new tab is
            // not currently being displayed.
            mShouldOpenSheetOnNextContentChange = true;
            mBottomSheet.displayNewTabUi(mTabModelSelector.getCurrentModel().isIncognito(), itemId);
        } else if (itemId != mSelectedItemId) {
            mShouldOpenSheetOnNextContentChange = true;
            selectItem(itemId);
        } else if (mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_FULL) {
            mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_FULL, true);
        }
    }

    /**
     * A notification that the omnibox focus state is changing.
     * @param hasFocus Whether or not the omnibox has focus.
     */
    public void onOmniboxFocusChange(boolean hasFocus) {
        mOmniboxHasFocus = hasFocus;

        if (!mNavItemSelectedWhileOmniboxFocused && !mOmniboxHasFocus) {
            showBottomSheetContent(R.id.action_home);
        }
        mNavItemSelectedWhileOmniboxFocused = false;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        mConsecutiveBookmarkTaps =
                item.getItemId() == R.id.action_bookmarks ? mConsecutiveBookmarkTaps + 1 : 0;
        if (mConsecutiveBookmarkTaps >= 5) {
            mConsecutiveBookmarkTaps = 0;
            doStarExplosion((ViewGroup) getRootView(), findViewById(R.id.action_bookmarks));
        }

        if (mOmniboxHasFocus) mNavItemSelectedWhileOmniboxFocused = true;

        if (mBottomSheet.getSheetState() == BottomSheet.SHEET_STATE_PEEK
                && !mShouldOpenSheetOnNextContentChange) {
            return false;
        }

        ViewHighlighter.turnOffHighlight(mHighlightedView);
        mHighlightedView = null;
        mHighlightItemId = null;

        boolean isShowingAuxContent = mBottomSheet.getCurrentSheetContent() != null
                && mBottomSheet.getCurrentSheetContent().getType() == TYPE_AUXILIARY_CONTENT;

        if (mSelectedItemId == item.getItemId() && !isShowingAuxContent) {
            getSheetContentForId(mSelectedItemId).scrollToTop();
            return false;
        }

        mBottomSheet.defocusOmnibox();

        mSnackbarManager.dismissAllSnackbars();
        showBottomSheetContent(item.getItemId());
        return true;
    }

    private void hideMenuLabels() {
        BottomNavigationMenuView menuView = getMenuView();
        for (int i = 0; i < menuView.getChildCount(); i++) {
            BottomNavigationItemView item = (BottomNavigationItemView) menuView.getChildAt(i);
            item.hideLabel();
        }
    }

    private BottomSheetContent getSheetContentForId(int navItemId) {
        if (mTabModelSelector.isIncognitoSelected() && navItemId == R.id.action_home) {
            navItemId = INCOGNITO_HOME_ID;
        }

        BottomSheetContent content = mBottomSheetContents.get(navItemId);
        if (content != null) return content;

        return createAndCacheContentForId(navItemId);
    }

    /**
     * Create and return the content for the provided nav button id.
     * @param navItemId The id to create and get the content for.
     * @return The created content.
     */
    private BottomSheetContent createAndCacheContentForId(int navItemId) {
        BottomSheetContent content = null;
        if (navItemId == R.id.action_home) {
            content = new SuggestionsBottomSheetContent(
                    mActivity, mBottomSheet, mTabModelSelector, mSnackbarManager);
        } else if (navItemId == R.id.action_downloads) {
            content = new DownloadSheetContent(
                    mActivity, mTabModelSelector.getCurrentModel().isIncognito(), mSnackbarManager);
        } else if (navItemId == R.id.action_bookmarks) {
            content = new BookmarkSheetContent(mActivity, mSnackbarManager);
        } else if (navItemId == R.id.action_history) {
            content = new HistorySheetContent(mActivity, mSnackbarManager);
        } else if (navItemId == INCOGNITO_HOME_ID) {
            content = new IncognitoBottomSheetContent(mActivity);
        }

        // Call this only after it's created to avoid adding an infinite amount of additional
        // padding.
        BottomSheetPaddingUtils.applyPaddingToContent(content, mBottomSheet);

        mBottomSheetContents.put(navItemId, content);
        return content;
    }

    private void showBottomSheetContent(int navItemId) {
        // There are some bugs related to programmatically selecting menu items that are fixed in
        // newer support library versions.
        // TODO(twellington): remove this after the support library is rolled.
        if (mSelectedItemId > 0) getMenu().findItem(mSelectedItemId).setChecked(false);
        mSelectedItemId = navItemId;
        getMenu().findItem(mSelectedItemId).setChecked(true);

        BottomSheetContent newContent = getSheetContentForId(mSelectedItemId);
        mBottomSheet.showContent(newContent);
    }

    private void announceBottomSheetContentSelected() {
        if (mSelectedItemId == R.id.action_home) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_home_tab));
        } else if (mSelectedItemId == R.id.action_downloads) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_downloads_tab));
        } else if (mSelectedItemId == R.id.action_bookmarks) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_bookmarks_tab));
        } else if (mSelectedItemId == R.id.action_history) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_history_tab));
        }
    }

    private void updateVisuals(
            boolean isIncognitoTabModelSelected, boolean bottomNavIsTransparent) {
        if (bottomNavIsTransparent) {
            setBackgroundResource(isIncognitoTabModelSelected
                            ? R.color.incognito_primary_color_home_bottom_nav
                            : R.color.primary_color_home_bottom_nav);
        } else {
            setBackgroundResource(isIncognitoTabModelSelected ? R.color.incognito_primary_color
                                                              : R.color.modern_primary_color);
        }

        ColorStateList tint = ApiCompatibilityUtils.getColorStateList(getResources(),
                isIncognitoTabModelSelected ? R.color.bottom_nav_tint_incognito
                                            : R.color.bottom_nav_tint);
        setItemIconTintList(tint);
        setItemTextColor(tint);
    }

    private void setIcons() {
        getMenu().findItem(R.id.action_home).setIcon(R.drawable.ic_home_grey600_24dp);
        getMenu().findItem(R.id.action_downloads).setIcon(R.drawable.ic_file_download_white_24dp);
        getMenu().findItem(R.id.action_bookmarks).setIcon(R.drawable.btn_star_filled);
        getMenu().findItem(R.id.action_history).setIcon(R.drawable.ic_watch_later_24dp);
    }

    private void removeIcons() {
        getMenu().findItem(R.id.action_home).setIcon(null);
        getMenu().findItem(R.id.action_downloads).setIcon(null);
        getMenu().findItem(R.id.action_bookmarks).setIcon(null);
        getMenu().findItem(R.id.action_history).setIcon(null);
    }

    @VisibleForTesting
    public int getSelectedItemIdForTests() {
        // TODO(twellington): A #getSelectedItemId() method was added to the support library
        //                    recently. Replace this custom implementation with that method after
        //                    the support library is rolled.
        return mSelectedItemId;
    }

    private void clearBottomSheetContents(boolean destroyHomeContent) {
        Iterator<Entry<Integer, BottomSheetContent>> contentIterator =
                mBottomSheetContents.entrySet().iterator();
        while (contentIterator.hasNext()) {
            Entry<Integer, BottomSheetContent> entry = contentIterator.next();
            if (!destroyHomeContent
                    && (entry.getKey() == R.id.action_home
                               || entry.getKey() == INCOGNITO_HOME_ID)) {
                continue;
            }

            entry.getValue().destroy();
            contentIterator.remove();
        }
    }

    /**
     * Explode some stars from the center of the bookmarks icon.
     * @param rootView The root view to run in.
     * @param selectedItemView The item that was selected.
     */
    private void doStarExplosion(ViewGroup rootView, View selectedItemView) {
        if (rootView == null || selectedItemView == null) return;

        Drawable starFull =
                ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.btn_star_filled);
        Drawable starEmpty = ApiCompatibilityUtils.getDrawable(getResources(), R.drawable.btn_star);

        int[] outPosition = new int[2];
        ViewUtils.getRelativeDrawPosition(rootView, selectedItemView, outPosition);

        // Center the star's start position over the icon in the middle of the button.
        outPosition[0] += selectedItemView.getWidth() / 2.f - starFull.getIntrinsicWidth() / 2.f;
        outPosition[1] += selectedItemView.getHeight() / 2.f - starFull.getIntrinsicHeight() / 2.f;

        for (int i = 0; i < 5; i++) {
            MagicStar star = new MagicStar(getContext(), outPosition[0], outPosition[1], 100 * i,
                    Math.random() > 0.5f ? starFull : starEmpty);
            rootView.addView(star);
        }
    }

    /**
     * This is an image view that runs a simple animation before detaching itself from the window.
     */
    private static class MagicStar extends ImageView {
        /** The starting position of the star. */
        private PointF mStartPosition;

        /** The direction of the star. */
        private PointF mVector;

        /** The velocity of the star. */
        private float mVelocity;

        /** The speed and direction that the star is rotating. Negative is counter-clockwise. */
        private float mRotationVelocity;

        /** The animation delay for the star. */
        private long mStartDelay;

        public MagicStar(
                Context context, float startX, float startY, long startDelay, Drawable drawable) {
            super(context);

            mStartPosition = new PointF(startX, startY);
            mStartDelay = startDelay;

            // Fire stars within 45 degrees of 'up'.
            float minAngle = (float) Math.toRadians(45.f);
            float maxAngle = (float) Math.toRadians(135.f);
            mVector = new PointF((float) Math.cos(getRandomInInterval(minAngle, maxAngle)),
                    (float) Math.sin(getRandomInInterval(minAngle, maxAngle)));

            mVelocity = getRandomInInterval(10.f, 15.f);
            mRotationVelocity = getRandomInInterval(-2.f, 2.f);

            setImageDrawable(drawable);
        }

        /**
         * Get a random number between min and max.
         * @param min The minimum value of the float.
         * @param max The maximum value of the float.
         * @return A random number between min and max.
         */
        private float getRandomInInterval(float min, float max) {
            return (float) (Math.random() * (max - min)) + min;
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();

            setAlpha(0.f);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
            setLayoutParams(params);

            setTranslationX(mStartPosition.x);
            setTranslationY(mStartPosition.y);

            doAnimation();
        }

        /**
         * Run the star's animation and detach it from the window when complete.
         */
        private void doAnimation() {
            ValueAnimator animator = ValueAnimator.ofFloat(0.f, 1.f);
            animator.setDuration(500);
            animator.setStartDelay(mStartDelay);

            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (float) valueAnimator.getAnimatedValue();
                    setAlpha(1.f - value);
                    setTranslationX(getTranslationX() - mVector.x * mVelocity);
                    setTranslationY(getTranslationY() - mVector.y * mVelocity);
                    setScaleX(0.4f + value);
                    setScaleY(0.4f + value);
                    setRotation((getRotation() + mRotationVelocity) % 360);
                }
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setAlpha(1.f);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    ((ViewGroup) getParent()).removeView(MagicStar.this);
                }
            });

            animator.start();
        }
    }
}
