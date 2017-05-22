// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.annotation.IntDef;
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
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.infobar.InfoBarIdentifier;
import org.chromium.chrome.browser.infobar.SimpleConfirmInfoBarBuilder;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr_shell")
public class VrShellDelegate implements ApplicationStatus.ActivityStateListener,
                                        View.OnSystemUiVisibilityChangeListener {
    private static final String TAG = "VrShellDelegate";
    // Pseudo-random number to avoid request id collisions.
    public static final int EXIT_VR_RESULT = 721251;

    public static final int ENTER_VR_NOT_NECESSARY = 0;
    public static final int ENTER_VR_CANCELLED = 1;
    public static final int ENTER_VR_REQUESTED = 2;
    public static final int ENTER_VR_SUCCEEDED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENTER_VR_NOT_NECESSARY, ENTER_VR_CANCELLED, ENTER_VR_REQUESTED, ENTER_VR_SUCCEEDED})
    public @interface EnterVRResult {}

    public static final int VR_NOT_AVAILABLE = 0;
    public static final int VR_CARDBOARD = 1;
    public static final int VR_DAYDREAM = 2; // Supports both Cardboard and Daydream viewer.

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VR_NOT_AVAILABLE, VR_CARDBOARD, VR_DAYDREAM})
    public @interface VrSupportLevel {}

    // TODO(bshe): These should be replaced by string provided by NDK. Currently, it only available
    // in SDK and we don't want add dependency to SDK just to get these strings.
    private static final String DAYDREAM_CATEGORY = "com.google.intent.category.DAYDREAM";
    private static final String CARDBOARD_CATEGORY = "com.google.intent.category.CARDBOARD";

    private static final String VR_ACTIVITY_ALIAS =
            "org.chromium.chrome.browser.VRChromeTabbedActivity";

    private static final long REENTER_VR_TIMEOUT_MS = 1000;

    private static final int VR_SYSTEM_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static VrShellDelegate sInstance;

    private final ChromeActivity mActivity;

    @VrSupportLevel
    private int mVrSupportLevel;

    private final VrClassesWrapper mVrClassesWrapper;
    private VrShell mVrShell;
    private NonPresentingGvrContext mNonPresentingGvrContext;
    private VrDaydreamApi mVrDaydreamApi;
    private VrCoreVersionChecker mVrCoreVersionChecker;
    private TabModelSelector mTabModelSelector;

    private boolean mInVr;
    private boolean mEnteringVr;
    private boolean mPaused;
    private int mRestoreSystemUiVisibilityFlag = -1;
    private Integer mRestoreOrientation = null;
    private long mNativeVrShellDelegate;
    private boolean mRequestedWebVR;
    private long mLastVRExit;
    private boolean mListeningForWebVrActivate;
    private boolean mListeningForWebVrActivateBeforePause;

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

    @VisibleForTesting
    public static VrShellDelegate getInstanceForTesting() {
        return getInstance();
    }

    /**
     * Whether or not we are currently in VR.
     */
    public static boolean isInVR() {
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
    public static void enterVRIfNecessary() {
        boolean created_delegate = sInstance == null;
        VrShellDelegate instance = getInstance();
        if (instance == null) return;
        if (instance.enterVRInternal() == ENTER_VR_CANCELLED && created_delegate) {
            instance.destroy();
        }
    }

    /**
     * Handles a VR intent, entering VR in the process.
     */
    public static void enterVRFromIntent(Intent intent) {
        assert isDaydreamVrIntent(intent);
        boolean created_delegate = sInstance == null;
        VrShellDelegate instance = getInstance();
        if (instance == null) return;
        if (!instance.enterVRFromIntent() && created_delegate) instance.destroy();
    }

    /**
     * Whether or not the intent is a Daydream VR Intent.
     */
    public static boolean isDaydreamVrIntent(Intent intent) {
        if (intent == null || intent.getCategories() == null) return false;
        return intent.getCategories().contains(DAYDREAM_CATEGORY);
    }

    /**
     * Handles the result of the exit VR flow (DOFF).
     */
    public static void onExitVRResult(int resultCode) {
        if (sInstance == null) return;
        sInstance.onExitVRResult(resultCode == Activity.RESULT_OK);
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
    public static void maybeRegisterVREntryHook(Activity activity) {
        if (sInstance != null) return; // Will be handled in onResume.
        if (!(activity instanceof ChromeTabbedActivity)) return;
        VrClassesWrapper wrapper = createVrClassesWrapper();
        if (wrapper == null) return;
        VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
        if (api == null) return;
        int vrSupportLevel = getVrSupportLevel(api, wrapper.createVrCoreVersionChecker(), null);
        if (isVrShellEnabled(vrSupportLevel)) registerDaydreamIntent(api, activity);
    }

    /**
     * When the app is pausing we need to unregister with the Daydream platform to prevent this app
     * from being launched from the background when the device enters VR.
     */
    public static void maybeUnregisterVREntryHook(Activity activity) {
        if (sInstance != null) return; // Will be handled in onPause.
        if (!(activity instanceof ChromeTabbedActivity)) return;
        VrClassesWrapper wrapper = createVrClassesWrapper();
        if (wrapper == null) return;
        VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
        if (api == null) return;
        unregisterDaydreamIntent(api);
    }

    @CalledByNative
    private static VrShellDelegate getInstance() {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (sInstance != null && activity instanceof ChromeTabbedActivity) return sInstance;
        if (!LibraryLoader.isInitialized()) return null;
        // Note that we only support ChromeTabbedActivity for now.
        if (activity == null || !(activity instanceof ChromeTabbedActivity)) return null;
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return null;
        sInstance = new VrShellDelegate((ChromeActivity) activity, wrapper);

        return sInstance;
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
    private static VrClassesWrapper createVrClassesWrapper() {
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

    private static PendingIntent getEnterVRPendingIntent(
            VrDaydreamApi dayreamApi, Activity activity) {
        return PendingIntent.getActivity(activity, 0,
                dayreamApi.createVrIntent(new ComponentName(activity, VR_ACTIVITY_ALIAS)),
                PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * Registers the Intent to fire after phone inserted into a headset.
     */
    private static void registerDaydreamIntent(VrDaydreamApi dayreamApi, Activity activity) {
        dayreamApi.registerDaydreamIntent(getEnterVRPendingIntent(dayreamApi, activity));
    }

    /**
     * Unregisters the Intent which registered by this context if any.
     */
    private static void unregisterDaydreamIntent(VrDaydreamApi dayreamApi) {
        dayreamApi.unregisterDaydreamIntent();
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

    private VrShellDelegate(ChromeActivity activity, VrClassesWrapper wrapper) {
        mActivity = activity;
        mVrClassesWrapper = wrapper;
        updateVrSupportLevel();
        mNativeVrShellDelegate = nativeInit();
        Choreographer choreographer = Choreographer.getInstance();
        choreographer.postFrameCallback(new FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mNativeVrShellDelegate == 0) return;
                Display display =
                        ((WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE))
                                .getDefaultDisplay();
                nativeUpdateVSyncInterval(
                        mNativeVrShellDelegate, frameTimeNanos, 1.0d / display.getRefreshRate());
            }
        });
        ApplicationStatus.registerStateListenerForActivity(this, activity);
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        switch (newState) {
            case ActivityState.DESTROYED:
                destroy();
                break;
            case ActivityState.PAUSED:
                pauseVR();
                break;
            case ActivityState.RESUMED:
                resumeVR();
                break;
            default:
                break;
        }
    }

    /**
     * Updates mVrSupportLevel to the correct value. isVrCoreCompatible might return different value
     * at runtime.
     */
    // TODO(bshe): Find a place to call this function again, i.e. page refresh or onResume.
    private void updateVrSupportLevel() {
        if (mVrClassesWrapper == null) {
            mVrSupportLevel = VR_NOT_AVAILABLE;
            return;
        }
        if (mVrCoreVersionChecker == null) {
            mVrCoreVersionChecker = mVrClassesWrapper.createVrCoreVersionChecker();
        }
        if (mVrDaydreamApi == null) {
            mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
        }
        mVrSupportLevel = getVrSupportLevel(
                mVrDaydreamApi, mVrCoreVersionChecker, mActivity.getActivityTab());
    }

    /**
     * Handle a VR intent, entering VR in the process unless we're unable to.
     */
    private boolean enterVRFromIntent() {
        // Vr Intent is only used on Daydream devices.
        if (mVrSupportLevel != VR_DAYDREAM) return false;
        if (mNativeVrShellDelegate == 0) return false;
        if (mListeningForWebVrActivateBeforePause && !mRequestedWebVR) {
            nativeDisplayActivate(mNativeVrShellDelegate);
            return false;
        }
        // Normally, if the active page doesn't have a vrdisplayactivate listener, and WebVR was not
        // presenting and VrShell was not enabled, we shouldn't enter VR and Daydream Homescreen
        // should show after DON flow. But due to a failure in unregisterDaydreamIntent, we still
        // try to enterVR. Here we detect this case and force switch to Daydream Homescreen.
        if (!mListeningForWebVrActivateBeforePause && !mRequestedWebVR
                && !isVrShellEnabled(mVrSupportLevel)) {
            mVrDaydreamApi.launchVrHomescreen();
            return false;
        }

        if (mInVr) {
            setEnterVRResult(true);
            return false;
        }
        if (!canEnterVR(mActivity.getActivityTab())) {
            setEnterVRResult(false);
            return false;
        }
        if (mPaused) {
            // We can't enter VR before the application resumes, or we encounter bizarre crashes
            // related to gpu surfaces. Set this flag to enter VR on the next resume.
            mEnteringVr = true;
        } else {
            enterVR();
        }
        return true;
    }

    private void enterVR() {
        if (mNativeVrShellDelegate == 0) return;
        if (mInVr) return;
        if (!isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    enterVR();
                }
            });
            return;
        }
        mEnteringVr = false;
        if (!createVrShell()) {
            restoreWindowMode();
            setEnterVRResult(false);
            return;
        }
        mVrClassesWrapper.setVrModeEnabled(mActivity, true);
        mInVr = true;
        // Lock orientation to landscape after enter VR.
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        addVrViews();
        mVrShell.initializeNative(mActivity.getActivityTab(), mRequestedWebVR);
        // onResume needs to be called on GvrLayout after initialization to make sure DON flow work
        // properly.
        mVrShell.resume();

        setEnterVRResult(true);
        mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(this);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (mInVr && !isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private boolean isWindowModeCorrectForVr() {
        int flags = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        int orientation = mActivity.getResources().getConfiguration().orientation;
        return flags == VR_SYSTEM_UI_FLAGS && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setWindowModeForVr(int requestedOrientation) {
        if (mRestoreOrientation == null) {
            mRestoreOrientation = mActivity.getRequestedOrientation();
        }
        mActivity.setRequestedOrientation(requestedOrientation);
        setupVrModeWindowFlags();
    }

    private void restoreWindowMode() {
        if (mRestoreOrientation != null) mActivity.setRequestedOrientation(mRestoreOrientation);
        mRestoreOrientation = null;
        clearVrModeWindowFlags();
    }

    private void setEnterVRResult(boolean success) {
        if (mRequestedWebVR && mNativeVrShellDelegate != 0) {
            nativeSetPresentResult(mNativeVrShellDelegate, success);
        }
        if (!success && !mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
            mVrClassesWrapper.setVrModeEnabled(mActivity, false);
        }
        mRequestedWebVR = false;
    }

    /* package */ boolean canEnterVR(Tab tab) {
        if (!LibraryLoader.isInitialized()) {
            return false;
        }
        if (mVrSupportLevel == VR_NOT_AVAILABLE || mNativeVrShellDelegate == 0) return false;
        // If vr shell is not enabled and this is not a web vr request, then return false.
        if (!isVrShellEnabled(mVrSupportLevel)
                && !(mRequestedWebVR || mListeningForWebVrActivate)) {
            return false;
        }
        // TODO(mthiesse): When we have VR UI for opening new tabs, etc., allow VR Shell to be
        // entered without any current tabs.
        if (tab == null) {
            return false;
        }
        // For now we don't handle sad tab page. crbug.com/661609
        if (tab.isShowingSadTab()) {
            return false;
        }
        // crbug.com/667781
        if (MultiWindowUtils.getInstance().isInMultiWindowMode(mActivity)) {
            return false;
        }
        return true;
    }

    @CalledByNative
    private void presentRequested() {
        // TODO(mthiesse): There's a GVR bug where they're not calling us back with the intent we
        // ask them to when we call DaydreamApi#launchInVr. As a temporary hack, remember locally
        // that we want to enter webVR.
        mRequestedWebVR = true;
        switch (enterVRInternal()) {
            case ENTER_VR_NOT_NECESSARY:
                mVrShell.setWebVrModeEnabled(true);
                if (mNativeVrShellDelegate != 0) {
                    nativeSetPresentResult(mNativeVrShellDelegate, true);
                }
                mRequestedWebVR = false;
                break;
            case ENTER_VR_CANCELLED:
                if (mNativeVrShellDelegate != 0) {
                    nativeSetPresentResult(mNativeVrShellDelegate, false);
                }
                mRequestedWebVR = false;
                break;
            case ENTER_VR_REQUESTED:
                break;
            case ENTER_VR_SUCCEEDED:
                if (mNativeVrShellDelegate != 0) {
                    nativeSetPresentResult(mNativeVrShellDelegate, true);
                }
                mRequestedWebVR = false;
                break;
            default:
                Log.e(TAG, "Unexpected enum.");
        }
    }

    /**
     * Enters VR Shell if necessary, displaying browser UI and tab contents in VR.
     */
    @EnterVRResult
    private int enterVRInternal() {
        // Update VR support level as it can change at runtime
        updateVrSupportLevel();
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return ENTER_VR_CANCELLED;
        if (mInVr) return ENTER_VR_NOT_NECESSARY;
        if (!canEnterVR(mActivity.getActivityTab())) return ENTER_VR_CANCELLED;

        if (mVrSupportLevel == VR_CARDBOARD || !mVrDaydreamApi.isDaydreamCurrentViewer()) {
            // Avoid using launchInVr which would trigger DON flow regardless current viewer type
            // due to the lack of support for unexported activities.
            enterVR();
        } else {
            // LANDSCAPE orientation is needed before we can safely enter VR. DON can make sure that
            // the device is at LANDSCAPE orientation once it is finished. So here we use SENSOR to
            // avoid forcing LANDSCAPE orientation in order to have a smoother transition.
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            if (!mVrDaydreamApi.launchInVr(getEnterVRPendingIntent(mVrDaydreamApi, mActivity))) {
                restoreWindowMode();
                return ENTER_VR_CANCELLED;
            }
        }
        return ENTER_VR_REQUESTED;
    }

    @CalledByNative
    private boolean exitWebVR() {
        if (!mInVr) return false;
        mVrShell.setWebVrModeEnabled(false);
        if (mVrSupportLevel == VR_CARDBOARD) {
            // Transition screen is not available for Cardboard only (non-Daydream) devices.
            // TODO(bshe): Fix this once b/33490788 is fixed.
            shutdownVR(false /* isPausing */, false /* showTransition */);
        } else {
            // TODO(bajones): Once VR Shell can be invoked outside of WebVR this
            // should no longer exit the shell outright. Need a way to determine
            // how VrShell was created.
            shutdownVR(
                    false /* isPausing */, !isVrShellEnabled(mVrSupportLevel) /* showTransition */);
        }
        return true;
    }

    private void resumeVR() {
        mPaused = false;
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return;
        if (mVrSupportLevel == VR_DAYDREAM
                && (isVrShellEnabled(mVrSupportLevel) || mListeningForWebVrActivateBeforePause)) {
            registerDaydreamIntent(mVrDaydreamApi, mActivity);
        }

        if (mEnteringVr) {
            enterVR();
        } else if (mRequestedWebVR) {
            // If this is still set, it means the user backed out of the DON flow, and we won't be
            // receiving an intent from daydream.
            if (mNativeVrShellDelegate != 0) nativeSetPresentResult(mNativeVrShellDelegate, false);
            restoreWindowMode();
            mRequestedWebVR = false;
        }

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            if (mNativeVrShellDelegate != 0) nativeOnResume(mNativeVrShellDelegate);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        if (mInVr) {
            setupVrModeWindowFlags();
            oldPolicy = StrictMode.allowThreadDiskWrites();
            try {
                mVrShell.resume();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to resume VrShell", e);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        } else if (mVrSupportLevel == VR_DAYDREAM && mVrDaydreamApi.isDaydreamCurrentViewer()
                && mLastVRExit + REENTER_VR_TIMEOUT_MS > SystemClock.uptimeMillis()) {
            enterVRInternal();
        }
    }

    private void pauseVR() {
        mPaused = true;
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return;

        if (mVrSupportLevel == VR_DAYDREAM) {
            unregisterDaydreamIntent(mVrDaydreamApi);

            // When the active web page has a vrdisplayactivate event handler,
            // mListeningForWebVrActivate should be set to true, which means a vrdisplayactive event
            // should be fired once DON flow finished. However, DON flow will pause our activity,
            // which makes the active page becomes invisible. And the event fires before the active
            // page becomes visible again after DON finished. So here we remember the value of
            // mListeningForWebVrActivity before pause and use this value to decide if
            // vrdisplayactivate event should be dispatched in enterVRFromIntent.
            mListeningForWebVrActivateBeforePause = mListeningForWebVrActivate;
        }
        if (mNativeVrShellDelegate != 0) nativeOnPause(mNativeVrShellDelegate);

        // TODO(mthiesse): When VR Shell lives in its own activity, and integrates with Daydream
        // home, pause instead of exiting VR here. For now, because VR Apps shouldn't show up in the
        // non-VR recents, and we don't want ChromeTabbedActivity disappearing, exit VR.
        shutdownVR(true /* isPausing */, false /* showTransition */);
    }

    private boolean onBackPressedInternal() {
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return false;
        if (!mInVr) return false;
        shutdownVR(false /* isPausing */, false /* showTransition */);
        return true;
    }

    private void onExitVRResult(boolean success) {
        assert mVrSupportLevel != VR_NOT_AVAILABLE;
        // For now, we don't handle re-entering VR when exit fails, so keep trying to exit.
        if (!success && sInstance.mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) return;
        sInstance.mVrClassesWrapper.setVrModeEnabled(sInstance.mActivity, false);
    }

    @CalledByNative
    private long createNonPresentingNativeContext() {
        if (mVrClassesWrapper == null) return 0;
        // Update VR support level as it can change at runtime
        updateVrSupportLevel();
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return 0;
        mNonPresentingGvrContext = mVrClassesWrapper.createNonPresentingGvrContext(mActivity);
        if (mNonPresentingGvrContext == null) return 0;
        return mNonPresentingGvrContext.getNativeGvrContext();
    }

    @CalledByNative
    private void shutdownNonPresentingNativeContext() {
        if (mNonPresentingGvrContext == null) return;
        mNonPresentingGvrContext.shutdown();
        mNonPresentingGvrContext = null;
    }

    @CalledByNative
    private void setListeningForWebVrActivate(boolean listening) {
        // Non-Daydream devices may not have the concept of display activate. So disable
        // mListeningForWebVrActivate for them.
        if (mVrSupportLevel != VR_DAYDREAM) return;
        mListeningForWebVrActivate = listening;
        if (listening) {
            registerDaydreamIntent(mVrDaydreamApi, mActivity);
        } else {
            unregisterDaydreamIntent(mVrDaydreamApi);
        }
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    /* package */ void shutdownVR(boolean isPausing, boolean showTransition) {
        if (!mInVr) return;
        mInVr = false;
        mRequestedWebVR = false;
        boolean transition = mVrSupportLevel == VR_DAYDREAM && showTransition;
        if (!isPausing) {
            if (!transition || !mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mVrClassesWrapper.setVrModeEnabled(mActivity, false);
            }
        } else {
            mVrClassesWrapper.setVrModeEnabled(mActivity, false);
            mLastVRExit = SystemClock.uptimeMillis();
        }
        restoreWindowMode();
        mVrShell.pause();
        removeVrViews();
        destroyVrShell();
        mActivity.getFullscreenManager().setPositionsForTabToNonFullscreen();
    }

    private static boolean isVrCoreCompatible(
            VrCoreVersionChecker versionChecker, Tab tabToShowInfobarIn) {
        int vrCoreCompatibility = versionChecker.getVrCoreCompatibility();

        if (vrCoreCompatibility == VrCoreVersionChecker.VR_NOT_AVAILABLE
                || vrCoreCompatibility == VrCoreVersionChecker.VR_OUT_OF_DATE) {
            promptToUpdateVrServices(vrCoreCompatibility, tabToShowInfobarIn);
        }

        return vrCoreCompatibility == VrCoreVersionChecker.VR_READY;
    }

    private static void promptToUpdateVrServices(int vrCoreCompatibility, Tab tab) {
        if (tab == null) {
            return;
        }
        final Activity activity = tab.getActivity();
        String infobarText;
        String buttonText;
        if (vrCoreCompatibility == VrCoreVersionChecker.VR_NOT_AVAILABLE) {
            // Supported, but not installed. Ask user to install instead of upgrade.
            infobarText = activity.getString(R.string.vr_services_check_infobar_install_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_install_button);
        } else if (vrCoreCompatibility == VrCoreVersionChecker.VR_OUT_OF_DATE) {
            infobarText = activity.getString(R.string.vr_services_check_infobar_update_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_update_button);
        } else {
            Log.e(TAG, "Unknown VrCore compatibility: " + vrCoreCompatibility);
            return;
        }

        SimpleConfirmInfoBarBuilder.create(tab,
                new SimpleConfirmInfoBarBuilder.Listener() {
                    @Override
                    public void onInfoBarDismissed() {}

                    @Override
                    public boolean onInfoBarButtonClicked(boolean isPrimary) {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id="
                                        + VrCoreVersionChecker.VR_CORE_PACKAGE_ID)));
                        return false;
                    }
                },
                InfoBarIdentifier.VR_SERVICES_UPGRADE_ANDROID, R.drawable.vr_services, infobarText,
                buttonText, null, true);
    }

    private boolean createVrShell() {
        if (mVrClassesWrapper == null) return false;
        if (mActivity.getCompositorViewHolder() == null) return false;
        mTabModelSelector = mActivity.getCompositorViewHolder().detachForVR();
        if (mTabModelSelector == null) return false;
        mVrShell = mVrClassesWrapper.createVrShell(mActivity, this, mTabModelSelector);
        return mVrShell != null;
    }

    private void addVrViews() {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(mVrShell.getContainer(), params);
        mActivity.onEnterVR();
    }

    private void removeVrViews() {
        mActivity.onExitVR();
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
            mActivity.getCompositorViewHolder().onExitVR(mTabModelSelector);
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
     * @return Pointer to the native VrShellDelegate object.
     */
    @CalledByNative
    private long getNativePointer() {
        return mNativeVrShellDelegate;
    }

    @CalledByNative
    private void showTab(int id) {
        Tab tab = mActivity.getTabModelSelector().getTabById(id);
        if (tab == null) {
            return;
        }
        int index = mActivity.getTabModelSelector().getModel(tab.isIncognito()).indexOf(tab);
        if (index == TabModel.INVALID_TAB_INDEX) {
            return;
        }
        TabModelUtils.setIndex(mActivity.getTabModelSelector().getModel(tab.isIncognito()), index);
    }

    @CalledByNative
    private void openNewTab(boolean incognito) {
        mActivity.getTabCreator(incognito).launchUrl(
                UrlConstants.NTP_URL, TabLaunchType.FROM_CHROME_UI);
    }

    private void destroy() {
        if (sInstance == null) return;
        if (mNativeVrShellDelegate != 0) nativeDestroy(mNativeVrShellDelegate);
        mNativeVrShellDelegate = 0;
        ApplicationStatus.unregisterActivityStateListener(this);
        sInstance = null;
    }

    private native long nativeInit();
    private static native void nativeOnLibraryAvailable();
    private native void nativeSetPresentResult(long nativeVrShellDelegate, boolean result);
    private native void nativeDisplayActivate(long nativeVrShellDelegate);
    private native void nativeUpdateVSyncInterval(long nativeVrShellDelegate, long timebaseNanos,
            double intervalSeconds);
    private native void nativeOnPause(long nativeVrShellDelegate);
    private native void nativeOnResume(long nativeVrShellDelegate);
    private native void nativeDestroy(long nativeVrShellDelegate);
}
