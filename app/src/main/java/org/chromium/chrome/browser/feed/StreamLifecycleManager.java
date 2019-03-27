// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feed;

import android.app.Activity;
import android.support.annotation.IntDef;

import com.google.android.libraries.feed.api.stream.Stream;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.Tab.TabHidingType;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages the lifecycle of a {@link Stream}.
 */
class StreamLifecycleManager implements ApplicationStatus.ActivityStateListener {
    /** The different states that the Stream can be in its lifecycle. */
    @IntDef({NOT_SPECIFIED, CREATED, SHOWN, ACTIVE, INACTIVE, HIDDEN, DESTROYED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface StreamState {}

    /** Key for the Stream instance state that may be stored in a navigation entry. */
    private static final String STREAM_SAVED_INSTANCE_STATE_KEY = "StreamSavedInstanceState";

    private static final int NOT_SPECIFIED = -1;
    private static final int CREATED = 0;
    private static final int SHOWN = 1;
    private static final int ACTIVE = 2;
    private static final int INACTIVE = 3;
    private static final int HIDDEN = 4;
    private static final int DESTROYED = 5;

    /** The {@link Stream} that this class manages. */
    private final Stream mStream;

    /** The {@link Activity} that {@link #mStream} is attached to. */
    private final Activity mActivity;

    /** The {@link Tab} that {@link #mStream} is attached to. */
    private final Tab mTab;

    /**
     * The {@link TabObserver} that observes tab state changes and notifies the {@link Stream}
     * accordingly.
     */
    private final TabObserver mTabObserver;

    /** The current state the Stream is in its lifecycle. */
    private @StreamState int mStreamState = NOT_SPECIFIED;

    /**
     * @param stream The {@link Stream} that this class manages.
     * @param activity The {@link Activity} that the {@link Stream} is attached to.
     * @param tab The {@link Tab} that the {@link Stream} is attached to.
     */
    StreamLifecycleManager(Stream stream, Activity activity, Tab tab) {
        mStream = stream;
        mActivity = activity;
        mTab = tab;

        // We don't need to handle mStream#onDestroy here since this class will be destroyed when
        // the associated FeedNewTabPage is destroyed.
        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onInteractabilityChanged(boolean isInteractable) {
                if (isInteractable) {
                    activate();
                } else {
                    deactivate();
                }
            }

            @Override
            public void onShown(Tab tab, @TabSelectionType int type) {
                show();
            }

            @Override
            public void onHidden(Tab tab, @TabHidingType int type) {
                hide();
            }

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                saveInstanceState();
            }
        };

        mStreamState = CREATED;
        mStream.onCreate(restoreInstanceState());
        show();
        activate();

        mTab.addObserver(mTabObserver);
        ApplicationStatus.registerStateListenerForActivity(this, mActivity);
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        switch (newState) {
            case ActivityState.STARTED:
                show();
                break;
            case ActivityState.RESUMED:
                activate();
                break;
            case ActivityState.PAUSED:
                deactivate();
                break;
            case ActivityState.STOPPED:
                hide();
                break;
            case ActivityState.DESTROYED:
                destroy();
                break;
            case ActivityState.CREATED:
            default:
                assert false : "Unhandled activity state change: " + newState;
        }
    }

    /** @return Whether the {@link Stream} can be shown. */
    private boolean canShow() {
        final int state = ApplicationStatus.getStateForActivity(mActivity);
        // We don't call Stream#onShow to prevent feed services from being warmed up if the user
        // has opted out from article suggestions during the previous session.
        return (mStreamState == CREATED || mStreamState == HIDDEN) && !mTab.isHidden()
                && (state == ActivityState.STARTED || state == ActivityState.RESUMED)
                && FeedProcessScopeFactory.areArticlesVisibleDuringSession();
    }

    /** Calls {@link Stream#onShow()}. */
    private void show() {
        if (!canShow()) return;

        mStreamState = SHOWN;
        mStream.onShow();
    }

    /** @return Whether the {@link Stream} can be activated. */
    private boolean canActivate() {
        return (mStreamState == SHOWN || mStreamState == INACTIVE) && mTab.isUserInteractable()
                && ApplicationStatus.getStateForActivity(mActivity) == ActivityState.RESUMED
                && FeedProcessScopeFactory.areArticlesVisibleDuringSession();
    }

    /** Calls {@link Stream#onActive()}. */
    void activate() {
        // Make sure the Stream can be shown and is set shown before setting it to active state.
        show();
        if (!canActivate()) return;

        mStreamState = ACTIVE;
        mStream.onActive();
    }

    /** Calls {@link Stream#onInactive()}. */
    private void deactivate() {
        if (mStreamState != ACTIVE) return;

        mStreamState = INACTIVE;
        mStream.onInactive();
    }

    /** Calls {@link Stream#onHide()}. */
    private void hide() {
        if (mStreamState == HIDDEN || mStreamState == CREATED || mStreamState == DESTROYED) return;

        // Make sure the Stream is inactive before setting it to hidden state.
        deactivate();
        mStreamState = HIDDEN;
        // Save instance state as the Stream begins to hide. This matches the activity lifecycle
        // that instance state is saved as the activity begins to stop.
        saveInstanceState();
        mStream.onHide();
    }

    /**
     * Clears any dependencies and calls {@link Stream#onDestroy()} when this class is not needed
     * anymore.
     */
    void destroy() {
        if (mStreamState == DESTROYED) return;

        // Make sure the Stream is hidden before setting it to destroyed state.
        hide();
        mStreamState = DESTROYED;
        mTab.removeObserver(mTabObserver);
        ApplicationStatus.unregisterActivityStateListener(this);
        mStream.onDestroy();
    }

    /** Save the Stream instance state to the navigation entry if necessary. */
    private void saveInstanceState() {
        if (mTab.getWebContents() == null) return;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        NavigationEntry entry = controller.getEntryAtIndex(index);
        if (entry == null) return;

        // At least under test conditions this method may be called initially for the load of the
        // NTP itself, at which point the last committed entry is not for the NTP yet. This method
        // will then be called a second time when the user navigates away, at which point the last
        // committed entry is for the NTP. The extra data must only be set in the latter case.
        if (!NewTabPage.isNTPUrl(entry.getUrl())) return;

        controller.setEntryExtraData(
                index, STREAM_SAVED_INSTANCE_STATE_KEY, mStream.getSavedInstanceStateString());
    }

    /**
     * @return The Stream instance state saved in navigation entry, or null if it is not previously
     *         saved.
     */
    private String restoreInstanceState() {
        if (mTab.getWebContents() == null) return null;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        return controller.getEntryExtraData(index, STREAM_SAVED_INSTANCE_STATE_KEY);
    }

    @VisibleForTesting
    TabObserver getTabObserverForTesting() {
        return mTabObserver;
    }
}
