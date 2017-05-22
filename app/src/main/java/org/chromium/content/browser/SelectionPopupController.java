// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Browser;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.content.R;
import org.chromium.content.browser.input.FloatingPastePopupMenu;
import org.chromium.content.browser.input.ImeAdapter;
import org.chromium.content.browser.input.LGEmailActionModeWorkaround;
import org.chromium.content.browser.input.LegacyPastePopupMenu;
import org.chromium.content.browser.input.PastePopupMenu;
import org.chromium.content.browser.input.PastePopupMenu.PastePopupMenuDelegate;
import org.chromium.content_public.browser.ActionModeCallbackHelper;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.touch_selection.SelectionEventType;

import java.util.List;

/**
 * A class that handles input-related web content selection UI like action mode
 * and paste popup view. It wraps an {@link ActionMode} created by the associated view,
 * providing modified interaction with it.
 *
 * Embedders can use {@link ActionModeCallbackHelper} implemented by this class
 * to create {@link ActionMode.Callback} instance and configure the selection action
 * mode tasks to their requirements.
 */
@TargetApi(Build.VERSION_CODES.M)
public class SelectionPopupController extends ActionModeCallbackHelper {
    private static final String TAG = "cr.SelectionPopCtlr";  // 20 char limit

    /**
     * Android Intent size limitations prevent sending over a megabyte of data. Limit
     * query lengths to 100kB because other things may be added to the Intent.
     */
    private static final int MAX_SHARE_QUERY_LENGTH = 100000;

    // Default delay for reshowing the {@link ActionMode} after it has been
    // hidden. This avoids flickering issues if there are trailing rect
    // invalidations after the ActionMode is shown. For example, after the user
    // stops dragging a selection handle, in turn showing the ActionMode, the
    // selection change response will be asynchronous. 300ms should accomodate
    // most such trailing, async delays.
    private static final int SHOW_DELAY_MS = 300;

    private final Context mContext;
    private final WindowAndroid mWindowAndroid;
    private final WebContents mWebContents;
    private final RenderCoordinates mRenderCoordinates;
    private final ImeAdapter mImeAdapter;
    private ActionMode.Callback mCallback;

    // Selection rectangle in DIP.
    private final Rect mSelectionRect = new Rect();

    // Self-repeating task that repeatedly hides the ActionMode. This is
    // required because ActionMode only exposes a temporary hide routine.
    private final Runnable mRepeatingHideRunnable;

    private View mView;
    private ActionMode mActionMode;

    // Bit field for mappings from menu item to a flag indicating it is allowed.
    private int mAllowedMenuItems;

    private boolean mHidden;
    private boolean mPendingInvalidateContentRect;

    private boolean mEditable;
    private boolean mIsPasswordType;
    private boolean mIsInsertion;

    // Indicates whether the action mode needs to be redrawn since last invalidation.
    private boolean mNeedsPrepare;

    private boolean mUnselectAllOnDismiss;
    private String mLastSelectedText;

    // Tracks whether a selection is currently active.  When applied to selected text, indicates
    // whether the last selected text is still highlighted.
    private boolean mHasSelection;

    // Lazily created paste popup menu, triggered either via long press in an
    // editable region or from tapping the insertion handle.
    private PastePopupMenu mPastePopupMenu;
    private boolean mWasPastePopupShowingOnInsertionDragStart;

    // The client that implements Contextual Search functionality, or null if none exists.
    private ContextualSearchClient mContextualSearchClient;

    /**
     * Create {@link SelectionPopupController} instance.
     * @param context Context for action mode.
     * @param window WindowAndroid instance.
     * @param webContents WebContents instance.
     * @param view Container view.
     * @param renderCoordinates Coordinates info used to position elements.
     * @param imeAdapter ImeAdapter instance to handle cursor position.
     */
    public SelectionPopupController(Context context, WindowAndroid window, WebContents webContents,
            View view, RenderCoordinates renderCoordinates, ImeAdapter imeAdapter) {
        mContext = context;
        mWindowAndroid = window;
        mWebContents = webContents;
        mView = view;
        mRenderCoordinates = renderCoordinates;
        mImeAdapter = imeAdapter;

        // The menu items are allowed by default.
        mAllowedMenuItems = MENU_ITEM_SHARE | MENU_ITEM_WEB_SEARCH | MENU_ITEM_PROCESS_TEXT;
        mRepeatingHideRunnable = new Runnable() {
            @Override
            public void run() {
                assert mHidden;
                final long hideDuration = getDefaultHideDuration();
                // Ensure the next hide call occurs before the ActionMode reappears.
                mView.postDelayed(mRepeatingHideRunnable, hideDuration - 1);
                hideActionModeTemporarily(hideDuration);
            }
        };
    }

    /**
     * Update the container view.
     */
    void setContainerView(View view) {
        assert view != null;

        // Cleans up action mode before switching to a new container view.
        if (isActionModeValid()) finishActionMode();
        mUnselectAllOnDismiss = true;
        destroyPastePopup();

        mView = view;
    }

    /**
     * Set the action mode callback.
     * @param callback ActionMode.Callback handling the callbacks from action mode.
     */
    void setCallback(ActionMode.Callback callback) {
        mCallback = callback;
    }

    @Override
    public boolean isActionModeValid() {
        return mActionMode != null;
    }

    // True if action mode is not yet initialized or set to no-op mode.
    private boolean isEmpty() {
        return mCallback == EMPTY_CALLBACK;
    }

    @Override
    public void setAllowedMenuItems(int allowedMenuItems) {
        mAllowedMenuItems = allowedMenuItems;
    }

    /**
     * Show (activate) android action mode by starting it.
     *
     * <p>Action mode in floating mode is tried first, and then falls back to
     * a normal one.
     * @return {@code true} if the action mode started successfully or is already on.
     */
    public boolean showActionMode() {
        if (isEmpty()) return false;

        // Just refreshes the view if it is already showing.
        if (isActionModeValid()) {
            invalidateActionMode();
            return true;
        }

        if (mView.getParent() != null) {
             // On ICS, startActionMode throws an NPE when getParent() is null.
            assert mWebContents != null;
            ActionMode actionMode = supportsFloatingActionMode()
                    ? startFloatingActionMode()
                    : mView.startActionMode(mCallback);
            if (actionMode != null) {
                // This is to work around an LGE email issue. See crbug.com/651706 for more details.
                LGEmailActionModeWorkaround.runIfNecessary(mContext, actionMode);
            }
            mActionMode = actionMode;
        }
        mUnselectAllOnDismiss = true;
        return isActionModeValid();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private ActionMode startFloatingActionMode() {
        ActionMode actionMode = mView.startActionMode(
                new FloatingActionModeCallback(this, mCallback), ActionMode.TYPE_FLOATING);
        return actionMode;
    }

    void showPastePopup(int x, int y) {
        if (mView.getParent() == null || mView.getVisibility() != View.VISIBLE) {
            return;
        }

        if (!supportsFloatingActionMode() && !canPaste()) return;

        PastePopupMenu pastePopupMenu = getPastePopup();
        if (pastePopupMenu == null) return;

        // Coordinates are in DIP.
        final float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
        final int xPix = (int) (x * deviceScale);
        final int yPix = (int) (y * deviceScale);
        final float browserControlsShownPix = mRenderCoordinates.getContentOffsetYPix();
        try {
            pastePopupMenu.show(xPix, (int) (yPix + browserControlsShownPix));
        } catch (WindowManager.BadTokenException e) {
        }
    }

    @Override
    public boolean supportsFloatingActionMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    void hidePastePopup() {
        if (mPastePopupMenu != null) mPastePopupMenu.hide();
    }

    private PastePopupMenu getPastePopup() {
        if (mPastePopupMenu == null) {
            PastePopupMenuDelegate delegate = new PastePopupMenuDelegate() {
                @Override
                public void paste() {
                    mWebContents.paste();
                    mWebContents.dismissTextHandles();
                }

                @Override
                public boolean canPaste() {
                    return SelectionPopupController.this.canPaste();
                }
            };
            Context windowContext = mWindowAndroid.getContext().get();
            if (windowContext == null) return null;
            if (supportsFloatingActionMode()) {
                mPastePopupMenu = new FloatingPastePopupMenu(windowContext, mView, delegate);
            } else {
                mPastePopupMenu = new LegacyPastePopupMenu(windowContext, mView, delegate);
            }
        }
        return mPastePopupMenu;
    }

    void destroyPastePopup() {
        hidePastePopup();
        mPastePopupMenu = null;
    }

    @VisibleForTesting
    public boolean isPastePopupShowing() {
        return mPastePopupMenu != null && mPastePopupMenu.isShowing();
    }

    // Composition methods for android.view.ActionMode

    /**
     * @see ActionMode#finish()
     */
    @Override
    public void finishActionMode() {
        if (isActionModeValid()) {
            mActionMode.finish();

            // Should be nulled out in case #onDestroyActionMode() is not invoked in response.
            mActionMode = null;
        }
    }

    /**
     * @see ActionMode#invalidate()
     * Note that invalidation will also reset visibility state. The caller
     * should account for this when making subsequent visibility updates.
     */
    private void invalidateActionMode() {
        if (!isActionModeValid()) return;
        if (mHidden) {
            assert canHideActionMode();
            mHidden = false;
            mView.removeCallbacks(mRepeatingHideRunnable);
            mPendingInvalidateContentRect = false;
        }

        // Try/catch necessary for framework bug, crbug.com/446717.
        try {
            mActionMode.invalidate();
        } catch (NullPointerException e) {
            Log.w(TAG, "Ignoring NPE from ActionMode.invalidate() as workaround for L", e);
        }
    }

    /**
     * @see ActionMode#invalidateContentRect()
     */
    public void invalidateContentRect() {
        if (supportsFloatingActionMode()) {
            if (mHidden) {
                mPendingInvalidateContentRect = true;
            } else {
                mPendingInvalidateContentRect = false;
                if (isActionModeValid()) mActionMode.invalidateContentRect();
            }
        }
    }

    /**
     * @see ActionMode#onWindowFocusChanged()
     */
    void onWindowFocusChanged(boolean hasWindowFocus) {
        if (supportsFloatingActionMode() && isActionModeValid()) {
            mActionMode.onWindowFocusChanged(hasWindowFocus);
        }
    }

    /**
     * Hide or reveal the ActionMode. Note that this only has visible
     * side-effects if the underlying ActionMode supports hiding.
     * @param hide whether to hide or show the ActionMode.
     */
    void hideActionMode(boolean hide) {
        if (!canHideActionMode()) return;
        if (mHidden == hide) return;
        mHidden = hide;
        if (mHidden) {
            mRepeatingHideRunnable.run();
        } else {
            mHidden = false;
            mView.removeCallbacks(mRepeatingHideRunnable);
            hideActionModeTemporarily(SHOW_DELAY_MS);
            if (mPendingInvalidateContentRect) {
                mPendingInvalidateContentRect = false;
                invalidateContentRect();
            }
        }
    }

    /**
     * @see ActionMode#hide(long)
     */
    private void hideActionModeTemporarily(long duration) {
        assert canHideActionMode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isActionModeValid()) mActionMode.hide(duration);
        }
    }

    private boolean canHideActionMode() {
        return supportsFloatingActionMode()
                && isActionModeValid()
                && mActionMode.getType() == ActionMode.TYPE_FLOATING;
    }

    private long getDefaultHideDuration() {
        if (supportsFloatingActionMode()) {
            return ViewConfiguration.getDefaultActionModeHideDuration();
        }
        return 2000;
    }

    // Default handlers for action mode callbacks.

    @Override
    public void onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(DeviceFormFactor.isTablet(mContext)
                        ? mContext.getString(R.string.actionbar_textselection_title)
                        : null);
        mode.setSubtitle(null);
        createActionMenu(mode, menu);
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (!mNeedsPrepare) return false;
        menu.clear();
        createActionMenu(mode, menu);
        return true;
    }

    /**
     * Initialize the menu by populating all the available items. Embedders should remove
     * the items that are not relevant to the input text being edited.
     */
    public static void initializeMenu(Context context, ActionMode mode, Menu menu) {
        try {
            mode.getMenuInflater().inflate(R.menu.select_action_menu, menu);
        } catch (Resources.NotFoundException e) {
            // TODO(tobiasjs) by the time we get here we have already
            // caused a resource loading failure to be logged. WebView
            // resource access needs to be improved so that this
            // logspam can be avoided.
            new MenuInflater(context).inflate(R.menu.select_action_menu, menu);
        }
    }

    private void createActionMenu(ActionMode mode, Menu menu) {
        mNeedsPrepare = false;
        initializeMenu(mContext, mode, menu);

        if (!isSelectionEditable() || !canPaste()) {
            menu.removeItem(R.id.select_action_menu_paste);
        }

        if (isInsertion()) {
            menu.removeItem(R.id.select_action_menu_select_all);
            menu.removeItem(R.id.select_action_menu_cut);
            menu.removeItem(R.id.select_action_menu_copy);
            menu.removeItem(R.id.select_action_menu_share);
            menu.removeItem(R.id.select_action_menu_web_search);
            return;
        }

        if (!isSelectionEditable()) {
            menu.removeItem(R.id.select_action_menu_cut);
        }

        if (isSelectionEditable() || !isSelectActionModeAllowed(MENU_ITEM_SHARE)) {
            menu.removeItem(R.id.select_action_menu_share);
        }

        if (isSelectionEditable() || isIncognito()
                || !isSelectActionModeAllowed(MENU_ITEM_WEB_SEARCH)) {
            menu.removeItem(R.id.select_action_menu_web_search);
        }

        if (isSelectionPassword()) {
            menu.removeItem(R.id.select_action_menu_copy);
            menu.removeItem(R.id.select_action_menu_cut);
            return;
        }

        initializeTextProcessingMenu(menu);
    }

    private boolean canPaste() {
        ClipboardManager clipMgr = (ClipboardManager)
                mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipMgr.hasPrimaryClip();
    }

    /**
     * Intialize the menu items for processing text, if there is any.
     */
    private void initializeTextProcessingMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || !isSelectActionModeAllowed(MENU_ITEM_PROCESS_TEXT)) {
            return;
        }

        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> supportedActivities =
                packageManager.queryIntentActivities(createProcessTextIntent(), 0);
        for (int i = 0; i < supportedActivities.size(); i++) {
            ResolveInfo resolveInfo = supportedActivities.get(i);
            CharSequence label = resolveInfo.loadLabel(mContext.getPackageManager());
            menu.add(R.id.select_action_menu_text_processing_menus, Menu.NONE, i, label)
                    .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static Intent createProcessTextIntent() {
        return new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Intent createProcessTextIntentForResolveInfo(ResolveInfo info) {
        boolean isReadOnly = !isSelectionEditable();
        return createProcessTextIntent()
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, isReadOnly)
                .setClassName(info.activityInfo.packageName, info.activityInfo.name);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (!isActionModeValid()) return true;

        int id = item.getItemId();
        int groupId = item.getGroupId();

        if (id == R.id.select_action_menu_select_all) {
            selectAll();
        } else if (id == R.id.select_action_menu_cut) {
            cut();
            mode.finish();
        } else if (id == R.id.select_action_menu_copy) {
            copy();
            mode.finish();
        } else if (id == R.id.select_action_menu_paste) {
            paste();
            mode.finish();
        } else if (id == R.id.select_action_menu_share) {
            share();
            mode.finish();
        } else if (id == R.id.select_action_menu_web_search) {
            search();
            mode.finish();
        } else if (groupId == R.id.select_action_menu_text_processing_menus) {
            processText(item.getIntent());
            // The ActionMode is not dismissed to match the behavior with
            // TextView in Android M.
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode() {
        mActionMode = null;
        if (mUnselectAllOnDismiss) {
            mWebContents.dismissTextHandles();
            clearSelection();
        }
    }

    /**
     * Called when an ActionMode needs to be positioned on screen, potentially occluding view
     * content. Note this may be called on a per-frame basis.
     *
     * @param mode The ActionMode that requires positioning.
     * @param view The View that originated the ActionMode, in whose coordinates the Rect should
     *             be provided.
     * @param outRect The Rect to be populated with the content position.
     */
    @Override
    public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
        float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
        outRect.set((int) (mSelectionRect.left * deviceScale),
                (int) (mSelectionRect.top * deviceScale),
                (int) (mSelectionRect.right * deviceScale),
                (int) (mSelectionRect.bottom * deviceScale));

        // The selection coordinates are relative to the content viewport, but we need
        // coordinates relative to the containing View.
        outRect.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
    }

    /**
     * Perform a select all action.
     */
    @VisibleForTesting
    void selectAll() {
        mWebContents.selectAll();
        // Even though the above statement logged a SelectAll user action, we want to
        // track whether the focus was in an editable field, so log that too.
        if (isSelectionEditable()) {
            RecordUserAction.record("MobileActionMode.SelectAllWasEditable");
        } else {
            RecordUserAction.record("MobileActionMode.SelectAllWasNonEditable");
        }
    }

    /**
     * Perform a cut (to clipboard) action.
     */
    @VisibleForTesting
    void cut() {
        mWebContents.cut();
    }

    /**
     * Perform a copy (to clipboard) action.
     */
    @VisibleForTesting
    void copy() {
        mWebContents.copy();
    }

    /**
     * Perform a paste action.
     */
    @VisibleForTesting
    void paste() {
        mWebContents.paste();
    }

    /**
     * Perform a share action.
     */
    @VisibleForTesting
    void share() {
        RecordUserAction.record("MobileActionMode.Share");
        String query = sanitizeQuery(getSelectedText(), MAX_SHARE_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, query);
        try {
            Intent i = Intent.createChooser(send, mContext.getString(R.string.actionbar_share));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * Perform a processText action (translating the text, for example).
     */
    private void processText(Intent intent) {
        RecordUserAction.record("MobileActionMode.ProcessTextIntent");
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        String query = sanitizeQuery(getSelectedText(), MAX_SEARCH_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, query);

        // Intent is sent by WindowAndroid by default.
        try {
            mWindowAndroid.showIntent(intent, new WindowAndroid.IntentCallback() {
                @Override
                public void onIntentCompleted(WindowAndroid window, int resultCode, Intent data) {
                    onReceivedProcessTextResult(resultCode, data);
                }
            }, null);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * Perform a search action.
     */
    @VisibleForTesting
    void search() {
        RecordUserAction.record("MobileActionMode.WebSearch");
        String query = sanitizeQuery(getSelectedText(), MAX_SEARCH_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra(SearchManager.EXTRA_NEW_SEARCH, true);
        i.putExtra(SearchManager.QUERY, query);
        i.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * @return true if the current selection is of password type.
     */
    @VisibleForTesting
    boolean isSelectionPassword() {
        return mIsPasswordType;
    }

    /**
     * @return true iff the current selection is editable (e.g. text within an input field).
     */
    boolean isSelectionEditable() {
        return mEditable;
    }

    /**
     * @return true if the current selection is an insertion point.
     */
    @VisibleForTesting
    public boolean isInsertion() {
        return mIsInsertion;
    }

    /**
     * @return true if the current selection is for incognito content.
     *         Note: This should remain constant for the callback's lifetime.
     */
    private boolean isIncognito() {
        return mWebContents.isIncognito();
    }

    /**
     * @see ActionModeCallbackHelper#sanitizeQuery(String, int)
     */
    public static String sanitizeQuery(String query, int maxLength) {
        if (TextUtils.isEmpty(query) || query.length() < maxLength) return query;
        Log.w(TAG, "Truncating oversized query (" + query.length() + ").");
        return query.substring(0, maxLength) + "…";
    }

    /**
     * @param actionModeItem the flag for the action mode item in question. The valid flags are
     *        {@link #MENU_ITEM_SHARE}, {@link #MENU_ITEM_WEB_SEARCH}, and
     *        {@link #MENU_ITEM_PROCESS_TEXT}.
     * @return true if the menu item action is allowed. Otherwise, the menu item
     *         should be removed from the menu.
     */
    private boolean isSelectActionModeAllowed(int actionModeItem) {
        boolean isAllowedByClient = (mAllowedMenuItems & actionModeItem) != 0;
        if (actionModeItem == MENU_ITEM_SHARE) {
            return isAllowedByClient && isShareAvailable();
        }
        return isAllowedByClient;
    }

    @Override
    public void onReceivedProcessTextResult(int resultCode, Intent data) {
        if (mWebContents == null || resultCode != Activity.RESULT_OK || data == null) return;

        // Do not handle the result if no text is selected or current selection is not editable.
        if (!mHasSelection || !isSelectionEditable()) return;

        CharSequence result = data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (result != null) {
            // TODO(hush): Use a variant of replace that re-selects the replaced text.
            // crbug.com/546710
            mWebContents.replace(result.toString());
        }
    }

    void restoreSelectionPopupsIfNecessary() {
        if (mHasSelection && !isActionModeValid()) {
            if (!showActionMode()) clearSelection();
        }
    }

    // All coordinates are in DIP.
    void onSelectionEvent(int eventType, int xAnchor, int yAnchor,
            int left, int top, int right, int bottom, boolean isScrollInProgress,
            boolean touchScrollInProgress) {
        // Ensure the provided selection coordinates form a non-empty rect, as required by
        // the selection action mode.
        if (left == right) ++right;
        if (top == bottom) ++bottom;
        switch (eventType) {
            case SelectionEventType.SELECTION_HANDLES_SHOWN:
                mSelectionRect.set(left, top, right, bottom);
                mHasSelection = true;
                mUnselectAllOnDismiss = true;
                if (!showActionMode()) clearSelection();
                break;

            case SelectionEventType.SELECTION_HANDLES_MOVED:
                mSelectionRect.set(left, top, right, bottom);
                invalidateContentRect();
                break;

            case SelectionEventType.SELECTION_HANDLES_CLEARED:
                mHasSelection = false;
                mUnselectAllOnDismiss = false;
                mSelectionRect.setEmpty();
                finishActionMode();
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STARTED:
                hideActionMode(true);
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STOPPED:
                hideActionMode(false);
                break;

            case SelectionEventType.INSERTION_HANDLE_SHOWN:
                mSelectionRect.set(left, top, right, bottom);
                setIsInsertion(true);
                break;

            case SelectionEventType.INSERTION_HANDLE_MOVED:
                mSelectionRect.set(left, top, right, bottom);
                if (!isScrollInProgress && isPastePopupShowing()) {
                    showPastePopup(xAnchor, yAnchor);
                } else {
                    hidePastePopup();
                }
                break;

            case SelectionEventType.INSERTION_HANDLE_TAPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    hidePastePopup();
                } else {
                    showPastePopup(xAnchor, yAnchor);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            case SelectionEventType.INSERTION_HANDLE_CLEARED:
                hidePastePopup();
                setIsInsertion(false);
                mSelectionRect.setEmpty();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STARTED:
                mWasPastePopupShowingOnInsertionDragStart = isPastePopupShowing();
                hidePastePopup();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STOPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    showPastePopup(xAnchor, yAnchor);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            default:
                assert false : "Invalid selection event type.";
        }

        if (mContextualSearchClient != null) {
            final float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
            int xAnchorPix = (int) (xAnchor * deviceScale);
            int yAnchorPix = (int) (yAnchor * deviceScale);
            mContextualSearchClient.onSelectionEvent(eventType, xAnchorPix, yAnchorPix);
        }
    }

    /**
     * Clears the current text selection. Note that we will try to move cursor to selection
     * end if applicable.
     */
    void clearSelection() {
        if (mWebContents == null || isEmpty()) return;
        mWebContents.collapseSelection();
    }

    void onSelectionChanged(String text) {
        mLastSelectedText = text;
        if (mContextualSearchClient != null) {
            mContextualSearchClient.onSelectionChanged(text);
        }
    }

    // The client that implements Contextual Search functionality, or null if none exists.
    void setContextualSearchClient(ContextualSearchClient contextualSearchClient) {
        mContextualSearchClient = contextualSearchClient;
    }

    void onShowUnhandledTapUIIfNeeded(int x, int y) {
        if (mContextualSearchClient != null) {
            mContextualSearchClient.showUnhandledTapUIIfNeeded(x, y);
        }
    }

    void destroyActionModeAndUnselect() {
        mUnselectAllOnDismiss = true;
        finishActionMode();
    }

    void destroyActionModeAndKeepSelection() {
        mUnselectAllOnDismiss = false;
        finishActionMode();
    }

    void updateSelectionState(boolean editable, boolean isPassword) {
        if (!editable) hidePastePopup();
        if (editable != isSelectionEditable() || isPassword != isSelectionPassword()) {
            mEditable = editable;
            mIsPasswordType = isPassword;
            if (isActionModeValid()) {
                mNeedsPrepare = true;
                mActionMode.invalidate();
            }
        }
    }

    /**
     * @return Whether the page has an active, touch-controlled selection region.
     */
    @VisibleForTesting
    public boolean hasSelection() {
        return mHasSelection;
    }

    @Override
    public String getSelectedText() {
        return mHasSelection ? mLastSelectedText : "";
    }

    private void setIsInsertion(boolean insertion) {
        if (isActionModeValid() && mIsInsertion != insertion) mNeedsPrepare = true;
        mIsInsertion = insertion;
    }

    private boolean isShareAvailable() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        return mContext.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }
}
