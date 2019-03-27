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

package com.google.android.libraries.feed.feedstore.internal;

import static com.google.android.libraries.feed.feedstore.internal.FeedStoreConstants.DISMISS_ACTION_JOURNAL;
import static com.google.android.libraries.feed.feedstore.internal.FeedStoreConstants.SEMANTIC_PROPERTIES_PREFIX;
import static com.google.android.libraries.feed.feedstore.internal.FeedStoreConstants.SHARED_STATE_PREFIX;

import android.util.Base64;
import com.google.android.libraries.feed.api.common.PayloadWithId;
import com.google.android.libraries.feed.api.common.SemanticPropertiesWithId;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.store.ActionMutation;
import com.google.android.libraries.feed.api.store.ActionMutation.ActionType;
import com.google.android.libraries.feed.api.store.ContentMutation;
import com.google.android.libraries.feed.api.store.SemanticPropertiesMutation;
import com.google.android.libraries.feed.api.store.SessionMutation;
import com.google.android.libraries.feed.api.store.StoreListener;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.protoextensions.FeedExtensionRegistry;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.host.storage.CommitResult;
import com.google.android.libraries.feed.host.storage.ContentMutation.Builder;
import com.google.android.libraries.feed.host.storage.ContentStorage;
import com.google.android.libraries.feed.host.storage.ContentStorageDirect;
import com.google.android.libraries.feed.host.storage.JournalMutation;
import com.google.android.libraries.feed.host.storage.JournalStorage;
import com.google.android.libraries.feed.host.storage.JournalStorageDirect;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.search.now.feed.client.StreamDataProto.StreamAction;
import com.google.search.now.feed.client.StreamDataProto.StreamPayload;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the Store. The PersistentFeedStore will call the host APIs {@link
 * ContentStorage} and {@link JournalStorage} to make persistent changes.
 */
public final class PersistentFeedStore implements ClearableStore, Dumpable {

  private static final String TAG = "PersistentFeedStore";

  private final TimingUtils timingUtils;
  private final FeedExtensionRegistry extensionRegistry;
  private final ContentStorageDirect contentStorageDirect;
  private final JournalStorageDirect journalStorageDirect;
  private final ThreadUtils threadUtils;
  private final Clock clock;
  private final FeedStoreHelper storeHelper;

  public PersistentFeedStore(
      TimingUtils timingUtils,
      FeedExtensionRegistry extensionRegistry,
      ContentStorageDirect contentStorageDirect,
      JournalStorageDirect journalStorageDirect,
      ThreadUtils threadUtils,
      Clock clock,
      FeedStoreHelper storeHelper) {
    this.timingUtils = timingUtils;
    this.extensionRegistry = extensionRegistry;
    this.contentStorageDirect = contentStorageDirect;
    this.journalStorageDirect = journalStorageDirect;
    this.threadUtils = threadUtils;
    this.clock = clock;
    this.storeHelper = storeHelper;
  }

  @Override
  public Result<List<PayloadWithId>> getPayloads(List<String> contentIds) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    List<PayloadWithId> payloads = new ArrayList<>(contentIds.size());
    Result<Map<String, byte[]>> contentResult = contentStorageDirect.get(contentIds);
    if (!contentResult.isSuccessful()) {
      Logger.e(TAG, "Unsuccessful fetching payloads for content ids %s", contentIds);
      tracker.stop("getPayloads failed", "items", contentIds);
      return Result.failure();
    }

    for (Map.Entry<String, byte[]> entry : contentResult.getValue().entrySet()) {
      try {
        StreamPayload streamPayload =
            StreamPayload.parseFrom(entry.getValue(), extensionRegistry.getExtensionRegistry());
        payloads.add(new PayloadWithId(entry.getKey(), streamPayload));
      } catch (InvalidProtocolBufferException e) {
        Logger.e(TAG, "Couldn't parse content proto for id %s", entry.getKey());
      }
    }
    tracker.stop("", "getPayloads", "items", contentIds.size());
    return Result.success(payloads);
  }

  @Override
  public Result<List<StreamSharedState>> getSharedStates() {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Result<Map<String, byte[]>> bytesResult = contentStorageDirect.getAll(SHARED_STATE_PREFIX);
    if (!bytesResult.isSuccessful()) {
      Logger.e(TAG, "Error fetching shared states");
      tracker.stop("getSharedStates", "failed");
      return Result.failure();
    }
    Collection<byte[]> result = bytesResult.getValue().values();
    List<StreamSharedState> sharedStates = new ArrayList<>(result.size());
    for (byte[] byteArray : result) {
      try {
        sharedStates.add(StreamSharedState.parseFrom(byteArray));
      } catch (InvalidProtocolBufferException e) {
        tracker.stop("getSharedStates", "failed");
        Logger.e(TAG, e, "Error parsing protocol buffer from bytes %s", byteArray);
      }
    }
    tracker.stop("", "getSharedStates", "items", sharedStates.size());
    return Result.success(sharedStates);
  }

  @Override
  public Result<List<StreamStructure>> getStreamStructures(StreamSession session) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    List<StreamStructure> streamStructures;
    Result<List<byte[]>> operationsResult = journalStorageDirect.read(session.getStreamToken());
    if (!operationsResult.isSuccessful()) {
      Logger.e(TAG, "Error fetching stream structures for stream session %s", session);
      tracker.stop("getStreamStructures failed", "session", session);
      return Result.failure();
    }
    List<byte[]> results = operationsResult.getValue();
    streamStructures = new ArrayList<>(results.size());
    for (byte[] bytes : results) {
      if (bytes.length == 0) {
        continue;
      }
      try {
        streamStructures.add(StreamStructure.parseFrom(bytes));
      } catch (InvalidProtocolBufferException e) {
        Logger.e(TAG, e, "Error parsing stream structure.");
      }
    }
    tracker.stop("", "getStreamStructures", "items", streamStructures.size());
    return Result.success(streamStructures);
  }

  @Override
  public Result<List<StreamSession>> getAllSessions() {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    List<StreamSession> sessions;

    Result<List<String>> namesResult = journalStorageDirect.getAllJournals();
    if (!namesResult.isSuccessful()) {
      Logger.e(TAG, "Error fetching all journals");
      tracker.stop("getAllSessions failed");
      return Result.failure();
    }

    List<String> result = namesResult.getValue();
    sessions = new ArrayList<>(result.size());
    for (String name : result) {
      if (HEAD.getStreamToken().equals(name)) {
        // Don't add $HEAD to the sessions list
        continue;
      }
      if (DISMISS_ACTION_JOURNAL.equals(name)) {
        // Don't add Dismiss Actions journal to sessions list
        continue;
      }
      sessions.add(StreamSession.newBuilder().setStreamToken(name).build());
    }
    tracker.stop("", "getAllSessions", "items", sessions.size());
    return Result.success(sessions);
  }

  @Override
  public Result<List<SemanticPropertiesWithId>> getSemanticProperties(List<String> contentIds) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    List<SemanticPropertiesWithId> semanticPropertiesWithIds = new ArrayList<>(contentIds.size());
    List<String> contentIdKeys = new ArrayList<>(contentIds.size());
    for (String contentId : contentIds) {
      contentIdKeys.add(SEMANTIC_PROPERTIES_PREFIX + contentId);
    }
    Result<Map<String, byte[]>> mapResult = contentStorageDirect.get(contentIdKeys);

    if (mapResult.isSuccessful()) {
      for (Map.Entry<String, byte[]> entry : mapResult.getValue().entrySet()) {
        String contentId = entry.getKey().replace(SEMANTIC_PROPERTIES_PREFIX, "");
        if (contentIds.contains(contentId)) {
          semanticPropertiesWithIds.add(new SemanticPropertiesWithId(contentId, entry.getValue()));
        }
      }
    } else {
      Logger.e(TAG, "Error fetching semantic properties for content ids %s", contentIds);
      tracker.stop("getSemanticProperties failed", contentIds);
    }

    tracker.stop("task", "getSemanticProperties", "size", semanticPropertiesWithIds.size());
    return Result.success(semanticPropertiesWithIds);
  }

  @Override
  public Result<List<StreamAction>> getAllDismissActions() {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);

    Result<List<byte[]>> listResult = journalStorageDirect.read(DISMISS_ACTION_JOURNAL);
    if (!listResult.isSuccessful()) {
      Logger.e(TAG, "Error retrieving dismiss journal");
      tracker.stop("getAllDismissActions failed");
      return Result.failure();
    } else {
      List<byte[]> actionByteArrays = listResult.getValue();
      List<StreamAction> actions = new ArrayList<>(actionByteArrays.size());
      for (byte[] bytes : actionByteArrays) {
        StreamAction action;
        try {
          action = StreamAction.parseFrom(bytes);
          actions.add(action);
        } catch (InvalidProtocolBufferException e) {
          Logger.e(
              TAG,
              e,
              "Error parsing StreamAction for bytes: %s (length %d)",
              Base64.encodeToString(bytes, Base64.DEFAULT),
              bytes.length);
        }
      }
      tracker.stop("task", "getAllDismissActions", "size", actions.size());
      return Result.success(actions);
    }
  }

  @Override
  public Result<StreamSession> createNewSession() {
    threadUtils.checkNotMainThread();

    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    StreamSession streamSession = storeHelper.getNewStreamSession();
    journalStorageDirect.commit(
        new JournalMutation.Builder(HEAD.getStreamToken())
            .copy(streamSession.getStreamToken())
            .build());
    tracker.stop("createNewSession", streamSession.getStreamToken());
    return Result.success(streamSession);
  }

  @Override
  public StreamSession getHeadSession() {
    return HEAD;
  }

  @Override
  public void removeSession(StreamSession session) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    if (session.getStreamToken().equals(HEAD.getStreamToken())) {
      throw new IllegalStateException("Unable to delete the $HEAD session");
    }
    journalStorageDirect.commit(
        new JournalMutation.Builder(session.getStreamToken()).delete().build());
    tracker.stop("removeSession", session.getStreamToken());
  }

  @Override
  public void clearHead() {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    journalStorageDirect.commit(
        new JournalMutation.Builder(HEAD.getStreamToken()).delete().append(new byte[0]).build());
    tracker.stop("", "clearHead");
  }

  @Override
  public ContentMutation editContent() {
    threadUtils.checkNotMainThread();
    return new FeedContentMutation(this::commitContentMutation);
  }

  @Override
  public SessionMutation editSession(StreamSession streamSession) {
    threadUtils.checkNotMainThread();
    return new FeedSessionMutation(
        feedSessionMutation -> commitSessionMutation(streamSession, feedSessionMutation));
  }

  @Override
  public SemanticPropertiesMutation editSemanticProperties() {
    threadUtils.checkNotMainThread();
    return new FeedSemanticPropertiesMutation(this::commitSemanticPropertiesMutation);
  }

  @Override
  public ActionMutation editActions() {
    threadUtils.checkNotMainThread();
    return new FeedActionMutation(this::commitActionMutation);
  }

  @Override
  public Runnable triggerContentGc(
      Set<String> reservedContentIds, Supplier<Set<String>> accessibleContent) {
    Supplier<Set<StreamAction>> dismissActionSupplier =
        () -> {
          Result<List<StreamAction>> dismissActionsResult = getAllDismissActions();
          if (!dismissActionsResult.isSuccessful()) {
            // TODO: clean up error condition
            Logger.e(TAG, "Error retrieving dismiss actions for content garbage collection");
            return Collections.emptySet();
          } else {
            return new HashSet<>(dismissActionsResult.getValue());
          }
        };

    return new ContentGc(
            accessibleContent,
            reservedContentIds,
            dismissActionSupplier,
            contentStorageDirect,
            timingUtils)
        ::gc;
  }

  @Override
  public Runnable triggerActionGc(List<StreamAction> actions, List<String> validContentIds) {
    return new ActionGc(
            actions, validContentIds, journalStorageDirect, timingUtils, DISMISS_ACTION_JOURNAL)
        ::gc;
  }

  @Override
  public void switchToEphemeralMode() {
    // TODO: implement cleanup
  }

  @Override
  public boolean isEphemeralMode() {
    return false;
  }

  @Override
  public void registerObserver(StoreListener observer) {
    throw new UnsupportedOperationException(
        "PersistentFeedStore does not support observer directly");
  }

  @Override
  public void unregisterObserver(StoreListener observer) {
    throw new UnsupportedOperationException(
        "PersistentFeedStore does not support observer directly");
  }

  private CommitResult commitSemanticPropertiesMutation(
      Map<String, ByteString> semanticPropertiesMap) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Builder mutationBuilder = new Builder();
    for (Map.Entry<String, ByteString> entry : semanticPropertiesMap.entrySet()) {
      mutationBuilder.upsert(
          SEMANTIC_PROPERTIES_PREFIX + entry.getKey(), entry.getValue().toByteArray());
    }
    CommitResult commitResult = contentStorageDirect.commit(mutationBuilder.build());
    tracker.stop(
        "task", "commitSemanticPropertiesMutation", "mutations", semanticPropertiesMap.size());
    return commitResult;
  }

  private Boolean commitSessionMutation(
      StreamSession streamSession, List<StreamStructure> streamStructures) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    JournalMutation.Builder mutation = new JournalMutation.Builder(streamSession.getStreamToken());
    if (streamStructures.isEmpty()) {
      // allow an empty journal to be created
      mutation.append(new byte[0]);
    }
    for (StreamStructure streamStructure : streamStructures) {
      mutation.append(streamStructure.toByteArray());
    }
    CommitResult mutationResult = journalStorageDirect.commit(mutation.build());
    boolean result = CommitResult.SUCCESS.equals(mutationResult);
    tracker.stop("", "commitSessionMutation", "mutations", streamStructures.size());
    Logger.i(
        TAG,
        "commitSessionMutation - Success %s, Update Session %s, stream structures %s",
        result,
        streamSession.getStreamToken(),
        streamStructures.size());
    return result;
  }

  private CommitResult commitContentMutation(List<PayloadWithId> mutations) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);

    Builder contentMutationBuilder = new Builder();
    for (PayloadWithId mutation : mutations) {
      String payloadId = mutation.contentId;
      StreamPayload payload = mutation.payload;
      if (mutation.payload.hasStreamSharedState()) {
        StreamSharedState streamSharedState = mutation.payload.getStreamSharedState();
        contentMutationBuilder.upsert(
            SHARED_STATE_PREFIX + streamSharedState.getContentId(),
            streamSharedState.toByteArray());
      } else {
        contentMutationBuilder.upsert(payloadId, payload.toByteArray());
      }
    }

    // Block waiting for the response from storage, to make this method synchronous.
    // TODO: handle errors
    CommitResult commitResult = contentStorageDirect.commit(contentMutationBuilder.build());
    tracker.stop("task", "commitContentMutation", "mutations", mutations.size());
    return commitResult;
  }

  private CommitResult commitActionMutation(Map<Integer, List<String>> actions) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);

    CommitResult commitResult = CommitResult.SUCCESS;
    for (Map.Entry<Integer, List<String>> entry : actions.entrySet()) {
      Integer actionType = entry.getKey();
      String journalName;
      if (ActionType.DISMISS == entry.getKey()) {
        journalName = DISMISS_ACTION_JOURNAL;
      } else {
        Logger.e(TAG, "Unknown journal name for action type %s", actionType);
        continue;
      }
      JournalMutation.Builder builder = new JournalMutation.Builder(journalName);
      for (String contentId : entry.getValue()) {
        StreamAction action =
            StreamAction.newBuilder()
                .setAction(actionType)
                .setFeatureContentId(contentId)
                .setTimestampSeconds(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()))
                .build();
        byte[] actionBytes = action.toByteArray();
        Logger.i(
            TAG,
            "Adding StreamAction bytes %s (length %d) to journal %s",
            Base64.encodeToString(actionBytes, Base64.DEFAULT),
            actionBytes.length,
            journalName);
        builder.append(actionBytes);
      }
      commitResult = journalStorageDirect.commit(builder.build());
      if (commitResult == CommitResult.FAILURE) {
        Logger.e(TAG, "Error committing action for type %s", actionType);
        break;
      }
    }

    tracker.stop("task", "commitActionMutation", "actions", actions.size());
    return commitResult;
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    if (contentStorageDirect instanceof Dumpable) {
      dumper.dump((Dumpable) contentStorageDirect);
    } else {
      dumper.forKey("contentStorageDirect").value("not dumpable");
    }
    if (journalStorageDirect instanceof Dumpable) {
      dumper.dump((Dumpable) journalStorageDirect);
    } else {
      dumper.forKey("journalStorage").value("not dumpable");
    }
  }

  /**
   * Clears contents and journals relating to the stream. Leaves actions and semantic properties
   * intact.
   *
   * @return {@code true} when the clear succeeded
   */
  public boolean clearNonActionContent() {
    try {
      if (!clearContentStorage()) {
        return false;
      }
      if (!clearJournalStorage()) {
        return false;
      }
    } catch (InterruptedException | ExecutionException e) {
      Logger.e(TAG, e, "Error clearing all contents.");
      return false;
    }
    return true;
  }

  /**
   * Cleans out journal storage (excluding dismiss actions journal)
   *
   * @return whether the clear succeeded
   */
  private boolean clearJournalStorage() throws InterruptedException, ExecutionException {
    Result<List<String>> allJournalsResult = journalStorageDirect.getAllJournals();
    if (!allJournalsResult.isSuccessful()) {
      Logger.e(TAG, "Error clearing all contents. Could not fetch all journals.");
      return false;
    }

    for (String journal : allJournalsResult.getValue()) {
      if (DISMISS_ACTION_JOURNAL.equals(journal)) {
        continue;
      }
      CommitResult result =
          journalStorageDirect.commit(new JournalMutation.Builder(journal).delete().build());
      if (result != CommitResult.SUCCESS) {
        Logger.e(TAG, "Error clearing all contents. Could not delete journal %s", journal);
        return false;
      }
    }
    return true;
  }

  /**
   * Cleans out content storage (excluding semantic properties)
   *
   * @return whether the clear succeeded
   */
  private boolean clearContentStorage() {
    // TODO: replace with getAllKeys call
    Result<Map<String, byte[]>> allContentMapResult = contentStorageDirect.getAll("");
    if (!allContentMapResult.isSuccessful()) {
      Logger.e(TAG, "Error clearing all contents. Could not fetch all content.");
      return false;
    }
    Builder contentMutationBuilder = new Builder();
    for (String key : allContentMapResult.getValue().keySet()) {
      if (key.contains(SEMANTIC_PROPERTIES_PREFIX)) {
        continue;
      }
      contentMutationBuilder.delete(key);
    }
    CommitResult result = contentStorageDirect.commit(contentMutationBuilder.build());
    if (result != CommitResult.SUCCESS) {
      Logger.e(TAG, "Error clearing all contents. Could not commit content deletions.");
      return false;
    }
    return true;
  }

  /** Wipes all content and journals */
  @Override
  public boolean clearAll() {
    threadUtils.checkNotMainThread();

    boolean success = true;
    // Run clear on both content and journal
    CommitResult result = contentStorageDirect.commit(new Builder().deleteAll().build());
    CommitResult journalResult = journalStorageDirect.deleteAll();

    // Confirm results were successful
    if (result != CommitResult.SUCCESS) {
      Logger.e(TAG, "Error clearing all. Could not delete all content from content storage.");
      success = false;
    }

    if (journalResult != CommitResult.SUCCESS) {
      Logger.e(TAG, "Error clearing all. Could not delete all journals from journal storage.");
      success = false;
    }

    // Try to recreate $HEAD (it needs to exist in order to create new sessions)
    // #clearHead() also will create it if it does not exist
    clearHead();

    return success;
  }
}
