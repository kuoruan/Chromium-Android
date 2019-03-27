// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.Preference;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.ui.widget.Toast;

import java.util.Locale;

/**
 * Utilities and common methods to handle settings managed by policies.
 */
public class ManagedPreferencesUtils {

    /**
     * Shows a toast indicating that the previous action is managed by the system administrator.
     *
     * This is usually used to explain to the user why a given control is disabled in the settings.
     *
     * @param context The context where the Toast will be shown.
     */
    public static void showManagedByAdministratorToast(Context context) {
        Toast.makeText(context, context.getString(R.string.managed_by_your_organization),
                     Toast.LENGTH_LONG)
                .show();
    }
    /**
     * Shows a toast indicating that the previous action is managed by the parent(s) of the
     * supervised user.
     * This is usually used to explain to the user why a given control is disabled in the settings.
     *
     * @param context The context where the Toast will be shown.
     */
    public static void showManagedByParentToast(Context context) {
        Toast.makeText(context, context.getString(getManagedByParentStringRes()), Toast.LENGTH_LONG)
                .show();
    }

    /**
     * @return The resource ID for the Managed By Enterprise icon.
     */
    public static int getManagedByEnterpriseIconId() {
        return R.drawable.controlled_setting_mandatory;
    }

    /**
     * Initializes the Preference based on the state of any policies that may affect it,
     * e.g. by showing a managed icon or disabling clicks on the preference.
     *
     * This should be called once, before the preference is displayed.
     *
     * @param delegate The delegate that controls whether the preference is managed. May be null,
     *         then this method does nothing.
     * @param preference The Preference that is being initialized
     */
    public static void initPreference(
            @Nullable ManagedPreferenceDelegate delegate, Preference preference) {
        if (delegate == null) return;

        if (delegate.isPreferenceControlledByPolicy(preference)) {
            preference.setIcon(getManagedByEnterpriseIconId());
        } else if (delegate.isPreferenceControlledByCustodian(preference)) {
            preference.setIcon(R.drawable.ic_account_child_grey600_36dp);
        }

        if (delegate.isPreferenceClickDisabledByPolicy(preference)) {
            // Disable the views and prevent the Preference from mucking with the enabled state.
            preference.setShouldDisableView(false);

            // Prevent default click behavior.
            preference.setFragment(null);
            preference.setIntent(null);
            preference.setOnPreferenceClickListener(null);
        }
    }

    /**
     * Disables the Preference's views if the preference is not clickable.
     *
     * Note: this disables the View instead of disabling the Preference, so that the Preference
     * still receives click events, which will trigger a "Managed by your administrator" toast.
     *
     * This should be called from the Preference's onBindView() method.
     *
     * @param delegate The delegate that controls whether the preference is managed. May be null,
     *         then this method does nothing.
     * @param preference The Preference that owns the view
     * @param view The View that was bound to the Preference
     */
    public static void onBindViewToPreference(
            @Nullable ManagedPreferenceDelegate delegate, Preference preference, View view) {
        if (delegate == null) return;

        if (delegate.isPreferenceClickDisabledByPolicy(preference)) {
            ViewUtils.setEnabledRecursive(view, false);
        }

        // Append managed information to summary if necessary.
        TextView summaryView = view.findViewById(android.R.id.summary);
        CharSequence summary =
                ManagedPreferencesUtils.getSummaryWithManagedInfo(delegate, preference,
                        summaryView.getVisibility() == View.VISIBLE ? summaryView.getText() : null);
        if (!TextUtils.isEmpty(summary)) {
            summaryView.setText(summary);
            summaryView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Intercepts the click event if the given Preference is managed and shows a toast in that case.
     *
     * This should be called from the Preference's onClick() method.
     *
     * @param delegate The delegate that controls whether the preference is managed. May be null,
     *         then this method does nothing and returns false.
     * @param preference The Preference that was clicked.
     * @return true if the click event was handled by this helper and shouldn't be further
     *         propagated; false otherwise.
     */
    public static boolean onClickPreference(
            @Nullable ManagedPreferenceDelegate delegate, Preference preference) {
        if (delegate == null || !delegate.isPreferenceClickDisabledByPolicy(preference)) {
            return false;
        }

        if (delegate.isPreferenceControlledByPolicy(preference)) {
            showManagedByAdministratorToast(preference.getContext());
        } else if (delegate.isPreferenceControlledByCustodian(preference)) {
            showManagedByParentToast(preference.getContext());
        } else {
            // If the preference is disabled, it should be either because it's managed by enterprise
            // policy or by the custodian.
            assert false;
        }
        return true;
    }

    /**
     * @param delegate The {@link ManagedPreferenceDelegate} that controls whether the preference is
     *        managed.
     * @param preference The {@link Preference} that the summary should be used for.
     * @param summary The original summary without the managed information.
     * @return The summary appended with information about whether the specified preference is
     *         managed.
     */
    private static CharSequence getSummaryWithManagedInfo(
            @Nullable ManagedPreferenceDelegate delegate, Preference preference,
            @Nullable CharSequence summary) {
        if (delegate == null) return summary;

        String extraSummary = null;
        if (delegate.isPreferenceControlledByPolicy(preference)) {
            extraSummary = preference.getContext().getString(R.string.managed_by_your_organization);
        } else if (delegate.isPreferenceControlledByCustodian(preference)) {
            extraSummary = preference.getContext().getString(getManagedByParentStringRes());
        }

        if (TextUtils.isEmpty(extraSummary)) return summary;
        if (TextUtils.isEmpty(summary)) return extraSummary;
        return String.format(Locale.getDefault(), "%s\n%s", summary, extraSummary);
    }

    private static @StringRes int getManagedByParentStringRes() {
        boolean singleParentIsManager =
                PrefServiceBridge.getInstance().getSupervisedUserSecondCustodianName().isEmpty();
        return singleParentIsManager ? R.string.managed_by_your_parent
                                     : R.string.managed_by_your_parents;
    }
}
