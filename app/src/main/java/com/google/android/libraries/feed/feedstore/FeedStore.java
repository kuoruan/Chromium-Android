// Copyright 2018 The Feed Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.libraries.feed.feedstore;

import com.google.android.libraries.feed.api.common.PayloadWithId;
import com.google.android.libraries.feed.api.common.SemanticPropertiesWithId;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.lifecycle.Resettable;
import com.google.android.libraries.feed.api.store.ActionMutation;
import com.google.android.libraries.feed.api.store.ContentMutation;
import com.google.android.libraries.feed.api.store.SemanticPropertiesMutation;
import com.google.android.libraries.feed.api.store.SessionMutation;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.api.store.StoreListener;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.feedobservable.FeedObservable;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.protoextensions.FeedExtensionRegistry;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.feedapplifecyclelistener.FeedLifecycleListener;
import com.google.android.libraries.feed.feedstore.internal.ClearableStore;
import com.google.android.libraries.feed.feedstore.internal.EphemeralFeedStore;
import com.google.android.libraries.feed.feedstore.internal.FeedStoreHelper;
import com.google.android.libraries.feed.feedstore.internal.PersistentFeedStore;
import com.google.android.libraries.feed.host.storage.CommitResult;
import com.google.android.libraries.feed.host.storage.ContentStorageDirect;
import com.google.android.libraries.feed.host.storage.JournalStorageDirect;
import com.google.protobuf.ByteString;
import com.google.search.now.feed.client.StreamDataProto.StreamAction;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link Store} implementation that delegates between {@link PersistentFeedStore} and {@link
 * EphemeralFeedStore}.
 *
 * <p>TODO: We need to design the StoreListener support correctly. For the switch to
 * ephemeral mode, the Observers are called from here. If we ever have events which are raised by
 * one of the delegates, we need to make sure the registered observers are called correctly,
 * independent of what delegate is actually running. The delegates currently throw a
 * IllegalStateException if the register/unregister methods are called.
 */
public final class FeedStore extends FeedObservable<StoreListener>
    implements Store, Resettable, FeedLifecycleListener {
  private static final String TAG = "FeedStore";

  // Permanent reference to the persistent store (used for setting off cleanup)
  private final PersistentFeedStore persistentStore;
  // The current store
  private ClearableStore delegate;

  private final TaskQueue taskQueue;

  // Needed for switching to ephemeral mode
  private final FeedStoreHelper storeHelper = new FeedStoreHelper();
  private final Clock clock;
  private final TimingUtils timingUtils;
  private final ThreadUtils threadUtils;

  private boolean isEphemeralMode = false;

  public FeedStore(
      TimingUtils timingUtils,
      FeedExtensionRegistry extensionRegistry,
      ContentStorageDirect contentStorage,
      JournalStorageDirect journalStorage,
      ThreadUtils threadUtils,
      TaskQueue taskQueue,
      Clock clock) {
    this.taskQueue = taskQueue;
    this.clock = clock;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtils;

    this.persistentStore =
        new PersistentFeedStore(
            this.timingUtils,
            extensionRegistry,
            contentStorage,
            journalStorage,
            threadUtils,
            this.clock,
            this.storeHelper);
    delegate = persistentStore;
  }

  @Override
  public Result<List<PayloadWithId>> getPayloads(List<String> contentIds) {
    return delegate.getPayloads(contentIds);
  }

  @Override
  public Result<List<StreamSharedState>> getSharedStates() {
    return delegate.getSharedStates();
  }

  @Override
  public Result<List<StreamStructure>> getStreamStructures(StreamSession session) {
    return delegate.getStreamStructures(session);
  }

  @Override
  public Result<List<StreamSession>> getAllSessions() {
    return delegate.getAllSessions();
  }

  @Override
  public Result<List<SemanticPropertiesWithId>> getSemanticProperties(List<String> contentIds) {
    return delegate.getSemanticProperties(contentIds);
  }

  @Override
  public Result<List<StreamAction>> getAllDismissActions() {
    return delegate.getAllDismissActions();
  }

  @Override
  public Result<StreamSession> createNewSession() {
    return delegate.createNewSession();
  }

  @Override
  public StreamSession getHeadSession() {
    return delegate.getHeadSession();
  }

  @Override
  public void removeSession(StreamSession session) {
    delegate.removeSession(session);
  }

  @Override
  public void clearHead() {
    delegate.clearHead();
  }

  @Override
  public ContentMutation editContent() {
    return delegate.editContent();
  }

  @Override
  public SessionMutation editSession(StreamSession streamSession) {
    return delegate.editSession(streamSession);
  }

  @Override
  public SemanticPropertiesMutation editSemanticProperties() {
    return delegate.editSemanticProperties();
  }

  @Override
  public ActionMutation editActions() {
    return delegate.editActions();
  }

  @Override
  public Runnable triggerContentGc(
      Set<String> reservedContentIds, Supplier<Set<String>> accessibleContent) {
    return delegate.triggerContentGc(reservedContentIds, accessibleContent);
  }

  @Override
  public Runnable triggerActionGc(List<StreamAction> actions, List<String> validContentIds) {
    return delegate.triggerActionGc(actions, validContentIds);
  }

  @Override
  public boolean isEphemeralMode() {
    return isEphemeralMode;
  }

  @Override
  public void switchToEphemeralMode() {
    // This should be called on a background thread because it's called during error handling.
    threadUtils.checkNotMainThread();
    if (!isEphemeralMode) {
      persistentStore.switchToEphemeralMode();
      delegate = new EphemeralFeedStore(clock, timingUtils, storeHelper);

      taskQueue.execute(
          "clearPersistentStoreTask",
          TaskType.BACKGROUND,
          () -> {
            ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
            // Try to just wipe content + sessions
            boolean clearSuccess = persistentStore.clearNonActionContent();
            // If that fails, wipe everything.
            if (!clearSuccess) {
              persistentStore.clearAll();
            }
            tracker.stop("clearPersistentStore", "completed");
          });

      isEphemeralMode = true;
      synchronized (observers) {
        for (StoreListener listener : observers) {
          listener.onSwitchToEphemeralMode();
        }
      }
    }
  }

  @Override
  public void reset() {
    persistentStore.clearNonActionContent();
  }

  @Override
  public void onLifecycleEvent(@LifecycleEvent String event) {
    switch (event) {
      case LifecycleEvent.ENTER_BACKGROUND:
        if (isEphemeralMode) {
          taskQueue.execute(
              "dumpEphemeralActions", TaskType.BACKGROUND, this::dumpEphemeralActions);
        }
        break;
      default:
        // Do nothing
    }
  }

  private void dumpEphemeralActions() {
    // Get all action-related content (actions + semantic data)
    Result<List<StreamAction>> dismissActionsResult = delegate.getAllDismissActions();
    if (!dismissActionsResult.isSuccessful()) {
      Logger.e(TAG, "Error retrieving actions when trying to dump ephemeral actions.");
      return;
    }
    List<StreamAction> dismissActions = dismissActionsResult.getValue();
    ActionMutation actionMutation = persistentStore.editActions();
    List<String> dismissActionContentIds = new ArrayList<>(dismissActions.size());
    for (StreamAction dismiss : dismissActions) {
      dismissActionContentIds.add(dismiss.getFeatureContentId());
      actionMutation.add(dismiss.getAction(), dismiss.getFeatureContentId());
    }
    Result<List<SemanticPropertiesWithId>> semanticPropertiesResult =
        delegate.getSemanticProperties(dismissActionContentIds);
    if (!semanticPropertiesResult.isSuccessful()) {
      Logger.e(TAG, "Error retrieving semantic properties when trying to dump ephemeral actions.");
      return;
    }
    SemanticPropertiesMutation semanticPropertiesMutation =
        persistentStore.editSemanticProperties();
    for (SemanticPropertiesWithId semanticProperties : semanticPropertiesResult.getValue()) {
      semanticPropertiesMutation.add(
          semanticProperties.contentId, ByteString.copyFrom(semanticProperties.semanticData));
    }

    // Attempt to write action-related content to persistent storage
    CommitResult commitResult = actionMutation.commit();
    if (commitResult != CommitResult.SUCCESS) {
      Logger.e(TAG, "Error writing actions to persistent store when dumping ephemeral actions.");
      return;
    }
    commitResult = semanticPropertiesMutation.commit();
    if (commitResult != CommitResult.SUCCESS) {
      Logger.e(
          TAG,
          "Error writing semantic properties to persistent store when dumping ephemeral actions.");
    }
  }
}
