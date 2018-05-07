// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;

import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.compositor.CompositorView;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.modaldialog.ModalDialogManager;
import org.chromium.chrome.browser.page_info.PageInfoPopup;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.vr_shell.keyboard.VrInputMethodManagerWrapper;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.newtab.NewTabButton;
import org.chromium.content_public.browser.ContentViewCore;
import org.chromium.content_public.browser.ImeAdapter;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.display.VirtualDisplayAndroid;

/**
 * This view extends from GvrLayout which wraps a GLSurfaceView that renders VR shell.
 */
@JNINamespace("vr")
public class VrShellImpl
        extends GvrLayout implements VrShell, SurfaceHolder.Callback,
                                     VrInputMethodManagerWrapper.BrowserKeyboardInterface {
    private static final String TAG = "VrShellImpl";
    private static final float INCHES_TO_METERS = 0.0254f;

    private final ChromeActivity mActivity;
    private final CompositorView mCompositorView;
    private final VrCompositorSurfaceManager mVrCompositorSurfaceManager;
    private final VrShellDelegate mDelegate;
    private final VirtualDisplayAndroid mContentVirtualDisplay;
    private final TabRedirectHandler mTabRedirectHandler;
    private final TabObserver mTabObserver;
    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private final View.OnTouchListener mTouchListener;
    private final boolean mVrBrowsingEnabled;

    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;

    private long mNativeVrShell;

    private View mPresentationView;

    // The tab that holds the main ContentViewCore.
    private Tab mTab;
    private ContentViewCore mContentViewCore;
    private Boolean mCanGoBack;
    private Boolean mCanGoForward;

    private VrWindowAndroid mContentVrWindowAndroid;

    private boolean mReprojectedRendering;

    private TabRedirectHandler mNonVrTabRedirectHandler;

    private TabModelSelector mTabModelSelector;
    private float mLastContentWidth;
    private float mLastContentHeight;
    private float mLastContentDpr;
    private Boolean mPaused;

    private boolean mPendingVSyncPause;

    private AndroidUiGestureTarget mAndroidUiGestureTarget;
    private AndroidUiGestureTarget mAndroidDialogGestureTarget;

    private OnDispatchTouchEventCallback mOnDispatchTouchEventForTesting;

    private Surface mContentSurface;
    private VrViewContainer mNonVrViews;
    private VrViewContainer mVrUiViewContainer;
    private FrameLayout mUiView;
    private ModalDialogManager mNonVrModalDialogManager;
    private VrModalPresenter mVrModalPresenter;

    public VrShellImpl(
            ChromeActivity activity, VrShellDelegate delegate, TabModelSelector tabModelSelector) {
        super(activity);
        mActivity = activity;
        mDelegate = delegate;
        mTabModelSelector = tabModelSelector;
        mVrBrowsingEnabled = mDelegate.isVrBrowsingEnabled();

        mActivity.getToolbarManager().setProgressBarEnabled(false);

        DisplayAndroid primaryDisplay = DisplayAndroid.getNonMultiDisplay(activity);
        mContentVirtualDisplay = VirtualDisplayAndroid.createVirtualDisplay();
        mContentVirtualDisplay.setTo(primaryDisplay);

        mContentVrWindowAndroid = new VrWindowAndroid(mActivity, mContentVirtualDisplay);
        reparentAllTabs(mContentVrWindowAndroid);

        mCompositorView = mActivity.getCompositorViewHolder().getCompositorView();
        mVrCompositorSurfaceManager = new VrCompositorSurfaceManager(mCompositorView);
        mCompositorView.replaceSurfaceManagerForVr(
                mVrCompositorSurfaceManager, mContentVrWindowAndroid);

        if (mVrBrowsingEnabled) injectVrRootView();

        // This overrides the default intent created by GVR to return to Chrome when the DON flow
        // is triggered by resuming the GvrLayout, which is the usual way Daydream apps enter VR.
        // See VrShellDelegate#getEnterVrPendingIntent for why we need to do this.
        setReentryIntent(VrShellDelegate.getEnterVrPendingIntent(activity));

        mReprojectedRendering = setAsyncReprojectionEnabled(true);
        if (mReprojectedRendering) {
            // No need render to a Surface if we're reprojected. We'll be rendering with surfaceless
            // EGL.
            mPresentationView = new FrameLayout(mActivity);

            // Only enable sustained performance mode when Async reprojection decouples the app
            // framerate from the display framerate.
            AndroidCompat.setSustainedPerformanceMode(mActivity, true);
        } else {
            SurfaceView surfaceView = new SurfaceView(mActivity);
            surfaceView.getHolder().addCallback(this);
            mPresentationView = surfaceView;
        }

        setPresentationView(mPresentationView);

        getUiLayout().setCloseButtonListener(mDelegate.getVrCloseButtonListener());
        getUiLayout().setSettingsButtonListener(mDelegate.getVrSettingsButtonListener());

        if (mVrBrowsingEnabled) injectVrHostedUiView();

        mTabRedirectHandler = new TabRedirectHandler(mActivity) {
            @Override
            public boolean shouldStayInChrome(boolean hasExternalProtocol) {
                return true;
            }
        };

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onContentChanged(Tab tab) {
                // Restore proper focus on the old CVC.
                if (mContentViewCore != null) mContentViewCore.onWindowFocusChanged(false);
                mContentViewCore = null;
                if (mNativeVrShell == 0) return;
                if (mLastContentWidth != 0) {
                    setContentCssSize(mLastContentWidth, mLastContentHeight, mLastContentDpr);
                }
                if (tab != null && tab.getContentViewCore() != null) {
                    mContentViewCore = tab.getContentViewCore();
                    mContentViewCore.getContainerView().requestFocus();
                    // We need the CVC to think it has Window Focus so it doesn't blur the page,
                    // even though we're drawing VR layouts over top of it.
                    mContentViewCore.onWindowFocusChanged(true);
                }
                nativeSwapContents(mNativeVrShell, tab);
                updateHistoryButtonsVisibility();
            }

            @Override
            public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
                onContentChanged(tab);
            }

            @Override
            public void onLoadProgressChanged(Tab tab, int progress) {
                if (mNativeVrShell == 0) return;
                nativeOnLoadProgressChanged(mNativeVrShell, progress / 100.0);
            }

            @Override
            public void onCrash(Tab tab, boolean sadTabShown) {
                updateHistoryButtonsVisibility();
            }

            @Override
            public void onLoadStarted(Tab tab, boolean toDifferentDocument) {
                if (!toDifferentDocument) return;
                updateHistoryButtonsVisibility();
            }

            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                if (!toDifferentDocument) return;
                updateHistoryButtonsVisibility();
            }

            @Override
            public void onUrlUpdated(Tab tab) {
                updateHistoryButtonsVisibility();
            }
        };

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onChange() {
                swapToForegroundTab();
            }

            @Override
            public void onNewTabCreated(Tab tab) {
                if (mNativeVrShell == 0) return;
                nativeOnTabUpdated(mNativeVrShell, tab.isIncognito(), tab.getId(), tab.getTitle());
            }
        };

        mTouchListener = new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    nativeOnTriggerEvent(mNativeVrShell, true);
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    nativeOnTriggerEvent(mNativeVrShell, false);
                    return true;
                }
                return false;
            }
        };
    }

    private void injectVrRootView() {
        // Inject a view into the hierarchy above R.id.content so that the rest of Chrome can
        // remain unaware/uncaring of its existence. This view is used to draw the view hierarchy
        // into a texture when browsing in VR. See https://crbug.com/793430.
        View content = mActivity.getWindow().findViewById(android.R.id.content);
        ViewGroup parent = (ViewGroup) content.getParent();
        VrViewContainer viewContainer = new VrViewContainer(mActivity);
        parent.removeView(content);
        parent.addView(viewContainer);
        viewContainer.addView(content);
        mNonVrViews = viewContainer;
    }

    private void injectVrHostedUiView() {
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_NATIVE_ANDROID_UI)) return;
        mNonVrModalDialogManager = mActivity.getModalDialogManager();
        mNonVrModalDialogManager.cancelAllDialogs();
        mVrModalPresenter = new VrModalPresenter(this);
        mActivity.setModalDialogManager(
                new ModalDialogManager(mVrModalPresenter, ModalDialogManager.APP_MODAL));

        ViewGroup decor = (ViewGroup) mActivity.getWindow().getDecorView();
        mUiView = new FrameLayout(decor.getContext());
        LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(mUiView, params);
        mVrUiViewContainer = new VrViewContainer(mActivity);
        mUiView.addView(mVrUiViewContainer);
    }

    private void removeVrRootView() {
        ViewGroup parent = (ViewGroup) mNonVrViews.getParent();
        assert mNonVrViews.getChildCount() == 1;
        ViewGroup child = (ViewGroup) mNonVrViews.getChildAt(0);
        mNonVrViews.removeAllViews();
        parent.removeView(mNonVrViews);
        parent.addView(child);
        // Ensure the omnibox doesn't get initial focus (as it would when re-attaching the views
        // to a window), and immediately bring up the keyboard.
        if (mActivity.getCompositorViewHolder() != null) {
            mActivity.getCompositorViewHolder().requestFocus();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.N)
    public void initializeNative(Tab currentTab, boolean forWebVr,
            boolean webVrAutopresentationExpected, boolean inCct) {
        Tab tab = mActivity.getActivityTab();
        if (mActivity.isInOverviewMode() || tab == null) {
            launchNTP();
            tab = mActivity.getActivityTab();
        }
        if (mActivity.getBottomSheet() != null) {
            // Make sure the bottom sheet (Chrome Home) is hidden.
            mActivity.getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_PEEK, false);
        }

        // Start with content rendering paused if the renderer-drawn controls are visible, as this
        // would cause the in-content omnibox to be shown to users.
        boolean pauseContent = mActivity.getFullscreenManager().getContentOffset() > 0;

        // Get physical and pixel size of the display, which is needed by native
        // to dynamically calculate the content's resolution and window size.
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        // We're supposed to be in landscape at this point, but it's possible for us to get here
        // before the change has fully propogated. In this case, the width and height are swapped,
        // which causes an incorrect display size to be used, and the page to appear zoomed in.
        if (dm.widthPixels < dm.heightPixels) {
            int tempWidth = dm.heightPixels;
            dm.heightPixels = dm.widthPixels;
            dm.widthPixels = tempWidth;
            float tempXDpi = dm.ydpi;
            dm.xdpi = dm.ydpi;
            dm.ydpi = tempXDpi;
        }
        float displayWidthMeters = (dm.widthPixels / dm.xdpi) * INCHES_TO_METERS;
        float displayHeightMeters = (dm.heightPixels / dm.ydpi) * INCHES_TO_METERS;

        boolean hasOrCanRequestAudioPermission =
                mActivity.getWindowAndroid().hasPermission(android.Manifest.permission.RECORD_AUDIO)
                || mActivity.getWindowAndroid().canRequestPermission(
                           android.Manifest.permission.RECORD_AUDIO);
        mNativeVrShell = nativeInit(mDelegate, forWebVr, webVrAutopresentationExpected, inCct,
                !mVrBrowsingEnabled, hasOrCanRequestAudioPermission,
                getGvrApi().getNativeGvrContext(), mReprojectedRendering, displayWidthMeters,
                displayHeightMeters, dm.widthPixels, dm.heightPixels, pauseContent);

        swapToTab(currentTab);
        createTabList();
        mActivity.getTabModelSelector().addObserver(mTabModelSelectorObserver);
        createTabModelSelectorTabObserver();
        updateHistoryButtonsVisibility();

        mPresentationView.setOnTouchListener(mTouchListener);

        if (mVrBrowsingEnabled) {
            mAndroidUiGestureTarget = new AndroidUiGestureTarget(mNonVrViews.getInputTarget(),
                    mContentVrWindowAndroid.getDisplay().getDipScale(), getNativePageScrollRatio(),
                    getTouchSlop());
            nativeSetAndroidGestureTarget(mNativeVrShell, mAndroidUiGestureTarget);
        }
    }

    private void createTabList() {
        assert mNativeVrShell != 0;
        TabModel main = mTabModelSelector.getModel(false);
        int count = main.getCount();
        Tab[] mainTabs = new Tab[count];
        for (int i = 0; i < count; ++i) {
            mainTabs[i] = main.getTabAt(i);
        }
        TabModel incognito = mTabModelSelector.getModel(true);
        count = incognito.getCount();
        Tab[] incognitoTabs = new Tab[count];
        for (int i = 0; i < count; ++i) {
            incognitoTabs[i] = incognito.getTabAt(i);
        }
        nativeOnTabListCreated(mNativeVrShell, mainTabs, incognitoTabs);
    }

    private void swapToForegroundTab() {
        Tab tab = mActivity.getActivityTab();
        if (tab == mTab) return;
        swapToTab(tab);
    }

    private void swapToTab(Tab tab) {
        if (mTab != null) {
            mTab.removeObserver(mTabObserver);
            restoreTabFromVR();
            mTab.updateFullscreenEnabledState();
        }

        mTab = tab;
        if (mTab != null) {
            initializeTabForVR();
            mTab.addObserver(mTabObserver);
            mTab.updateFullscreenEnabledState();
        }
        mTabObserver.onContentChanged(mTab);
    }

    private void initializeTabForVR() {
        if (mTab == null) return;
        // Make sure we are not redirecting to another app, i.e. out of VR mode.
        mNonVrTabRedirectHandler = mTab.getTabRedirectHandler();
        mTab.setTabRedirectHandler(mTabRedirectHandler);
        assert mTab.getWindowAndroid() == mContentVrWindowAndroid;
        initializeImeForVr();
    }

    private void initializeImeForVr() {
        assert mTab != null;
        if (mTab.getWebContents() == null) return;
        ImeAdapter imeAdapter = ImeAdapter.fromWebContents(mTab.getWebContents());
        if (imeAdapter != null) {
            imeAdapter.setInputMethodManagerWrapper(
                    new VrInputMethodManagerWrapper(mActivity, this));
        }
    }

    private void uninitializeImeForVr() {
        assert mTab != null;
        if (mTab.getWebContents() == null) return;
        ImeAdapter imeAdapter = ImeAdapter.fromWebContents(mTab.getWebContents());
        if (imeAdapter != null) {
            imeAdapter.setInputMethodManagerWrapper(
                    ImeAdapter.createDefaultInputMethodManagerWrapper(mActivity));
        }
    }

    private void restoreTabFromVR() {
        if (mTab == null) return;
        mTab.setTabRedirectHandler(mNonVrTabRedirectHandler);
        mNonVrTabRedirectHandler = null;
        uninitializeImeForVr();
    }

    private void reparentAllTabs(WindowAndroid window) {
        // Ensure new tabs are created with the correct window.
        boolean[] values = {true, false};
        for (boolean incognito : values) {
            TabCreator tabCreator = mActivity.getTabCreator(incognito);
            if (tabCreator instanceof ChromeTabCreator) {
                ((ChromeTabCreator) tabCreator).setWindowAndroid(window);
            }
        }

        // Reparent all existing tabs.
        for (TabModel model : mActivity.getTabModelSelector().getModels()) {
            for (int i = 0; i < model.getCount(); ++i) {
                model.getTabAt(i).updateWindowAndroid(window);
            }
        }
    }

    // Returns true if Chrome has permission to use audio input.
    @CalledByNative
    public boolean hasAudioPermission() {
        return mDelegate.hasAudioPermission();
    }

    // Exits VR, telling the user to remove their headset, and returning to Chromium.
    @CalledByNative
    public void forceExitVr() {
        mDelegate.showDoff(false);
    }

    // Called because showing PageInfo isn't supported in VR. This happens when the user clicks on
    // the security icon in the URL bar.
    @CalledByNative
    public void onUnhandledPageInfo() {
        VrShellDelegate.requestToExitVr(new OnExitVrRequestListener() {
            @Override
            public void onSucceeded() {
                PageInfoPopup.show(
                        mActivity, mActivity.getActivityTab(), null, PageInfoPopup.OPENED_FROM_VR);
            }

            @Override
            public void onDenied() {}
        }, UiUnsupportedMode.UNHANDLED_PAGE_INFO);
    }

    // Called because showing audio permission dialog isn't supported in VR. This happens when
    // the user wants to do a voice search.
    @CalledByNative
    public void onUnhandledPermissionPrompt() {
        VrShellDelegate.requestToExitVr(new OnExitVrRequestListener() {
            @Override
            public void onSucceeded() {
                PermissionCallback callback = new PermissionCallback() {
                    @Override
                    public void onRequestPermissionsResult(
                            String[] permissions, int[] grantResults) {
                        ThreadUtils.postOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VrShellDelegate.enterVrIfNecessary();
                            }
                        });
                    }
                };
                String[] permissionArray = new String[1];
                permissionArray[0] = android.Manifest.permission.RECORD_AUDIO;
                mActivity.getWindowAndroid().requestPermissions(permissionArray, callback);
            }

            @Override
            public void onDenied() {}
        }, UiUnsupportedMode.VOICE_SEARCH_NEEDS_RECORD_AUDIO_OS_PERMISSION);
    }

    // Called when the user has an older GVR Keyboard installed on their device and we need them to
    // have a newer one.
    @CalledByNative
    public void onNeedsKeyboardUpdate() {
        VrShellDelegate.requestToExitVr(new OnExitVrRequestListener() {
            @Override
            public void onSucceeded() {
                mDelegate.promptForKeyboardUpdate();
            }

            @Override
            public void onDenied() {}
        }, UiUnsupportedMode.NEEDS_KEYBOARD_UPDATE);
    }

    // Exits CCT, returning to the app that opened it.
    @CalledByNative
    public void exitCct() {
        mDelegate.exitCctFromUi();
    }

    // Close the current hosted Dialog in VR
    @CalledByNative
    public void closeCurrentDialog() {
        mVrModalPresenter.closeCurrentDialog();
    }

    @CalledByNative
    public void setContentCssSize(float width, float height, float dpr) {
        ThreadUtils.assertOnUiThread();
        boolean surfaceUninitialized = mLastContentWidth == 0;
        mLastContentWidth = width;
        mLastContentHeight = height;
        mLastContentDpr = dpr;

        // Java views don't listen to our DPR changes, so to get them to render at the correct
        // size we need to make them larger.
        DisplayAndroid primaryDisplay = DisplayAndroid.getNonMultiDisplay(mActivity);
        float dip = primaryDisplay.getDipScale();

        int contentWidth = (int) Math.ceil(width * dpr);
        int contentHeight = (int) Math.ceil(height * dpr);

        int overlayWidth = (int) Math.ceil(width * dip);
        int overlayHeight = (int) Math.ceil(height * dip);

        nativeBufferBoundsChanged(
                mNativeVrShell, contentWidth, contentHeight, overlayWidth, overlayHeight);
        if (mContentSurface != null) {
            if (surfaceUninitialized) {
                mVrCompositorSurfaceManager.setSurface(
                        mContentSurface, PixelFormat.OPAQUE, contentWidth, contentHeight);
            } else {
                mVrCompositorSurfaceManager.surfaceResized(contentWidth, contentHeight);
            }
        }
        Point size = new Point(contentWidth, contentHeight);
        mContentVirtualDisplay.update(size, dpr, dip / dpr, null, null, null, null, null);
        if (mTab != null && mTab.getWebContents() != null) {
            mTab.getWebContents().setSize(contentWidth, contentHeight);
        }
        if (mVrBrowsingEnabled) mNonVrViews.resize(overlayWidth, overlayHeight);
    }

    @CalledByNative
    public void contentSurfaceCreated(Surface surface) {
        mContentSurface = surface;
        if (mLastContentWidth == 0) return;
        int width = (int) Math.ceil(mLastContentWidth * mLastContentDpr);
        int height = (int) Math.ceil(mLastContentHeight * mLastContentDpr);
        mVrCompositorSurfaceManager.setSurface(mContentSurface, PixelFormat.OPAQUE, width, height);
    }

    @CalledByNative
    public void contentOverlaySurfaceCreated(Surface surface) {
        if (mVrBrowsingEnabled) mNonVrViews.setSurface(surface);
    }

    @CalledByNative
    public void dialogSurfaceCreated(Surface surface) {
        if (mVrBrowsingEnabled && mVrUiViewContainer != null)
            mVrUiViewContainer.setSurface(surface);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean parentConsumed = super.dispatchTouchEvent(event);
        if (mOnDispatchTouchEventForTesting != null) {
            mOnDispatchTouchEventForTesting.onDispatchTouchEvent(parentConsumed);
        }
        return parentConsumed;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mTab != null && mTab.getContentViewCore() != null
                && mTab.getContentViewCore().dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mTab != null && mTab.getContentViewCore() != null
                && mTab.getContentViewCore().onGenericMotionEvent(event)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onResume() {
        if (mPaused != null && !mPaused) return;
        mPaused = false;
        super.onResume();
        if (mNativeVrShell != 0) {
            // Refreshing the viewer profile may accesses disk under some circumstances outside of
            // our control.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                nativeOnResume(mNativeVrShell);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
    }

    @Override
    public void onPause() {
        if (mPaused != null && mPaused) return;
        mPaused = true;
        super.onPause();
        if (mNativeVrShell != 0) nativeOnPause(mNativeVrShell);
    }

    @Override
    public void shutdown() {
        if (mVrBrowsingEnabled) {
            mNonVrViews.destroy();
            if (mVrUiViewContainer != null) mVrUiViewContainer.destroy();
            removeVrRootView();
        }

        mActivity.getFullscreenManager().setPersistentFullscreenMode(false);
        reparentAllTabs(mActivity.getWindowAndroid());
        if (mNativeVrShell != 0) {
            nativeDestroy(mNativeVrShell);
            mNativeVrShell = 0;
        }
        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        mTabModelSelectorTabObserver.destroy();
        if (mTab != null) {
            mTab.removeObserver(mTabObserver);
            restoreTabFromVR();
            uninitializeImeForVr();
            if (mTab.getContentViewCore() != null) {
                View parent = mTab.getContentViewCore().getContainerView();
                mTab.getWebContents().setSize(parent.getWidth(), parent.getHeight());
            }
            mTab.updateFullscreenEnabledState();
        }

        mContentVirtualDisplay.destroy();

        mCompositorView.onExitVr(mActivity.getWindowAndroid());

        if (mActivity.getToolbarManager() != null) {
            mActivity.getToolbarManager().setProgressBarEnabled(true);
        }

        // Since VSync was paused, control heights may not have been propagated. If we request to
        // show the controls before the old values have propagated we'll end up with the old values
        // (ie. the controls hidden). The values will have propagated with the next frame received
        // from the compositor, so we can tell the controls to show at that point.
        if (mActivity.getCompositorViewHolder() != null
                && mActivity.getCompositorViewHolder().getCompositorView() != null) {
            mActivity.getCompositorViewHolder().getCompositorView().surfaceRedrawNeededAsync(() -> {
                ChromeFullscreenManager manager = mActivity.getFullscreenManager();
                manager.getBrowserVisibilityDelegate().showControlsTransient();
            });
        }

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_NATIVE_ANDROID_UI)) {
            mActivity.getModalDialogManager().cancelAllDialogs();
            mActivity.setModalDialogManager(mNonVrModalDialogManager);
        }

        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mUiView);
        super.shutdown();
    }

    @Override
    public void pause() {
        onPause();
    }

    @Override
    public void resume() {
        onResume();
    }

    @Override
    public void teardown() {
        shutdown();
    }

    /**
     * Set View for the Dialog that should show up on top of the main content.
     */
    @Override
    public void setDialogView(View view) {
        if (getWebVrModeEnabled()) return;
        if (view == null) return;
        assert mVrUiViewContainer.getChildCount() == 0;
        mVrUiViewContainer.addView(view);
    }

    /**
     * Close the popup Dialog in VR.
     */
    @Override
    public void closeVrDialog() {
        nativeCloseAlertDialog(mNativeVrShell);
        mVrUiViewContainer.removeAllViews();
    }

    /**
     * Set size of the Dialog in VR.
     */
    @Override
    public void setDialogSize(int width, int height) {
        if (getWebVrModeEnabled()) return;
        nativeSetAlertDialogSize(mNativeVrShell, width, height);
    }

    /**
     * Initialize the Dialog in VR.
     */
    @Override
    public void initVrDialog(int width, int height) {
        if (getWebVrModeEnabled()) {
            if (mVrBrowsingEnabled) {
                mDelegate.exitWebVRPresent();
            } else {
                // TODO (asimjour): We should be able to show the dialogs in webvr. But for now,
                // we close the dialog. Closing the dialog cannot happen in the show() method,
                // therefore we have to post task the call to Presenter to close the dialog.
                ThreadUtils.postOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mVrModalPresenter.closeCurrentDialog();
                    }
                });
                return;
            }
        }

        nativeSetAlertDialog(mNativeVrShell, width, height);
        mAndroidDialogGestureTarget =
                new AndroidUiGestureTarget(mVrUiViewContainer.getInputTarget(), 1.0f,
                        getNativePageScrollRatio(), getTouchSlop());
        nativeSetDialogGestureTarget(mNativeVrShell, mAndroidDialogGestureTarget);
    }

    @Override
    public void setWebVrModeEnabled(boolean enabled, boolean showToast) {
        if (mNativeVrShell != 0) nativeSetWebVrMode(mNativeVrShell, enabled, showToast);
        if (!enabled) {
            mContentVrWindowAndroid.setVSyncPaused(false);
            mPendingVSyncPause = false;
            return;
        }
        // Wait for the compositor to produce a frame to allow the omnibox to start hiding
        // before we pause VSync. Control heights may not be correct as the omnibox might
        // animate, but this is handled when exiting VR.
        mPendingVSyncPause = true;
        mActivity.getCompositorViewHolder().getCompositorView().surfaceRedrawNeededAsync(() -> {
            if (mPendingVSyncPause) {
                mContentVrWindowAndroid.setVSyncPaused(true);
                mPendingVSyncPause = false;
            }
        });
    }

    @Override
    public boolean getWebVrModeEnabled() {
        if (mNativeVrShell == 0) return false;
        return nativeGetWebVrMode(mNativeVrShell);
    }

    @Override
    public boolean isDisplayingUrlForTesting() {
        assert mNativeVrShell != 0;
        return nativeIsDisplayingUrlForTesting(mNativeVrShell);
    }

    @Override
    public FrameLayout getContainer() {
        return this;
    }

    @Override
    public void rawTopContentOffsetChanged(float topContentOffset) {
        if (topContentOffset != 0) return;
        // Wait until a new frame is definitely available.
        mActivity.getCompositorViewHolder().getCompositorView().surfaceRedrawNeededAsync(() -> {
            if (mNativeVrShell != 0) nativeResumeContentRendering(mNativeVrShell);
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mNativeVrShell == 0) return;
        nativeSetSurface(mNativeVrShell, holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // No need to do anything here, we don't care about surface width/height.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mVrCompositorSurfaceManager.surfaceDestroyed();
        if (mNativeVrShell != 0) nativeSetSurface(mNativeVrShell, null);
    }

    private void createTabModelSelectorTabObserver() {
        assert mTabModelSelectorTabObserver == null;
        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(mTabModelSelector) {
            @Override
            public void onTitleUpdated(Tab tab) {
                if (mNativeVrShell == 0) return;
                nativeOnTabUpdated(mNativeVrShell, tab.isIncognito(), tab.getId(), tab.getTitle());
            }

            @Override
            public void onClosingStateChanged(Tab tab, boolean closing) {
                if (mNativeVrShell == 0) return;
                if (closing) {
                    nativeOnTabRemoved(mNativeVrShell, tab.isIncognito(), tab.getId());
                } else {
                    nativeOnTabUpdated(mNativeVrShell, tab.isIncognito(), tab.getId(),
                            tab.getTitle());
                }
            }

            @Override
            public void onDestroyed(Tab tab) {
                if (mNativeVrShell == 0) return;
                nativeOnTabRemoved(mNativeVrShell, tab.isIncognito(), tab.getId());
            }
        };
    }

    @CalledByNative
    public boolean hasDaydreamSupport() {
        return mDelegate.hasDaydreamSupport();
    }

    @Override
    public void requestToExitVr(@UiUnsupportedMode int reason) {
        if (mNativeVrShell != 0) nativeRequestToExitVr(mNativeVrShell, reason);
    }

    @CalledByNative
    private void onExitVrRequestResult(@UiUnsupportedMode int reason, boolean shouldExit) {
        if (shouldExit) {
            if (mNativeVrShell != 0) nativeLogUnsupportedModeUserMetric(mNativeVrShell, reason);
        }
        mDelegate.onExitVrRequestResult(shouldExit);
    }

    @CalledByNative
    private void loadUrl(String url) {
        if (mTab == null) {
            mActivity.getCurrentTabCreator().createNewTab(
                    new LoadUrlParams(url), TabLaunchType.FROM_CHROME_UI, null);
        } else {
            mTab.loadUrl(new LoadUrlParams(url));
        }
    }

    @VisibleForTesting
    @Override
    public void navigateForward() {
        if (!mCanGoForward) return;
        mActivity.getToolbarManager().forward();
        updateHistoryButtonsVisibility();
    }

    @VisibleForTesting
    @Override
    @CalledByNative
    public void navigateBack() {
        if (!mCanGoBack) return;
        if (mActivity instanceof ChromeTabbedActivity) {
            // TODO(mthiesse): We should do this for custom tabs as well, as back for custom tabs
            // is also expected to close tabs.
            ((ChromeTabbedActivity) mActivity).handleBackPressed();
        } else {
            mActivity.getToolbarManager().back();
        }
        updateHistoryButtonsVisibility();
    }

    private void updateHistoryButtonsVisibility() {
        if (mNativeVrShell == 0) return;
        if (mTab == null) {
            mCanGoBack = false;
            mCanGoForward = false;
            nativeSetHistoryButtonsEnabled(mNativeVrShell, mCanGoBack, mCanGoForward);
            return;
        }
        boolean willCloseTab = false;
        if (mActivity instanceof ChromeTabbedActivity) {
            // If hitting back would minimize Chrome, disable the back button.
            // See ChromeTabbedActivity#handleBackPressed().
            willCloseTab = ChromeTabbedActivity.backShouldCloseTab(mTab)
                    && !mTab.isCreatedForExternalApp();
        }
        boolean canGoBack = mTab.canGoBack() || willCloseTab;
        boolean canGoForward = mTab.canGoForward();
        if ((mCanGoBack != null && canGoBack == mCanGoBack)
                && (mCanGoForward != null && canGoForward == mCanGoForward)) {
            return;
        }
        mCanGoBack = canGoBack;
        mCanGoForward = canGoForward;
        nativeSetHistoryButtonsEnabled(mNativeVrShell, mCanGoBack, mCanGoForward);
    }

    private float getNativePageScrollRatio() {
        return mActivity.getWindowAndroid().getDisplay().getDipScale()
                / mContentVrWindowAndroid.getDisplay().getDipScale();
    }

    private int getTouchSlop() {
        ViewConfiguration vc = ViewConfiguration.get(mActivity);
        return vc.getScaledTouchSlop();
    }

    private void launchNTP() {
        NewTabButton button = (NewTabButton) mActivity.findViewById(R.id.new_tab_button);
        button.callOnClick();
    }

    /**
     * Sets the callback that will be run when VrShellImpl's dispatchTouchEvent
     * is run and the parent consumed the event.
     * @param callback The Callback to be run.
     */
    @VisibleForTesting
    public void setOnDispatchTouchEventForTesting(OnDispatchTouchEventCallback callback) {
        mOnDispatchTouchEventForTesting = callback;
    }

    @VisibleForTesting
    @Override
    public Boolean isBackButtonEnabled() {
        return mCanGoBack;
    }

    @VisibleForTesting
    @Override
    public Boolean isForwardButtonEnabled() {
        return mCanGoForward;
    }

    @VisibleForTesting
    public float getContentWidthForTesting() {
        return mLastContentWidth;
    }

    @VisibleForTesting
    public float getContentHeightForTesting() {
        return mLastContentHeight;
    }

    @VisibleForTesting
    public View getPresentationViewForTesting() {
        return mPresentationView;
    }

    @Override
    public void showSoftInput(boolean show) {
        assert mNativeVrShell != 0;
        nativeShowSoftInput(mNativeVrShell, show);
    }

    @Override
    public void updateIndices(
            int selectionStart, int selectionEnd, int compositionStart, int compositionEnd) {
        assert mNativeVrShell != 0;
        nativeUpdateWebInputIndices(
                mNativeVrShell, selectionStart, selectionEnd, compositionStart, compositionEnd);
    }

    @Override
    public void acceptDoffPromptForTesting() {
        nativeAcceptDoffPromptForTesting(mNativeVrShell);
    }

    private native long nativeInit(VrShellDelegate delegate, boolean forWebVR,
            boolean webVrAutopresentationExpected, boolean inCct, boolean browsingDisabled,
            boolean hasOrCanRequestAudioPermission, long gvrApi, boolean reprojectedRendering,
            float displayWidthMeters, float displayHeightMeters, int displayWidthPixels,
            int displayHeightPixels, boolean pauseContent);
    private native void nativeSetSurface(long nativeVrShell, Surface surface);
    private native void nativeSwapContents(long nativeVrShell, Tab tab);
    private native void nativeSetAndroidGestureTarget(
            long nativeVrShell, AndroidUiGestureTarget androidUiGestureTarget);
    private native void nativeSetDialogGestureTarget(
            long nativeVrShell, AndroidUiGestureTarget dialogGestureTarget);
    private native void nativeDestroy(long nativeVrShell);
    private native void nativeOnTriggerEvent(long nativeVrShell, boolean touched);
    private native void nativeOnPause(long nativeVrShell);
    private native void nativeOnResume(long nativeVrShell);
    private native void nativeOnLoadProgressChanged(long nativeVrShell, double progress);
    private native void nativeBufferBoundsChanged(long nativeVrShell, int contentWidth,
            int contentHeight, int overlayWidth, int overlayHeight);
    private native void nativeSetWebVrMode(long nativeVrShell, boolean enabled, boolean showToast);
    private native boolean nativeGetWebVrMode(long nativeVrShell);
    private native boolean nativeIsDisplayingUrlForTesting(long nativeVrShell);
    private native void nativeOnTabListCreated(long nativeVrShell, Tab[] mainTabs,
            Tab[] incognitoTabs);
    private native void nativeOnTabUpdated(long nativeVrShell, boolean incognito, int id,
            String title);
    private native void nativeOnTabRemoved(long nativeVrShell, boolean incognito, int id);
    private native void nativeCloseAlertDialog(long nativeVrShell);
    private native void nativeSetAlertDialog(long nativeVrShell, int width, int height);
    private native void nativeSetAlertDialogSize(long nativeVrShell, int width, int height);
    private native void nativeSetHistoryButtonsEnabled(
            long nativeVrShell, boolean canGoBack, boolean canGoForward);
    private native void nativeRequestToExitVr(long nativeVrShell, @UiUnsupportedMode int reason);
    private native void nativeLogUnsupportedModeUserMetric(
            long nativeVrShell, @UiUnsupportedMode int mode);
    private native void nativeShowSoftInput(long nativeVrShell, boolean show);
    private native void nativeUpdateWebInputIndices(long nativeVrShell, int selectionStart,
            int selectionEnd, int compositionStart, int compositionEnd);
    private native void nativeAcceptDoffPromptForTesting(long nativeVrShell);
    private native void nativeResumeContentRendering(long nativeVrShell);
}
