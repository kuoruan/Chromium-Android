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

import com.google.android.libraries.feed.api.common.PayloadWithId;
import com.google.android.libraries.feed.api.common.SemanticPropertiesWithId;
import com.google.android.libraries.feed.api.store.ActionMutation;
import com.google.android.libraries.feed.api.store.ActionMutation.ActionType;
import com.google.android.libraries.feed.api.store.ContentMutation;
import com.google.android.libraries.feed.api.store.SemanticPropertiesMutation;
import com.google.android.libraries.feed.api.store.SessionMutation;
import com.google.android.libraries.feed.api.store.StoreListener;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.host.storage.CommitResult;
import com.google.protobuf.ByteString;
import com.google.search.now.feed.client.StreamDataProto.StreamAction;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Ephemeral version of Store */
public final class EphemeralFeedStore implements ClearableStore {

  private static final String TAG = "EphemeralFeedStore";
  private static final Runnable EMPTY_RUNNABLE = () -> {};

  private final Clock clock;
  private final TimingUtils timingUtils;
  private final FeedStoreHelper storeHelper;

  private final Map<String, PayloadWithId> payloadWithIdMap = new HashMap<>();
  private final Map<String, StreamSharedState> sharedStateMap = new HashMap<>();
  private final Map<String, ByteString> semanticPropertiesMap = new HashMap<>();
  private final Map<Integer, List<StreamAction>> actionsMap = new HashMap<>();
  private final Map<String, List<StreamStructure>> sessionsMap = new HashMap<>();

  public EphemeralFeedStore(Clock clock, TimingUtils timingUtils, FeedStoreHelper storeHelper) {
    this.clock = clock;
    this.timingUtils = timingUtils;
    this.storeHelper = storeHelper;
  }

  @Override
  public Result<List<PayloadWithId>> getPayloads(List<String> contentIds) {
    List<PayloadWithId> payloads = new ArrayList<>(contentIds.size());
    for (String contentId : contentIds) {
      PayloadWithId payload = payloadWithIdMap.get(contentId);
      if (payload != null) {
        payloads.add(payload);
      }
    }
    return Result.success(payloads);
  }

  @Override
  public Result<List<StreamSharedState>> getSharedStates() {
    return Result.success(Collections.unmodifiableList(new ArrayList<>(sharedStateMap.values())));
  }

  @Override
  public Result<List<StreamStructure>> getStreamStructures(StreamSession session) {
    List<StreamStructure> streamStructures = sessionsMap.get(session.getStreamToken());
    if (streamStructures == null) {
      streamStructures = Collections.emptyList();
    }
    return Result.success(streamStructures);
  }

  @Override
  public Result<List<StreamSession>> getAllSessions() {
    Set<String> sessions = sessionsMap.keySet();
    ArrayList<StreamSession> returnValues = new ArrayList<>();
    for (String sessionToken : sessions) {
      if (!HEAD.getStreamToken().equals(sessionToken)) {
        returnValues.add(StreamSession.newBuilder().setStreamToken(sessionToken).build());
      }
    }
    return Result.success(returnValues);
  }

  @Override
  public Result<List<SemanticPropertiesWithId>> getSemanticProperties(List<String> contentIds) {
    List<SemanticPropertiesWithId> semanticPropertiesWithIds = new ArrayList<>(contentIds.size());
    for (String contentId : contentIds) {
      ByteString semanticProperties = semanticPropertiesMap.get(contentId);
      if (semanticProperties != null) {
        // TODO: switch SemanticPropertiesWithId to use byte array directly
        semanticPropertiesWithIds.add(
            new SemanticPropertiesWithId(contentId, semanticProperties.toByteArray()));
      }
    }
    return Result.success(semanticPropertiesWithIds);
  }

  @Override
  public Result<List<StreamAction>> getAllDismissActions() {
    List<StreamAction> dismissActions = actionsMap.get(ActionType.DISMISS);
    if (dismissActions == null) {
      dismissActions = Collections.emptyList();
    }
    return Result.success(dismissActions);
  }

  @Override
  public Result<StreamSession> createNewSession() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    StreamSession streamSession = storeHelper.getNewStreamSession();
    Result<List<StreamStructure>> streamStructuresResult = getStreamStructures(HEAD);
    sessionsMap.put(
        streamSession.getStreamToken(), new ArrayList<>(streamStructuresResult.getValue()));
    tracker.stop("createNewSession", streamSession.getStreamToken());
    return Result.success(streamSession);
  }

  @Override
  public StreamSession getHeadSession() {
    return HEAD;
  }

  @Override
  public void removeSession(StreamSession session) {
    if (session.getStreamToken().equals(HEAD.getStreamToken())) {
      throw new IllegalStateException("Unable to delete the $HEAD session");
    }
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    sessionsMap.remove(session.getStreamToken());
    tracker.stop("removeSession", session.getStreamToken());
  }

  @Override
  public void clearHead() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    sessionsMap.remove(HEAD.getStreamToken());
    tracker.stop("", "clearHead");
  }

  @Override
  public ContentMutation editContent() {
    return new FeedContentMutation(this::commitContentMutation);
  }

  private CommitResult commitContentMutation(List<PayloadWithId> mutations) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);

    for (PayloadWithId mutation : mutations) {
      String contentId = mutation.contentId;
      if (mutation.payload.hasStreamSharedState()) {
        StreamSharedState streamSharedState = mutation.payload.getStreamSharedState();
        sharedStateMap.put(contentId, streamSharedState);
      } else {
        payloadWithIdMap.put(contentId, mutation);
      }
    }
    tracker.stop("task", "commitContentMutation", "mutations", mutations.size());
    return CommitResult.SUCCESS;
  }

  @Override
  public SessionMutation editSession(StreamSession streamSession) {
    return new FeedSessionMutation(
        feedSessionMutation -> commitSessionMutation(streamSession, feedSessionMutation));
  }

  private Boolean commitSessionMutation(
      StreamSession streamSession, List<StreamStructure> streamStructures) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    List<StreamStructure> sessionStructures = sessionsMap.get(streamSession.getStreamToken());
    if (sessionStructures == null) {
      sessionStructures = new ArrayList<>();
      sessionsMap.put(streamSession.getStreamToken(), sessionStructures);
    }
    sessionStructures.addAll(streamStructures);
    tracker.stop("", "commitSessionMutation", "mutations", streamStructures.size());
    return Boolean.TRUE;
  }

  @Override
  public SemanticPropertiesMutation editSemanticProperties() {
    return new FeedSemanticPropertiesMutation(this::commitSemanticPropertiesMutation);
  }

  private CommitResult commitSemanticPropertiesMutation(
      Map<String, ByteString> semanticPropertiesMap) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    this.semanticPropertiesMap.putAll(semanticPropertiesMap);
    tracker.stop("", "commitSemanticPropertiesMutation", "mutations", semanticPropertiesMap.size());
    return CommitResult.SUCCESS;
  }

  @Override
  public ActionMutation editActions() {
    return new FeedActionMutation(this::commitActionMutation);
  }

  private CommitResult commitActionMutation(Map<Integer, List<String>> actions) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    CommitResult commitResult = CommitResult.SUCCESS;
    for (Map.Entry<Integer, List<String>> entry : actions.entrySet()) {
      Integer actionType = entry.getKey();
      List<StreamAction> actionsList = actionsMap.get(actionType);
      if (actionsList == null) {
        actionsList = new ArrayList<>();
        actionsMap.put(actionType, actionsList);
      }
      for (String contentId : entry.getValue()) {
        StreamAction action =
            StreamAction.newBuilder()
                .setAction(actionType)
                .setFeatureContentId(contentId)
                .setTimestampSeconds(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()))
                .build();
        actionsList.add(action);
      }
    }

    tracker.stop("task", "commitActionMutation", "actions", actions.size());
    return commitResult;
  }

  @Override
  public Runnable triggerContentGc(
      Set<String> reservedContentIds, Supplier<Set<String>> accessibleContent) {
    // No garbage collection in ephemeral mode
    return EMPTY_RUNNABLE;
  }

  @Override
  public Runnable triggerActionGc(List<StreamAction> actions, List<String> validContentIds) {
    // No garbage collection in ephemeral mode
    return EMPTY_RUNNABLE;
  }

  @Override
  public void switchToEphemeralMode() {
    // Do nothing
  }

  @Override
  public boolean isEphemeralMode() {
    return true;
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

  @Override
  public boolean clearAll() {
    payloadWithIdMap.clear();
    actionsMap.clear();
    semanticPropertiesMap.clear();
    sessionsMap.clear();
    sharedStateMap.clear();
    return true;
  }
}
