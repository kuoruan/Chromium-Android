// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.password;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.preferences.ChromeBaseCheckBoxPreference;
import org.chromium.chrome.browser.preferences.ChromeBasePreference;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.TextMessagePreference;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The "Save passwords" screen in Settings, which allows the user to enable or disable password
 * saving, to view saved passwords (just the username and URL), and to delete saved passwords.
 */
public class SavePasswordsPreferences
        extends PreferenceFragment implements PasswordManagerHandler.PasswordListObserver,
                                              Preference.OnPreferenceClickListener {
    // ExportState describes at which state a password export is.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EXPORT_STATE_INACTIVE, EXPORT_STATE_REQUESTED, EXPORT_STATE_CONFIRMED})
    private @interface ExportState {}
    /**
     * EXPORT_STATE_INACTIVE: there is no currently running export. Either the user did not request
     * one, or the last one completed (i.e., a share intent picker or an error message were
     * displayed or the user cancelled it).
     */
    private static final int EXPORT_STATE_INACTIVE = 0;
    /**
     * EXPORT_STATE_REQUESTED: the user requested the export in the menu but did not authenticate
     * and confirm it yet.
     */
    private static final int EXPORT_STATE_REQUESTED = 1;
    /**
     * EXPORT_STATE_CONFIRMED: the user confirmed the export and Chrome is still busy preparing the
     * data for the share intent.
     */
    private static final int EXPORT_STATE_CONFIRMED = 2;

    // Keys for name/password dictionaries.
    public static final String PASSWORD_LIST_URL = "url";
    public static final String PASSWORD_LIST_NAME = "name";
    public static final String PASSWORD_LIST_PASSWORD = "password";

    // Used to pass the password id into a new activity.
    public static final String PASSWORD_LIST_ID = "id";

    // The key for saving |mSearchQuery| to instance bundle.
    private static final String SAVED_STATE_SEARCH_QUERY = "saved-state-search-query";

    // The key for saving |mExportState| to instance bundle.
    private static final String SAVED_STATE_EXPORT_STATE = "saved-state-export-state";

    // The key for saving |mEntriesCount| to instance bundle.
    private static final String SAVED_STATE_ENTRIES_COUNT = "saved-state-entries-count";

    // The key for saving |mExportFileUri| to instance bundle.
    private static final String SAVED_STATE_EXPORT_FILE_URI = "saved-state-export-file-uri";

    public static final String PREF_SAVE_PASSWORDS_SWITCH = "save_passwords_switch";
    public static final String PREF_AUTOSIGNIN_SWITCH = "autosignin_switch";

    // A PasswordEntryEditor receives a boolean value with this key. If set true, the the entry was
    // part of a search result.
    public static final String EXTRA_FOUND_VIA_SEARCH = "found_via_search_args";

    private static final String PREF_KEY_CATEGORY_SAVED_PASSWORDS = "saved_passwords";
    private static final String PREF_KEY_CATEGORY_EXCEPTIONS = "exceptions";
    private static final String PREF_KEY_MANAGE_ACCOUNT_LINK = "manage_account_link";
    private static final String PREF_KEY_SAVED_PASSWORDS_NO_TEXT = "saved_passwords_no_text";

    // Name of the feature controlling the password export functionality.
    private static final String EXPORT_PASSWORDS = "PasswordExport";

    // Name of the subdirectory in cache which stores the exported passwords file.
    private static final String PASSWORDS_CACHE_DIR = "/passwords";

    // Name of the histogram that records whether the user visiting the settings triggered the
    // search.
    private static final String HISTOGRAM_SEARCH_TRIGGERED =
            "PasswordManager.Android.PasswordSearchTriggered";

    // Potential values of the histogram recording the result of exporting. This needs to match
    // ExportPasswordsResult from
    // //components/password_manager/core/browser/password_manager_metrics_util.h.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EXPORT_RESULT_SUCCESS, EXPORT_RESULT_USER_ABORTED, EXPORT_RESULT_WRITE_FAILED,
            EXPORT_RESULT_NO_CONSUMER})
    @VisibleForTesting
    public @interface HistogramExportResult {}
    @VisibleForTesting
    public static final int EXPORT_RESULT_SUCCESS = 0;
    @VisibleForTesting
    public static final int EXPORT_RESULT_USER_ABORTED = 1;
    @VisibleForTesting
    public static final int EXPORT_RESULT_WRITE_FAILED = 2;
    @VisibleForTesting
    public static final int EXPORT_RESULT_NO_CONSUMER = 3;
    // If you add new values to HistogramExportResult, also update EXPORT_RESULT_COUNT to match its
    // new size.
    private static final int EXPORT_RESULT_COUNT = 4;

    private static final int ORDER_SWITCH = 0;
    private static final int ORDER_AUTO_SIGNIN_CHECKBOX = 1;
    private static final int ORDER_MANAGE_ACCOUNT_LINK = 2;
    private static final int ORDER_SAVED_PASSWORDS = 3;
    private static final int ORDER_EXCEPTIONS = 4;
    private static final int ORDER_SAVED_PASSWORDS_NO_TEXT = 5;

    private boolean mNoPasswords;
    private boolean mNoPasswordExceptions;

    @ExportState
    private int mExportState;

    // When the user requests that passwords are exported and once the passwords are sent over from
    // native code and stored in a cache file, this variable contains the content:// URI for that
    // cache file, or an empty URI if there was a problem with storing to that file. During all
    // other times, this variable is null. In particular, after the export is requested, the
    // variable being null means that the passwords have not arrived from the native code yet.
    @Nullable
    private Uri mExportFileUri;

    // Just before the exporting flow requests the passwords to be serialized by the native code,
    // this timestamp is assigned the result of System.currentTimeMillis().
    private Long mExportPreparationStart;

    // The number of password entries contained in the most recent serialized data for password
    // export.
    private int mEntriesCount;

    private MenuItem mHelpItem;

    private String mSearchQuery;
    private Preference mLinkPref;
    private ChromeSwitchPreference mSavePasswordsSwitch;
    private ChromeBaseCheckBoxPreference mAutoSignInSwitch;
    private TextMessagePreference mEmptyView;
    private boolean mSearchRecorded;
    private Menu mMenuForTesting;

    // Contains the reference to the progress-bar dialog after the user confirms the password
    // export and before the serialized passwords arrive, so that the dialog can be dismissed on the
    // passwords' arrival. It is null during all other times.
    @Nullable
    private ProgressBarDialogFragment mProgressBarDialogFragment;

    // If an error dialog should be shown, this contains the arguments for it, such as the error
    // message. If no error dialog should be shown, this is null.
    @Nullable
    private ExportErrorDialogFragment.ErrorDialogParams mErrorDialogParams;

    // Contains the reference to the export warning dialog when it is displayed, so that the dialog
    // can be dismissed if Chrome goes to background (without being killed) and is restored too late
    // for the reauthentication time window to still allow exporting. It is null during all other
    // times.
    private ExportWarningDialogFragment mExportWarningDialogFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_saved_passwords_title);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
        PasswordManagerHandlerProvider.getInstance().addObserver(this);

        setHasOptionsMenu(providesPasswordExport() || providesPasswordSearch());

        if (savedInstanceState == null) return;

        if (savedInstanceState.containsKey(SAVED_STATE_EXPORT_STATE)) {
            mExportState = savedInstanceState.getInt(SAVED_STATE_EXPORT_STATE);
            if (mExportState == EXPORT_STATE_CONFIRMED) {
                // If export is underway, ensure that the UI is updated.
                tryExporting();
            }
        }
        if (savedInstanceState.containsKey(SAVED_STATE_EXPORT_FILE_URI)) {
            String uriString = savedInstanceState.getString(SAVED_STATE_EXPORT_FILE_URI);
            if (uriString.isEmpty()) {
                mExportFileUri = Uri.EMPTY;
            } else {
                mExportFileUri = Uri.parse(uriString);
            }
        }
        if (savedInstanceState.containsKey(SAVED_STATE_SEARCH_QUERY)) {
            mSearchQuery = savedInstanceState.getString(SAVED_STATE_SEARCH_QUERY);
            mSearchRecorded = mSearchQuery != null; // We record a search when a query is set.
        }
        mEntriesCount = savedInstanceState.getInt(SAVED_STATE_ENTRIES_COUNT);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        mMenuForTesting = menu;
        inflater.inflate(R.menu.save_password_preferences_action_bar_menu, menu);
        menu.findItem(R.id.export_passwords).setVisible(providesPasswordExport());
        menu.findItem(R.id.export_passwords).setEnabled(false);
        MenuItem searchItem = menu.findItem(R.id.menu_id_search);
        searchItem.setVisible(providesPasswordSearch());
        if (providesPasswordSearch()) {
            mHelpItem = menu.findItem(R.id.menu_id_general_help);
            setUpSearchAction(searchItem);
        }
    }

    /**
     * Prepares the searchItem's icon and searchView. Sets up listeners to clicks and interactions
     * with the searchItem or its searchView.
     * @param searchItem the item containing the SearchView. Must not be null.
     */
    private void setUpSearchAction(MenuItem searchItem) {
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        searchItem.setIcon(convertToPlainWhite(searchItem.getIcon()));
        if (mSearchQuery != null) { // If a query was recovered, restore the search view.
            searchItem.expandActionView();
            searchView.setIconified(false);
            searchView.setQuery(mSearchQuery, false);
        }
        searchItem.setOnMenuItemClickListener((MenuItem m) -> {
            filterPasswords("");
            return false; // Continue with the default action.
        });
        searchView.findViewById(R.id.search_close_btn).setOnClickListener((View v) -> {
            searchView.setQuery(null, false);
            searchView.setIconified(true);
            filterPasswords(null); // Reset filter to bring back all preferences.
        });
        searchView.setOnSearchClickListener(view -> filterPasswords(""));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true; // Continue with default action - nothing.
            }

            @Override
            public boolean onQueryTextChange(String query) {
                maybeRecordTriggeredPasswordSearch(true);
                return filterPasswords(query);
            }
        });
    }

    /**
     * Record the search only, if the feature is enabled and it hasn't been recorded for this
     * instance of the view.
     * @param searchTriggered Whether to log a triggered search or no triggered search.
     */
    private void maybeRecordTriggeredPasswordSearch(boolean searchTriggered) {
        if (providesPasswordSearch() && !mSearchRecorded) {
            mSearchRecorded = true;
            RecordHistogram.recordBooleanHistogram(HISTOGRAM_SEARCH_TRIGGERED, searchTriggered);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.export_passwords)
                .setEnabled(!mNoPasswords && mExportState == EXPORT_STATE_INACTIVE);
        super.onPrepareOptionsMenu(menu);
    }

    // An encapsulation of a URI and an error string, used by the processing in
    // exportPasswordsIntoFile.
    private static class ExportResult {
        public final Uri mUri;
        @Nullable
        public final String mError;

        // Constructs the successful result: a valid URI and no error.
        public ExportResult(Uri uri) {
            assert uri != null && uri != Uri.EMPTY;
            mUri = uri;
            mError = null;
        }

        // Constructs the failed result: an empty URI and a non-empty error string.
        public ExportResult(String error) {
            assert !TextUtils.isEmpty(error);
            mUri = Uri.EMPTY;
            mError = error;
        }
    }

    /**
     * A helper method which first fires an AsyncTask to turn the string with serialized passwords
     * into a cache file with a shareable URI, and then, depending on success, either calls the code
     * for firing the share intent or displays an error.
     * @param serializedPasswords A string with a CSV representation of the user's passwords.
     */
    private void shareSerializedPasswords(byte[] serializedPasswords) {
        AsyncTask<byte[], Void, ExportResult> task = new AsyncTask<byte[], Void, ExportResult>() {
            @Override
            protected ExportResult doInBackground(byte[]... serializedPasswords) {
                assert serializedPasswords.length == 1;
                // Record the time it took to read and serialise the passwords. This excludes the
                // time to write them into a file, to be consistent with desktop (where writing is
                // blocked on the user choosing a file destination).
                RecordHistogram.recordMediumTimesHistogram(
                        "PasswordManager.TimeReadingExportedPasswords",
                        System.currentTimeMillis() - mExportPreparationStart,
                        TimeUnit.MILLISECONDS);
                return exportPasswordsIntoFile(serializedPasswords[0]);
            }

            @Override
            protected void onPostExecute(ExportResult result) {
                // Don't display any UI if the user cancelled the export in the meantime.
                if (mExportState == EXPORT_STATE_INACTIVE) return;

                if (result.mError != null) {
                    showExportErrorAndAbort(R.string.save_password_preferences_export_tips,
                            result.mError, R.string.try_again, EXPORT_RESULT_WRITE_FAILED);
                } else {
                    mExportFileUri = result.mUri;
                    tryExporting();
                }
            }
        };
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serializedPasswords);
    }

    /** Starts the password export flow.
     * Current state of export flow: the user just tapped the menu item for export
     * The next steps are: passing reauthentication, confirming the export, waiting for exported
     * data (if needed) and choosing a consumer app for the data.
     */
    private void startExporting() {
        assert mExportState == EXPORT_STATE_INACTIVE;
        // Disable re-triggering exporting until the current exporting finishes.
        mExportState = EXPORT_STATE_REQUESTED;

        // Start fetching the serialized passwords now to use the time the user spends
        // reauthenticating and reading the warning message. If the user cancels the export or
        // fails the reauthentication, the serialised passwords will simply get ignored when
        // they arrive.
        mExportPreparationStart = System.currentTimeMillis();
        PasswordManagerHandlerProvider.getInstance().getPasswordManagerHandler().serializePasswords(
                new ByteArrayIntCallback() {
                    @Override
                    public void onResult(byte[] byteArray, int number) {
                        mEntriesCount = number;
                        shareSerializedPasswords(byteArray);
                    }
                });
        if (!ReauthenticationManager.isScreenLockSetUp(getActivity().getApplicationContext())) {
            Toast.makeText(getActivity().getApplicationContext(),
                         R.string.password_export_set_lock_screen, Toast.LENGTH_LONG)
                    .show();
            // Re-enable exporting, the current one was cancelled by Chrome.
            mExportState = EXPORT_STATE_INACTIVE;
        } else {
            // Always trigger reauthentication at the start of the exporting flow, even if the last
            // one succeeded recently.
            ReauthenticationManager.displayReauthenticationFragment(
                    R.string.lockscreen_description_export, getView().getId(), getFragmentManager(),
                    ReauthenticationManager.REAUTH_SCOPE_BULK);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.export_passwords) {
            startExporting();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Continues with the password export flow after the user successfully reauthenticated.
     * Current state of export flow: the user tapped the menu item for export and passed
     * reauthentication. The next steps are: confirming the export, waiting for exported data (if
     * needed) and choosing a consumer app for the data.
     */
    private void exportAfterReauth() {
        assert mExportWarningDialogFragment == null;
        mExportWarningDialogFragment = new ExportWarningDialogFragment();
        mExportWarningDialogFragment.setExportWarningHandler(
                new ExportWarningDialogFragment.Handler() {
                    /**
                     * On positive button response asks the parent to continue with the export flow.
                     */
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            mExportState = EXPORT_STATE_CONFIRMED;
                            // If the error dialog has been waiting, display it now, otherwise
                            // continue the export flow.
                            if (mErrorDialogParams != null) {
                                showExportErrorDialogFragment();
                            } else {
                                tryExporting();
                            }
                        } else if (which == AlertDialog.BUTTON_NEGATIVE) {
                            RecordHistogram.recordEnumeratedHistogram(
                                    "PasswordManager.ExportPasswordsToCSVResult",
                                    EXPORT_RESULT_USER_ABORTED, EXPORT_RESULT_COUNT);
                        }
                    }

                    /**
                     * Mark the dismissal of the dialog, so that waiting UI (such as error
                     * reporting) can be shown.
                     */
                    @Override
                    public void onDismiss() {
                        // Unless the positive button action moved the exporting state forward,
                        // cancel the export. This happens both when the user taps the negative
                        // button or when they tap outside of the dialog to dismiss it.
                        if (mExportState != EXPORT_STATE_CONFIRMED) {
                            mExportState = EXPORT_STATE_INACTIVE;
                        }

                        mExportWarningDialogFragment = null;
                        // If the error dialog has been waiting, display it now.
                        if (mErrorDialogParams != null) showExportErrorDialogFragment();
                    }
                });
        mExportWarningDialogFragment.show(getFragmentManager(), null);
    }

    /**
     * Starts the exporting intent if both blocking events are completed: serializing and the
     * confirmation flow.
     * At this point, the user the user has tapped the menu item for export and passed
     * reauthentication. Upon calling this method, the user has either also confirmed the export, or
     * the exported data have been prepared. The method is called twice, once for each of those
     * events. The next step after both the export is confirmed and the data is ready is to offer
     * the user an intent chooser for sharing the exported passwords.
     */
    private void tryExporting() {
        if (mExportState != EXPORT_STATE_CONFIRMED) return;
        if (mExportFileUri == null) {
            // The serialization has not finished. Until this finishes, a progress bar is
            // displayed with an option to cancel the export.
            assert mProgressBarDialogFragment == null;
            mProgressBarDialogFragment = new ProgressBarDialogFragment();
            mProgressBarDialogFragment.setCancelProgressHandler(
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == AlertDialog.BUTTON_NEGATIVE) {
                                mExportState = EXPORT_STATE_INACTIVE;
                                RecordHistogram.recordEnumeratedHistogram(
                                        "PasswordManager.ExportPasswordsToCSVResult",
                                        EXPORT_RESULT_USER_ABORTED, EXPORT_RESULT_COUNT);
                            }
                        }
                    });
            mProgressBarDialogFragment.show(getFragmentManager(), null);
        } else {
            // Note: if the serialization is quicker than the user interacting with the
            // confirmation dialog, then there is no progress bar shown.
            if (mProgressBarDialogFragment != null) mProgressBarDialogFragment.dismiss();
            sendExportIntent();
        }
    }

    /**
     * Call this to abort the export UI flow and display an error description to the user.
     * @param descriptionId The resource ID of a string with a brief explanation of the error.
     * @param detailedDescription An optional string with more technical details about the error.
     * @param positiveButtonLabelId The resource ID of the label of the positive button in the error
     * dialog.
     */
    @VisibleForTesting
    public void showExportErrorAndAbort(int descriptionId, @Nullable String detailedDescription,
            int positiveButtonLabelId, @HistogramExportResult int histogramExportResult) {
        assert mErrorDialogParams == null;
        if (mProgressBarDialogFragment != null) mProgressBarDialogFragment.dismiss();

        RecordHistogram.recordEnumeratedHistogram("PasswordManager.ExportPasswordsToCSVResult",
                histogramExportResult, EXPORT_RESULT_COUNT);

        mErrorDialogParams = new ExportErrorDialogFragment.ErrorDialogParams();
        mErrorDialogParams.positiveButtonLabelId = positiveButtonLabelId;
        mErrorDialogParams.description = getActivity().getResources().getString(descriptionId);

        if (detailedDescription != null) {
            mErrorDialogParams.detailedDescription = getActivity().getResources().getString(
                    R.string.save_password_preferences_export_error_details, detailedDescription);
        }

        if (mExportWarningDialogFragment == null) showExportErrorDialogFragment();
    }

    /**
     * This is a helper method to {@link showExportErrorAndAbort}, responsible for showing the
     * actual UI.*/
    private void showExportErrorDialogFragment() {
        assert mErrorDialogParams != null;

        ExportErrorDialogFragment exportErrorDialogFragment = new ExportErrorDialogFragment();
        int positiveButtonLabelId = mErrorDialogParams.positiveButtonLabelId;
        exportErrorDialogFragment.initialize(mErrorDialogParams);
        mErrorDialogParams = null;

        exportErrorDialogFragment.setExportErrorHandler(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    if (positiveButtonLabelId
                            == R.string.save_password_preferences_export_learn_google_drive) {
                        // Link to the help article about how to use Google Drive.
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://support.google.com/drive/answer/2424384"));
                        intent.setPackage(getActivity().getPackageName());
                        getActivity().startActivity(intent);
                    } else if (positiveButtonLabelId == R.string.try_again) {
                        mExportState = EXPORT_STATE_REQUESTED;
                        exportAfterReauth();
                    }
                } else if (which == AlertDialog.BUTTON_NEGATIVE) {
                    // Re-enable exporting, the current one was just cancelled.
                    mExportState = EXPORT_STATE_INACTIVE;
                }
            }
        });
        exportErrorDialogFragment.show(getFragmentManager(), null);
    }

    /**
     * This method saves the contents of |serializedPasswords| into a temporary file and returns a
     * sharing URI for it. In case of failure, returns EMPTY. It should only be run on the
     * background thread of an AsyncTask, because it does I/O operations.
     * @param serializedPasswords A byte array with serialized passwords in CSV format
     */
    private ExportResult exportPasswordsIntoFile(byte[] serializedPasswords) {
        // First ensure that the PASSWORDS_CACHE_DIR cache directory exists.
        File passwordsDir =
                new File(ContextUtils.getApplicationContext().getCacheDir() + PASSWORDS_CACHE_DIR);
        passwordsDir.mkdir();
        // Now create or overwrite the temporary file for exported passwords there and return its
        // content:// URI.
        File tempFile;
        try {
            tempFile = File.createTempFile("pwd-export", ".csv", passwordsDir);
        } catch (IOException e) {
            return new ExportResult(e.getMessage());
        }
        tempFile.deleteOnExit();
        try (BufferedOutputStream outputStream =
                        new BufferedOutputStream(new FileOutputStream(tempFile))) {
            outputStream.write(serializedPasswords);
        } catch (IOException e) {
            return new ExportResult(e.getMessage());
        }
        try {
            return new ExportResult(ContentUriUtils.getContentUriFromFile(tempFile));
        } catch (IllegalArgumentException e) {
            return new ExportResult(e.getMessage());
        }
    }

    /**
     * If the URI of the file with exported passwords is not null, passes it into an implicit
     * intent, so that the user can use a storage app to save the exported passwords.
     */
    private void sendExportIntent() {
        assert mExportState == EXPORT_STATE_CONFIRMED;
        mExportState = EXPORT_STATE_INACTIVE;

        if (mExportFileUri == Uri.EMPTY) return;

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, mExportFileUri);
        send.putExtra(
                Intent.EXTRA_SUBJECT, getString(R.string.save_password_preferences_export_subject));

        try {
            Intent chooser = Intent.createChooser(send, null);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ContextUtils.getApplicationContext().startActivity(chooser);
            RecordHistogram.recordEnumeratedHistogram("PasswordManager.ExportPasswordsToCSVResult",
                    EXPORT_RESULT_SUCCESS, EXPORT_RESULT_COUNT);
            RecordHistogram.recordCountHistogram(
                    "PasswordManager.ExportedPasswordsPerUserInCSV", mEntriesCount);
        } catch (ActivityNotFoundException e) {
            showExportErrorAndAbort(R.string.save_password_preferences_export_no_app, null,
                    R.string.save_password_preferences_export_learn_google_drive,
                    EXPORT_RESULT_NO_CONSUMER);
        }
        mExportFileUri = null;
    }

    private boolean filterPasswords(String query) {
        mSearchQuery = query;
        // Hide the help option. It's not useful during search but might be clicked by accident.
        mHelpItem.setShowAsAction(mSearchQuery != null ? MenuItem.SHOW_AS_ACTION_NEVER
                                                       : MenuItem.SHOW_AS_ACTION_IF_ROOM);
        rebuildPasswordLists();
        return false; // Query has been handled. Don't trigger default action of SearchView.
    }

    /**
     * Empty screen message when no passwords or exceptions are stored.
     */
    private void displayEmptyScreenMessage() {
        mEmptyView = new TextMessagePreference(getActivity(), null);
        mEmptyView.setSummary(R.string.saved_passwords_none_text);
        mEmptyView.setKey(PREF_KEY_SAVED_PASSWORDS_NO_TEXT);
        mEmptyView.setOrder(ORDER_SAVED_PASSWORDS_NO_TEXT);
        getPreferenceScreen().addPreference(mEmptyView);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ReauthenticationManager.resetLastReauth();
    }

    void rebuildPasswordLists() {
        mNoPasswords = false;
        mNoPasswordExceptions = false;
        getPreferenceScreen().removeAll();
        createSavePasswordsSwitch();
        createAutoSignInCheckbox();
        PasswordManagerHandlerProvider.getInstance()
                .getPasswordManagerHandler()
                .updatePasswordLists();
    }

    /**
     * Removes the UI displaying the list of saved passwords or exceptions.
     * @param preferenceCategoryKey The key string identifying the PreferenceCategory to be removed.
     */
    private void resetList(String preferenceCategoryKey) {
        PreferenceCategory profileCategory =
                (PreferenceCategory) getPreferenceScreen().findPreference(preferenceCategoryKey);
        if (profileCategory != null) {
            profileCategory.removeAll();
            getPreferenceScreen().removePreference(profileCategory);
        }
    }

    /**
     * Removes the message informing the user that there are no saved entries to display.
     */
    private void resetNoEntriesTextMessage() {
        Preference message = getPreferenceScreen().findPreference(PREF_KEY_SAVED_PASSWORDS_NO_TEXT);
        if (message != null) {
            getPreferenceScreen().removePreference(message);
        }
    }

    @Override
    public void passwordListAvailable(int count) {
        resetList(PREF_KEY_CATEGORY_SAVED_PASSWORDS);
        resetNoEntriesTextMessage();

        mNoPasswords = count == 0;
        if (mNoPasswords) {
            if (mNoPasswordExceptions) displayEmptyScreenMessage();
            return;
        }

        displayManageAccountLink();

        PreferenceGroup passwordParent;
        if (mSearchQuery == null) {
            PreferenceCategory profileCategory = new PreferenceCategory(getActivity());
            profileCategory.setKey(PREF_KEY_CATEGORY_SAVED_PASSWORDS);
            profileCategory.setTitle(R.string.prefs_saved_passwords_title);
            profileCategory.setOrder(ORDER_SAVED_PASSWORDS);
            getPreferenceScreen().addPreference(profileCategory);
            passwordParent = profileCategory;
        } else {
            passwordParent = getPreferenceScreen();
        }
        for (int i = 0; i < count; i++) {
            SavedPasswordEntry saved = PasswordManagerHandlerProvider.getInstance()
                                               .getPasswordManagerHandler()
                                               .getSavedPasswordEntry(i);
            String url = saved.getUrl();
            String name = saved.getUserName();
            String password = saved.getPassword();
            if (shouldBeFiltered(url, name)) {
                continue; // The current password won't show with the active filter, try the next.
            }
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            screen.setTitle(url);
            screen.setOnPreferenceClickListener(this);
            screen.setSummary(name);
            Bundle args = screen.getExtras();
            args.putString(PASSWORD_LIST_NAME, name);
            args.putString(PASSWORD_LIST_URL, url);
            args.putString(PASSWORD_LIST_PASSWORD, password);
            args.putInt(PASSWORD_LIST_ID, i);
            passwordParent.addPreference(screen);
        }
        mNoPasswords = passwordParent.getPreferenceCount() == 0;
        if (mNoPasswords) {
            if (count == 0) displayEmptyScreenMessage(); // Show if the list was already empty.
            if (mSearchQuery == null) {
                // If not searching, the category needs to be removed again.
                getPreferenceScreen().removePreference(passwordParent);
            } else {
                getView().announceForAccessibility(
                        getResources().getText(R.string.accessible_find_in_page_no_results));
            }
        }
    }

    /**
     * Returns true if there is a search query that requires the exclusion of an entry based on
     * the passed url or name.
     * @param url the visible URL of the entry to check. May be empty but must not be null.
     * @param name the visible user name of the entry to check. May be empty but must not be null.
     * @return Returns whether the entry with the passed url and name should be filtered.
     */
    private boolean shouldBeFiltered(final String url, final String name) {
        if (mSearchQuery == null) {
            return false;
        }
        return !url.toLowerCase(Locale.ENGLISH).contains(mSearchQuery.toLowerCase(Locale.ENGLISH))
                && !name.toLowerCase(Locale.getDefault())
                            .contains(mSearchQuery.toLowerCase(Locale.getDefault()));
    }

    @Override
    public void passwordExceptionListAvailable(int count) {
        if (mSearchQuery != null) return; // Don't show exceptions if a search is ongoing.
        resetList(PREF_KEY_CATEGORY_EXCEPTIONS);
        resetNoEntriesTextMessage();

        mNoPasswordExceptions = count == 0;
        if (mNoPasswordExceptions) {
            if (mNoPasswords) displayEmptyScreenMessage();
            return;
        }

        displayManageAccountLink();

        PreferenceCategory profileCategory = new PreferenceCategory(getActivity());
        profileCategory.setKey(PREF_KEY_CATEGORY_EXCEPTIONS);
        profileCategory.setTitle(R.string.section_saved_passwords_exceptions);
        profileCategory.setOrder(ORDER_EXCEPTIONS);
        getPreferenceScreen().addPreference(profileCategory);
        for (int i = 0; i < count; i++) {
            String exception = PasswordManagerHandlerProvider.getInstance()
                                       .getPasswordManagerHandler()
                                       .getSavedPasswordException(i);
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            screen.setTitle(exception);
            screen.setOnPreferenceClickListener(this);
            Bundle args = screen.getExtras();
            args.putString(PASSWORD_LIST_URL, exception);
            args.putInt(PASSWORD_LIST_ID, i);
            profileCategory.addPreference(screen);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mExportState == EXPORT_STATE_REQUESTED) {
            // If Chrome returns to foreground from being paused (but without being killed), and
            // exportAfterReauth was called before pausing, the warning dialog is still
            // displayed and ready to be used, and this is indicated by
            // |mExportWarningDialogFragment| being non-null.
            if (ReauthenticationManager.authenticationStillValid(
                        ReauthenticationManager.REAUTH_SCOPE_BULK)) {
                if (mExportWarningDialogFragment == null) exportAfterReauth();
            } else {
                if (mExportWarningDialogFragment != null) mExportWarningDialogFragment.dismiss();
                RecordHistogram.recordEnumeratedHistogram(
                        "PasswordManager.ExportPasswordsToCSVResult", EXPORT_RESULT_USER_ABORTED,
                        EXPORT_RESULT_COUNT);
                mExportState = EXPORT_STATE_INACTIVE;
            }
        }
        rebuildPasswordLists();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVED_STATE_EXPORT_STATE, mExportState);
        outState.putInt(SAVED_STATE_ENTRIES_COUNT, mEntriesCount);
        if (mExportFileUri != null) {
            outState.putString(SAVED_STATE_EXPORT_FILE_URI, mExportFileUri.toString());
        }
        if (mSearchQuery != null) {
            outState.putString(SAVED_STATE_SEARCH_QUERY, mSearchQuery);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        maybeRecordTriggeredPasswordSearch(false);
        PasswordManagerHandlerProvider.getInstance().removeObserver(this);
    }

    /**
     *  Preference was clicked. Either navigate to manage account site or launch the PasswordEditor
     *  depending on which preference it was.
     */
    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mLinkPref) {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW, Uri.parse(PasswordUIView.getAccountDashboardURL()));
            intent.setPackage(getActivity().getPackageName());
            getActivity().startActivity(intent);
        } else {
            // Launch preference activity with PasswordEntryEditor fragment with
            // intent extras specifying the object.
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                    getActivity(), PasswordEntryEditor.class.getName());
            intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, preference.getExtras());
            intent.putExtra(SavePasswordsPreferences.EXTRA_FOUND_VIA_SEARCH, mSearchQuery != null);
            startActivity(intent);
        }
        return true;
    }

    /**
     * Convert a given icon to a plain white version by applying the MATRIX_TRANSFORM_TO_WHITE color
     * filter. The resulting drawable will be brighter than a usual grayscale conversion.
     *
     * For grayscale conversion, use the function ColorMatrix#setSaturation(0) instead.
     * @param icon The drawable to be converted.
     * @return Returns the bright white version of the passed drawable.
     */
    private static Drawable convertToPlainWhite(Drawable icon) {
        icon.mutate().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        return icon;
    }

    private void createSavePasswordsSwitch() {
        if (mSearchQuery != null) {
            return; // Don't create this option when the preferences are filtered for passwords.
        }
        mSavePasswordsSwitch = new ChromeSwitchPreference(getActivity(), null);
        mSavePasswordsSwitch.setKey(PREF_SAVE_PASSWORDS_SWITCH);
        mSavePasswordsSwitch.setTitle(R.string.prefs_saved_passwords);
        mSavePasswordsSwitch.setOrder(ORDER_SWITCH);
        mSavePasswordsSwitch.setSummaryOn(R.string.text_on);
        mSavePasswordsSwitch.setSummaryOff(R.string.text_off);
        mSavePasswordsSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setRememberPasswordsEnabled((boolean) newValue);
                return true;
            }
        });
        mSavePasswordsSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isRememberPasswordsManaged();
            }
        });
        getPreferenceScreen().addPreference(mSavePasswordsSwitch);

        // Note: setting the switch state before the preference is added to the screen results in
        // some odd behavior where the switch state doesn't always match the internal enabled state
        // (e.g. the switch will say "On" when save passwords is really turned off), so
        // .setChecked() should be called after .addPreference()
        mSavePasswordsSwitch.setChecked(
                PrefServiceBridge.getInstance().isRememberPasswordsEnabled());
    }

    private void createAutoSignInCheckbox() {
        if (mSearchQuery != null) {
            return; // Don't create this option when the preferences are filtered for passwords.
        }
        mAutoSignInSwitch = new ChromeBaseCheckBoxPreference(getActivity(), null);
        mAutoSignInSwitch.setKey(PREF_AUTOSIGNIN_SWITCH);
        mAutoSignInSwitch.setTitle(R.string.passwords_auto_signin_title);
        mAutoSignInSwitch.setOrder(ORDER_AUTO_SIGNIN_CHECKBOX);
        mAutoSignInSwitch.setSummary(R.string.passwords_auto_signin_description);
        mAutoSignInSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setPasswordManagerAutoSigninEnabled(
                        (boolean) newValue);
                return true;
            }
        });
        mAutoSignInSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isPasswordManagerAutoSigninManaged();
            }
        });
        getPreferenceScreen().addPreference(mAutoSignInSwitch);
        mAutoSignInSwitch.setChecked(
                PrefServiceBridge.getInstance().isPasswordManagerAutoSigninEnabled());
    }

    private void displayManageAccountLink() {
        if (mSearchQuery != null && !mNoPasswords) {
            return; // Don't add the Manage Account link if there is a search going on.
        }
        if (getPreferenceScreen().findPreference(PREF_KEY_MANAGE_ACCOUNT_LINK) != null) {
            return; // Don't add the Manage Account link if it's present.
        }
        if (mLinkPref != null) {
            // If we created the link before, reuse it.
            getPreferenceScreen().addPreference(mLinkPref);
            return;
        }
        ForegroundColorSpan colorSpan = new ForegroundColorSpan(
                ApiCompatibilityUtils.getColor(getResources(), R.color.google_blue_700));
        SpannableString title = SpanApplier.applySpans(getString(R.string.manage_passwords_text),
                new SpanApplier.SpanInfo("<link>", "</link>", colorSpan));
        mLinkPref = new ChromeBasePreference(getActivity());
        mLinkPref.setKey(PREF_KEY_MANAGE_ACCOUNT_LINK);
        mLinkPref.setTitle(title);
        mLinkPref.setOnPreferenceClickListener(this);
        mLinkPref.setOrder(ORDER_MANAGE_ACCOUNT_LINK);
        getPreferenceScreen().addPreference(mLinkPref);
    }

    /**
     * Returns whether the password export feature is ready to use.
     * @return Returns true if the flag is set and the Reauthentication Api is available.
     */
    private boolean providesPasswordExport() {
        return ChromeFeatureList.isEnabled(EXPORT_PASSWORDS)
                && ReauthenticationManager.isReauthenticationApiAvailable();
    }

    /**
     * Returns whether the password search feature is ready to use.
     * @return Returns true if the flag is set.
     */
    private boolean providesPasswordSearch() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.PASSWORD_SEARCH);
    }

    @VisibleForTesting
    Menu getMenuForTesting() {
        return mMenuForTesting;
    }
}
