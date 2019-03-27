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
import android.text.TextUtils;
import com.google.android.libraries.feed.api.common.MutationContext;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi.KnownContentListener;
import com.google.android.libraries.feed.api.modelprovider.ModelError;
import com.google.android.libraries.feed.api.modelprovider.ModelError.ErrorType;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider.State;
import com.google.android.libraries.feed.api.store.ContentMutation;
import com.google.android.libraries.feed.api.store.SemanticPropertiesMutation;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.logging.StringFormattingUtils;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi;
import com.google.search.now.feed.client.StreamDataProto;
import com.google.search.now.feed.client.StreamDataProto.StreamDataOperation;
import com.google.search.now.feed.client.StreamDataProto.StreamPayload;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure.Operation;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Class which implements factory to create the task which commits a mutation to the SessionManager
 * and Sessions. This is created and used by the {@link
 * com.google.android.libraries.feed.feedsessionmanager.FeedSessionManager} to commit a mutation
 * defined as a List of {@link StreamDataOperation}.
 */
public final class SessionManagerMutation implements Dumpable {
  private static final String TAG = "SessionManagerMutation";

  private final Store store;
  private final SessionCache sessionCache;
  private final ContentCache contentCache;
  private final TaskQueue taskQueue;
  private final SchedulerApi schedulerApi;
  private final ThreadUtils threadUtils;
  private final TimingUtils timingUtils;
  private final Clock clock;

  // operation counts for the dumper
  private int createCount = 0;
  private int commitCount = 0;
  private int errorCount = 0;

  /** Listens for errors which need to be reported to a ModelProvider. */
  public interface ModelErrorObserver {
    void onError(/*@Nullable*/ Session session, ModelError error);
  }

  public SessionManagerMutation(
      Store store,
      SessionCache sessionCache,
      ContentCache contentCache,
      TaskQueue taskQueue,
      SchedulerApi schedulerApi,
      ThreadUtils threadUtils,
      TimingUtils timingUtils,
      Clock clock) {
    this.store = store;
    this.sessionCache = sessionCache;
    this.contentCache = contentCache;
    this.taskQueue = taskQueue;
    this.schedulerApi = schedulerApi;
    this.threadUtils = threadUtils;
    this.timingUtils = timingUtils;
    this.clock = clock;
  }

  /**
   * Return a Consumer of StreamDataOperations which will update the {@link
   * com.google.android.libraries.feed.feedsessionmanager.FeedSessionManager}.
   */
  public Consumer<Result<List<StreamDataOperation>>> createCommitter(
      String task,
      MutationContext mutationContext,
      ModelErrorObserver modelErrorObserver,
      Map<String, StreamSharedState> sharedStateCache,
      /*@Nullable*/ KnownContentListener knownContentListener) {
    createCount++;
    return new MutationCommitter(
        task, mutationContext, modelErrorObserver, sharedStateCache, knownContentListener);
  }

  public void resetHead() {
    HeadMutationCommitter mutation = new HeadMutationCommitter();
    taskQueue.execute("invalidateHead", TaskType.HEAD_INVALIDATE, () -> mutation.resetHead(null));
  }

  public void forceResetHead() {
    HeadMutationCommitter mutation = new HeadMutationCommitter();
    mutation.resetHead(null);
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    dumper.forKey("mutationsCreated").value(createCount);
    dumper.forKey("commitCount").value(commitCount).compactPrevious();
    dumper.forKey("errorCount").value(errorCount).compactPrevious();
  }

  // TODO: Tiktok doesn't allow MutationCommitter to extend HeadMutationCommitter
  class HeadMutationCommitter {
    /** This runs as a task on the executor thread and also as part of a SessionMutation commit. */
    @VisibleForTesting
    void resetHead(/*@Nullable*/ StreamSession streamSession) {
      threadUtils.checkNotMainThread();
      ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
      Collection<Session> currentSessions = sessionCache.getSessions();

      // If we have old sessions and we received a clear head, let's invalidate the session that
      // initiated the clear.
      store.clearHead();
      for (Session session : currentSessions) {
        ModelProvider modelProvider = session.getModelProvider();
        if (modelProvider != null
            && session.invalidateOnResetHead()
            && shouldInvalidateSession(streamSession, modelProvider)) {
          invalidateSession(modelProvider, session);
        }
      }
      timeTracker.stop("task", "resetHead");
    }

    /**
     * This will determine if the ModelProvider (session) should be invalidated.
     *
     * <ol>
     *   <li>ModelProvider should be READY
     *   <li>Clearing head was initiated externally, not from an existing session
     *   <li>Clearing head was initiated by the ModelProvider
     *   <li>The ModelProvider doesn't yet have a session, so we'll create the session from the new
     *       $HEAD
     * </ol>
     */
    @VisibleForTesting
    boolean shouldInvalidateSession(
        /*@Nullable*/ StreamSession streamSession, ModelProvider modelProvider) {
      // if the model provider isn't READY, don't invalidate the session
      if (modelProvider.getCurrentState() != State.READY) {
        return false;
      }
      // Clear was done outside of any session.
      if (streamSession == null) {
        return true;
      }
      // Invalidate if the ModelProvider doesn't yet have a session or if it matches the session
      // which initiated the request clearing $HEAD.
      String sessionToken = modelProvider.getSessionToken();
      return (sessionToken == null || sessionToken.equals(streamSession.getStreamToken()));
    }

    private void invalidateSession(ModelProvider modelProvider, Session session) {
      threadUtils.checkNotMainThread();
      Logger.i(TAG, "Invalidate session %s", session.getStreamSession().getStreamToken());
      modelProvider.invalidate();
    }
  }

  @VisibleForTesting
  class MutationCommitter extends HeadMutationCommitter
      implements Consumer<Result<List<StreamDataOperation>>> {

    private final String task;
    private final MutationContext mutationContext;
    private final ModelErrorObserver modelErrorObserver;
    private final Map<String, StreamSharedState> sharedStateCache;
    /*@Nullable*/ private final KnownContentListener knownContentListener;

    private final List<StreamStructure> streamStructures = new ArrayList<>();

    @VisibleForTesting boolean clearedHead = false;
    private List<StreamDataOperation> dataOperations;
    private CountDownLatch mutationComplete;

    private MutationCommitter(
        String task,
        MutationContext mutationContext,
        ModelErrorObserver modelErrorObserver,
        Map<String, StreamSharedState> sharedStateCache,
        /*@Nullable*/ KnownContentListener knownContentListener) {
      this.task = task;
      this.mutationContext = mutationContext;
      this.modelErrorObserver = modelErrorObserver;
      this.sharedStateCache = sharedStateCache;
      this.knownContentListener = knownContentListener;
    }

    @Override
    public void accept(Result<List<StreamDataOperation>> updateResults) {
      if (!updateResults.isSuccessful()) {
        errorCount++;
        Session session = null;
        StreamSession streamSession = mutationContext.getRequestingSession();
        String sessionToken = (streamSession != null) ? streamSession.getStreamToken() : null;
        if (sessionToken != null) {
          session = sessionCache.get(sessionToken);
        }
        if (mutationContext.getContinuationToken() != null) {
          StreamToken streamToken = mutationContext.getContinuationToken();
          if (session != null && streamToken != null) {
            Logger.e(TAG, "Error found with a token request %s", streamToken.getContentId());
            modelErrorObserver.onError(
                session,
                new ModelError(ErrorType.PAGINATION_ERROR, streamToken.getNextPageToken()));
          } else {
            Logger.e(TAG, "Unable to process PAGINATION_ERROR");
          }
        } else {
          Logger.e(TAG, "Update error, the update is being ignored");
          modelErrorObserver.onError(session, new ModelError(ErrorType.NO_CARDS_ERROR, null));
          taskQueue.execute("MutationCommitter:requestFailure", TaskType.HEAD_RESET, () -> {});
        }
        return;
      }
      dataOperations = updateResults.getValue();
      for (StreamDataOperation operation : dataOperations) {
        if (operation.getStreamStructure().getOperation() == Operation.CLEAR_ALL) {
          clearedHead = true;
          break;
        }
      }
      int taskType;
      if (mutationContext != null && mutationContext.isUserInitiated()) {
        taskType = TaskType.IMMEDIATE;
      } else {
        taskType = clearedHead ? TaskType.HEAD_RESET : TaskType.USER_FACING;
      }
      taskQueue.execute(
          "SessionMutation(task "
              + task
              + ", size "
              + dataOperations.size()
              + ", clearHead "
              + clearedHead
              + ")",
          taskType,
          this::commitTask);
    }

    private void commitTask() {
      ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
      commitContent();
      commitSessionUpdates();
      commitCount++;
      timeTracker.stop(
          "task",
          "sessionMutation.commitTask:" + task,
          "mutations",
          streamStructures.size(),
          "userInitiated",
          mutationContext != null ? mutationContext.isUserInitiated() : "No MutationContext");
    }

    private void commitContent() {
      threadUtils.checkNotMainThread();
      ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);

      contentCache.startMutation();
      ContentMutation contentMutation = store.editContent();
      SemanticPropertiesMutation semanticPropertiesMutation = store.editSemanticProperties();
      for (StreamDataOperation dataOperation : dataOperations) {
        Operation operation = dataOperation.getStreamStructure().getOperation();
        if (operation == Operation.CLEAR_ALL) {
          streamStructures.add(dataOperation.getStreamStructure());
          resetHead((mutationContext != null) ? mutationContext.getRequestingSession() : null);
          continue;
        }

        if (operation == Operation.UPDATE_OR_APPEND) {
          if (!validDataOperation(dataOperation)) {
            continue;
          }
          String contentId = dataOperation.getStreamStructure().getContentId();
          StreamPayload payload = dataOperation.getStreamPayload();
          contentCache.put(contentId, payload);
          if (payload.hasStreamSharedState()) {
            // don't add StreamSharedState to the metadata list stored for sessions
            contentMutation.add(contentId, payload);
            sharedStateCache.put(
                dataOperation.getStreamStructure().getContentId(), payload.getStreamSharedState());
          } else if (payload.hasStreamFeature() || payload.hasStreamToken()) {
            contentMutation.add(contentId, payload);
            streamStructures.add(dataOperation.getStreamStructure());
          } else if (dataOperation.getStreamPayload().hasSemanticData()) {
            semanticPropertiesMutation.add(
                contentId, dataOperation.getStreamPayload().getSemanticData());
          } else {
            Logger.e(TAG, "Unsupported UPDATE_OR_APPEND payload");
          }
          continue;
        }

        if (operation == Operation.REMOVE) {
          // We don't update the content for REMOVED items, content will be garbage collected.
          streamStructures.add(dataOperation.getStreamStructure());
          continue;
        }
        errorCount++;
        Logger.e(
            TAG, "Unsupported Mutation: %s", dataOperation.getStreamStructure().getOperation());
      }
      mutationComplete = new CountDownLatch(2);
      taskQueue.execute(
          "contentMutation", TaskType.BACKGROUND, () -> countDownTask(contentMutation::commit));
      taskQueue.execute(
          "semanticPropertiesMutation",
          TaskType.BACKGROUND,
          () -> countDownTask(
              () -> {
                semanticPropertiesMutation.commit();
                contentCache.finishMutation();
              }));
      timeTracker.stop("", "contentUpdate", "items", dataOperations.size());
    }

    private void countDownTask(Runnable runnable) {
      runnable.run();
      mutationComplete.countDown();
      if (mutationComplete.getCount() == 0) {
        Consumer<Boolean> completionConsumer = mutationContext.getMutationCompleteConsumer();
        if (completionConsumer != null) {
          completionConsumer.accept(true);
        }
      }
    }

    private void commitSessionUpdates() {
      threadUtils.checkNotMainThread();
      ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);

      StreamDataProto.StreamToken mutationSourceToken =
          (mutationContext != null) ? mutationContext.getContinuationToken() : null;
      // For sessions we want to add the remove operation if the mutation source was a
      // continuation token.
      if (mutationSourceToken != null) {
        StreamStructure removeOperation = addTokenRemoveOperation(mutationSourceToken);
        if (removeOperation != null) {
          streamStructures.add(0, removeOperation);
        }
      }
      Collection<Session> updates = sessionCache.getSessions();
      HeadSessionImpl head = sessionCache.getHead();
      for (Session session : updates) {
        ModelProvider modelProvider = session.getModelProvider();
        if (modelProvider != null && modelProvider.getCurrentState() == State.INVALIDATED) {
          Logger.w(TAG, "Removing an invalidate session");
          // Remove all invalidated sessions
          sessionCache.remove(session.getStreamSession().getStreamToken());
          continue;
        }
        if (session == head) {
          long updateTime = clock.currentTimeMillis();
          if (clearedHead) {
            session.updateSession(clearedHead, streamStructures, mutationContext);
            head.updateAccessTime(updateTime);
            schedulerApi.onReceiveNewContent(updateTime);
            if (knownContentListener != null) {
              knownContentListener.onNewContentReceived(/* isNewRefresh */ true, updateTime);
            }
            Logger.i(
                TAG,
                "Cleared Head, new creation time %s",
                StringFormattingUtils.formatLogDate(updateTime));
            continue;
          } else if (knownContentListener != null) {
            knownContentListener.onNewContentReceived(/* isNewRefresh */ false, updateTime);
          }
        }
        Logger.i(TAG, "Update Session %s", session.getStreamSession().getStreamToken());
        session.updateSession(clearedHead, streamStructures, mutationContext);
      }
      timeTracker.stop("", "sessionUpdate", "sessions", updates.size());
    }

    @VisibleForTesting
    boolean validDataOperation(StreamDataOperation dataOperation) {
      if (!dataOperation.hasStreamPayload() || !dataOperation.hasStreamStructure()) {
        Logger.e(TAG, "Invalid StreamDataOperation - payload or streamStructure not defined");
        errorCount++;
        return false;
      }
      String contentId = dataOperation.getStreamStructure().getContentId();
      if (TextUtils.isEmpty(contentId)) {
        Logger.e(TAG, "Invalid StreamDataOperation - No ID Found");
        errorCount++;
        return false;
      }
      return true;
    }

    /*@Nullable*/
    private StreamStructure addTokenRemoveOperation(StreamToken token) {
      return StreamStructure.newBuilder()
          .setContentId(token.getContentId())
          .setParentContentId(token.getParentId())
          .setOperation(Operation.REMOVE)
          .build();
    }
  }
}
