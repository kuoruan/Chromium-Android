// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.PackageUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.infobar.InfoBarIdentifier;
import org.chromium.chrome.browser.infobar.SimpleConfirmInfoBarBuilder;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.webapps.WebappActivity;
import org.chromium.content_public.browser.ScreenOrientationDelegate;
import org.chromium.content_public.browser.ScreenOrientationDelegateManager;
import org.chromium.ui.UiUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr_shell")
public class VrShellDelegate
        implements ApplicationStatus.ActivityStateListener, View.OnSystemUiVisibilityChangeListener,
                   ScreenOrientationDelegate {
    private static final String TAG = "VrShellDelegate";

    // Pseudo-random number to avoid request id collisions.
    public static final int EXIT_VR_RESULT = 721251;

    private static final int ENTER_VR_NOT_NECESSARY = 0;
    private static final int ENTER_VR_CANCELLED = 1;
    private static final int ENTER_VR_REQUESTED = 2;
    private static final int ENTER_VR_SUCCEEDED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENTER_VR_NOT_NECESSARY, ENTER_VR_CANCELLED, ENTER_VR_REQUESTED, ENTER_VR_SUCCEEDED})
    private @interface EnterVRResult {}

    private static final int VR_NOT_AVAILABLE = 0;
    private static final int VR_CARDBOARD = 1;
    private static final int VR_DAYDREAM = 2; // Supports both Cardboard and Daydream viewer.

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VR_NOT_AVAILABLE, VR_CARDBOARD, VR_DAYDREAM})
    private @interface VrSupportLevel {}

    private static final String DAYDREAM_VR_EXTRA = "android.intent.extra.VR_LAUNCH";
    private static final String DAYDREAM_HOME_PACKAGE = "com.google.android.vr.home";
    static final String VR_FRE_INTENT_EXTRA = "org.chromium.chrome.browser.vr_shell.VR_FRE";

    // Linter and formatter disagree on how the line below should be formatted.
    /* package */
    static final String VR_ENTRY_RESULT_ACTION =
            "org.chromium.chrome.browser.vr_shell.VrEntryResult";

    private static final long REENTER_VR_TIMEOUT_MS = 1000;
    private static final int EXPECT_DON_TIMEOUT_MS = 2000;
    private static final long ENTER_VR_FAILED_TIMEOUT_MS = 10000;

    private static final String FEEDBACK_REPORT_TYPE = "USER_INITIATED_FEEDBACK_REPORT_VR";

    private static final int VR_SYSTEM_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final String VR_CORE_MARKET_URI =
            "market://details?id=" + VrCoreVersionChecker.VR_CORE_PACKAGE_ID;

    private static VrShellDelegate sInstance;
    private static VrBroadcastReceiver sVrBroadcastReceiver;
    private static boolean sRegisteredDaydreamHook = false;

    // TODO(crbug.com/746409): Remove this suppression after this lint error is fixed.
    @SuppressWarnings("StaticFieldLeak")
    private static View sBlackOverlayView;

    private ChromeActivity mActivity;

    @VrSupportLevel
    private int mVrSupportLevel;
    private int mCachedVrCorePackageVersion;

    // How often to prompt the user to enter VR feedback.
    private int mFeedbackFrequency;

    private final VrClassesWrapper mVrClassesWrapper;
    private VrShell mVrShell;
    private NonPresentingGvrContext mNonPresentingGvrContext;
    private VrDaydreamApi mVrDaydreamApi;
    private Boolean mIsDaydreamCurrentViewer;
    private VrCoreVersionChecker mVrCoreVersionChecker;
    private TabModelSelector mTabModelSelector;

    private boolean mInVr;
    private final Handler mEnterVrHandler;
    private final Handler mExpectPauseOrDonSucceeded;
    private boolean mProbablyInDon;
    private boolean mNeedsAnimationCancel;
    private boolean mCancellingEntryAnimation;

    // Whether or not the VR Device ON flow succeeded. If this is true it means the user has a VR
    // headset on, but we haven't switched into VR mode yet.
    // See further documentation here: https://developers.google.com/vr/daydream/guides/vr-entry
    private boolean mDonSucceeded;
    // Best effort whether or not the system was in VR when Chrome launched.
    private Boolean mInVrAtChromeLaunch;
    private boolean mShowingDaydreamDoff;
    private boolean mDoffOptional;
    // Listener to be called once we exited VR due to to an unsupported mode, e.g. the user clicked
    // the URL bar security icon.
    private OnExitVrRequestListener mOnExitVrRequestListener;
    private boolean mExitedDueToUnsupportedMode = false;
    private boolean mExitingCct;
    private boolean mPaused;
    private int mRestoreSystemUiVisibilityFlag = -1;
    private Integer mRestoreOrientation = null;
    private long mNativeVrShellDelegate;
    private boolean mRequestedWebVr;
    private boolean mRequestedWebVrBeforePause;
    private boolean mListeningForWebVrActivate;
    private boolean mListeningForWebVrActivateBeforePause;
    // Whether or not we should autopresent WebVr. If this is set, it means that a first
    // party app has asked us to autopresent WebVr content and we're waiting for the WebVr
    // content to call requestPresent.
    private boolean mAutopresentWebVr;

    // Set to true if performed VR browsing at least once. That is, this was not simply a WebVr
    // presentation experience.
    private boolean mVrBrowserUsed;

    private final VSyncEstimator mVSyncEstimator;
    private boolean mWaitingForVrTimeout;

    private static final class VrBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<ChromeActivity> mTargetActivity;

        public VrBroadcastReceiver(ChromeActivity activity) {
            mTargetActivity = new WeakReference<ChromeActivity>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ChromeActivity activity = mTargetActivity.get();
            if (activity == null) return;
            getInstance(activity);
            assert sInstance != null;
            if (sInstance == null) return;
            sInstance.mDonSucceeded = true;
            sInstance.mProbablyInDon = false;
            sInstance.mExpectPauseOrDonSucceeded.removeCallbacksAndMessages(null);
            if (sInstance.mPaused) {
                if (sInstance.mInVrAtChromeLaunch == null) sInstance.mInVrAtChromeLaunch = false;
                // We add a black overlay view so that we can show black while the VR UI is loading.
                // Note that this alone isn't sufficient to prevent 2D UI from showing while
                // resuming the Activity, see the comment about the custom animation below.
                // However, if we're already in VR (in one of the cases where we chose not to exit
                // VR before the DON flow), we don't need to add the overlay.
                if (!sInstance.mInVr) addBlackOverlayViewForActivity(sInstance.mActivity);
                sInstance.mNeedsAnimationCancel = !sInstance.mInVr;

                // We start the Activity with a custom animation that keeps it hidden while starting
                // up to avoid Android showing stale 2D screenshots when the user is in their VR
                // headset. The animation lasts up to 10 seconds, but is cancelled when we're
                // resumed as at that time we'll be showing the black overlay added above.
                int animation = sInstance.mInVr ? 0 : R.anim.stay_hidden;
                Bundle options =
                        ActivityOptions.makeCustomAnimation(activity, animation, 0).toBundle();
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .moveTaskToFront(activity.getTaskId(), 0, options);
            } else {
                if (sInstance.mInVrAtChromeLaunch == null) sInstance.mInVrAtChromeLaunch = true;
                // If a WebVR app calls requestPresent in response to the displayactivate event
                // after the DON flow completes, the DON flow is skipped, meaning our app won't be
                // paused when daydream fires our BroadcastReceiver, so onResume won't be called.
                sInstance.handleDonFlowSuccess();
            }
        }

        /**
         * Unregisters this {@link BroadcastReceiver} from the activity it's registered to.
         */
        public void unregister() {
            ChromeActivity activity = mTargetActivity.get();
            if (activity == null) return;
            try {
                activity.unregisterReceiver(VrBroadcastReceiver.this);
            } catch (IllegalArgumentException e) {
                // Ignore this. This means our receiver was already unregistered somehow.
            }
        }
    }

    /**
     * Called when the native library is first available.
     */
    public static void onNativeLibraryAvailable() {
        // Check if VR classes are available before trying to use them. Note that the native
        // vr_shell_delegate.cc is compiled out of unsupported platforms (like x86).
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return;
        nativeOnLibraryAvailable();

        if (sInstance != null) {
            sInstance.cancelStartupAnimationIfNeeded();
        }
    }

    @VisibleForTesting
    public static VrShellDelegate getInstanceForTesting() {
        return getInstance();
    }

    @VisibleForTesting
    public static boolean isDisplayingUrlForTesting() {
        if (sInstance == null) return false;
        return sInstance.mVrShell.isDisplayingUrlForTesting();
    }

    /**
     * Whether or not we are currently in VR.
     */
    public static boolean isInVr() {
        if (sInstance == null) return false;
        return sInstance.mInVr;
    }

    /**
     * See {@link ChromeActivity#handleBackPressed}
     * Only handles the back press while in VR.
     */
    public static boolean onBackPressed() {
        if (sInstance == null) return false;
        return sInstance.onBackPressedInternal();
    }

    /**
     * Enters VR on the current tab if possible.
     */
    public static void enterVrIfNecessary() {
        boolean created_delegate = sInstance == null;
        VrShellDelegate instance = getInstance();
        if (instance == null) return;
        if (instance.enterVrInternal() == ENTER_VR_CANCELLED && created_delegate) {
            instance.destroy();
        }
    }

    /**
     * Handles the result of the exit VR flow (DOFF).
     */
    public static void onExitVrResult(int resultCode) {
        if (sInstance == null) return;
        sInstance.onExitVrResult(resultCode == Activity.RESULT_OK);
    }

    /**
     * Returns the current {@VrSupportLevel}.
     */
    public static int getVrSupportLevel(VrDaydreamApi daydreamApi,
            VrCoreVersionChecker versionChecker, Tab tabToShowInfobarIn) {
        if (versionChecker == null || daydreamApi == null
                || !isVrCoreCompatible(versionChecker, tabToShowInfobarIn)) {
            return VR_NOT_AVAILABLE;
        }

        if (daydreamApi.isDaydreamReadyDevice()) return VR_DAYDREAM;

        return VR_CARDBOARD;
    }

    /**
     * If VR Shell is enabled, and the activity is supported, register with the Daydream
     * platform that this app would like to be launched in VR when the device enters VR.
     */
    public static void maybeRegisterVrEntryHook(final ChromeActivity activity) {
        // Daydream is not supported on pre-N devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (sInstance != null) return; // Will be handled in onResume.
        if (!activitySupportsVrBrowsing(activity)) return;

        // Reading VR support level and version can be slow, so do it asynchronously.
        new AsyncTask<Void, Void, VrDaydreamApi>() {
            @Override
            protected VrDaydreamApi doInBackground(Void... params) {
                VrClassesWrapper wrapper = getVrClassesWrapper();
                if (wrapper == null) return null;
                VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
                if (api == null) return null;
                int vrSupportLevel =
                        getVrSupportLevel(api, wrapper.createVrCoreVersionChecker(), null);
                if (!isVrShellEnabled(vrSupportLevel)) return null;
                return api;
            }

            @Override
            protected void onPostExecute(VrDaydreamApi api) {
                // Registering the daydream intent has to be done on the UI thread. Note that this
                // call is slow (~10ms at time of writing).
                if (api != null
                        && ApplicationStatus.getStateForActivity(activity)
                                == ActivityState.RESUMED) {
                    registerDaydreamIntent(api, activity);
                }
            }
        }
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * When the app is pausing we need to unregister with the Daydream platform to prevent this app
     * from being launched from the background when the device enters VR.
     */
    public static void maybeUnregisterVrEntryHook(ChromeActivity activity) {
        // Daydream is not supported on pre-N devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (sInstance != null) return; // Will be handled in onPause.
        if (!sRegisteredDaydreamHook) return;
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return;
        VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
        if (api == null) return;
        unregisterDaydreamIntent(api);
    }

    public static void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode && isInVr()) {
            sInstance.shutdownVr(
                    true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
        }
    }

    public static void showDoffAndExitVr(boolean optional) {
        assert sInstance != null;
        sInstance.showDoffAndExitVrInternal(optional);
    }

    public static void requestToExitVr(
            OnExitVrRequestListener listener, @UiUnsupportedMode int reason) {
        assert listener != null;
        if (sInstance == null) {
            listener.onDenied();
            return;
        }
        sInstance.requestToExitVrInternal(listener, reason);
    }

    @CalledByNative
    private static VrShellDelegate getInstance() {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (!(activity instanceof ChromeActivity)) return null;
        return getInstance((ChromeActivity) activity);
    }

    private static VrShellDelegate getInstance(ChromeActivity activity) {
        if (!LibraryLoader.isInitialized()) return null;
        if (activity == null || !activitySupportsPresentation(activity)) return null;
        if (sInstance != null) return sInstance;
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return null;
        ThreadUtils.assertOnUiThread();
        sInstance = new VrShellDelegate(activity, wrapper);
        return sInstance;
    }

    private static boolean activitySupportsPresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity || activity instanceof CustomTabActivity
                || activity instanceof WebappActivity;
    }

    private static boolean activitySupportsAutopresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity;
    }

    private static boolean activitySupportsVrBrowsing(Activity activity) {
        if (activity instanceof ChromeTabbedActivity) return true;
        if (activity instanceof CustomTabActivity) {
            return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_CUSTOM_TAB_BROWSING);
        }
        return false;
    }

    private static boolean activitySupportsExitFeedback(Activity activity) {
        return activity instanceof ChromeTabbedActivity
                && ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_FEEDBACK);
    }

    /**
     * @return A helper class for creating VR-specific classes that may not be available at compile
     * time.
     */
    private static VrClassesWrapper getVrClassesWrapper() {
        if (sInstance != null) return sInstance.mVrClassesWrapper;
        return createVrClassesWrapper();
    }

    @SuppressWarnings("unchecked")
    /* package */ static VrClassesWrapper createVrClassesWrapper() {
        try {
            Class<? extends VrClassesWrapper> vrClassesBuilderClass =
                    (Class<? extends VrClassesWrapper>) Class.forName(
                            "org.chromium.chrome.browser.vr_shell.VrClassesWrapperImpl");
            Constructor<?> vrClassesBuilderConstructor = vrClassesBuilderClass.getConstructor();
            return (VrClassesWrapper) vrClassesBuilderConstructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e) {
            if (!(e instanceof ClassNotFoundException)) {
                Log.e(TAG, "Unable to instantiate VrClassesWrapper", e);
            }
            return null;
        }
    }

    // We need a custom Intent for entering VR in order to support VR in Custom Tabs. Custom Tabs
    // are not a singleInstance activity, so they cannot be resumed through Activity PendingIntents,
    // which is the typical way Daydream resumes your Activity. Instead, we use a broadcast intent
    // and then use the broadcast to bring ourselves back to the foreground.
    /* package */ static PendingIntent getEnterVrPendingIntent(ChromeActivity activity) {
        if (sVrBroadcastReceiver != null) sVrBroadcastReceiver.unregister();
        IntentFilter filter = new IntentFilter(VR_ENTRY_RESULT_ACTION);
        VrBroadcastReceiver receiver = new VrBroadcastReceiver(activity);
        // If we set sVrBroadcastReceiver then use it in registerReceiver, findBugs considers this
        // a thread-safety issue since it thinks the receiver isn't fully initialized before being
        // exposed to other threads. This isn't actually an issue in this case, but we need to set
        // sVrBroadcastReceiver after we're done using it here to fix the compile error.
        activity.registerReceiver(receiver, filter);
        sVrBroadcastReceiver = receiver;
        Intent vrIntent = new Intent(VR_ENTRY_RESULT_ACTION);
        vrIntent.setPackage(activity.getPackageName());
        return PendingIntent.getBroadcast(activity, 0, vrIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Registers the Intent to fire after phone inserted into a headset.
     */
    private static void registerDaydreamIntent(
            final VrDaydreamApi daydreamApi, final ChromeActivity activity) {
        if (sRegisteredDaydreamHook) return;
        if (!daydreamApi.registerDaydreamIntent(getEnterVrPendingIntent(activity))) return;
        sRegisteredDaydreamHook = true;
    }

    /**
     * Unregisters the Intent which registered by this context if any.
     */
    private static void unregisterDaydreamIntent(VrDaydreamApi daydreamApi) {
        if (!sRegisteredDaydreamHook) return;
        daydreamApi.unregisterDaydreamIntent();
        sRegisteredDaydreamHook = false;
    }

    /**
     * @return Whether or not VR Shell is currently enabled.
     */
    private static boolean isVrShellEnabled(int vrSupportLevel) {
        // Only enable ChromeVR (VrShell) on Daydream devices as it currently needs a Daydream
        // controller.
        if (vrSupportLevel != VR_DAYDREAM) return false;
        return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_SHELL);
    }

    /**
     *  @return Whether or not VR is supported on this platform.
     */
    private static boolean isVrEnabled() {
        return getVrClassesWrapper() != null;
    }

    private class VSyncEstimator {
        private static final long NANOS_PER_SECOND = 1000000000;

        private static final long VSYNC_TIMEBASE_UPDATE_DELTA = 1 * NANOS_PER_SECOND;
        private static final double MIN_VSYNC_INTERVAL_THRESHOLD = 1.2;

        // Estimates based on too few frames are unstable, probably anything above 2 is reasonable.
        // Higher numbers will reduce how frequently we update the native vsync base/interval.
        private static final int MIN_FRAME_COUNT = 5;

        private final long mReportedVSyncNanos;
        private final long mMinVSyncIntervalNanos;

        private long mVSyncTimebaseNanos;
        private long mVSyncIntervalNanos;
        private long mVSyncIntervalMicros;

        private int mVSyncCount;

        private final FrameCallback mCallback = new FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mNativeVrShellDelegate == 0) return;
                Choreographer.getInstance().postFrameCallback(this);
                ++mVSyncCount;
                if (mVSyncTimebaseNanos == 0) {
                    updateVSyncInterval(frameTimeNanos, mVSyncIntervalNanos);
                    return;
                }
                if (mVSyncCount < MIN_FRAME_COUNT) return;
                long elapsed = frameTimeNanos - mVSyncTimebaseNanos;
                // If you're hitting the assert below, you probably added the callback twice.
                assert elapsed != 0;
                long vSyncIntervalNanos = elapsed / mVSyncCount;
                if (vSyncIntervalNanos < mMinVSyncIntervalNanos) {
                    // We may run slow, but we should never run fast. If the VSync interval is too
                    // low, something is very wrong.
                    Log.v(TAG, "Error computing VSync interval. Resetting.");
                    assert false;
                    vSyncIntervalNanos = mReportedVSyncNanos;
                }
                updateVSyncInterval(frameTimeNanos, vSyncIntervalNanos);
            }
        };

        public VSyncEstimator() {
            Display display = ((WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE))
                                      .getDefaultDisplay();
            mReportedVSyncNanos = (long) ((1.0d / display.getRefreshRate()) * NANOS_PER_SECOND);
            mVSyncIntervalNanos = mReportedVSyncNanos;
            mMinVSyncIntervalNanos = (long) (mReportedVSyncNanos / MIN_VSYNC_INTERVAL_THRESHOLD);
        }

        void updateVSyncInterval(long frameTimeNanos, long vSyncIntervalNanos) {
            mVSyncIntervalNanos = vSyncIntervalNanos;
            long vSyncIntervalMicros = mVSyncIntervalNanos / 1000;
            if (vSyncIntervalMicros == mVSyncIntervalMicros
                    && frameTimeNanos - mVSyncTimebaseNanos < VSYNC_TIMEBASE_UPDATE_DELTA) {
                return;
            }
            mVSyncIntervalMicros = vSyncIntervalMicros;
            mVSyncTimebaseNanos = frameTimeNanos;
            mVSyncCount = 0;

            nativeUpdateVSyncInterval(
                    mNativeVrShellDelegate, mVSyncTimebaseNanos, mVSyncIntervalMicros);
        }

        public void pause() {
            Choreographer.getInstance().removeFrameCallback(mCallback);
        }

        public void resume() {
            mVSyncTimebaseNanos = 0;
            mVSyncCount = 0;
            Choreographer.getInstance().postFrameCallback(mCallback);
        }
    }

    private VrShellDelegate(ChromeActivity activity, VrClassesWrapper wrapper) {
        mActivity = activity;
        mVrClassesWrapper = wrapper;
        // If an activity isn't resumed at the point, it must have been paused.
        mPaused = ApplicationStatus.getStateForActivity(activity) != ActivityState.RESUMED;
        updateVrSupportLevel(null);
        mNativeVrShellDelegate = nativeInit();
        createNonPresentingNativeContext();
        mFeedbackFrequency = VrFeedbackStatus.getFeedbackFrequency();
        mEnterVrHandler = new Handler();
        mExpectPauseOrDonSucceeded = new Handler();
        mVSyncEstimator = new VSyncEstimator();
        ApplicationStatus.registerStateListenerForAllActivities(this);
        if (!mPaused) onResume();
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        switch (newState) {
            case ActivityState.DESTROYED:
                if (activity == mActivity) destroy();
                break;
            case ActivityState.PAUSED:
                if (activity == mActivity) onPause();
                // Other activities should only pause while we're paused due to Android lifecycle.
                assert mPaused;
                break;
            case ActivityState.STOPPED:
                if (activity == mActivity) onStop();
                break;
            case ActivityState.STARTED:
                if (activity == mActivity) onStart();
                break;
            case ActivityState.RESUMED:
                if (mInVr && activity != mActivity) {
                    if (mShowingDaydreamDoff) {
                        onExitVrResult(true);
                    } else {
                        shutdownVr(true /* disableVrMode */, false /* canReenter */,
                                false /* stayingInChrome */);
                    }
                }
                if (!activitySupportsPresentation(activity)) return;
                swapHostActivity((ChromeActivity) activity);
                onResume();
                break;
            default:
                break;
        }
    }

    // Called when an activity that supports VR is resumed, and attaches VrShellDelegate to that
    // activity.
    private void swapHostActivity(ChromeActivity activity) {
        assert mActivity != null;
        if (mActivity == activity) return;
        mActivity = activity;
        mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
        if (mNativeVrShellDelegate == 0 || mNonPresentingGvrContext == null) return;
        resetNonPresentingNativeContext();
    }

    private void maybeUpdateVrSupportLevel() {
        // If we're on Daydream support level, Chrome will get restarted by Android in response to
        // VrCore being updated/downgraded, so we don't need to check.
        if (mVrSupportLevel == VR_DAYDREAM) return;
        if (mVrClassesWrapper == null) return;
        int version = getVrCorePackageVersion();
        // If VrCore package hasn't changed, no need to update.
        if (version == mCachedVrCorePackageVersion) return;
        updateVrSupportLevel(version);
    }

    private int getVrCorePackageVersion() {
        return PackageUtils.getPackageVersion(
                ContextUtils.getApplicationContext(), VrCoreVersionChecker.VR_CORE_PACKAGE_ID);
    }

    /**
     * Updates mVrSupportLevel to the correct value. isVrCoreCompatible might return different value
     * at runtime.
     */
    // TODO(bshe): Find a place to call this function again, i.e. page refresh or onResume.
    private void updateVrSupportLevel(Integer vrCorePackageVersion) {
        if (mVrClassesWrapper == null) {
            mVrSupportLevel = VR_NOT_AVAILABLE;
            shutdownNonPresentingNativeContext();
            return;
        }
        if (vrCorePackageVersion == null) vrCorePackageVersion = getVrCorePackageVersion();
        mCachedVrCorePackageVersion = vrCorePackageVersion;

        if (mVrCoreVersionChecker == null) {
            mVrCoreVersionChecker = mVrClassesWrapper.createVrCoreVersionChecker();
        }
        if (mVrDaydreamApi == null) {
            mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
        }
        int supportLevel = getVrSupportLevel(
                mVrDaydreamApi, mVrCoreVersionChecker, mActivity.getActivityTab());
        if (supportLevel == mVrSupportLevel) return;
        mVrSupportLevel = supportLevel;
        resetNonPresentingNativeContext();
    }

    /**
     * Returns whether the device has support for Daydream.
     */
    /* package */ boolean hasDaydreamSupport() {
        return mVrSupportLevel == VR_DAYDREAM;
    }

    private void maybeSetPresentResult(boolean result, boolean donCompleted) {
        if (mNativeVrShellDelegate == 0 || !mRequestedWebVr) return;
        if (!result) {
            nativeSetPresentResult(mNativeVrShellDelegate, false);
            mRequestedWebVr = false;
        } else if (!isDaydreamCurrentViewer() || donCompleted) {
            // Wait until DON success to report presentation success.
            nativeSetPresentResult(mNativeVrShellDelegate, true);
            mRequestedWebVr = false;
        }
    }

    /**
     * Handle a successful VR DON flow, entering VR in the process unless we're unable to.
     * @return False if VR entry failed.
     */
    private boolean enterVrAfterDon() {
        if (mNativeVrShellDelegate == 0) return false;
        if (!canEnterVr(mActivity.getActivityTab(), true)) return false;

        // If the page is listening for vrdisplayactivate we assume it wants to request
        // presentation. Go into WebVR mode tentatively. If the page doesn't request presentation
        // in the vrdisplayactivate handler we will exit presentation later. Note that in the
        // case of autopresentation, we don't want to enter WebVR mode so that we can show the
        // splash screen. In this case, we enter WebVR mode when the site requests presentation.
        boolean tentativeWebVrMode =
                mListeningForWebVrActivateBeforePause && !mRequestedWebVr && !mAutopresentWebVr;
        if (tentativeWebVrMode) {
            nativeDisplayActivate(mNativeVrShellDelegate);
        }

        enterVr(tentativeWebVrMode);

        // The user has successfully completed a DON flow.
        RecordUserAction.record("VR.DON");

        return true;
    }

    private void enterVr(final boolean tentativeWebVrMode) {
        // We can't enter VR before the application resumes, or we encounter bizarre crashes
        // related to gpu surfaces.
        // TODO(mthiesse): Is the above comment still accurate? It may have been tied to our HTML
        // UI which is gone.
        assert !mPaused;
        if (mInVr) return;
        if (mNativeVrShellDelegate == 0) {
            cancelPendingVrEntry();
            return;
        }
        mVrClassesWrapper.setVrModeEnabled(mActivity, true);
        if (!isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mEnterVrHandler.post(new Runnable() {
                @Override
                public void run() {
                    enterVr(tentativeWebVrMode);
                }
            });
            return;
        }
        // We need to add VR UI asynchronously, or we get flashes of 2D content. Presumably this is
        // because adding the VR UI is slow and Android times out and decides to just show
        // something.
        mEnterVrHandler.post(new Runnable() {
            @Override
            public void run() {
                enterVrWithCorrectWindowMode(tentativeWebVrMode);
            }
        });
    }

    private void enterVrWithCorrectWindowMode(final boolean tentativeWebVrMode) {
        if (mInVr) return;
        if (mNativeVrShellDelegate == 0) {
            cancelPendingVrEntry();
            return;
        }
        mInVr = true;
        boolean donSuceeded = mDonSucceeded;
        mDonSucceeded = false;
        if (!createVrShell()) {
            maybeSetPresentResult(false, donSuceeded);
            cancelPendingVrEntry();
            mInVr = false;
            mVrDaydreamApi.launchVrHomescreen();
            return;
        }
        mExitedDueToUnsupportedMode = false;
        shutdownNonPresentingNativeContext();

        // Lock orientation to landscape after enter VR.
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ScreenOrientationDelegateManager.setOrientationDelegate(this);

        addVrViews();
        boolean webVrMode = mRequestedWebVr || tentativeWebVrMode && !mAutopresentWebVr;
        mVrShell.initializeNative(mActivity.getActivityTab(), webVrMode, mAutopresentWebVr,
                mActivity instanceof CustomTabActivity);
        mVrShell.setWebVrModeEnabled(webVrMode, false);

        // We're entering VR, but not in WebVr mode.
        mVrBrowserUsed = !webVrMode && !mAutopresentWebVr;

        // onResume needs to be called on GvrLayout after initialization to make sure DON flow works
        // properly.
        if (!mPaused) {
            mVrShell.resume();
            mVSyncEstimator.resume();
        }

        maybeSetPresentResult(true, donSuceeded);
        mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(this);
        removeBlackOverlayView();
        if (!donSuceeded && !mAutopresentWebVr && isDaydreamCurrentViewer()) {
            // TODO(mthiesse): This is a VERY dirty hack. We need to know whether or not entering VR
            // will trigger the DON flow, so that we can wait for it to complete before we let the
            // webVR page know that it can present. However, Daydream APIs currently make this
            // impossible for apps to know, so if the DON hasn't started after a delay we just
            // assume it will never start. Once we know whether or not entering VR will trigger the
            // DON flow, we should remove this. See b/63116739.
            mProbablyInDon = true;
            mExpectPauseOrDonSucceeded.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mProbablyInDon = false;
                    mDonSucceeded = true;
                    handleDonFlowSuccess();
                }
            }, EXPECT_DON_TIMEOUT_MS);
        }
    }

    private static void addBlackOverlayViewForActivity(ChromeActivity activity) {
        if (sBlackOverlayView != null) return;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        sBlackOverlayView = new View(activity);
        sBlackOverlayView.setBackgroundColor(Color.BLACK);
        activity.getWindow().addContentView(sBlackOverlayView, params);
    }

    private static void removeBlackOverlayView() {
        if (sBlackOverlayView != null) UiUtils.removeViewFromParent(sBlackOverlayView);
        sBlackOverlayView = null;
    }

    private static boolean isTrustedDaydreamIntent(Intent intent) {
        return isVrIntent(intent)
                && IntentHandler.isIntentFromTrustedApp(intent, DAYDREAM_HOME_PACKAGE);
    }

    private void onAutopresentIntent() {
        // Autopresent intents are only expected from trusted first party apps while
        // we're not in vr.
        assert !mInVr;
        mDonSucceeded = true;
        mAutopresentWebVr = true;
    }

    private void onAutopresentUnsupported() {
        // Auto-presentation is unsupported, but we still need to remove the black overlay before we
        // exit to Daydream so that the user doesn't see black when they come back to Chrome. The
        // overlay will be removed when we get paused by Daydream.
        assert !mInVr;
        mNeedsAnimationCancel = false;
        mVrDaydreamApi.launchVrHomescreen();
    }

    private void onVrIntent() {
        // We assume that when we get a VR intent, we're in the headset.
        mNeedsAnimationCancel = true;
    }

    /**
     * This is called every time ChromeActivity gets a new intent.
     */
    public static void onNewIntentWithNative(ChromeActivity activity, Intent intent) {
        if (!isVrIntent(intent) || !activitySupportsVrBrowsing(activity)) return;
        VrShellDelegate instance = getInstance(activity);
        if (instance == null) return;
        instance.onVrIntent();
        if (isTrustedDaydreamIntent(intent)) {
            if (!ChromeFeatureList.isEnabled(ChromeFeatureList.WEBVR_AUTOPRESENT)
                    || !activitySupportsPresentation(activity)
                    || !isVrShellEnabled(instance.mVrSupportLevel)) {
                instance.onAutopresentUnsupported();
                return;
            }
            instance.onAutopresentIntent();
        }
    }

    /**
     * This is called when ChromeTabbedActivity gets a new intent before native is initialized.
     */
    public static void maybeHandleVrIntentPreNative(ChromeActivity activity, Intent intent) {
        if (isTrustedDaydreamIntent(intent)) {
            // We add a black overlay view so that we can show black while the VR UI is loading.
            // Note that this alone isn't sufficient to prevent 2D UI from showing when
            // auto-presenting WebVR. See comment about the custom animation in {@link
            // getVrIntentOptions}.
            addBlackOverlayViewForActivity(activity);
        }
    }

    /**
     * @return An intent that will launch a VR activity that will prompt the
     * user to take off their headset and foward the freIntent to the standard
     * 2D FRE activity.
     */
    public static Intent setupVrFreIntent(Context context, Intent freIntent) {
        if (!isVrEnabled()) return freIntent;
        Intent intent = new Intent();
        intent.setClassName(context, VrFirstRunActivity.class.getName());
        intent.putExtra(VR_FRE_INTENT_EXTRA, new Intent(freIntent));
        intent.putExtra(DAYDREAM_VR_EXTRA, true);
        return intent;
    }

    /**
     * @return Whether or not the given intent is a VR-specific intent.
     */
    public static boolean isVrIntent(Intent intent) {
        // For simplicity, we only return true here if VR is enabled on the platform.
        return IntentUtils.safeGetBooleanExtra(intent, DAYDREAM_VR_EXTRA, false) && isVrEnabled();
    }

    /*
     * Remove VR-specific extras from the given intent.
     */
    public static void removeVrExtras(Intent intent) {
        intent.removeExtra(DAYDREAM_VR_EXTRA);
    }

    /**
     * @return Options that a VR-specific Chrome activity should be launched with.
     */
    public static Bundle getVrIntentOptions(Context context) {
        // These options are used to start the Activity with a custom animation to keep it hidden
        // for a few hundread milliseconds - enough time for us to draw the first black view.
        // The animation is sufficient to hide the 2D screenshot but not to the 2D UI while the
        // WebVR page is being loaded because the animation is somehow cancelled when we try to
        // enter VR (I don't know what's cancelling it). To hide the 2D UI, we resort to the black
        // overlay view added in {@link startWithVrIntentPreNative}.
        return ActivityOptions.makeCustomAnimation(context, R.anim.stay_hidden, 0).toBundle();
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (mInVr && !isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean canUnlockOrientation(Activity activity, int defaultOrientation) {
        if (mActivity == activity && mRestoreOrientation != null) {
            mRestoreOrientation = defaultOrientation;
            return false;
        }
        return true;
    }

    @Override
    public boolean canLockOrientation() {
        return false;
    }

    private boolean isWindowModeCorrectForVr() {
        int flags = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        int orientation = mActivity.getResources().getConfiguration().orientation;
        // Mask the flags to only those that we care about.
        return (flags & VR_SYSTEM_UI_FLAGS) == VR_SYSTEM_UI_FLAGS
                && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setWindowModeForVr(int requestedOrientation) {
        if (mRestoreOrientation == null) {
            mRestoreOrientation = mActivity.getRequestedOrientation();
        }
        mActivity.setRequestedOrientation(requestedOrientation);
        ScreenOrientationDelegateManager.setOrientationDelegate(this);
        setupVrModeWindowFlags();
    }

    private void restoreWindowMode() {
        if (mRestoreOrientation != null) mActivity.setRequestedOrientation(mRestoreOrientation);
        ScreenOrientationDelegateManager.setOrientationDelegate(null);
        mRestoreOrientation = null;
        clearVrModeWindowFlags();
    }

    /* package */ boolean canEnterVr(Tab tab, boolean justCompletedDon) {
        if (!LibraryLoader.isInitialized()) return false;
        if (mVrSupportLevel == VR_NOT_AVAILABLE || mNativeVrShellDelegate == 0) return false;

        // If vr shell is not enabled and this is not a web vr request, then return false.
        boolean presenting = mRequestedWebVr || mListeningForWebVrActivate
                || (justCompletedDon && mListeningForWebVrActivateBeforePause) || mAutopresentWebVr;
        if (!isVrShellEnabled(mVrSupportLevel) && !presenting) return false;

        // TODO(mthiesse): When we have VR UI for opening new tabs, etc., allow VR Shell to be
        // entered without any current tabs.
        if (tab == null) return false;

        // For now we don't handle sad tab page. crbug.com/661609
        if (tab.isShowingSadTab()) return false;
        return true;
    }

    @CalledByNative
    private void presentRequested() {
        mRequestedWebVr = true;
        switch (enterVrInternal()) {
            case ENTER_VR_NOT_NECESSARY:
                mVrShell.setWebVrModeEnabled(true, !mAutopresentWebVr);
                maybeSetPresentResult(true, true);
                break;
            case ENTER_VR_CANCELLED:
                maybeSetPresentResult(false, mDonSucceeded);
                break;
            case ENTER_VR_REQUESTED:
                break;
            case ENTER_VR_SUCCEEDED:
                maybeSetPresentResult(true, mDonSucceeded);
                break;
            default:
                Log.e(TAG, "Unexpected enum.");
        }
        mAutopresentWebVr = false;
    }

    /**
     * Enters VR Shell if necessary, displaying browser UI and tab contents in VR.
     */
    @EnterVRResult
    private int enterVrInternal() {
        if (mPaused) return ENTER_VR_CANCELLED;
        if (mInVr) return ENTER_VR_NOT_NECESSARY;
        if (mWaitingForVrTimeout) return ENTER_VR_CANCELLED;

        // Update VR support level as it can change at runtime
        maybeUpdateVrSupportLevel();
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return ENTER_VR_CANCELLED;
        if (!canEnterVr(mActivity.getActivityTab(), false)) return ENTER_VR_CANCELLED;
        enterVr(false);
        return ENTER_VR_REQUESTED;
    }

    private void requestToExitVrInternal(
            OnExitVrRequestListener listener, @UiUnsupportedMode int reason) {
        assert listener != null;
        // If we are currently processing another request or we are not in VR, deny the request.
        if (sInstance.mOnExitVrRequestListener != null || !sInstance.mInVr) {
            listener.onDenied();
            return;
        }
        mOnExitVrRequestListener = listener;
        mVrShell.requestToExitVr(reason);
    }

    @CalledByNative
    private boolean exitWebVRPresent() {
        if (!mInVr) return false;
        if (!isVrShellEnabled(mVrSupportLevel) || !isDaydreamCurrentViewer()
                || !activitySupportsVrBrowsing(mActivity)) {
            if (isDaydreamCurrentViewer() && showDoff(false /* optional */)) return false;
            shutdownVr(
                    true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
        } else {
            mVrBrowserUsed = true;
            mAutopresentWebVr = false;
            mVrShell.setWebVrModeEnabled(false, false);
        }
        return true;
    }

    private boolean cancelStartupAnimationIfNeeded() {
        if (!mNeedsAnimationCancel) return false;
        mCancellingEntryAnimation = true;
        Bundle options = ActivityOptions.makeCustomAnimation(mActivity, 0, 0).toBundle();
        mActivity.startActivity(new Intent(mActivity, VrCancelAnimationActivity.class), options);
        mNeedsAnimationCancel = false;
        return true;
    }

    private void onResume() {
        if (cancelStartupAnimationIfNeeded()) return;

        mPaused = false;

        maybeUpdateVrSupportLevel();

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            if (mNativeVrShellDelegate != 0) nativeOnResume(mNativeVrShellDelegate);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        if (mVrSupportLevel != VR_DAYDREAM) return;
        if (isVrShellEnabled(mVrSupportLevel) && activitySupportsVrBrowsing(mActivity)) {
            // Perform slow initialization asynchronously.
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (!mPaused) {
                        registerDaydreamIntent(mVrDaydreamApi, mActivity);
                        createNonPresentingNativeContext();
                    }
                }
            });
        }

        if (mInVr) {
            mVrShell.resume();
            mVSyncEstimator.resume();
        }

        if (mDonSucceeded) {
            mCancellingEntryAnimation = false;
            handleDonFlowSuccess();
        } else if (mProbablyInDon) {
            // This means the user backed out of the DON flow, and we won't be entering VR.
            maybeSetPresentResult(false, mDonSucceeded);
            shutdownVr(true, false, false);
            mWaitingForVrTimeout = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mWaitingForVrTimeout = false;
                }
            }, ENTER_VR_FAILED_TIMEOUT_MS);
        }

        mProbablyInDon = false;
    }

    private void handleDonFlowSuccess() {
        if (mInVr) {
            maybeSetPresentResult(true, mDonSucceeded);
            mDonSucceeded = false;
            return;
        }
        // If we fail to enter VR when we should have entered VR, return to the home screen.
        if (!mInVr && !enterVrAfterDon()) {
            cancelPendingVrEntry();
            maybeSetPresentResult(false, mDonSucceeded);
            mVrDaydreamApi.launchVrHomescreen();
            mDonSucceeded = false;
        }
    }

    private void onPause() {
        mPaused = true;
        if (mCancellingEntryAnimation) return;
        mExpectPauseOrDonSucceeded.removeCallbacksAndMessages(null);
        unregisterDaydreamIntent(mVrDaydreamApi);
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return;

        if (mInVr) mVSyncEstimator.pause();

        // TODO(ymalik): We should be able to remove this if we handle it for multi-window in
        // {@link onMultiWindowModeChanged} since we're calling it in onStop.
        if (!mInVr) cancelPendingVrEntry();

        // When the active web page has a vrdisplayactivate event handler,
        // mListeningForWebVrActivate should be set to true, which means a vrdisplayactive event
        // should be fired once DON flow finished. However, DON flow will pause our activity,
        // which makes the active page becomes invisible. And the event fires before the active
        // page becomes visible again after DON finished. So here we remember the value of
        // mListeningForWebVrActivity before pause and use this value to decide if
        // vrdisplayactivate event should be dispatched in enterVRFromIntent.
        mListeningForWebVrActivateBeforePause = mListeningForWebVrActivate;

        if (mNativeVrShellDelegate != 0) nativeOnPause(mNativeVrShellDelegate);

        mIsDaydreamCurrentViewer = null;
    }

    private void onStart() {
        if (mDonSucceeded) {
            // We're about to enter VR, so set the VR Mode as early as possible to avoid screen
            // brightness flickering while in the headset.
            mVrClassesWrapper.setVrModeEnabled(mActivity, true);
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void onStop() {
        cancelPendingVrEntry();
        assert !mCancellingEntryAnimation;
        // We defer pausing of VrShell until the app is stopped to keep head tracking working for
        // as long as possible while going to daydream home.
        if (mInVr) mVrShell.pause();
        if (mShowingDaydreamDoff || mProbablyInDon) return;

        // TODO(mthiesse): When the user resumes Chrome in a 2D context, we don't want to tear down
        // VR UI, so for now, exit VR.
        shutdownVr(true /* disableVrMode */, true /* canReenter */, false /* stayingInChrome */);
    }

    private boolean onBackPressedInternal() {
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return false;
        cancelPendingVrEntry();
        if (!mInVr) return false;
        shutdownVr(true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
        return true;
    }

    private boolean showDoff(boolean optional) {
        if (!isDaydreamCurrentViewer()) return false;
        if (!mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) return false;
        mShowingDaydreamDoff = true;
        mDoffOptional = optional;
        return true;
    }

    private void onExitVrResult(boolean success) {
        assert mVrSupportLevel != VR_NOT_AVAILABLE;

        // We may have manually handled the exit early by swapping to another Chrome activity that
        // supports VR while in the DOFF activity. If that happens we want to exit early when the
        // real DOFF flow calls us back.
        if (!mShowingDaydreamDoff) return;

        // If Doff is not optional and user backed out, keep trying to exit.
        if (!mDoffOptional && !success && showDoff(false /* optional */)) return;

        mShowingDaydreamDoff = false;
        if (success) {
            // If DOFF didn't succeed(for example, user clicked back button at DOFF screen), we
            // don't know if user really intends to exit VR or not at this point. So we shouldn't
            // call callOnExitVrRequestListener to tell the listener that the exit VR request has
            // succeeded or been denied.
            callOnExitVrRequestListener(success);
            shutdownVr(true /* disableVrMode */, false /* canReenter */,
                    !mExitingCct /* stayingInChrome */);
            if (mExitingCct) ((CustomTabActivity) mActivity).finishAndClose(false);
        }
        mExitingCct = false;
    }

    private boolean isDaydreamCurrentViewer() {
        if (mIsDaydreamCurrentViewer == null) {
            mIsDaydreamCurrentViewer = mVrDaydreamApi.isDaydreamCurrentViewer();
        }
        return mIsDaydreamCurrentViewer;
    }

    private void resetNonPresentingNativeContext() {
        shutdownNonPresentingNativeContext();
        createNonPresentingNativeContext();
    }

    private void createNonPresentingNativeContext() {
        if (mNonPresentingGvrContext != null) return;
        if (mVrClassesWrapper == null) return;
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return;
        if (mNativeVrShellDelegate == 0) return;
        mNonPresentingGvrContext = mVrClassesWrapper.createNonPresentingGvrContext(mActivity);
        if (mNonPresentingGvrContext == null) return;
        nativeUpdateNonPresentingContext(
                mNativeVrShellDelegate, mNonPresentingGvrContext.getNativeGvrContext());
    }

    private void shutdownNonPresentingNativeContext() {
        if (mNonPresentingGvrContext == null) return;
        if (mNativeVrShellDelegate != 0) {
            nativeUpdateNonPresentingContext(mNativeVrShellDelegate, 0);
        }
        mNonPresentingGvrContext.shutdown();
        mNonPresentingGvrContext = null;
    }

    @CalledByNative
    private void setListeningForWebVrActivate(boolean listening) {
        // Non-Daydream devices may not have the concept of display activate. So disable
        // mListeningForWebVrActivate for them.
        if (mVrSupportLevel != VR_DAYDREAM) return;
        mListeningForWebVrActivate = listening;
        if (mPaused) return;
        if (listening) {
            registerDaydreamIntent(mVrDaydreamApi, mActivity);
            if (mAutopresentWebVr) {
                // Dispatch vrdisplayactivate so that the WebVr page can call requestPresent
                // to start presentation.
                // TODO(ymalik): There will be a delay between when we're asked to autopresent and
                // when the WebVr site calls requestPresent. In this time, the user sees 2D Chrome
                // UI which is suboptimal.
                nativeDisplayActivate(mNativeVrShellDelegate);
            }
        } else if (!canEnterVr(mActivity.getActivityTab(), false)) {
            unregisterDaydreamIntent(mVrDaydreamApi);
        }
    }

    private void cancelPendingVrEntry() {
        // Ensure we can't asynchronously enter VR after trying to exit it.
        mEnterVrHandler.removeCallbacksAndMessages(null);
        removeBlackOverlayView();
        mDonSucceeded = false;
        if (!mShowingDaydreamDoff) {
            mVrClassesWrapper.setVrModeEnabled(mActivity, false);
            restoreWindowMode();
        }
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void shutdownVr(boolean disableVrMode, boolean canReenter, boolean stayingInChrome) {
        cancelPendingVrEntry();
        if (!mInVr) return;

        if (mShowingDaydreamDoff) {
            onExitVrResult(true);
            return;
        }
        mInVr = false;
        mAutopresentWebVr = false;

        // The user has exited VR.
        RecordUserAction.record("VR.DOFF");

        restoreWindowMode();
        mVrShell.pause();
        mVSyncEstimator.pause();
        removeVrViews();
        destroyVrShell();
        if (disableVrMode) mVrClassesWrapper.setVrModeEnabled(mActivity, false);

        promptForFeedbackIfNeeded(stayingInChrome);
        if (stayingInChrome) createNonPresentingNativeContext();

        assert mOnExitVrRequestListener == null;
    }

    private void callOnExitVrRequestListener(boolean success) {
        if (mOnExitVrRequestListener != null) {
            if (success) {
                mOnExitVrRequestListener.onSucceeded();
            } else {
                mOnExitVrRequestListener.onDenied();
            }
        }
        mOnExitVrRequestListener = null;
    }

    private void showDoffAndExitVrInternal(boolean optional) {
        if (mShowingDaydreamDoff) return;
        if (showDoff(optional)) return;
        shutdownVr(true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
    }

    /* package */ void onExitVrRequestResult(boolean shouldExit) {
        assert mOnExitVrRequestListener != null;
        if (shouldExit) {
            mExitedDueToUnsupportedMode = true;
            showDoffAndExitVrInternal(true);
        } else {
            callOnExitVrRequestListener(false);
        }
    }

    /* package */ void exitCct() {
        if (mShowingDaydreamDoff) return;
        assert mActivity instanceof CustomTabActivity;
        if (mInVrAtChromeLaunch != null && !mInVrAtChromeLaunch) {
            if (showDoff(true /* optional */)) {
                mExitingCct = true;
                return;
            }
            shutdownVr(
                    true /* disableVrMode */, false /* canReenter */, false /* stayingInChrome */);
            ((CustomTabActivity) mActivity).finishAndClose(false);
        }
    }

    private static void startFeedback(Tab tab) {
        // TODO(ymalik): This call will connect to the Google Services api which can be slow. Can we
        // connect to it beforehand when we know that we'll be prompting for feedback?
        HelpAndFeedback.getInstance(tab.getActivity())
                .showFeedback(tab.getActivity(), tab.getProfile(), tab.getUrl(),
                        ContextUtils.getApplicationContext().getPackageName() + "."
                                + FEEDBACK_REPORT_TYPE);
    }

    private static void promptForFeedback(final Tab tab) {
        final ChromeActivity activity = tab.getActivity();
        SimpleConfirmInfoBarBuilder.Listener listener = new SimpleConfirmInfoBarBuilder.Listener() {
            @Override
            public void onInfoBarDismissed() {}

            @Override
            public boolean onInfoBarButtonClicked(boolean isPrimary) {
                if (isPrimary) {
                    startFeedback(tab);
                } else {
                    VrFeedbackStatus.setFeedbackOptOut(true);
                }
                return false;
            }
        };

        SimpleConfirmInfoBarBuilder.create(tab, listener,
                InfoBarIdentifier.VR_FEEDBACK_INFOBAR_ANDROID, R.drawable.vr_services,
                activity.getString(R.string.vr_shell_feedback_infobar_description),
                activity.getString(R.string.vr_shell_feedback_infobar_feedback_button),
                activity.getString(R.string.no_thanks), true /* autoExpire  */);
    }

    /**
     * Prompts the user to enter feedback for their VR Browsing experience.
     */
    private void promptForFeedbackIfNeeded(boolean stayingInChrome) {
        // We only prompt for feedback if:
        // 1) The user hasn't explicitly opted-out of it in the past
        // 2) The user has performed VR browsing
        // 3) The user is exiting VR and going back into 2D Chrome
        // 4) We're not exiting to complete an unsupported VR action in 2D (e.g. viewing PageInfo)
        // 5) Every n'th visit (where n = mFeedbackFrequency)

        if (!activitySupportsExitFeedback(mActivity)) return;
        if (!stayingInChrome) return;
        if (VrFeedbackStatus.getFeedbackOptOut()) return;
        if (!mVrBrowserUsed) return;
        if (mExitedDueToUnsupportedMode) return;

        int exitCount = VrFeedbackStatus.getUserExitedAndEntered2DCount();
        VrFeedbackStatus.setUserExitedAndEntered2DCount((exitCount + 1) % mFeedbackFrequency);

        if (exitCount > 0) return;

        promptForFeedback(mActivity.getActivityTab());
    }

    private static boolean isVrCoreCompatible(
            final VrCoreVersionChecker versionChecker, final Tab tabToShowInfobarIn) {
        final int vrCoreCompatibility = versionChecker.getVrCoreCompatibility();
        boolean needsUpdate = vrCoreCompatibility == VrCoreCompatibility.VR_NOT_AVAILABLE
                || vrCoreCompatibility == VrCoreCompatibility.VR_OUT_OF_DATE;
        if (tabToShowInfobarIn != null && needsUpdate) {
            ThreadUtils.assertOnUiThread();
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    promptToUpdateVrServices(vrCoreCompatibility, tabToShowInfobarIn);
                }
            });
        }

        return vrCoreCompatibility == VrCoreCompatibility.VR_READY;
    }

    private static void promptToUpdateVrServices(int vrCoreCompatibility, Tab tab) {
        final Activity activity = tab.getActivity();
        String infobarText;
        String buttonText;
        if (vrCoreCompatibility == VrCoreCompatibility.VR_NOT_AVAILABLE) {
            // Supported, but not installed. Ask user to install instead of upgrade.
            infobarText = activity.getString(R.string.vr_services_check_infobar_install_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_install_button);
        } else if (vrCoreCompatibility == VrCoreCompatibility.VR_OUT_OF_DATE) {
            infobarText = activity.getString(R.string.vr_services_check_infobar_update_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_update_button);
        } else {
            Log.e(TAG, "Unknown VrCore compatibility: " + vrCoreCompatibility);
            return;
        }

        SimpleConfirmInfoBarBuilder.Listener listener = new SimpleConfirmInfoBarBuilder.Listener() {
            @Override
            public void onInfoBarDismissed() {}

            @Override
            public boolean onInfoBarButtonClicked(boolean isPrimary) {
                activity.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(VR_CORE_MARKET_URI)));
                return false;
            }
        };
        SimpleConfirmInfoBarBuilder.create(tab, listener,
                InfoBarIdentifier.VR_SERVICES_UPGRADE_ANDROID, R.drawable.vr_services, infobarText,
                buttonText, null, true);
    }

    private boolean createVrShell() {
        assert mVrShell == null;
        if (mVrClassesWrapper == null) return false;
        if (mActivity.getCompositorViewHolder() == null) return false;
        mTabModelSelector = mActivity.getCompositorViewHolder().detachForVr();
        if (mTabModelSelector == null) return false;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            mVrShell = mVrClassesWrapper.createVrShell(mActivity, this, mTabModelSelector);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return mVrShell != null;
    }

    private void addVrViews() {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(mVrShell.getContainer(), params);
        mActivity.onEnterVr();
    }

    private void removeVrViews() {
        mVrShell.onBeforeWindowDetached();
        mActivity.onExitVr();
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mVrShell.getContainer());
    }

    private void setupVrModeWindowFlags() {
        if (mRestoreSystemUiVisibilityFlag == -1) {
            mRestoreSystemUiVisibilityFlag = mActivity.getWindow().getDecorView()
                    .getSystemUiVisibility();
        }
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActivity.getWindow().getDecorView().setSystemUiVisibility(VR_SYSTEM_UI_FLAGS);
    }

    private void clearVrModeWindowFlags() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mRestoreSystemUiVisibilityFlag != -1) {
            mActivity.getWindow().getDecorView()
                    .setSystemUiVisibility(mRestoreSystemUiVisibilityFlag);
        }
        mRestoreSystemUiVisibilityFlag = -1;
    }

    /**
     * Clean up VrShell, and associated native objects.
     */
    private void destroyVrShell() {
        if (mVrShell != null) {
            mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(null);
            mVrShell.teardown();
            mVrShell = null;
            if (mActivity.getCompositorViewHolder() != null) {
                mActivity.getCompositorViewHolder().onExitVr(mTabModelSelector);
            }
            mTabModelSelector = null;
        }
    }

    /**
     * @param api The VrDaydreamApi object this delegate will use instead of the default one
     */
    @VisibleForTesting
    public void overrideDaydreamApiForTesting(VrDaydreamApi api) {
        mVrDaydreamApi = api;
    }

    /**
     * @return The VrShell for the VrShellDelegate instance
     */
    @VisibleForTesting
    public static VrShell getVrShellForTesting() {
        return sInstance == null ? null : sInstance.mVrShell;
    }

    /**
     * @param versionChecker The VrCoreVersionChecker object this delegate will use
     */
    @VisibleForTesting
    public void overrideVrCoreVersionCheckerForTesting(VrCoreVersionChecker versionChecker) {
        mVrCoreVersionChecker = versionChecker;
        updateVrSupportLevel(null);
    }

    /**
     * @param frequency Sets how often to show the feedback prompt.
     */
    @VisibleForTesting
    public void setFeedbackFrequencyForTesting(int frequency) {
        mFeedbackFrequency = frequency;
    }

    @VisibleForTesting
    public boolean isListeningForWebVrActivate() {
        return mListeningForWebVrActivate;
    }

    @VisibleForTesting
    public boolean isClearActivatePending() {
        assert mNativeVrShellDelegate != 0;
        return nativeIsClearActivatePending(mNativeVrShellDelegate);
    }

    /**
     * @return Pointer to the native VrShellDelegate object.
     */
    @CalledByNative
    private long getNativePointer() {
        return mNativeVrShellDelegate;
    }

    @CalledByNative
    private long getVrCoreInfo() {
        assert mVrCoreVersionChecker != null;
        return mVrCoreVersionChecker.makeNativeVrCoreInfo();
    }

    private void destroy() {
        if (sInstance == null) return;
        shutdownVr(false /* disableVrMode */, false /* canReenter */, false /* stayingInChrome */);
        if (mNativeVrShellDelegate != 0) nativeDestroy(mNativeVrShellDelegate);
        mNativeVrShellDelegate = 0;
        shutdownNonPresentingNativeContext();
        ApplicationStatus.unregisterActivityStateListener(this);
        sInstance = null;
    }

    private native long nativeInit();
    private static native void nativeOnLibraryAvailable();
    private native void nativeSetPresentResult(long nativeVrShellDelegate, boolean result);
    private native void nativeDisplayActivate(long nativeVrShellDelegate);
    private native void nativeUpdateVSyncInterval(
            long nativeVrShellDelegate, long timebaseNanos, long intervalMicros);
    private native void nativeOnPause(long nativeVrShellDelegate);
    private native void nativeOnResume(long nativeVrShellDelegate);
    private native void nativeUpdateNonPresentingContext(long nativeVrShellDelegate, long context);
    private native boolean nativeIsClearActivatePending(long nativeVrShellDelegate);
    private native void nativeDestroy(long nativeVrShellDelegate);
}
