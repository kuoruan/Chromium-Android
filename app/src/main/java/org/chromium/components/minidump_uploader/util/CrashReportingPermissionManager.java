// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.minidump_uploader.util;

/**
 * Interface for crash reporting permissions.
 */
public interface CrashReportingPermissionManager {
    /**
     * Checks whether this client is in-sample for usage metrics and crash reporting. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     *
     * @returns boolean Whether client is in-sample.
     */
    public boolean isClientInMetricsSample();

    /**
     * Checks whether uploading of crash dumps is permitted for the available network(s).
     *
     * @return whether uploading crash dumps is permitted.
     */
    public boolean isNetworkAvailableForCrashUploads();

    // TODO(isherman): Remove this function. It's only used for an assertion, and our JobService
    // implementations simply hardcode their return value to true.
    /**
     * Checks whether uploading of usage metrics is currently permitted. This is a combination of
     * the below checks, plus networking restrictions.
     *
     * @return whether uploading usage metrics is currently permitted.
     */
    public boolean isMetricsUploadPermitted();

    // TODO(isherman): Remove this function. It was previously used to enable a specific type of
    // test, but it should not be necessary anymore. (Note: There are currently some clients that do
    // partly depend on it, so it is not necessarily safe to remove without contacting those
    // clients.)
    /**
     * Checks whether uploading of crash dumps is permitted, based on the corresponding command line
     * flag only.
     *
     * @return whether uploading of crash dumps is enabled or disabled by a command line flag.
     */
    public boolean isCrashUploadDisabledByCommandLine();

    /**
     * Checks whether uploading of usage metrics and crash dumps is currently permitted, based on
     * user consent only.
     *
     * @return whether the user has consented to reporting usage metrics and crash dumps.
     */
    public boolean isUsageAndCrashReportingPermittedByUser();

    /**
     * Checks whether to ignore all consent and upload limitations for usage metrics and crash
     * reporting. Used by test devices to avoid a UI dependency.
     *
     * @return whether crash dumps should be uploaded if at all possible.
     */
    public boolean isUploadEnabledForTests();
}
