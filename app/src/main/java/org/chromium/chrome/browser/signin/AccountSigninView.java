// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.StringRes;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.AccountTrackerService.OnSystemAccountsSeededListener;
import org.chromium.chrome.browser.signin.ConfirmImportSyncDataDialog.ImportSyncType;
import org.chromium.components.signin.AccountManagerDelegateException;
import org.chromium.components.signin.AccountManagerFacade;
import org.chromium.components.signin.AccountManagerResult;
import org.chromium.components.signin.AccountsChangeObserver;
import org.chromium.components.signin.GmsAvailabilityException;
import org.chromium.components.signin.GmsJustUpdatedException;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;
import org.chromium.ui.widget.ButtonCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This view allows the user to select an account to log in to, add an account, cancel account
 * selection, etc. Users of this class should call {@link #initFromSelectionPage} or
 * {@link #initFromConfirmationPage} after the view has been inflated.
 */
public class AccountSigninView extends FrameLayout {
    /**
     * Callbacks for various account selection events.
     */
    public interface Listener {
        /**
         * The user canceled account selection.
         */
        void onAccountSelectionCanceled();

        /**
         * The user wants to make a new account.
         */
        void onNewAccount();

        /**
         * The user completed the View and selected an account.
         * @param accountName The name of the account
         * @param isDefaultAccount Whether selected account is a default one (first of all accounts)
         * @param settingsClicked If true, user requested to see their sync settings, if false
         *                        they just clicked Done.
         */
        void onAccountSelected(
                String accountName, boolean isDefaultAccount, boolean settingsClicked);

        /**
         * Failed to set the forced account because it wasn't found.
         * @param forcedAccountName The name of the forced-sign-in account
         */
        void onFailedToSetForcedAccount(String forcedAccountName);
    }

    /**
     * Provides UI objects for new UI component creation.
     */
    public interface Delegate {
        /**
         * Provides an Activity for the View to check GMSCore version.
         */
        Activity getActivity();

        /**
         * Provides a FragmentManager for the View to create dialogs. This is done through a
         * different mechanism than getActivity().getFragmentManager() as a potential fix to
         * https://crbug.com/646978 on the theory that getActivity() and getFragmentManager()
         * return null at different times.
         */
        FragmentManager getFragmentManager();
    }

    private static final String TAG = "AccountSigninView";

    private static final String SETTINGS_LINK_OPEN = "<LINK1>";
    private static final String SETTINGS_LINK_CLOSE = "</LINK1>";

    /** Specifies different behaviors for "Undo" button on signin confirmation page. */
    @IntDef({UNDO_INVISIBLE, UNDO_BACK_TO_SELECTION, UNDO_ABORT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UndoBehavior {}

    /** "Undo" button is invisible. */
    public static final int UNDO_INVISIBLE = 0;
    /** "Undo" button opens account selection page. */
    public static final int UNDO_BACK_TO_SELECTION = 1;
    /** "Undo" button calls {@link Listener#onAccountSelectionCanceled()}. */
    public static final int UNDO_ABORT = 2;

    private final AccountsChangeObserver mAccountsChangedObserver;
    private final ProfileDataCache.Observer mProfileDataCacheObserver;
    private final ProfileDataCache mProfileDataCache;
    private List<String> mAccountNames;
    private AccountSigninChooseView mSigninChooseView;
    private ButtonCompat mPositiveButton;
    private Button mNegativeButton;
    private Button mMoreButton;
    private Listener mListener;
    private Delegate mDelegate;
    private @UndoBehavior int mUndoBehavior;
    private String mSelectedAccountName;
    private boolean mIsDefaultAccountSelected;
    private @StringRes int mCancelButtonTextId;
    private boolean mIsChildAccount;
    private UserRecoverableErrorHandler.ModalDialog mGooglePlayServicesUpdateErrorHandler;
    private AlertDialog mGmsIsUpdatingDialog;
    private long mGmsIsUpdatingDialogShowTime;

    private AccountSigninConfirmationView mSigninConfirmationView;
    private ImageView mSigninAccountImage;
    private TextView mSigninAccountName;
    private TextView mSigninAccountEmail;
    private TextView mSigninPersonalizeServiceDescription;
    private TextView mSigninSettingsControl;
    private ConfirmSyncDataStateMachine mConfirmSyncDataStateMachine;

    public AccountSigninView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccountsChangedObserver = this::triggerUpdateAccounts;
        mProfileDataCacheObserver = (String accountId) -> updateProfileData();
        mProfileDataCache = new ProfileDataCache(context, Profile.getLastUsedProfile(),
                context.getResources().getDimensionPixelSize(R.dimen.signin_account_image_size));

        mCancelButtonTextId = R.string.no_thanks;
    }

    /**
     * Initializes the view from account selection page. After selecting the account, signin
     * confirmation page will be opened.
     *
     * @param isChildAccount Whether this view is for a child account.
     * @param delegate The UI object creation delegate.
     * @param listener The account selection event listener.
     */
    public void initFromSelectionPage(
            boolean isChildAccount, Delegate delegate, Listener listener) {
        mIsChildAccount = isChildAccount;
        mUndoBehavior = UNDO_BACK_TO_SELECTION;
        mDelegate = delegate;
        mListener = listener;
        showSigninPage();
    }

    /**
     * Initializes the view from account selection page. After selecting the account, signin
     * confirmation page will be opened.
     *
     * @param delegate The UI object creation delegate.
     * @param listener The account selection event listener.
     */
    public void initFromAddAccountPage(Delegate delegate, Listener listener) {
        mIsChildAccount = false; // Children profiles can't add accounts.
        mUndoBehavior = UNDO_ABORT;
        mDelegate = delegate;
        mListener = listener;
        showSigninPage();

        RecordUserAction.record("Signin_AddAccountToDevice");
        mListener.onNewAccount();
    }

    /**
     * Initializes the view from signin confirmation page. The account name should be provided by
     * the caller.
     *
     * @param isChildAccount Whether this view is for a child account.
     * @param accountName An account that should be used for confirmation page and signin.
     * @param isDefaultAccount Whether {@param accountName} is a default account, used for metrics.
     * @param undoBehavior "Undo" button behavior (see {@link UndoBehavior}).
     * @param delegate The UI object creation delegate.
     * @param listener The account selection event listener.
     */
    public void initFromConfirmationPage(boolean isChildAccount, String accountName,
            boolean isDefaultAccount, @UndoBehavior int undoBehavior, Delegate delegate,
            Listener listener) {
        mIsChildAccount = isChildAccount;
        mUndoBehavior = undoBehavior;
        mDelegate = delegate;
        mListener = listener;
        showConfirmSigninPageAccountTrackerServiceCheck(accountName, isDefaultAccount);
        triggerUpdateAccounts();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSigninChooseView = (AccountSigninChooseView) findViewById(R.id.account_signin_choose_view);
        mSigninChooseView.setAddNewAccountObserver(() -> {
            mListener.onNewAccount();
            RecordUserAction.record("Signin_AddAccountToDevice");
        });

        mPositiveButton = (ButtonCompat) findViewById(R.id.positive_button);
        mNegativeButton = (Button) findViewById(R.id.negative_button);
        mMoreButton = (Button) findViewById(R.id.more_button);
        mSigninConfirmationView =
                (AccountSigninConfirmationView) findViewById(R.id.signin_confirmation_view);
        mSigninAccountImage = (ImageView) findViewById(R.id.signin_account_image);
        mSigninAccountName = (TextView) findViewById(R.id.signin_account_name);
        mSigninAccountEmail = (TextView) findViewById(R.id.signin_account_email);
        mSigninPersonalizeServiceDescription =
                (TextView) findViewById(R.id.signin_personalize_service_description);
        mSigninSettingsControl = (TextView) findViewById(R.id.signin_settings_control);
        // For the spans to be clickable.
        mSigninSettingsControl.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        triggerUpdateAccounts();
        AccountManagerFacade.get().addObserver(mAccountsChangedObserver);
        mProfileDataCache.addObserver(mProfileDataCacheObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mConfirmSyncDataStateMachine != null) {
            mConfirmSyncDataStateMachine.cancel(false /* dismissDialogs */);
            mConfirmSyncDataStateMachine = null;
        }
        mProfileDataCache.removeObserver(mProfileDataCacheObserver);
        AccountManagerFacade.get().removeObserver(mAccountsChangedObserver);
        super.onDetachedFromWindow();
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            triggerUpdateAccounts();
            return;
        }
        if (visibility == View.INVISIBLE && mGooglePlayServicesUpdateErrorHandler != null) {
            mGooglePlayServicesUpdateErrorHandler.cancelDialog();
            mGooglePlayServicesUpdateErrorHandler = null;
        }
    }

    /**
     * Changes the visuals slightly for when this view appears in the recent tabs page instead of
     * in first run.
     * This is currently used when signing in from the Recent Tabs or Bookmarks pages.
     */
    public void configureForRecentTabsOrBookmarksPage() {
        mCancelButtonTextId = R.string.cancel;
        setUpCancelButton();
    }

    private void setButtonsEnabled(boolean enabled) {
        mPositiveButton.setEnabled(enabled);
        mNegativeButton.setEnabled(enabled);
    }

    /**
     * Refresh the list of available system accounts asynchronously.
     */
    private void triggerUpdateAccounts() {
        AccountManagerFacade.get().getGoogleAccountNames(this::updateAccounts);
    }

    private void updateAccounts(AccountManagerResult<List<String>> result) {
        if (!ViewCompat.isAttachedToWindow(AccountSigninView.this)) {
            // This callback is invoked after AccountSigninView is detached from window
            // (e.g., Chrome is minimized). Updating view now is redundant and dangerous
            // (getFragmentManager() can return null, etc.). See https://crbug.com/733117.
            return;
        }

        final List<String> accountNames;
        try {
            accountNames = result.get();
        } catch (GmsAvailabilityException e) {
            dismissGmsUpdatingDialog();
            if (e.isUserResolvableError()) {
                showGmsErrorDialog(e.getGmsAvailabilityReturnCode());
            } else {
                Log.e(TAG, "Unresolvable GmsAvailabilityException.", e);
            }
            return;
        } catch (GmsJustUpdatedException e) {
            dismissGmsErrorDialog();
            showGmsUpdatingDialog();
            return;
        } catch (AccountManagerDelegateException e) {
            Log.e(TAG, "Unknown exception from AccountManagerFacade.", e);
            dismissGmsErrorDialog();
            dismissGmsUpdatingDialog();
            return;
        }
        dismissGmsErrorDialog();
        dismissGmsUpdatingDialog();

        if (mSelectedAccountName != null) {
            if (accountNames.contains(mSelectedAccountName)) return;

            if (mUndoBehavior == UNDO_BACK_TO_SELECTION) {
                RecordUserAction.record("Signin_Undo_Signin");
                showSigninPage();
            } else {
                mListener.onFailedToSetForcedAccount(mSelectedAccountName);
            }
            return;
        }

        List<String> oldAccountNames = mAccountNames;
        mAccountNames = accountNames;

        int oldSelectedAccount = mSigninChooseView.getSelectedAccountPosition();
        AccountSelectionResult selection = selectAccountAfterAccountsUpdate(
                oldAccountNames, mAccountNames, oldSelectedAccount);
        int accountToSelect = selection.getSelectedAccountIndex();
        boolean shouldJumpToConfirmationScreen = selection.shouldJumpToConfirmationScreen();

        mSigninChooseView.updateAccounts(mAccountNames, accountToSelect, mProfileDataCache);
        setUpSigninButton(!mAccountNames.isEmpty());
        mProfileDataCache.update(mAccountNames);

        boolean selectedAccountChanged = oldAccountNames != null && !oldAccountNames.isEmpty()
                && (mAccountNames.isEmpty()
                           || !mAccountNames.get(accountToSelect)
                                       .equals(oldAccountNames.get(oldSelectedAccount)));
        if (selectedAccountChanged && mConfirmSyncDataStateMachine != null) {
            // Any dialogs that may have been showing are now invalid (they were created
            // for the previously selected account).
            mConfirmSyncDataStateMachine.cancel(true /* dismissDialogs */);
            mConfirmSyncDataStateMachine = null;
        }

        if (shouldJumpToConfirmationScreen) {
            showConfirmSigninPageAccountTrackerServiceCheck();
        }
    }

    private boolean hasGmsError() {
        return mGooglePlayServicesUpdateErrorHandler != null || mGmsIsUpdatingDialog != null;
    }

    private void showGmsErrorDialog(int gmsErrorCode) {
        if (mGooglePlayServicesUpdateErrorHandler != null
                && mGooglePlayServicesUpdateErrorHandler.isShowing()) {
            return;
        }
        boolean cancelable = !SigninManager.get(getContext()).isForceSigninEnabled();
        mGooglePlayServicesUpdateErrorHandler =
                new UserRecoverableErrorHandler.ModalDialog(mDelegate.getActivity(), cancelable);
        mGooglePlayServicesUpdateErrorHandler.handleError(getContext(), gmsErrorCode);
    }

    private void showGmsUpdatingDialog() {
        if (mGmsIsUpdatingDialog != null) {
            return;
        }
        mGmsIsUpdatingDialog = new AlertDialog.Builder(getContext())
                .setCancelable(false)
                .setView(R.layout.updating_gms_progress_view)
                .create();
        mGmsIsUpdatingDialog.show();
        mGmsIsUpdatingDialogShowTime = SystemClock.elapsedRealtime();
    }

    private void dismissGmsErrorDialog() {
        if (mGooglePlayServicesUpdateErrorHandler == null) {
            return;
        }
        mGooglePlayServicesUpdateErrorHandler.cancelDialog();
        mGooglePlayServicesUpdateErrorHandler = null;
    }

    private void dismissGmsUpdatingDialog() {
        if (mGmsIsUpdatingDialog == null) {
            return;
        }
        mGmsIsUpdatingDialog.dismiss();
        mGmsIsUpdatingDialog = null;
        RecordHistogram.recordTimesHistogram("Signin.AndroidGmsUpdatingDialogShownTime",
                SystemClock.elapsedRealtime() - mGmsIsUpdatingDialogShowTime,
                TimeUnit.MILLISECONDS);
    }

    private static class AccountSelectionResult {
        private final int mSelectedAccountIndex;
        private final boolean mShouldJumpToConfirmationScreen;

        AccountSelectionResult(int selectedAccountIndex, boolean shouldJumpToConfirmationScreen) {
            mSelectedAccountIndex = selectedAccountIndex;
            mShouldJumpToConfirmationScreen = shouldJumpToConfirmationScreen;
        }

        int getSelectedAccountIndex() {
            return mSelectedAccountIndex;
        }

        boolean shouldJumpToConfirmationScreen() {
            return mShouldJumpToConfirmationScreen;
        }
    }

    /**
     * Determine what account should be selected after account list update. This function also
     * decides whether AccountSigninView should jump to confirmation screen.
     *
     * @param oldList Old list of user accounts.
     * @param newList New list of user accounts.
     * @param oldIndex Index of the selected account in the old list.
     * @return {@link AccountSelectionResult} that encapsulates new index and jump/no jump flag.
     */
    private static AccountSelectionResult selectAccountAfterAccountsUpdate(
            List<String> oldList, List<String> newList, int oldIndex) {
        if (oldList == null || newList == null) return new AccountSelectionResult(0, false);
        // Return the old index if nothing changed
        if (oldList.size() == newList.size() && oldList.containsAll(newList)) {
            return new AccountSelectionResult(oldIndex, false);
        }
        if (newList.containsAll(oldList)) {
            // A new account(s) has been added and no accounts have been deleted. Select new account
            // and jump to the confirmation screen if only one account was added.
            boolean shouldJumpToConfirmationScreen = newList.size() == oldList.size() + 1;
            for (int i = 0; i < newList.size(); i++) {
                if (!oldList.contains(newList.get(i))) {
                    return new AccountSelectionResult(i, shouldJumpToConfirmationScreen);
                }
            }
        }
        return new AccountSelectionResult(0, false);
    }

    private void updateProfileData() {
        mSigninChooseView.updateAccountProfileImages(mProfileDataCache);

        if (mSelectedAccountName != null) updateSignedInAccountInfo();
    }

    private void updateSignedInAccountInfo() {
        DisplayableProfileData profileData =
                mProfileDataCache.getProfileDataOrDefault(mSelectedAccountName);
        mSigninAccountImage.setImageDrawable(profileData.getImage());
        String name = null;
        if (mIsChildAccount) name = profileData.getGivenName();
        if (name == null) name = profileData.getFullNameOrEmail();
        mSigninAccountName.setText(getResources().getString(R.string.signin_hi_name, name));
        mSigninAccountEmail.setText(mSelectedAccountName);
    }

    private void showSigninPage() {
        mSelectedAccountName = null;

        mSigninConfirmationView.setVisibility(View.GONE);
        mSigninChooseView.setVisibility(View.VISIBLE);

        setUpCancelButton();
        triggerUpdateAccounts();
    }

    private void showConfirmSigninPage() {
        updateSignedInAccountInfo();
        mProfileDataCache.update(Collections.singletonList(mSelectedAccountName));

        mSigninChooseView.setVisibility(View.GONE);
        mSigninConfirmationView.setVisibility(View.VISIBLE);

        setButtonsEnabled(true);
        setUpConfirmButton();
        setUpUndoButton();

        NoUnderlineClickableSpan settingsSpan = new NoUnderlineClickableSpan() {
            @Override
            public void onClick(View widget) {
                mListener.onAccountSelected(mSelectedAccountName, mIsDefaultAccountSelected, true);
                RecordUserAction.record("Signin_Signin_WithAdvancedSyncSettings");
            }
        };
        if (mIsChildAccount) {
            mSigninPersonalizeServiceDescription.setText(
                    R.string.sync_confirmation_personalize_services_body_child_account);
        }
        mSigninSettingsControl.setText(
                SpanApplier.applySpans(getSettingsControlDescription(mIsChildAccount),
                        new SpanInfo(SETTINGS_LINK_OPEN, SETTINGS_LINK_CLOSE, settingsSpan)));
    }

    private void showConfirmSigninPageAccountTrackerServiceCheck() {
        int index = mSigninChooseView.getSelectedAccountPosition();
        showConfirmSigninPageAccountTrackerServiceCheck(mAccountNames.get(index), index == 0);
    }

    private void showConfirmSigninPageAccountTrackerServiceCheck(
            final String accountName, final boolean isDefaultAccount) {
        assert accountName != null;
        // Disable the buttons to prevent them being clicked again while waiting for the callbacks.
        setButtonsEnabled(false);

        mSelectedAccountName = accountName;
        mIsDefaultAccountSelected = isDefaultAccount;

        // Ensure that the AccountTrackerService has a fully up to date GAIA id <-> email mapping,
        // as this is needed for the previous account check.
        final long seedingStartTime = SystemClock.elapsedRealtime();
        if (AccountTrackerService.get().checkAndSeedSystemAccounts()) {
            showConfirmSigninPagePreviousAccountCheck(seedingStartTime);
        } else {
            AccountTrackerService.get().addSystemAccountsSeededListener(
                    new OnSystemAccountsSeededListener() {
                        @Override
                        public void onSystemAccountsSeedingComplete() {
                            AccountTrackerService.get().removeSystemAccountsSeededListener(this);
                            showConfirmSigninPagePreviousAccountCheck(seedingStartTime);
                        }

                        @Override
                        public void onSystemAccountsChanged() {}
                    });
        }
    }

    private void showConfirmSigninPagePreviousAccountCheck(long seedingStartTime) {
        RecordHistogram.recordTimesHistogram("Signin.AndroidAccountSigninViewSeedingTime",
                SystemClock.elapsedRealtime() - seedingStartTime, TimeUnit.MILLISECONDS);
        mConfirmSyncDataStateMachine = new ConfirmSyncDataStateMachine(getContext(),
                mDelegate.getFragmentManager(), ImportSyncType.PREVIOUS_DATA_FOUND,
                PrefServiceBridge.getInstance().getSyncLastAccountName(), mSelectedAccountName,
                new ConfirmImportSyncDataDialog.Listener() {
                    @Override
                    public void onConfirm(boolean wipeData) {
                        mConfirmSyncDataStateMachine = null;
                        SigninManager.wipeSyncUserDataIfRequired(wipeData).then(
                                (Void v) -> showConfirmSigninPage());
                    }

                    @Override
                    public void onCancel() {
                        mConfirmSyncDataStateMachine = null;
                        setButtonsEnabled(true);
                        onSigninConfirmationCancel();
                    }
                });
    }

    private void setUpCancelButton() {
        setNegativeButtonVisible(true);

        mNegativeButton.setText(mCancelButtonTextId);
        mNegativeButton.setOnClickListener(view -> {
            setButtonsEnabled(false);
            mListener.onAccountSelectionCanceled();
        });
    }

    private void setUpSigninButton(boolean hasAccounts) {
        if (hasAccounts) {
            mPositiveButton.setText(R.string.continue_sign_in);
            mPositiveButton.setOnClickListener(
                    view -> showConfirmSigninPageAccountTrackerServiceCheck());
        } else {
            mPositiveButton.setText(R.string.choose_account_sign_in);
            mPositiveButton.setOnClickListener(view -> {
                if (hasGmsError()) return;

                RecordUserAction.record("Signin_AddAccountToDevice");
                mListener.onNewAccount();
            });
        }
        setUpMoreButtonVisible(false);
    }

    private void setUpUndoButton() {
        if (mUndoBehavior == UNDO_INVISIBLE) {
            setNegativeButtonVisible(false);
            return;
        }
        setNegativeButtonVisible(true);
        mNegativeButton.setText(getResources().getText(R.string.undo));
        mNegativeButton.setOnClickListener(view -> {
            RecordUserAction.record("Signin_Undo_Signin");
            onSigninConfirmationCancel();
        });
    }

    private void onSigninConfirmationCancel() {
        if (mUndoBehavior == UNDO_BACK_TO_SELECTION) {
            showSigninPage();
        } else {
            assert mUndoBehavior == UNDO_ABORT;
            mListener.onAccountSelectionCanceled();
        }
    }

    private void setUpConfirmButton() {
        mPositiveButton.setText(R.string.signin_accept);
        mPositiveButton.setOnClickListener(view -> {
            mListener.onAccountSelected(mSelectedAccountName, mIsDefaultAccountSelected, false);
            RecordUserAction.record("Signin_Signin_WithDefaultSyncSettings");
        });
        setUpMoreButtonVisible(true);
    }

    /*
    * mMoreButton is used to scroll mSigninConfirmationView down. It displays at the same position
    * as mPositiveButton.
    */
    private void setUpMoreButtonVisible(boolean enabled) {
        if (enabled) {
            mPositiveButton.setVisibility(View.GONE);
            mMoreButton.setVisibility(View.VISIBLE);
            mMoreButton.setOnClickListener(view -> {
                mSigninConfirmationView.smoothScrollBy(0, mSigninConfirmationView.getHeight());
                RecordUserAction.record("Signin_MoreButton_Shown");
            });
            mSigninConfirmationView.setObserver(() -> setUpMoreButtonVisible(false));
        } else {
            mPositiveButton.setVisibility(View.VISIBLE);
            mMoreButton.setVisibility(View.GONE);
            mSigninConfirmationView.setObserver(null);
        }
    }

    private void setNegativeButtonVisible(boolean enabled) {
        if (enabled) {
            mNegativeButton.setVisibility(View.VISIBLE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.GONE);
        } else {
            mNegativeButton.setVisibility(View.GONE);
            findViewById(R.id.positive_button_end_padding).setVisibility(View.INVISIBLE);
        }
    }

    private String getSettingsControlDescription(boolean childAccount) {
        if (childAccount) {
            return getResources().getString(
                    R.string.signin_signed_in_settings_description_child_account);
        } else {
            return getResources().getString(R.string.signin_signed_in_settings_description);
        }
    }

    /**
     * @return Whether the view is in signed in mode.
     */
    public boolean isInConfirmationScreen() {
        return mSelectedAccountName != null;
    }
}
