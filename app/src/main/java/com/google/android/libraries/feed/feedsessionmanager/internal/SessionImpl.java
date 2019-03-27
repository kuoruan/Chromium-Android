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

package com.google.android.libraries.feed.feedsessionmanager.internal;

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.common.MutationContext;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.modelprovider.ModelMutation;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider.ViewDepthProvider;
import com.google.android.libraries.feed.api.store.SessionMutation;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure.Operation;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Implementation of a {@link Session}. */
// TODO: Tiktok doesn't allow HeadSessionImpl to extend SessionImpl
public class SessionImpl implements InitializableSession, Dumpable {
  private static final String TAG = "SessionImpl";

  protected final Store store;
  protected final TaskQueue taskQueue;
  protected final ThreadUtils threadUtils;
  protected final TimingUtils timingUtils;
  private final boolean limitPagingUpdates;

  // Allow creation of the session without a model provider, this becomes an unbound session
  /*@Nullable*/ protected ModelProvider modelProvider;
  /*@Nullable*/ protected ViewDepthProvider viewDepthProvider;
  protected boolean legacyHeadContent = false;

  protected StreamSession streamSession;

  @VisibleForTesting public final Set<String> contentInSession = new HashSet<>();

  // operation counts for the dumper
  int updateCount = 0;

  SessionImpl(
      Store store,
      boolean limitPagingUpdates,
      TaskQueue taskQueue,
      TimingUtils timingUtils,
      ThreadUtils threadUtils) {
    this.store = store;
    this.taskQueue = taskQueue;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtils;
    this.limitPagingUpdates = limitPagingUpdates;
  }

  @Override
  public void bindModelProvider(
      /*@Nullable*/ ModelProvider modelProvider, /*@Nullable*/ ViewDepthProvider viewDepthProvider) {
    this.modelProvider = modelProvider;
    this.viewDepthProvider = viewDepthProvider;
  }

  @Override
  public void setStreamSession(StreamSession streamSession) {
    this.streamSession = streamSession;
  }

  @Override
  /*@Nullable*/
  public ModelProvider getModelProvider() {
    return modelProvider;
  }

  @Override
  public void populateModelProvider(
      StreamSession streamSession,
      List<StreamStructure> head,
      boolean cachedBindings,
      boolean legacyHeadContent) {
    Logger.i(TAG, "PopulateModelProvider %s items", head.size());
    this.legacyHeadContent = legacyHeadContent;
    this.streamSession = streamSession;
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);

    ModelMutation modelMutation = (modelProvider != null) ? modelProvider.edit() : null;
    if (modelMutation != null) {
      modelMutation.hasCachedBindings(cachedBindings);
    }

    // Walk through head and add all the DataOperations to the session.
    for (StreamStructure streamStructure : head) {
      if (streamStructure.getOperation() == Operation.UPDATE_OR_APPEND) {
        if (contentInSession.contains(streamStructure.getContentId())) {
          Logger.w(
              TAG, "Updated operation stored in $HEAD, Item %s", streamStructure.getContentId());
          continue;
        } else {
          contentInSession.add(streamStructure.getContentId());
          if (modelMutation != null) {
            modelMutation.addChild(streamStructure);
          }
        }
        continue;
      } else if (streamStructure.getOperation() == Operation.REMOVE) {
        contentInSession.remove(streamStructure.getContentId());
        if (modelMutation != null) {
          modelMutation.removeChild(streamStructure);
        }
        continue;
      }
      Logger.e(TAG, "unsupported StreamDataOperation: %s", streamStructure.getOperation());
    }
    if (modelMutation != null) {
      modelMutation.setStreamSession(streamSession);
      modelMutation.commit();
    }
    timeTracker.stop("populateSession", streamSession.getStreamToken(), "operations", head.size());
  }

  @Override
  public void updateSession(
      boolean clearHead,
      List<StreamStructure> streamStructures,
      /*@Nullable*/ MutationContext mutationContext) {
    StreamSession ss = Validators.checkNotNull(streamSession);
    if (clearHead) {
      Logger.i(TAG, "Session %s not updated due to clearHead", ss.getStreamToken());
      return;
    }
    updateCount++;
    updateSessionInternal(streamStructures, mutationContext);
  }

  @Override
  public boolean invalidateOnResetHead() {
    return true;
  }

  void updateSessionInternal(
      List<StreamStructure> streamStructures, /*@Nullable*/ MutationContext mutationContext) {

    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
    StreamToken mutationSourceToken =
        mutationContext != null ? mutationContext.getContinuationToken() : null;
    if (mutationSourceToken != null) {
      String contentId = mutationSourceToken.getContentId();
      if (!contentInSession.contains(contentId)) {
        // Make sure that mutationSourceToken is a token in this session, if not, we don't update
        // the session.
        timeTracker.stop(
            "updateSessionIgnored", streamSession.getStreamToken(), "Token Not Found", contentId);
        Logger.i(TAG, "Token %s not found in session, ignoring update", contentId);
        return;
      } else if (limitPagingUpdates) {
        StreamSession mutationSession =
            Validators.checkNotNull(mutationContext).getRequestingSession();
        if (mutationSession == null) {
          Logger.i(TAG, "Request Session was not set, ignoring update");
          return;
        }
        if (!streamSession.getStreamToken().equals(mutationSession.getStreamToken())) {
          Logger.i(TAG, "Limiting Updates, paging request made on another session");
          return;
        }
      }
    }

    ModelMutation modelMutation = (modelProvider != null) ? modelProvider.edit() : null;
    if (modelMutation != null && mutationContext != null) {
      modelMutation.setMutationContext(mutationContext);
      if (mutationContext.getContinuationToken() != null) {
        modelMutation.hasCachedBindings(true);
      }
    }

    int updateCnt = 0;
    int addFeatureCnt = 0;
    SessionMutation sessionMutation = store.editSession(streamSession);
    for (StreamStructure streamStructure : streamStructures) {
      String contentKey = streamStructure.getContentId();
      if (streamStructure.getOperation() == Operation.UPDATE_OR_APPEND) {
        if (contentInSession.contains(contentKey)) {
          if (modelMutation != null) {
            // this is an update operation so we can ignore it
            modelMutation.updateChild(streamStructure);
            updateCnt++;
          }
        } else {
          contentInSession.add(contentKey);
          sessionMutation.add(streamStructure);
          if (modelMutation != null) {
            modelMutation.addChild(streamStructure);
          }
          addFeatureCnt++;
        }
      } else if (streamStructure.getOperation() == Operation.REMOVE) {
        sessionMutation.add(streamStructure);
        contentInSession.remove(contentKey);
        if (modelMutation != null) {
          modelMutation.removeChild(streamStructure);
        }
      } else if (streamStructure.getOperation() == Operation.CLEAR_ALL) {
        Logger.i(TAG, "CLEAR_ALL not support on this session type");
      } else {
        Logger.e(TAG, "Unknown operation, ignoring: %s", streamStructure.getOperation());
      }
    }

    // Commit the Model Provider mutation after the store is updated.
    int taskType =
        mutationContext != null && mutationContext.isUserInitiated()
            ? TaskType.IMMEDIATE
            : TaskType.USER_FACING;
    taskQueue.execute("SessionImpl.sessionMutation", taskType, sessionMutation::commit);
    if (modelMutation != null) {
      modelMutation.commit();
    }
    timeTracker.stop(
        "updateSession",
        streamSession.getStreamToken(),
        "features",
        addFeatureCnt,
        "updates",
        updateCnt);
  }

  @Override
  public StreamSession getStreamSession() {
    return Validators.checkNotNull(streamSession);
  }

  @Override
  public void updateAccessTime(long time) {
    streamSession = streamSession.toBuilder().setLastAccessed(time).build();
  }

  @Override
  public Set<String> getContentInSession() {
    return new HashSet<>(contentInSession);
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    dumper.forKey("sessionName").value(streamSession.getStreamToken());
    dumper
        .forKey("")
        .value((modelProvider == null) ? "sessionIsUnbound" : "sessionIsBound")
        .compactPrevious();
    dumper.forKey("accessTime").value(new Date(streamSession.getLastAccessed())).compactPrevious();
    dumper.forKey("contentInSession").value(contentInSession.size());
    dumper.forKey("updateCount").value(updateCount).compactPrevious();
    if (modelProvider instanceof Dumpable) {
      dumper.dump((Dumpable) modelProvider);
    }
  }
}
