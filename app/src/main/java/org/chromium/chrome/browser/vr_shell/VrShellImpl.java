// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.StrictMode;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.FrameLayout;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.MotionEventSynthesizer;
import org.chromium.content.browser.WindowAndroidChangedObserver;
import org.chromium.content.browser.WindowAndroidProvider;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.ViewAndroidDelegate;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.display.VirtualDisplayAndroid;

/**
 * This view extends from GvrLayout which wraps a GLSurfaceView that renders VR shell.
 */
@JNINamespace("vr_shell")
public class VrShellImpl
        extends GvrLayout implements VrShell, SurfaceHolder.Callback, WindowAndroidProvider {
    private static final String TAG = "VrShellImpl";

    // TODO(mthiesse): These values work well for Pixel/Pixel XL in VR, but we need to come up with
    // a way to compute good values for any screen size/scaling ratio.

    // Increasing DPR any more than this doesn't appear to increase text quality.
    private static final float DEFAULT_DPR = 1.2f;
    // For WebVR we just create a DPR 1.0 display that matches the physical display size.
    private static final float WEBVR_DPR = 1.0f;
    // Fairly arbitrary values that put a good amount of content on the screen without making the
    // text too small to read.
    private static final float DEFAULT_CONTENT_WIDTH = 960f;
    private static final float DEFAULT_CONTENT_HEIGHT = 640f;
    // Temporary values that will be changed when the UI loads and figures out how what size it
    // needs to be.
    private static final float DEFAULT_UI_WIDTH = 1920f;
    private static final float DEFAULT_UI_HEIGHT = 1080f;

    private final ChromeActivity mActivity;
    private final VrShellDelegate mDelegate;
    private final VirtualDisplayAndroid mContentVirtualDisplay;
    private final VirtualDisplayAndroid mUiVirtualDisplay;
    private final TabRedirectHandler mTabRedirectHandler;
    private final TabObserver mTabObserver;
    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private final View.OnTouchListener mTouchListener;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;

    private long mNativeVrShell;

    private FrameLayout mUiCVCContainer;
    private FrameLayout mRenderToSurfaceLayout;
    private Surface mSurface;
    private View mPresentationView;

    // The tab that holds the main ContentViewCore.
    private Tab mTab;
    private NativePage mNativePage;

    private WindowAndroid mOriginalWindowAndroid;
    private VrWindowAndroid mContentVrWindowAndroid;

    private WebContents mUiContents;
    private ContentViewCore mUiCVC;
    private VrWindowAndroid mUiVrWindowAndroid;

    private boolean mReprojectedRendering;

    private TabRedirectHandler mNonVrTabRedirectHandler;
    private TabModelSelector mTabModelSelector;
    private float mLastContentWidth;
    private float mLastContentHeight;
    private float mLastContentDpr;

    private MotionEventSynthesizer mMotionEventSynthesizer;

    public VrShellImpl(
            ChromeActivity activity, VrShellDelegate delegate, TabModelSelector tabModelSelector) {
        super(activity);
        mActivity = activity;
        mDelegate = delegate;
        mTabModelSelector = tabModelSelector;
        mUiCVCContainer = new FrameLayout(getContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return true;
            }
        };
        addView(mUiCVCContainer, 0, new FrameLayout.LayoutParams(0, 0));

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

        getUiLayout().setCloseButtonListener(new Runnable() {
            @Override
            public void run() {
                mDelegate.shutdownVR(false /* isPausing */, false /* showTransition */);
            }
        });

        DisplayAndroid primaryDisplay = DisplayAndroid.getNonMultiDisplay(activity);
        mContentVirtualDisplay = VirtualDisplayAndroid.createVirtualDisplay();
        mContentVirtualDisplay.setTo(primaryDisplay);
        mUiVirtualDisplay = VirtualDisplayAndroid.createVirtualDisplay();
        mUiVirtualDisplay.setTo(primaryDisplay);

        mTabRedirectHandler = new TabRedirectHandler(mActivity) {
            @Override
            public boolean shouldStayInChrome(boolean hasExternalProtocol) {
                return true;
            }
        };

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onContentChanged(Tab tab) {
                if (mNativeVrShell == 0) return;
                if (tab.isShowingSadTab()) {
                    // For now we don't support the sad tab page. crbug.com/661609.
                    forceExitVr();
                    return;
                }
                if (mNativePage != null) {
                    UiUtils.removeViewFromParent(mNativePage.getView());
                    mNativePage = null;
                    mMotionEventSynthesizer = null;
                    if (tab.getNativePage() == null) {
                        nativeRestoreContentSurface(mNativeVrShell);
                        mRenderToSurfaceLayout.setVisibility(View.INVISIBLE);
                        mSurface = null;
                    }
                }
                if (tab.getNativePage() != null) {
                    mRenderToSurfaceLayout.setVisibility(View.VISIBLE);
                    mNativePage = tab.getNativePage();
                    if (mSurface == null) mSurface = nativeTakeContentSurface(mNativeVrShell);
                    mRenderToSurfaceLayout.addView(mNativePage.getView(),
                            new FrameLayout.LayoutParams(
                                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    mNativePage.getView().invalidate();
                    mMotionEventSynthesizer =
                            new MotionEventSynthesizer(mNativePage.getView(), VrShellImpl.this);
                }
                setContentCssSize(mLastContentWidth, mLastContentHeight, mLastContentDpr);
                if (tab.getNativePage() == null && mTab.getContentViewCore() != null) {
                    mTab.getContentViewCore().onAttachedToWindow();
                    mTab.getContentViewCore().getContainerView().requestFocus();
                    nativeSwapContents(
                            mNativeVrShell, mTab.getContentViewCore().getWebContents(), null);
                } else {
                    nativeSwapContents(mNativeVrShell, null, mMotionEventSynthesizer);
                }
            }

            @Override
            public void onWebContentsSwapped(
                    Tab tab, boolean didStartLoad, boolean didFinishLoad) {
                onContentChanged(tab);
            }

            @Override
            public void onLoadProgressChanged(Tab tab, int progress) {
                if (mNativeVrShell == 0) return;
                nativeOnLoadProgressChanged(mNativeVrShell, progress / 100.0);
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
                if (!CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VR_SHELL_DEV)
                        && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    nativeOnTriggerEvent(mNativeVrShell);
                    return true;
                }
                return false;
            }
        };

        mRenderToSurfaceLayout = new FrameLayout(mActivity) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (mSurface == null) return;
                // TODO(mthiesse): Support mSurface.lockHardwareCanvas(); crbug.com/692775
                final Canvas surfaceCanvas = mSurface.lockCanvas(null);
                super.dispatchDraw(surfaceCanvas);
                mSurface.unlockCanvasAndPost(surfaceCanvas);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return true;
            }
        };
        mRenderToSurfaceLayout.setVisibility(View.INVISIBLE);
        // We need a pre-draw listener to invalidate the native page because scrolling usually
        // doesn't trigger an onDraw call, so our texture won't get updated.
        mRenderToSurfaceLayout.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (mRenderToSurfaceLayout.isDirty()) {
                    mRenderToSurfaceLayout.invalidate();
                    if (mNativePage != null) mNativePage.getView().invalidate();
                }
                return true;
            }
        });
        addView(mRenderToSurfaceLayout);
    }

    @Override
    public void initializeNative(Tab currentTab, boolean forWebVR) {
        mContentVrWindowAndroid = new VrWindowAndroid(mActivity, mContentVirtualDisplay);

        mUiVrWindowAndroid = new VrWindowAndroid(mActivity, mUiVirtualDisplay);
        mUiContents = WebContentsFactory.createWebContents(true, false);
        mUiCVC = new ContentViewCore(mActivity, ChromeVersionInfo.getProductVersion());
        ContentView uiContentView = ContentView.createContentView(mActivity, mUiCVC);
        mUiCVC.initialize(ViewAndroidDelegate.createBasicDelegate(uiContentView),
                uiContentView, mUiContents, mUiVrWindowAndroid);

        mNativeVrShell = nativeInit(mUiContents, mContentVrWindowAndroid.getNativePointer(),
                mUiVrWindowAndroid.getNativePointer(), forWebVR, mDelegate,
                getGvrApi().getNativeGvrContext(), mReprojectedRendering);

        // Set the UI and content sizes before we load the UI.
        setUiCssSize(DEFAULT_UI_WIDTH, DEFAULT_UI_HEIGHT, DEFAULT_DPR);
        if (forWebVR) {
            DisplayAndroid primaryDisplay = DisplayAndroid.getNonMultiDisplay(mActivity);
            setContentCssSize(primaryDisplay.getPhysicalDisplayWidth(),
                    primaryDisplay.getPhysicalDisplayHeight(), WEBVR_DPR);
        } else {
            setContentCssSize(DEFAULT_CONTENT_WIDTH, DEFAULT_CONTENT_HEIGHT, DEFAULT_DPR);
        }

        swapToForegroundTab();
        createTabList();
        mActivity.getTabModelSelector().addObserver(mTabModelSelectorObserver);
        createTabModelSelectorTabObserver();

        nativeLoadUIContent(mNativeVrShell);

        mPresentationView.setOnTouchListener(mTouchListener);

        uiContentView.setVisibility(View.VISIBLE);
        mUiCVC.onShow();
        mUiCVCContainer.addView(uiContentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mUiCVC.setBottomControlsHeight(0);
        mUiCVC.setTopControlsHeight(0, false);
        mUiVrWindowAndroid.onVisibilityChanged(true);
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
        if (!mDelegate.canEnterVR(tab)) {
            forceExitVr();
            return;
        }
        if (mTab != null) {
            mTab.removeObserver(mTabObserver);
            restoreTabFromVR();
            mTab.updateFullscreenEnabledState();
        }

        mTab = tab;
        initializeTabForVR();
        mTab.addObserver(mTabObserver);
        mTab.updateFullscreenEnabledState();
        mTabObserver.onContentChanged(mTab);
    }

    private void initializeTabForVR() {
        mOriginalWindowAndroid = mTab.getWindowAndroid();
        mTab.updateWindowAndroid(mContentVrWindowAndroid);

        // Make sure we are not redirecting to another app, i.e. out of VR mode.
        mNonVrTabRedirectHandler = mTab.getTabRedirectHandler();
        mTab.setTabRedirectHandler(mTabRedirectHandler);
    }

    private void restoreTabFromVR() {
        mTab.setTabRedirectHandler(mNonVrTabRedirectHandler);
        mTab.updateWindowAndroid(mOriginalWindowAndroid);
        mOriginalWindowAndroid = null;
        mNonVrTabRedirectHandler = null;
    }

    // Exits VR, telling the user to remove their headset, and returning to Chromium.
    @CalledByNative
    public void forceExitVr() {
        mDelegate.shutdownVR(false /* isPausing */, true /* showTransition */);
    }

    @CalledByNative
    public void setUiCssSize(float width, float height, float dpr) {
        ThreadUtils.assertOnUiThread();
        if (dpr != DEFAULT_DPR) {
            Log.w(TAG, "Changing UI DPR causes the UI to flicker and should generally not be "
                    + "done.");
        }
        int surfaceWidth = (int) Math.ceil(width * dpr);
        int surfaceHeight = (int) Math.ceil(height * dpr);

        Point size = new Point(surfaceWidth, surfaceHeight);
        mUiVirtualDisplay.update(size, size, dpr, null, null, null);
        mUiCVC.onSizeChanged(surfaceWidth, surfaceHeight, 0, 0);
        mUiCVC.onPhysicalBackingSizeChanged(surfaceWidth, surfaceHeight);
        nativeUIPhysicalBoundsChanged(mNativeVrShell, surfaceWidth, surfaceHeight, dpr);
    }

    @CalledByNative
    public void setContentCssSize(float width, float height, float dpr) {
        ThreadUtils.assertOnUiThread();
        mLastContentWidth = width;
        mLastContentHeight = height;
        mLastContentDpr = dpr;

        if (mNativePage != null) {
            // Native pages don't listen to our DPR changes, so to get them to render at the correct
            // size we need to make them larger.
            DisplayAndroid primaryDisplay = DisplayAndroid.getNonMultiDisplay(mActivity);
            float dip = primaryDisplay.getDipScale();
            width *= (dip / dpr);
            height *= (dip / dpr);
        }

        int surfaceWidth = (int) Math.ceil(width * dpr);
        int surfaceHeight = (int) Math.ceil(height * dpr);

        Point size = new Point(surfaceWidth, surfaceHeight);
        mContentVirtualDisplay.update(size, size, dpr, null, null, null);
        if (mTab != null && mTab.getContentViewCore() != null) {
            mTab.getContentViewCore().onSizeChanged(surfaceWidth, surfaceHeight, 0, 0);
            mTab.getContentViewCore().onPhysicalBackingSizeChanged(surfaceWidth, surfaceHeight);
        }
        mRenderToSurfaceLayout.setLayoutParams(
                new FrameLayout.LayoutParams(surfaceWidth, surfaceHeight));
        nativeContentPhysicalBoundsChanged(mNativeVrShell, surfaceWidth, surfaceHeight, dpr);
    }

    @CalledByNative
    public void contentSurfaceChanged() {
        if (mSurface != null || mNativePage == null) return;
        mSurface = nativeTakeContentSurface(mNativeVrShell);
        mNativePage.getView().invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Normally, touch event is dispatched to presentation view only if the phone is paired with
        // a Cardboard viewer. This is annoying when we just want to quickly verify a Cardboard
        // behavior. This allows us to trigger cardboard trigger event without pair to a Cardboard.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VR_SHELL_DEV)
                && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            nativeOnTriggerEvent(mNativeVrShell);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onResume() {
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
        super.onPause();
        if (mNativeVrShell != 0) {
            nativeOnPause(mNativeVrShell);
        }
    }

    @Override
    public void shutdown() {
        if (mNativeVrShell != 0) {
            nativeDestroy(mNativeVrShell);
            mNativeVrShell = 0;
        }
        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        mTabModelSelectorTabObserver.destroy();
        mTab.removeObserver(mTabObserver);
        restoreTabFromVR();
        mUiContents.destroy();
        mContentVirtualDisplay.destroy();
        mUiVirtualDisplay.destroy();
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

    @Override
    public void setWebVrModeEnabled(boolean enabled) {
        nativeSetWebVrMode(mNativeVrShell, enabled);
    }

    @Override
    public FrameLayout getContainer() {
        return this;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        nativeSetSurface(mNativeVrShell, holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // No need to do anything here, we don't care about surface width/height.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO(mthiesse): For now we don't need to handle this because we exit VR on activity pause
        // (which destroys the surface). If in the future we don't destroy VR Shell on exiting,
        // we will need to handle this, or at least properly handle surfaceCreated being called
        // multiple times.
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
    public void navigateForward() {
        mActivity.getToolbarManager().forward();
    }

    @CalledByNative
    public void navigateBack() {
        mActivity.getToolbarManager().back();
    }

    @CalledByNative
    public void loadURL(String url, int transition) {
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setVerbatimHeaders(GeolocationHeader.getGeoHeader(url, mTab));
        loadUrlParams.setTransitionType(transition);
        mTab.loadUrl(loadUrlParams);
    }

    @CalledByNative
    public void reload() {
        mTab.reload();
    }

    @CalledByNative
    public float getNativePageScrollRatio() {
        return mOriginalWindowAndroid.getDisplay().getDipScale()
                / mContentVrWindowAndroid.getDisplay().getDipScale();
    }

    @Override
    public WindowAndroid getWindowAndroid() {
        return mContentVrWindowAndroid;
    }

    @Override
    public void addWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {}

    @Override
    public void removeWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {}

    private native long nativeInit(WebContents uiWebContents, long nativeContentWindowAndroid,
            long nativeUiWindowAndroid, boolean forWebVR, VrShellDelegate delegate, long gvrApi,
            boolean reprojectedRendering);
    private native void nativeSetSurface(long nativeVrShell, Surface surface);
    private native void nativeSwapContents(
            long nativeVrShell, WebContents webContents, MotionEventSynthesizer eventSynthesizer);
    private native void nativeLoadUIContent(long nativeVrShell);
    private native void nativeDestroy(long nativeVrShell);
    private native void nativeOnTriggerEvent(long nativeVrShell);
    private native void nativeOnPause(long nativeVrShell);
    private native void nativeOnResume(long nativeVrShell);
    private native void nativeOnLoadProgressChanged(long nativeVrShell, double progress);
    private native void nativeContentPhysicalBoundsChanged(long nativeVrShell, int width,
            int height, float dpr);
    private native void nativeUIPhysicalBoundsChanged(long nativeVrShell, int width, int height,
            float dpr);
    private native void nativeSetWebVrMode(long nativeVrShell, boolean enabled);
    private native void nativeOnTabListCreated(long nativeVrShell, Tab[] mainTabs,
            Tab[] incognitoTabs);
    private native void nativeOnTabUpdated(long nativeVrShell, boolean incognito, int id,
            String title);
    private native void nativeOnTabRemoved(long nativeVrShell, boolean incognito, int id);
    private native Surface nativeTakeContentSurface(long nativeVrShell);
    private native void nativeRestoreContentSurface(long nativeVrShell);
}
