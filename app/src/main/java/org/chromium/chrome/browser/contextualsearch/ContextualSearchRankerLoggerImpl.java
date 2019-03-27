// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.support.annotation.Nullable;

import org.chromium.base.VisibleForTesting;
import org.chromium.content_public.browser.WebContents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements the UMA logging for Ranker that's used for Contextual Search Tap Suppression.
 */
public class ContextualSearchRankerLoggerImpl implements ContextualSearchInteractionRecorder {
    // Names for all our features and labels.
    // Map keys should contain @Feature int values only.
    private static final Map<Integer, String> ALL_NAMES;
    @VisibleForTesting
    // Map keys should contain @Feature int values only.
    static final Map<Integer, String> OUTCOMES;
    @VisibleForTesting
    // Map keys should contain @Feature int values only.
    static final Map<Integer, String> FEATURES;
    static {
        // Map keys should contain @Feature int values only.
        Map<Integer, String> outcomes = new HashMap<Integer, String>();
        outcomes.put(Feature.OUTCOME_WAS_PANEL_OPENED, "OutcomeWasPanelOpened");
        outcomes.put(Feature.OUTCOME_WAS_QUICK_ACTION_CLICKED, "OutcomeWasQuickActionClicked");
        outcomes.put(Feature.OUTCOME_WAS_QUICK_ANSWER_SEEN, "OutcomeWasQuickAnswerSeen");
        // UKM CS v2 outcomes.
        outcomes.put(Feature.OUTCOME_WAS_CARDS_DATA_SHOWN, "OutcomeWasCardsDataShown");
        OUTCOMES = Collections.unmodifiableMap(outcomes);

        // NOTE: this list needs to be kept in sync with the white list in
        // predictor_config_definitions.cc and with ukm.xml!
        // Integer values should contain @Feature values only.
        Map<Integer, String> features = new HashMap<Integer, String>();
        features.put(Feature.DURATION_AFTER_SCROLL_MS, "DurationAfterScrollMs");
        features.put(Feature.SCREEN_TOP_DPS, "ScreenTopDps");
        features.put(Feature.WAS_SCREEN_BOTTOM, "WasScreenBottom");
        features.put(Feature.PREVIOUS_WEEK_IMPRESSIONS_COUNT, "PreviousWeekImpressionsCount");
        features.put(Feature.PREVIOUS_WEEK_CTR_PERCENT, "PreviousWeekCtrPercent");
        features.put(Feature.PREVIOUS_28DAY_IMPRESSIONS_COUNT, "Previous28DayImpressionsCount");
        features.put(Feature.PREVIOUS_28DAY_CTR_PERCENT, "Previous28DayCtrPercent");
        // UKM CS v2 features.
        features.put(Feature.DID_OPT_IN, "DidOptIn");
        features.put(Feature.IS_SHORT_WORD, "IsShortWord");
        features.put(Feature.IS_LONG_WORD, "IsLongWord");
        features.put(Feature.IS_WORD_EDGE, "IsWordEdge");
        features.put(Feature.IS_ENTITY, "IsEntity");
        features.put(Feature.TAP_DURATION_MS, "TapDurationMs");
        // UKM CS v3 features.
        features.put(Feature.FONT_SIZE, "FontSize");
        features.put(Feature.IS_HTTP, "IsHttp");
        features.put(Feature.IS_SECOND_TAP_OVERRIDE, "IsSecondTapOverride");
        features.put(Feature.IS_ENTITY_ELIGIBLE, "IsEntityEligible");
        features.put(Feature.IS_LANGUAGE_MISMATCH, "IsLanguageMismatch");
        features.put(Feature.PORTION_OF_ELEMENT, "PortionOfElement");
        // UKM CS v4 features.
        features.put(Feature.TAP_COUNT, "TapCount");
        features.put(Feature.OPEN_COUNT, "OpenCount");
        features.put(Feature.QUICK_ANSWER_COUNT, "QuickAnswerCount");
        features.put(Feature.ENTITY_IMPRESSIONS_COUNT, "EntityImpressionsCount");
        features.put(Feature.ENTITY_OPENS_COUNT, "EntityOpensCount");
        features.put(Feature.QUICK_ACTION_IMPRESSIONS_COUNT, "QuickActionImpressionsCount");
        features.put(Feature.QUICK_ACTIONS_TAKEN_COUNT, "QuickActionsTaken");
        features.put(Feature.QUICK_ACTIONS_IGNORED_COUNT, "QuickActionsIgnored");
        FEATURES = Collections.unmodifiableMap(features);

        // Map keys should contain @Feature int values only.
        Map<Integer, String> allNames = new HashMap<Integer, String>();
        allNames.putAll(outcomes);
        allNames.putAll(features);
        ALL_NAMES = Collections.unmodifiableMap(allNames);
    }

    // Pointer to the native instance of this class.
    private long mNativePointer;

    // Whether logging for the current page has been setup.
    private boolean mIsLoggingReadyForPage;

    // The WebContents of the base page that the log data is associated with.
    private WebContents mBasePageWebContents;

    // Whether inference has already occurred for this interaction (and calling #logFeature is no
    // longer allowed).
    private boolean mHasInferenceOccurred;

    // What kind of ML prediction we were able to get.
    private @AssistRankerPrediction int mAssistRankerPrediction =
            AssistRankerPrediction.UNDETERMINED;

    // Map that accumulates all of the Features to log for a specific user-interaction.
    // Map keys should contain @Feature int values only.
    private Map<Integer, Object> mFeaturesToLog;

    // A for-testing copy of all the features to log setup so that it will survive a {@link #reset}.
    // Map keys should contain @Feature int values only.
    private Map<Integer, Object> mFeaturesLoggedForTesting;
    private Map<Integer, Object> mOutcomesLoggedForTesting;

    private ContextualSearchInteractionPersister mInteractionPersister;

    private long mEventIdToPersist;

    /**
     * Constructs a Ranker Logger and associated native implementation to write Contextual Search
     * ML data to Ranker.
     */
    public ContextualSearchRankerLoggerImpl() {
        this(new ContextualSearchInteractionPersisterImpl());
    }

    /**
     * Constructs a Ranker Logger implementation for testing.
     * @param interactionPersister The {@link ContextualSearchInteractionPersister} to use for this
     *        instance.
     */
    public ContextualSearchRankerLoggerImpl(
            ContextualSearchInteractionPersister interactionPersister) {
        mInteractionPersister = interactionPersister;
        if (isEnabled()) mNativePointer = nativeInit();
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.  The nativeDestroy will call the destructor on the native instance.
     */
    void destroy() {
        // TODO(donnd): looks like this is never being called.  Fix.
        if (isEnabled()) {
            assert mNativePointer != 0;
            writeLogAndReset();
            nativeDestroy(mNativePointer);
            mNativePointer = 0;
        }
        mIsLoggingReadyForPage = false;
    }

    @Override
    public void setupLoggingForPage(@Nullable WebContents basePageWebContents) {
        mIsLoggingReadyForPage = true;
        mBasePageWebContents = basePageWebContents;
        mHasInferenceOccurred = false;
        nativeSetupLoggingAndRanker(mNativePointer, basePageWebContents);
    }

    @Override
    public boolean isQueryEnabled() {
        return nativeIsQueryEnabled(mNativePointer);
    }

    @Override
    public void logFeature(@Feature int feature, Object value) {
        assert mIsLoggingReadyForPage : "mIsLoggingReadyForPage false.";
        assert !mHasInferenceOccurred;
        if (!isEnabled()) return;

        logInternal(feature, value);
    }

    @Override
    public void logOutcome(@Feature int feature, Object value) {
        assert mIsLoggingReadyForPage;
        assert mHasInferenceOccurred;
        if (!isEnabled()) return;

        logInternal(feature, value);
    }

    @Override
    public @AssistRankerPrediction int runPredictionForTapSuppression() {
        assert mIsLoggingReadyForPage;
        assert !mHasInferenceOccurred;
        mHasInferenceOccurred = true;
        if (isEnabled() && mBasePageWebContents != null && mFeaturesToLog != null
                && !mFeaturesToLog.isEmpty()) {
            for (Map.Entry<Integer, Object> entry : mFeaturesToLog.entrySet()) {
                logObject(entry.getKey(), entry.getValue());
            }
            mFeaturesLoggedForTesting = mFeaturesToLog;
            mFeaturesToLog = new HashMap<Integer, Object>();
            mAssistRankerPrediction = nativeRunInference(mNativePointer);
            ContextualSearchUma.logRecordedFeaturesToRanker();
        }
        return mAssistRankerPrediction;
    }

    @Override
    public @AssistRankerPrediction int getPredictionForTapSuppression() {
        return mAssistRankerPrediction;
    }

    @Override
    public void reset() {
        mIsLoggingReadyForPage = false;
        mHasInferenceOccurred = false;
        mFeaturesToLog = null;
        mBasePageWebContents = null;
        mAssistRankerPrediction = AssistRankerPrediction.UNDETERMINED;
    }

    @Override
    public void writeLogAndReset() {
        if (isEnabled()) {
            if (mBasePageWebContents != null && mFeaturesToLog != null
                    && !mFeaturesToLog.isEmpty()) {
                assert mIsLoggingReadyForPage;
                assert mHasInferenceOccurred;
                // Only the outcomes will be present, since we logged inference features at
                // inference time.
                for (Map.Entry<Integer, Object> entry : mFeaturesToLog.entrySet()) {
                    logObject(entry.getKey(), entry.getValue());
                }
                mOutcomesLoggedForTesting = mFeaturesToLog;
                ContextualSearchUma.logRecordedOutcomesToRanker();
                // Also persist the outcomes if we are persisting this interaction.
                if (mEventIdToPersist != 0) {
                    mInteractionPersister.persistInteractions(mEventIdToPersist, mFeaturesToLog);
                    mEventIdToPersist = 0;
                }
            }
            nativeWriteLogAndReset(mNativePointer);
        }
        reset();
    }

    @Override
    public void persistInteraction(long eventId) {
        if (eventId != 0) mEventIdToPersist = eventId;
    }

    @Override
    public ContextualSearchInteractionPersister getInteractionPersister() {
        return mInteractionPersister;
    }

    /**
     * Logs the given feature/value to the internal map that accumulates an entire record (which can
     * be logged by calling writeLogAndReset).
     * @param feature The feature to log.
     * @param value The value to log.
     */
    private void logInternal(@Feature int feature, Object value) {
        if (mFeaturesToLog == null) mFeaturesToLog = new HashMap<Integer, Object>();
        mFeaturesToLog.put(feature, value);
    }

    /** Whether actually writing data is enabled.  If not, we may do nothing, or just print. */
    private boolean isEnabled() {
        return !ContextualSearchFieldTrial.isUkmRankerLoggingDisabled();
    }

    /**
     * Logs the given {@link ContextualSearchInteractionRecorder.Feature} with the given value
     * {@link Object}.
     * @param feature The feature to log.
     * @param value An {@link Object} value to log (must be convertible to a {@code long}).
     */
    private void logObject(@Feature int feature, Object value) {
        if (value instanceof Boolean) {
            logToNative(feature, ((boolean) value ? 1 : 0));
        } else if (value instanceof Integer) {
            logToNative(feature, (int) value);
        } else if (value instanceof Character) {
            logToNative(feature, Character.getNumericValue((char) value));
        } else {
            assert false : "Could not log feature to Ranker: " + String.valueOf(feature)
                           + " of class "
                           + value.getClass();
        }
    }

    /**
     * Logs to the native instance.  All native logging must go through this bottleneck.
     * @param feature The feature to log.
     * @param value The value to log.
     */
    private void logToNative(@Feature int feature, int value) {
        String featureName = getFeatureName(feature);
        assert featureName != null : "No Name for feature " + feature;
        nativeLogInt32(mNativePointer, featureName, value);
    }

    /**
     * @return The name of the given feature.
     */
    private String getFeatureName(@Feature int feature) {
        return ALL_NAMES.get(feature);
    }

    /**
     * Gets the current set of features that have been logged.  Should only be used for testing
     * purposes!
     * @return The current set of features that have been logged, or {@code null}.
     */
    @VisibleForTesting
    @Nullable
    Map<Integer, Object> getFeaturesLogged() {
        return mFeaturesLoggedForTesting;
    }

    /**
     * Gets the current set of outcomes that have been logged.  Should only be used for
     * testing purposes!
     * @return The current set of outcomes that have been logged, or {@code null}.
     */
    @VisibleForTesting
    @Nullable
    Map<Integer, Object> getOutcomesLogged() {
        return mOutcomesLoggedForTesting;
    }

    // ============================================================================================
    // Native methods.
    // ============================================================================================
    private native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchRankerLoggerImpl);
    private native void nativeLogInt32(
            long nativeContextualSearchRankerLoggerImpl, String featureString, int value);
    private native void nativeSetupLoggingAndRanker(
            long nativeContextualSearchRankerLoggerImpl, WebContents basePageWebContents);
    // Returns an AssistRankerPrediction integer value.
    private native int nativeRunInference(long nativeContextualSearchRankerLoggerImpl);
    private native void nativeWriteLogAndReset(long nativeContextualSearchRankerLoggerImpl);
    private native boolean nativeIsQueryEnabled(long nativeContextualSearchRankerLoggerImpl);
}
