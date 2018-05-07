// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.util.DisplayMetrics;
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
import org.chromium.chrome.browser.ApplicationLifetime;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.infobar.InfoBarIdentifier;
import org.chromium.chrome.browser.infobar.SimpleConfirmInfoBarBuilder;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.webapps.WebappActivity;
import org.chromium.content_public.browser.ScreenOrientationDelegate;
import org.chromium.content_public.browser.ScreenOrientationDelegateManager;
import org.chromium.ui.UiUtils;
import org.chromium.ui.display.DisplayAndroidManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr")
public class VrShellDelegate
        implements ApplicationStatus.ActivityStateListener, View.OnSystemUiVisibilityChangeListener,
                   ScreenOrientationDelegate {
    public static final int VR_SYSTEM_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final String TAG = "VrShellDelegate";
    private static final boolean DEBUG_LOGS = false;

    // Pseudo-random number to avoid request id collisions. Result codes must fit in lower 16 bits
    // when used with startActivityForResult...
    public static final int EXIT_VR_RESULT = 7212;
    public static final int VR_SERVICES_UPDATE_RESULT = 7213;
    public static final int GVR_KEYBOARD_UPDATE_RESULT = 7214;

    // Android N doesn't allow us to dynamically control the preview window based on headset mode,
    // so we used an animation to hide the preview window instead.
    public static final boolean USE_HIDE_ANIMATION = Build.VERSION.SDK_INT < Build.VERSION_CODES.O;

    private static final int ENTER_VR_NOT_NECESSARY = 0;
    private static final int ENTER_VR_CANCELLED = 1;
    private static final int ENTER_VR_REQUESTED = 2;
    private static final int ENTER_VR_SUCCEEDED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENTER_VR_NOT_NECESSARY, ENTER_VR_CANCELLED, ENTER_VR_REQUESTED, ENTER_VR_SUCCEEDED})
    private @interface EnterVRResult {}

    // Linter and formatter disagree on how the line below should be formatted.
    /* package */
    static final String VR_ENTRY_RESULT_ACTION =
            "org.chromium.chrome.browser.vr_shell.VrEntryResult";

    private static final String VR_INTENT_DISPATCHER_COMPONENT =
            "com.google.android.apps.chrome.VrIntentDispatcher";

    private static final long REENTER_VR_TIMEOUT_MS = 1000;
    private static final int EXPECT_DON_TIMEOUT_MS = 2000;

    private static final String FEEDBACK_REPORT_TYPE = "USER_INITIATED_FEEDBACK_REPORT_VR";

    private static final String VR_CORE_MARKET_URI =
            "market://details?id=" + VrCoreVersionChecker.VR_CORE_PACKAGE_ID;

    private static final String GVR_KEYBOARD_PACKAGE_ID = "com.google.android.vr.inputmethod";
    private static final String GVR_KEYBOARD_MARKET_URI =
            "market://details?id=" + GVR_KEYBOARD_PACKAGE_ID;

    // This value is intentionally probably overkill. This is the time we need to wait from when
    // Chrome is resumed, to when Chrome actually renders a black frame, so that we can cancel the
    // stay_hidden animation and not see a white monoscopic frame in-headset. 150ms is definitely
    // too short, 250ms is sometimes too short for debug builds. 500ms should hopefully be safe even
    // under fairly exceptional conditions, and won't delay entering VR a noticeable amount given
    // how slow it already is.
    private static final int WINDOW_FADE_ANIMATION_DURATION_MS = 500;

    private static VrShellDelegate sInstance;
    private static VrBroadcastReceiver sVrBroadcastReceiver;
    private static boolean sRegisteredDaydreamHook;
    private static boolean sAddedBlackOverlayView;
    private static boolean sRegisteredVrAssetsComponent;
    private static Boolean sIconComponentEnabled;

    private ChromeActivity mActivity;

    private @VrSupportLevel int mVrSupportLevel;
    private int mCachedVrCorePackageVersion;
    private int mCachedGvrKeyboardPackageVersion;

    // How often to prompt the user to enter VR feedback.
    private int mFeedbackFrequency;

    private final VrClassesWrapper mVrClassesWrapper;
    private VrShell mVrShell;
    private VrDaydreamApi mVrDaydreamApi;
    private Boolean mIsDaydreamCurrentViewer;
    private VrCoreVersionChecker mVrCoreVersionChecker;

    private boolean mProbablyInDon;
    private boolean mInVr;
    private final Handler mExpectPauseOrDonSucceeded;
    private boolean mNeedsAnimationCancel;
    private boolean mCancellingEntryAnimation;

    // Whether or not the VR Device ON flow succeeded. If this is true it means the user has a VR
    // headset on, but we haven't switched into VR mode yet.
    // See further documentation here: https://developers.google.com/vr/daydream/guides/vr-entry
    private boolean mDonSucceeded;
    // Best effort whether or not the system was in VR when Chrome launched.
    private Boolean mInVrAtChromeLaunch;
    private boolean mShowingDaydreamDoff;
    private boolean mShowingExitVrPrompt;
    private boolean mDoffOptional;
    // Listener to be called once we exited VR due to to an unsupported mode, e.g. the user clicked
    // the URL bar security icon.
    private OnExitVrRequestListener mOnExitVrRequestListener;
    private boolean mExitedDueToUnsupportedMode;
    private boolean mExitingCct;
    private boolean mPaused;
    private boolean mStopped;
    private boolean mVisible;
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
    // If set to true, we attempt to enter VR mode when the activity is resumed.
    private boolean mEnterVrOnStartup;
    private boolean mExitCctOnStartup;

    private boolean mInternalIntentUsedToStartVr;

    // Set to true if performed VR browsing at least once. That is, this was not simply a WebVr
    // presentation experience.
    private boolean mVrBrowserUsed;

    private int mExpectedDensityChange;

    // Gets run when the user exits VR mode by clicking the 'x' button or system UI back button.
    private Runnable mCloseButtonListener;

    // Gets run when the user exits VR mode by clicking the Gear button.
    private Runnable mSettingsButtonListener;

    private static final List<VrModeObserver> sVrModeObservers = new ArrayList<>();

    /**
     * Used to observe changes to whether Chrome is currently being viewed in VR.
     */
    public static interface VrModeObserver {
        /**
         * Called when Chrome enters VR rendering mode.
         */
        void onEnterVr();

        /**
         * Called when Chrome exits VR rendering mode.
         */
        void onExitVr();
    }

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

            // Note that even though we are definitely entering VR here, we don't want to set
            // the window mode yet, as setting the window mode while we're in the background can
            // racily lead to that window mode change essentially being ignored, with future
            // attempts to set the same window mode also being ignored.

            sInstance.mDonSucceeded = true;
            sInstance.mProbablyInDon = false;
            sInstance.mExpectPauseOrDonSucceeded.removeCallbacksAndMessages(null);
            sInstance.mVrClassesWrapper.setVrModeEnabled(sInstance.mActivity, true);
            if (DEBUG_LOGS) Log.i(TAG, "VrBroadcastReceiver onReceive");

            // We add a black overlay view so that we can show black while the VR UI is loading.
            if (!sInstance.mInVr) addBlackOverlayViewForActivity(sInstance.mActivity);

            if (sInstance.mPaused) {
                if (sInstance.mInVrAtChromeLaunch == null) sInstance.mInVrAtChromeLaunch = false;

                if (activity instanceof ChromeTabbedActivity) {
                    // We can special case singleInstance activities like CTA to avoid having to use
                    // moveTaskToFront. Using moveTaskToFront prevents us from disabling window
                    // animations, and causes the system UI to show up during the preview window and
                    // window animations.
                    Intent launchIntent = new Intent(activity, activity.getClass());
                    launchIntent = sInstance.mVrDaydreamApi.setupVrIntent(launchIntent);
                    sInstance.mInternalIntentUsedToStartVr = true;
                    sInstance.mVrDaydreamApi.launchInVr(PendingIntent.getActivity(
                            activity, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    // We start the Activity with a custom animation that keeps it hidden while
                    // starting up to avoid Android showing stale 2D screenshots when the user is in
                    // their VR headset. The animation lasts up to 10 seconds, but is cancelled when
                    // we're resumed as at that time we'll be showing the black overlay added above.
                    int animation = !sInstance.mInVr && USE_HIDE_ANIMATION ? R.anim.stay_hidden : 0;
                    sInstance.mNeedsAnimationCancel = animation != 0;
                    Bundle options =
                            ActivityOptions.makeCustomAnimation(activity, animation, 0).toBundle();
                    ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                            .moveTaskToFront(activity.getTaskId(), 0, options);
                }
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

        WeakReference<ChromeActivity> targetActivity() {
            return mTargetActivity;
        }
    }

    /**
     * Registers the given {@link VrModeObserver}.
     *
     * @param observer The VrModeObserver to register.
     */
    public static void registerVrModeObserver(VrModeObserver observer) {
        sVrModeObservers.add(observer);
    }

    /**
     * Unregisters the given {@link VrModeObserver}.
     *
     * @param observer The VrModeObserver to remove.
     */
    public static void unregisterVrModeObserver(VrModeObserver observer) {
        sVrModeObservers.remove(observer);
    }

    /**
     * See {@link Activity#onActivityResult}.
     */
    public static boolean onActivityResultWithNative(int requestCode, int resultCode) {
        // Handles the result of the exit VR flow (DOFF).
        if (requestCode == EXIT_VR_RESULT) {
            if (sInstance != null) sInstance.onExitVrResult(resultCode == Activity.RESULT_OK);
            return true;
        }
        // Handles the result of requesting to update VR services.
        if (requestCode == VR_SERVICES_UPDATE_RESULT) {
            if (sInstance != null) sInstance.onVrServicesMaybeUpdated();
            return true;
        }
        // Handles the result of requesting to update GVR Keyboard.
        if (requestCode == GVR_KEYBOARD_UPDATE_RESULT) {
            if (sInstance != null) sInstance.onGvrKeyboardMaybeUpdated();
            return true;
        }
        return false;
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
    }

    protected static boolean isDisplayingUrl() {
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
     *
     * @return Whether VR entry succeeded (or is in progress).
     */
    public static boolean enterVrIfNecessary() {
        boolean created_delegate = sInstance == null;
        VrShellDelegate instance = getInstance();
        if (instance == null) return false;
        int result = instance.enterVrInternal();
        if (result == ENTER_VR_CANCELLED && created_delegate) instance.destroy();
        return result != ENTER_VR_CANCELLED;
    }

    /**
     * Returns the current {@VrSupportLevel}.
     */
    public static int getVrSupportLevel(VrDaydreamApi daydreamApi,
            VrCoreVersionChecker versionChecker, Tab tabToShowInfobarIn) {
        // TODO(mthiesse, crbug.com/791090): Re-enable VR mode for devices that boot to VR once we
        // support those devices.
        if (versionChecker == null || daydreamApi == null || daydreamApi.bootsToVr()
                || !isVrCoreCompatible(versionChecker, tabToShowInfobarIn)) {
            return VrSupportLevel.VR_NOT_AVAILABLE;
        }

        if (daydreamApi.isDaydreamReadyDevice()) return VrSupportLevel.VR_DAYDREAM;

        return VrSupportLevel.VR_CARDBOARD;
    }

    /**
     * If VR Shell is enabled, and the activity is supported, register with the Daydream
     * platform that this app would like to be launched in VR when the device enters VR.
     */
    public static void maybeRegisterVrEntryHook(final ChromeActivity activity) {
        // Daydream is not supported on pre-N devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (sInstance != null) return; // Will be handled in onResume.
        if (!activitySupportsVrBrowsing(activity) && sRegisteredVrAssetsComponent) return;

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
                updateDayreamIconComponentState(activity);
                return api;
            }

            @Override
            protected void onPostExecute(VrDaydreamApi api) {
                // Registering the daydream intent has to be done on the UI thread. Note that this
                // call is slow (~10ms at time of writing).
                if (api == null) return;
                if (!sRegisteredVrAssetsComponent) {
                    registerVrAssetsComponentIfDaydreamUser(api.isDaydreamCurrentViewer());
                }
                if (ApplicationStatus.getStateForActivity(activity) == ActivityState.RESUMED
                        && activitySupportsVrBrowsing(activity)) {
                    registerDaydreamIntent(api, activity);
                }
                api.close();
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
        api.close();
    }

    public static void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode && isInVr()) {
            sInstance.shutdownVr(true /* disableVrMode */, true /* stayingInChrome */);
        }
    }

    public static void requestToExitVr(OnExitVrRequestListener listener) {
        requestToExitVr(listener, UiUnsupportedMode.GENERIC_UNSUPPORTED_FEATURE);
    }

    public static void requestToExitVr(
            OnExitVrRequestListener listener, @UiUnsupportedMode int reason) {
        // If we're not in VR, just say that we've successfully exited VR.
        if (sInstance == null || !sInstance.mInVr) {
            listener.onSucceeded();
            return;
        }
        sInstance.requestToExitVrInternal(listener, reason);
    }

    /**
     * Called when the {@link ChromeActivity} becomes visible.
     */
    public static void onActivityShown(ChromeActivity activity) {
        if (sInstance != null && sInstance.mActivity == activity) sInstance.onActivityShown();
    }

    /**
     * Called when the {@link ChromeActivity} is hidden.
     */
    public static void onActivityHidden(ChromeActivity activity) {
        if (sInstance != null && sInstance.mActivity == activity) sInstance.onActivityHidden();
    }

    /**
     * @return Whether VrShellDelegate handled the density change. If the density change is
     * unhandled, the Activity should be recreated in order to handle the change.
     */
    public static boolean onDensityChanged(int oldDpi, int newDpi) {
        if (DEBUG_LOGS) Log.i(TAG, "onDensityChanged [%d]->[%d] ", oldDpi, newDpi);
        if (sInstance == null) return false;
        // If density changed while in VR, we expect a second density change to restore the density
        // to what it previously was when we exit VR. We shouldn't have to recreate the activity as
        // all non-VR UI is still using the old density.
        if (sInstance.mExpectedDensityChange != 0) {
            assert !sInstance.mInVr && !sInstance.mDonSucceeded;
            int expectedDensity = sInstance.mExpectedDensityChange;
            sInstance.mExpectedDensityChange = 0;
            return (newDpi == expectedDensity);
        }
        if (sInstance.mInVr || sInstance.mDonSucceeded) {
            sInstance.mExpectedDensityChange = oldDpi;
            return true;
        }
        return false;
    }

    public static boolean activitySupportsVrBrowsing(Activity activity) {
        if (activity instanceof ChromeTabbedActivity) return true;
        if (activity instanceof CustomTabActivity) {
            return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_IN_CUSTOM_TAB);
        }
        return false;
    }

    /**
     * @param topContentOffset The top content offset (usually applied by the omnibox).
     */
    public static void rawTopContentOffsetChanged(float topContentOffset) {
        assert isInVr();
        sInstance.mVrShell.rawTopContentOffsetChanged(topContentOffset);
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

    private static void updateDayreamIconComponentState(ChromeActivity activity) {
        boolean enabled = ChromeFeatureList.isEnabled(ChromeFeatureList.VR_ICON_IN_DAYDREAM_HOME);

        if (sIconComponentEnabled != null && enabled == sIconComponentEnabled) return;

        int componentState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                     : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        ComponentName component = new ComponentName(activity, VR_INTENT_DISPATCHER_COMPONENT);
        activity.getPackageManager().setComponentEnabledSetting(
                component, componentState, PackageManager.DONT_KILL_APP);
        sIconComponentEnabled = enabled;
    }

    private static boolean activitySupportsPresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity || activity instanceof CustomTabActivity
                || activity instanceof WebappActivity;
    }

    private static boolean activitySupportsAutopresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity || activity instanceof CustomTabActivity;
    }

    private static boolean activitySupportsExitFeedback(Activity activity) {
        return activity instanceof ChromeTabbedActivity
                && ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_FEEDBACK);
    }

    private static void registerVrAssetsComponentIfDaydreamUser(boolean isDaydreamCurrentViewer) {
        assert !sRegisteredVrAssetsComponent;
        if (isDaydreamCurrentViewer) {
            nativeRegisterVrAssetsComponent();
            sRegisteredVrAssetsComponent = true;
        }
    }

    /**
     * @return A helper class for creating VR-specific classes that may not be available at compile
     * time.
     */
    protected static VrClassesWrapper getVrClassesWrapper() {
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
    /* package */ static boolean isVrShellEnabled(int vrSupportLevel) {
        // Only enable ChromeVR (VrShell) on Daydream devices as it currently needs a Daydream
        // controller.
        if (vrSupportLevel != VrSupportLevel.VR_DAYDREAM) return false;
        if (deviceChangesDensityInVr()) return false;
        return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING);
    }

    private static boolean deviceChangesDensityInVr() {
        // If the screen density changed while in VR, we have to disable the VR browser as java UI
        // used or created by VR browsing will be broken.
        if (sInstance != null) {
            if (sInstance.mExpectedDensityChange != 0) return true;
            if (sInstance.mVrSupportLevel != VrSupportLevel.VR_DAYDREAM) return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        Display display = DisplayAndroidManager.getDefaultDisplayForContext(
                ContextUtils.getApplicationContext());
        Display.Mode[] modes = display.getSupportedModes();
        // Devices with only one mode won't switch modes while in VR.
        if (modes.length <= 1) return false;
        Display.Mode vr_mode = modes[0];
        for (int i = 1; i < modes.length; ++i) {
            if (modes[i].getPhysicalWidth() > vr_mode.getPhysicalWidth()) vr_mode = modes[i];
        }

        // If we're currently in the mode supported by VR the density won't change.
        // We actually can't use display.getMode() to get the current mode as that just always
        // returns the same mode ignoring the override, so we just check that our current display
        // size is not equal to the vr mode size.
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        if (vr_mode.getPhysicalWidth() != metrics.widthPixels
                && vr_mode.getPhysicalWidth() != metrics.heightPixels) {
            return true;
        }
        if (vr_mode.getPhysicalHeight() != metrics.widthPixels
                && vr_mode.getPhysicalHeight() != metrics.heightPixels) {
            return true;
        }
        return false;
    }

    /**
     *  @return Whether or not VR is supported on this platform.
     */
    /* package */ static boolean isVrEnabled() {
        return getVrClassesWrapper() != null;
    }

    private static void onActivityDestroyed(Activity activity) {
        if (sVrBroadcastReceiver == null) return;
        if (sVrBroadcastReceiver.targetActivity().get() != activity) return;
        sVrBroadcastReceiver.unregister();
        sVrBroadcastReceiver = null;
    }

    protected VrShellDelegate(ChromeActivity activity, VrClassesWrapper wrapper) {
        mActivity = activity;
        mVrClassesWrapper = wrapper;
        // If an activity isn't resumed at the point, it must have been paused.
        mPaused = ApplicationStatus.getStateForActivity(activity) != ActivityState.RESUMED;
        mStopped = ApplicationStatus.getStateForActivity(activity) == ActivityState.STOPPED;
        mVisible = activity.hasWindowFocus();
        updateVrSupportLevel(null);
        mNativeVrShellDelegate = nativeInit();
        mFeedbackFrequency = VrFeedbackStatus.getFeedbackFrequency();
        mExpectPauseOrDonSucceeded = new Handler();
        ApplicationStatus.registerStateListenerForAllActivities(this);
        if (!mPaused) onResume();
        sInstance = this;
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        switch (newState) {
            case ActivityState.DESTROYED:
                if (activity == mActivity) {
                    destroy();
                    onActivityDestroyed(activity);
                }
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
                        shutdownVr(true /* disableVrMode */, false /* stayingInChrome */);
                    }
                }
                if (!activitySupportsPresentation(activity)) return;
                if (!(activity instanceof ChromeActivity)) return;
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
        mListeningForWebVrActivateBeforePause = false;
        if (mVrDaydreamApi != null) mVrDaydreamApi.close();
        mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
    }

    private void maybeUpdateVrSupportLevel() {
        // If we're on Daydream support level, Chrome will get restarted by Android in response to
        // VrCore being updated/downgraded, so we don't need to check.
        if (mVrSupportLevel == VrSupportLevel.VR_DAYDREAM) return;
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

    private int getGvrKeyboardPackageVersion() {
        return PackageUtils.getPackageVersion(
                ContextUtils.getApplicationContext(), GVR_KEYBOARD_PACKAGE_ID);
    }

    /**
     * Updates mVrSupportLevel to the correct value. isVrCoreCompatible might return different value
     * at runtime.
     */
    // TODO(bshe): Find a place to call this function again, i.e. page refresh or onResume.
    private void updateVrSupportLevel(Integer vrCorePackageVersion) {
        if (mVrClassesWrapper == null) {
            mVrSupportLevel = VrSupportLevel.VR_NOT_AVAILABLE;
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
    }

    @CalledByNative
    @VrSupportLevel
    /* package */ int getVrSupportLevel() {
        return mVrSupportLevel;
    }

    private void onVrServicesMaybeUpdated() {
        if (mCachedVrCorePackageVersion == getVrCorePackageVersion()) return;
        ApplicationLifetime.terminate(true);
    }

    private void onGvrKeyboardMaybeUpdated() {
        if (mCachedGvrKeyboardPackageVersion == getGvrKeyboardPackageVersion()) return;
        ApplicationLifetime.terminate(true);
    }

    /**
     * Returns whether the device has support for Daydream.
     */
    /* package */ boolean hasDaydreamSupport() {
        return mVrSupportLevel == VrSupportLevel.VR_DAYDREAM;
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
        if (!canEnterVr(true)) return false;

        // If the page is listening for vrdisplayactivate we assume it wants to request
        // presentation. Go into WebVR mode tentatively. If the page doesn't request presentation
        // in the vrdisplayactivate handler we will exit presentation later. Note that in the
        // case of autopresentation, we don't want to enter WebVR mode so that we can show the
        // splash screen. In this case, we enter WebVR mode when the site requests presentation.
        // Note that we don't want to dispatch vrdisplayactivate for auto-present and vr intents.
        boolean tentativeWebVrMode =
                mListeningForWebVrActivateBeforePause && !mRequestedWebVr && !mEnterVrOnStartup;
        if (tentativeWebVrMode && !mAutopresentWebVr) {
            // Before we fire DisplayActivate, we need focus to propagate to the WebContents we're
            // about to send DisplayActivate to. Focus propagates during onResume, which is when
            // this function is called, so if we post DisplayActivate to fire after onResume, focus
            // will have propagated.
            assert !mPaused;
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (mNativeVrShellDelegate == 0) return;
                    nativeDisplayActivate(mNativeVrShellDelegate);
                }
            });
        }

        enterVr(tentativeWebVrMode);
        mEnterVrOnStartup = false;

        // The user has successfully completed a DON flow.
        RecordUserAction.record("VR.DON");

        return true;
    }

    /* package */ boolean isVrBrowsingEnabled() {
        return isVrShellEnabled(mVrSupportLevel) && activitySupportsVrBrowsing(mActivity)
                && isDaydreamCurrentViewer();
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
        mInVr = true;
        mVrClassesWrapper.setVrModeEnabled(mActivity, true);

        setWindowModeForVr();
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

        addVrViews();
        boolean webVrMode = mRequestedWebVr || tentativeWebVrMode || mAutopresentWebVr;
        mVrShell.initializeNative(mActivity.getActivityTab(), webVrMode, mAutopresentWebVr,
                mActivity instanceof CustomTabActivity);
        mVrShell.setWebVrModeEnabled(webVrMode, false);

        // We're entering VR, but not in WebVr mode.
        mVrBrowserUsed = !webVrMode && !mAutopresentWebVr;

        // resume needs to be called on GvrLayout after initialization to make sure DON flow works
        // properly.
        if (mVisible) mVrShell.resume();

        mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(this);
        removeBlackOverlayView(mActivity);
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
                    mEnterVrOnStartup = false;
                    handleDonFlowSuccess();
                }
            }, EXPECT_DON_TIMEOUT_MS);
        }
        maybeSetPresentResult(true, donSuceeded);

        for (VrModeObserver observer : sVrModeObservers) observer.onEnterVr();
    }

    private static void addBlackOverlayViewForActivity(ChromeActivity activity) {
        if (sAddedBlackOverlayView) return;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        View v = new View(activity);
        v.setId(R.id.vr_overlay_view);
        v.setBackgroundColor(Color.BLACK);
        activity.getWindow().addContentView(v, params);
        sAddedBlackOverlayView = true;
    }

    private static void removeBlackOverlayView(ChromeActivity activity) {
        if (!sAddedBlackOverlayView) return;
        View v = activity.getWindow().findViewById(R.id.vr_overlay_view);
        assert v != null;
        UiUtils.removeViewFromParent(v);
        sAddedBlackOverlayView = false;
    }

    private void onVrIntent() {
        if (mInVr) return;

        if (USE_HIDE_ANIMATION) mNeedsAnimationCancel = true;
        mEnterVrOnStartup = true;

        // TODO(mthiesse): Assuming we've gone through DON flow saves ~2 seconds on VR entry. See
        // the comments in enterVr(). This may not always be the case in the future, but for now
        // it's a reasonable assumption.
        mDonSucceeded = true;
        mInVrAtChromeLaunch = true;

        if (!mPaused) enterVrAfterDon();
    }

    private void onAutopresentIntent() {
        // Autopresent intents are only expected from trusted first party apps while
        // we're not in vr.
        assert !mInVr;
        if (USE_HIDE_ANIMATION) mNeedsAnimationCancel = true;
        mAutopresentWebVr = true;

        // We assume that the user is already in VR mode when launched for auto-presentation.
        mDonSucceeded = true;
        mInVrAtChromeLaunch = true;
    }

    private void onEnterVrUnsupported() {
        // Auto-presentation is unsupported, but we still need to remove the black overlay before we
        // exit to Daydream so that the user doesn't see black when they come back to Chrome. The
        // overlay will be removed when we get paused by Daydream.
        assert !mInVr;
        mNeedsAnimationCancel = false;
        mEnterVrOnStartup = false;
        // We remove the VR-specific system UI flags here so that the system UI shows up properly
        // when Chrome is resumed in non-VR mode.
        mActivity.getWindow().getDecorView().setSystemUiVisibility(0);

        boolean launched = mVrDaydreamApi.launchVrHomescreen();
        assert launched;

        // Some Samsung devices change the screen density after exiting VR mode which causes
        // us to restart Chrome with the VR intent that originally started it. We don't want to
        // enable VR mode when the user opens Chrome again in 2D mode, so we remove VR specific
        // extras.
        VrIntentUtils.removeVrExtras(mActivity.getIntent());
    }

    /**
     * This is called every time ChromeActivity gets a new intent.
     */
    public static void onNewIntentWithNative(ChromeActivity activity, Intent intent) {
        if (!VrIntentUtils.isVrIntent(intent)) return;

        VrShellDelegate instance = getInstance(activity);
        if (instance == null) return;

        // Nothing to do if we were launched by an internal intent.
        if (instance.mInternalIntentUsedToStartVr) {
            instance.mInternalIntentUsedToStartVr = false;
            return;
        }

        // TODO(ymalik): We should cache whether or not VR mode is set so we don't set it
        // needlessly.
        setVrModeEnabled(activity);
        // TODO(ymalik): This should use isTrustedAutopresentIntent once the Daydream Home change
        // that adds the autopresent intent extra rolls out on most devices. This will allow us to
        // differentiate trusted auto-present intents from other VR intents.
        if (VrIntentUtils.getHandlerInstance().isTrustedDaydreamIntent(intent)) {
            if (DEBUG_LOGS) Log.i(TAG, "onNewIntentWithNative: autopresent");
            assert activitySupportsAutopresentation(activity);
            assert instance.getVrSupportLevel() == VrSupportLevel.VR_DAYDREAM;

            // TODO(mthiesse): This needs to be set here to correctly close the CCT when we early
            // exit here. We should use a different variable or refactor or something to make this
            // more clear.
            instance.mAutopresentWebVr = true;
            if (!ChromeFeatureList.isEnabled(ChromeFeatureList.WEBVR_AUTOPRESENT_FROM_INTENT)) {
                instance.onEnterVrUnsupported();
                return;
            }
            instance.onAutopresentIntent();
        } else if (isVrShellEnabled(instance.mVrSupportLevel)
                && (ChromeFeatureList.isEnabled(ChromeFeatureList.VR_LAUNCH_INTENT)
                           || ChromeFeatureList.isEnabled(
                                      ChromeFeatureList.VR_ICON_IN_DAYDREAM_HOME))) {
            if (DEBUG_LOGS) Log.i(TAG, "onNewIntentWithNative: vr");
            instance.onVrIntent();
        } else {
            if (DEBUG_LOGS) Log.i(TAG, "onNewIntentWithNative: unsupported");
            // TODO(ymalik): Currently we always return to Daydream home, this makes less sense if
            // a non-VR app sends an intent, perhaps just ignoring the intent is better.
            instance.onEnterVrUnsupported();
            return;
        }

        if (!instance.mPaused) {
            // Note that cancelling the animation below is what causes us to enter VR mode. We start
            // an intermediate activity to cancel the animation which causes onPause and onResume to
            // be called and we enter VR mode in onResume (because we set the mEnterVrOnStartup bit
            // above). If Chrome is already running, onResume which will be called after
            // VrShellDelegate#onNewIntentWithNative which will cancel the animation and enter VR
            // after that.
            if (!instance.cancelStartupAnimationIfNeeded()) {
                // If we didn't cancel the startup animation, we won't be getting another onResume
                // call, so enter VR here.
                instance.handleDonFlowSuccess();

                // This is extremely unlikely to happen in practice, but it's theoretically possible
                // for the page to have loaded and registered an activate handler before this point.
                // Usually the displayActivate is sent from
                // VrShellDelegate#setListeningForWebVrActivate.
                if (instance.mAutopresentWebVr && instance.mListeningForWebVrActivate) {
                    // Dispatch vrdisplayactivate so that the WebVr page can call requestPresent
                    // to start presentation.
                    instance.nativeDisplayActivate(instance.mNativeVrShellDelegate);
                }
            }
        }
    }

    /**
     * This is called when ChromeTabbedActivity gets a new intent before native is initialized.
     */
    public static void maybeHandleVrIntentPreNative(ChromeActivity activity, Intent intent) {
        if (!VrIntentUtils.isVrIntent(intent)) return;

        if (sInstance != null) sInstance.swapHostActivity(activity);

        // We add a black overlay view so that we can show black while the VR UI is loading.
        // Note that this alone isn't sufficient to prevent 2D UI from showing when
        // auto-presenting WebVR. See comment about the custom animation in {@link
        // getVrIntentOptions}.
        // TODO(crbug.com/775574): This hack doesn't really work to hide the 2D UI on Samsung
        // devices since Chrome gets paused and we prematurely remove the overlay.
        if (sInstance == null || !sInstance.mInVr) addBlackOverlayViewForActivity(activity);

        // If we're already in VR, or launching from an internal intent, we don't want to set system
        // ui visibility here, or we'll incorrectly restore window mode later.
        if (sInstance != null && (sInstance.mInVr || sInstance.mInternalIntentUsedToStartVr)) {
            return;
        }
        if (DEBUG_LOGS) Log.i(TAG, "maybeHandleVrIntentPreNative: preparing for transition");

        // Enable VR mode and hide system UI. We do this here so we don't get kicked out of
        // VR mode and to prevent seeing a flash of system UI.
        getVrClassesWrapper().setVrModeEnabled(activity, true);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.getWindow().getDecorView().setSystemUiVisibility(VR_SYSTEM_UI_FLAGS);
    }

    /**
     * Asynchronously enable VR mode.
     */
    public static void setVrModeEnabled(Activity activity) {
        getVrClassesWrapper().setVrModeEnabled(activity, true);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (mInVr && !isWindowModeCorrectForVr()) {
            setWindowModeForVr();
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

    public boolean hasAudioPermission() {
        return mActivity.getWindowAndroid().hasPermission(android.Manifest.permission.RECORD_AUDIO);
    }

    private boolean isWindowModeCorrectForVr() {
        int flags = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        int orientation = mActivity.getResources().getConfiguration().orientation;
        // Mask the flags to only those that we care about.
        return (flags & VR_SYSTEM_UI_FLAGS) == VR_SYSTEM_UI_FLAGS
                && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setWindowModeForVr() {
        // Decouple the compositor size from the view size, or we'll get an unnecessary resize due
        // to the orientation change when entering VR, then another resize once VR has settled on
        // the content size.
        if (mActivity.getCompositorViewHolder() != null) {
            mActivity.getCompositorViewHolder().onEnterVr();
        }
        ScreenOrientationDelegateManager.setOrientationDelegate(this);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set correct orientation.
        if (mRestoreOrientation == null) {
            mRestoreOrientation = mActivity.getRequestedOrientation();
        }
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Hide system UI.
        int flags = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        if (mRestoreSystemUiVisibilityFlag == -1) {
            mRestoreSystemUiVisibilityFlag = flags;
            // We may have hidden the system UI earlier so that we don't see it when Chrome is
            // started in VR mode. So we should not restore to the flags we added after exiting VR
            // mode. This has the issue that if we hide it for another reason before entering VR
            // mode, we always show it after exiting VR mode, which is probably okay.
            if (mEnterVrOnStartup) mRestoreSystemUiVisibilityFlag &= ~VR_SYSTEM_UI_FLAGS;
        }
        mActivity.getWindow().getDecorView().setSystemUiVisibility(VR_SYSTEM_UI_FLAGS);
    }

    private void restoreWindowMode() {
        ScreenOrientationDelegateManager.setOrientationDelegate(null);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Restore orientation.
        if (mRestoreOrientation != null) mActivity.setRequestedOrientation(mRestoreOrientation);
        mRestoreOrientation = null;

        // Restore system UI visibility.
        if (mRestoreSystemUiVisibilityFlag != -1) {
            mActivity.getWindow().getDecorView().setSystemUiVisibility(
                    mRestoreSystemUiVisibilityFlag);
        }
        mRestoreSystemUiVisibilityFlag = -1;
        if (mActivity.getCompositorViewHolder() != null) {
            mActivity.getCompositorViewHolder().onExitVr();
        }
    }

    /* package */ boolean canEnterVr(boolean justCompletedDon) {
        if (!LibraryLoader.isInitialized()) return false;
        if (mVrSupportLevel == VrSupportLevel.VR_NOT_AVAILABLE || mNativeVrShellDelegate == 0)
            return false;

        // If vr shell is not enabled and this is not a web vr request, then return false.
        boolean presenting = mRequestedWebVr || mListeningForWebVrActivate
                || (justCompletedDon && mListeningForWebVrActivateBeforePause) || mAutopresentWebVr;
        if (!isVrShellEnabled(mVrSupportLevel) && !presenting) return false;
        return true;
    }

    @CalledByNative
    private void presentRequested() {
        if (DEBUG_LOGS) Log.i(TAG, "WebVR page requested presentation");
        mRequestedWebVr = true;
        switch (enterVrInternal()) {
            case ENTER_VR_NOT_NECESSARY:
                mVrShell.setWebVrModeEnabled(true, !mAutopresentWebVr && isVrBrowsingEnabled());
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
    }

    /**
     * Enters VR Shell if necessary, displaying browser UI and tab contents in VR.
     */
    @EnterVRResult
    private int enterVrInternal() {
        if (mPaused) return ENTER_VR_CANCELLED;
        if (mInVr) return ENTER_VR_NOT_NECESSARY;

        // Update VR support level as it can change at runtime
        maybeUpdateVrSupportLevel();
        if (mVrSupportLevel == VrSupportLevel.VR_NOT_AVAILABLE) return ENTER_VR_CANCELLED;
        if (!canEnterVr(false)) return ENTER_VR_CANCELLED;
        if (mVrSupportLevel == VrSupportLevel.VR_DAYDREAM && isDaydreamCurrentViewer()) {
            // TODO(mthiesse): This is a workaround for b/66486878 (see also crbug.com/767594).
            // We have to trigger the DON flow before setting VR mode enabled to prevent the DON
            // flow from failing on the S8/S8+.
            // Due to b/66493165, we also can't create our VR UI before the density has changed,
            // so we can't trigger the DON flow by resuming the GvrLayout. This basically means that
            // calling launchInVr on ourself is the only viable option for getting into VR on the
            // S8/S8+.
            // This also fixes the issue tracked in crbug.com/767944, so this should not be removed
            // until the root cause of that has been found and fixed.
            mVrDaydreamApi.launchInVr(getEnterVrPendingIntent(mActivity));
            mProbablyInDon = true;
        } else {
            enterVr(false);
        }
        return ENTER_VR_REQUESTED;
    }

    private void requestToExitVrInternal(
            OnExitVrRequestListener listener, @UiUnsupportedMode int reason) {
        assert listener != null;
        // If we are currently processing another request, deny the request.
        if (mOnExitVrRequestListener != null) {
            listener.onDenied();
            return;
        }
        mOnExitVrRequestListener = listener;
        mShowingExitVrPrompt = true;
        mVrShell.requestToExitVr(reason);
    }

    @CalledByNative
    /* package */ void exitWebVRPresent() {
        if (!mInVr) return;
        if (mAutopresentWebVr) {
            // For autopresent from Daydream home, we do NOT want to show ChromeVR. So if we
            // ever exit WebVR for whatever reason (navigation, call exitPresent etc), go back to
            // Daydream home.
            mVrDaydreamApi.launchVrHomescreen();
            return;
        }
        if (!isVrBrowsingEnabled()) {
            if (isDaydreamCurrentViewer()) {
                mVrDaydreamApi.launchVrHomescreen();
            } else {
                shutdownVr(true /* disableVrMode */, true /* stayingInChrome */);
            }
        } else {
            mVrBrowserUsed = true;
            mVrShell.setWebVrModeEnabled(false, false);
        }
    }

    private boolean cancelStartupAnimationIfNeeded() {
        if (!mNeedsAnimationCancel) return false;
        if (DEBUG_LOGS) Log.e(TAG, "canceling startup animation");
        mCancellingEntryAnimation = true;
        Bundle options = ActivityOptions.makeCustomAnimation(mActivity, 0, 0).toBundle();
        Intent intent = mVrDaydreamApi.setupVrIntent(
                new Intent(mActivity, VrCancelAnimationActivity.class));
        // We don't want this to run in a new task stack, or we may end up resuming the wrong
        // Activity when the VrCancelAnimationActivity finishes.
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity.startActivity(intent, options);
        mNeedsAnimationCancel = false;
        return true;
    }

    protected void onResume() {
        if (DEBUG_LOGS) Log.i(TAG, "onResume");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return;
        if (maybeCloseVrCct()) return;
        if (mNeedsAnimationCancel) {
            // At least on some devices, like the Samsung S8+, a Window animation is run after our
            // Activity is shown that fades between a stale screenshot from before pausing to the
            // currently rendered content. It's impossible to cancel window animations, and in order
            // to modify the animation we would need to set up the desired animations before
            // calling setContentView, which we can't do because it would affect non-VR usage.
            // To work around this, we keep the stay_hidden animation active until the window
            // animation of the stale screenshot finishes and our black overlay is shown. We then
            // cancel the stay_hidden animation, revealing our black overlay, which we then replace
            // with VR UI.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
            // Just in case any platforms/users modify the window animation scale, we'll multiply
            // our wait time by that scale value.
            float scale = Settings.Global.getFloat(
                    mActivity.getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    cancelStartupAnimationIfNeeded();
                }
            }, (long) (WINDOW_FADE_ANIMATION_DURATION_MS * scale));
            return;
        }

        mPaused = false;

        // We call resume here to be symmetric with onPause in case we get paused/resumed without
        // being hidden/shown. However, we still don't want to resume if we're not visible to avoid
        // doing VR rendering that won't be seen.
        if (mInVr && mVisible) mVrShell.resume();

        maybeUpdateVrSupportLevel();

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            if (mNativeVrShellDelegate != 0) nativeOnResume(mNativeVrShellDelegate);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        if (mVrSupportLevel != VrSupportLevel.VR_DAYDREAM) return;
        if (isVrShellEnabled(mVrSupportLevel) && activitySupportsVrBrowsing(mActivity)) {
            // Perform slow initialization asynchronously.
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (!mPaused) registerDaydreamIntent(mVrDaydreamApi, mActivity);
                    if (!sRegisteredVrAssetsComponent) {
                        registerVrAssetsComponentIfDaydreamUser(isDaydreamCurrentViewer());
                    }
                    updateDayreamIconComponentState(mActivity);
                }
            });
        }

        mCancellingEntryAnimation = false;

        if (mEnterVrOnStartup) {
            // This means that Chrome was started with a VR intent, so we should enter VR.
            // TODO(crbug.com/776235): VR intents are dispatched to ChromeActivity via a launcher
            // which should handle the DON flow to simplify the logic in VrShellDelegate.
            assert !mProbablyInDon;
            if (DEBUG_LOGS) Log.i(TAG, "onResume: entering VR mode for VR intent");

            if (enterVrInternal() == ENTER_VR_CANCELLED) {
                cancelPendingVrEntry();
            }
        } else if (mDonSucceeded) {
            handleDonFlowSuccess();
        } else {
            if (mProbablyInDon) {
                // This means the user backed out of the DON flow, and we won't be entering VR.
                maybeSetPresentResult(false, mDonSucceeded);

                shutdownVr(true, false);
            }
            // If we were resumed at the wrong density, we need to trigger activity recreation.
            if (!mInVr && mExpectedDensityChange != 0
                    && (mActivity.getResources().getConfiguration().densityDpi
                               != mExpectedDensityChange)) {
                mActivity.recreate();
            }
        }

        mProbablyInDon = false;
    }

    private void handleDonFlowSuccess() {
        setWindowModeForVr();
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

    // Android lifecycle doesn't guarantee that this will be called after onResume (though it
    // will usually be), so make sure anything we do here can happen before or after
    // onResume.
    private void onActivityShown() {
        mVisible = true;

        // Only resume VrShell once we're visible so that we don't start rendering before being
        // visible and delaying startup.
        if (mInVr && !mPaused) mVrShell.resume();
    }

    private void onActivityHidden() {
        mVisible = false;
        // In case we're hidden before onPause is called, we pause here. Duplicate calls to pause
        // are safe.
        if (mInVr) mVrShell.pause();
        if (mShowingDaydreamDoff || mProbablyInDon) return;

        // TODO(mthiesse): When the user resumes Chrome in a 2D context, we don't want to tear down
        // VR UI, so for now, exit VR.
        shutdownVr(true /* disableVrMode */, false /* stayingInChrome */);
    }

    protected void onPause() {
        if (DEBUG_LOGS) Log.i(TAG, "onPause");
        mPaused = true;
        if (mCancellingEntryAnimation) return;
        mExpectPauseOrDonSucceeded.removeCallbacksAndMessages(null);
        unregisterDaydreamIntent(mVrDaydreamApi);
        if (mVrSupportLevel == VrSupportLevel.VR_NOT_AVAILABLE) return;

        // TODO(ymalik): We should be able to remove this if we handle it for multi-window in
        // {@link onMultiWindowModeChanged} since we're calling it in onStop.
        if (!mInVr && !mProbablyInDon) cancelPendingVrEntry();

        // When the active web page has a vrdisplayactivate event handler,
        // mListeningForWebVrActivate should be set to true, which means a vrdisplayactive event
        // should be fired once DON flow finished. However, DON flow will pause our activity,
        // which makes the active page becomes invisible. And the event fires before the active
        // page becomes visible again after DON finished. So here we remember the value of
        // mListeningForWebVrActivity before pause and use this value to decide if
        // vrdisplayactivate event should be dispatched in enterVRFromIntent.
        mListeningForWebVrActivateBeforePause = mListeningForWebVrActivate;

        if (mInVr) mVrShell.pause();
        if (mNativeVrShellDelegate != 0) nativeOnPause(mNativeVrShellDelegate);

        mIsDaydreamCurrentViewer = null;
    }

    private void onStart() {
        if (maybeCloseVrCct()) return;
        mStopped = false;
        if (mDonSucceeded) setWindowModeForVr();
    }

    private void onStop() {
        if (DEBUG_LOGS) Log.i(TAG, "onStop");
        mStopped = true;
        if (!mProbablyInDon) cancelPendingVrEntry();
        assert !mCancellingEntryAnimation;
    }

    private boolean maybeCloseVrCct() {
        if (!mExitCctOnStartup) return false;
        mVrDaydreamApi.launchVrHomescreen();
        assert mActivity instanceof CustomTabActivity;
        ((CustomTabActivity) mActivity).finishAndClose(false);
        return true;
    }

    private boolean onBackPressedInternal() {
        if (mVrSupportLevel == VrSupportLevel.VR_NOT_AVAILABLE) return false;
        cancelPendingVrEntry();
        if (!mInVr) return false;
        // Back button should be handled the same way as the close button.
        getVrCloseButtonListener().run();
        return true;
    }

    /**
     * @return Whether the user is currently seeing the DOFF screen.
     */
    /* package */ boolean showDoff(boolean optional) {
        assert !mShowingDaydreamDoff;
        if (!isDaydreamCurrentViewer()) return false;

        // To avoid taking the user out of VR mode when started for auto-presentation, never show
        // DOFF and bail to Daydream if we're forced to leave Chrome.
        if (!mAutopresentWebVr) {
            try {
                if (mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                    mShowingDaydreamDoff = true;
                    mDoffOptional = optional;
                    return true;
                }
            } catch (IllegalArgumentException | SecurityException e) {
                // DOFF calls can unpredictably throw exceptions if VrCore doesn't think Chrome is
                // the active component, for example.
            }
        }
        if (!optional) mVrDaydreamApi.launchVrHomescreen();
        return false;
    }

    private void onExitVrResult(boolean success) {
        assert mVrSupportLevel != VrSupportLevel.VR_NOT_AVAILABLE;

        // We may have manually handled the exit early by swapping to another Chrome activity that
        // supports VR while in the DOFF activity. If that happens we want to exit early when the
        // real DOFF flow calls us back.
        if (!mShowingDaydreamDoff) return;

        // If Doff is not optional and user backed out, launch DD home. We can't re-trigger doff
        // here because we're not yet the active VR component and Daydream will throw a Security
        // Exception.
        if (!mDoffOptional && !success) mVrDaydreamApi.launchVrHomescreen();

        mShowingDaydreamDoff = false;
        if (success) {
            shutdownVr(true /* disableVrMode */, !mExitingCct /* stayingInChrome */);
            if (mExitingCct) ((CustomTabActivity) mActivity).finishAndClose(false);
        }
        mExitingCct = false;
        callOnExitVrRequestListener(success);
    }

    private boolean isDaydreamCurrentViewer() {
        if (mIsDaydreamCurrentViewer == null) {
            mIsDaydreamCurrentViewer = mVrDaydreamApi.isDaydreamCurrentViewer();
        }
        return mIsDaydreamCurrentViewer;
    }

    @CalledByNative
    private void setListeningForWebVrActivate(boolean listening) {
        if (DEBUG_LOGS) Log.i(TAG, "WebVR page listening for vrdisplayactivate: " + listening);
        // Non-Daydream devices may not have the concept of display activate. So disable
        // mListeningForWebVrActivate for them.
        if (mVrSupportLevel != VrSupportLevel.VR_DAYDREAM) return;
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
        } else if (!canEnterVr(false)) {
            unregisterDaydreamIntent(mVrDaydreamApi);
        }
    }

    private void cancelPendingVrEntry() {
        removeBlackOverlayView(mActivity);
        mDonSucceeded = false;
        if (!mShowingDaydreamDoff) {
            mVrClassesWrapper.setVrModeEnabled(mActivity, false);
            restoreWindowMode();
        }
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    protected void shutdownVr(boolean disableVrMode, boolean stayingInChrome) {
        cancelPendingVrEntry();
        // Ensure shutdownVr runs if we're stopping.
        if (handleFinishAutopresentation() && !mStopped) return;
        mAutopresentWebVr = false;

        if (!mInVr) return;
        if (mShowingDaydreamDoff) {
            onExitVrResult(true);
            return;
        }
        mInVr = false;

        // Some Samsung devices change the screen density after exiting VR mode which causes
        // us to restart Chrome with the VR intent that originally started it. We don't want to
        // enable VR mode again, so we remove VR specific extras.
        VrIntentUtils.removeVrExtras(mActivity.getIntent());

        // The user has exited VR.
        RecordUserAction.record("VR.DOFF");

        restoreWindowMode();
        mVrShell.pause();
        removeVrViews();
        destroyVrShell();
        if (disableVrMode) mVrClassesWrapper.setVrModeEnabled(mActivity, false);

        promptForFeedbackIfNeeded(stayingInChrome);

        // User exited VR (via something like the system back button) while looking at the exit VR
        // prompt.
        if (mShowingExitVrPrompt) callOnExitVrRequestListener(true);

        for (VrModeObserver observer : sVrModeObservers) observer.onExitVr();
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

    /* package */ void onExitVrRequestResult(boolean shouldExit) {
        assert mOnExitVrRequestListener != null;
        mShowingExitVrPrompt = false;
        if (shouldExit) {
            mExitedDueToUnsupportedMode = true;
            if (!showDoff(true /* optional */)) callOnExitVrRequestListener(false);
        } else {
            callOnExitVrRequestListener(false);
        }
    }

    /* package */ void exitCctFromUi() {
        CustomTabActivity customTabActivity = (CustomTabActivity) mActivity;
        if (!isDaydreamCurrentViewer() || (mInVrAtChromeLaunch != null && mInVrAtChromeLaunch)) {
            customTabActivity.finishAndClose(false);
            return;
        }
        if (showDoff(true /* optional */)) mExitingCct = true;
    }

    /**
     * Returns the callback for the user-triggered close button to exit VR mode.
     */
    /* package */ Runnable getVrCloseButtonListener() {
        if (mCloseButtonListener != null) return mCloseButtonListener;
        final boolean startedForAutopresentation = mAutopresentWebVr;
        mCloseButtonListener = new Runnable() {
            @Override
            public void run() {
                // Avoid launching DD home when we shutdown VR.
                mAutopresentWebVr = false;

                shutdownVr(true /* disableVrMode */,
                        !startedForAutopresentation /* stayingInChrome */);

                if (!startedForAutopresentation) return;

                // We override the default behavior of the close button because we may stay in
                // Chrome after exiting VR. This is not true for auto-presented content and we want
                // to do what Daydream does for other VR apps by default (which is currently to open
                // 2D launcher). Note that we shutdownVr when Chrome is stopped by this intent.
                final Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivity.startActivity(homeIntent);

                ((CustomTabActivity) mActivity).finishAndClose(false);
            }
        };
        return mCloseButtonListener;
    }

    /**
     * Returns the callback for the user-triggered close button to exit VR mode.
     */
    /* package */ Runnable getVrSettingsButtonListener() {
        if (mSettingsButtonListener != null) return mSettingsButtonListener;
        final boolean startedForAutopresentation = mAutopresentWebVr;
        mSettingsButtonListener = new Runnable() {
            @Override
            public void run() {
                // Avoid launching DD home when we shutdown VR.
                mAutopresentWebVr = false;

                shutdownVr(true /* disableVrMode */, false /* stayingInChrome */);

                if (startedForAutopresentation) mExitCctOnStartup = true;
                mVrDaydreamApi.launchGvrSettings();
            }
        };
        return mSettingsButtonListener;
    }

    /**
     * Returns true if finishing auto-presentation was handled.
     */
    private boolean handleFinishAutopresentation() {
        if (!mAutopresentWebVr) return false;
        // Should only autopresent CustomTabActivity for now.
        assert mActivity instanceof CustomTabActivity;
        ((CustomTabActivity) mActivity).finishAndClose(false);
        return true;
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
        if (tab == null) return;
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
                activity.startActivityForResult(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(VR_CORE_MARKET_URI)),
                        VR_SERVICES_UPDATE_RESULT);
                return false;
            }
        };
        SimpleConfirmInfoBarBuilder.create(tab, listener,
                InfoBarIdentifier.VR_SERVICES_UPGRADE_ANDROID, R.drawable.vr_services, infobarText,
                buttonText, null, true);
    }

    /* package */ void promptForKeyboardUpdate() {
        mCachedGvrKeyboardPackageVersion = getGvrKeyboardPackageVersion();
        mActivity.startActivityForResult(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GVR_KEYBOARD_MARKET_URI)),
                GVR_KEYBOARD_UPDATE_RESULT);
    }

    private boolean createVrShell() {
        assert mVrShell == null;
        if (mVrClassesWrapper == null) return false;
        if (mActivity.getCompositorViewHolder() == null) return false;
        TabModelSelector tabModelSelector = mActivity.getTabModelSelector();
        if (tabModelSelector == null) return false;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            mVrShell = mVrClassesWrapper.createVrShell(mActivity, this, tabModelSelector);
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
        mActivity.onExitVr();
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mVrShell.getContainer());
    }

    /**
     * Clean up VrShell, and associated native objects.
     */
    private void destroyVrShell() {
        if (mVrShell != null) {
            mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(null);
            mVrShell.teardown();
            mVrShell = null;
        }
    }

    /**
     * @param api The VrDaydreamApi object this delegate will use instead of the default one
     */
    protected void overrideDaydreamApi(VrDaydreamApi api) {
        mVrDaydreamApi = api;
    }

    /**
     * @return The VrShell for the VrShellDelegate instance
     */
    protected static VrShell getVrShell() {
        return sInstance == null ? null : sInstance.mVrShell;
    }

    /**
     * @param versionChecker The VrCoreVersionChecker object this delegate will use
     */
    protected void overrideVrCoreVersionChecker(VrCoreVersionChecker versionChecker) {
        mVrCoreVersionChecker = versionChecker;
        updateVrSupportLevel(null);
    }

    /**
     * @param frequency Sets how often to show the feedback prompt.
     */
    protected void setFeedbackFrequency(int frequency) {
        mFeedbackFrequency = frequency;
    }

    protected boolean isListeningForWebVrActivate() {
        return mListeningForWebVrActivate;
    }

    protected boolean isClearActivatePending() {
        assert mNativeVrShellDelegate != 0;
        return nativeIsClearActivatePending(mNativeVrShellDelegate);
    }

    protected boolean isVrEntryComplete() {
        return mInVr && !mProbablyInDon;
    }

    protected boolean getProbablyInDon() {
        return mProbablyInDon;
    }

    protected boolean getDonSucceeded() {
        return mDonSucceeded;
    }

    protected boolean isShowingDoff() {
        return mShowingDaydreamDoff;
    }

    protected void acceptDoffPromptForTesting() {
        mVrShell.acceptDoffPromptForTesting();
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
        shutdownVr(false /* disableVrMode */, false /* stayingInChrome */);
        if (mNativeVrShellDelegate != 0) nativeDestroy(mNativeVrShellDelegate);
        if (mVrDaydreamApi != null) mVrDaydreamApi.close();
        mNativeVrShellDelegate = 0;
        ApplicationStatus.unregisterActivityStateListener(this);
        sInstance = null;
    }

    private native long nativeInit();
    private static native void nativeOnLibraryAvailable();
    private native void nativeSetPresentResult(long nativeVrShellDelegate, boolean result);
    private native void nativeDisplayActivate(long nativeVrShellDelegate);
    private native void nativeOnPause(long nativeVrShellDelegate);
    private native void nativeOnResume(long nativeVrShellDelegate);
    private native boolean nativeIsClearActivatePending(long nativeVrShellDelegate);
    private native void nativeDestroy(long nativeVrShellDelegate);
    private static native void nativeRegisterVrAssetsComponent();
}
