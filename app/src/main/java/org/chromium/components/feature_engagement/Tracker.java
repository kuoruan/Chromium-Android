// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement;

import android.support.annotation.CheckResult;

import org.chromium.base.Callback;

/**
 * Tracker is the Java representation of a native Tracker object.
 * It is owned by the native BrowserContext.
 *
 * Tracker is the core class for the feature engagement.
 */
public interface Tracker {
    /**
     * Must be called whenever an event happens.
     */
    void notifyEvent(String event);

    /**
     * This function must be called whenever the triggering condition for a specific feature
     * happens. Returns true iff the display of the in-product help must happen.
     * If {@code true} is returned, the caller *must* call {@link #dismissed()} when display
     * of feature enlightenment ends.
     * @return whether feature enlightenment should be displayed.
     */
    @CheckResult
    boolean shouldTriggerHelpUI(String feature);

    /**
     * This function can be called to query if a particular |feature| meets its particular
     * precondition for triggering within the bounds of the current feature configuration.
     * Calling this method requires the {@link Tracker} to already have been initialized.
     * See {@link #isInitialized()) and {@link #addOnInitializedCallback(Callback<Boolean>)} for how
     * to ensure the call to this is delayed.
     * This function can typically be used to ensure that expensive operations for tracking other
     * state related to in-product help do not happen if in-product help has already been displayed
     * for the given |feature|.
     */
    @TriggerState
    int getTriggerState(String feature);

    /**
     * Must be called after display of feature enlightenment finishes for a particular feature.
     */
    void dismissed(String feature);

    /**
     * Returns whether the tracker has been successfully initialized. During startup, this will be
     * false until the internal models have been loaded at which point it is set to true if the
     * initialization was successful. The state will never change from initialized to uninitialized.
     * Callers can invoke AddOnInitializedCallback(...) to be notified when the result of the
     * initialization is ready.
     */
    boolean isInitialized();

    /**
     * For features that trigger on startup, they can register a callback to ensure that they are
     * informed when the tracker has finished the initialization. If the tracker has already been
     * initialized, the callback will still be invoked with the result. The callback is guaranteed
     * to be invoked exactly one time.
     *
     * The |result| parameter indicates whether the initialization was a success and the tracker is
     * ready to receive calls.
     */
    void addOnInitializedCallback(Callback<Boolean> callback);
}
