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

package com.google.android.libraries.feed.feedsessionmanager;

import static com.google.android.libraries.feed.host.scheduler.SchedulerApi.RequestBehavior.NO_REQUEST_WITH_TIMEOUT;
import static com.google.android.libraries.feed.host.scheduler.SchedulerApi.RequestBehavior.REQUEST_WITH_CONTENT;
import static com.google.android.libraries.feed.host.scheduler.SchedulerApi.RequestBehavior.REQUEST_WITH_TIMEOUT;
import static com.google.android.libraries.feed.host.scheduler.SchedulerApi.RequestBehavior.REQUEST_WITH_WAIT;

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.common.MutationContext;
import com.google.android.libraries.feed.api.common.PayloadWithId;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi.KnownContentListener;
import com.google.android.libraries.feed.api.modelprovider.ModelError;
import com.google.android.libraries.feed.api.modelprovider.ModelError.ErrorType;
import com.google.android.libraries.feed.api.modelprovider.ModelMutation;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider.ViewDepthProvider;
import com.google.android.libraries.feed.api.protocoladapter.ProtocolAdapter;
import com.google.android.libraries.feed.api.requestmanager.RequestManager;
import com.google.android.libraries.feed.api.sessionmanager.SessionManager;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.api.store.StoreListener;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.feedobservable.FeedObservable;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.functional.Function;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.logging.StringFormattingUtils;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.feedapplifecyclelistener.FeedLifecycleListener;
import com.google.android.libraries.feed.feedsessionmanager.internal.ContentCache;
import com.google.android.libraries.feed.feedsessionmanager.internal.HeadAsStructure;
import com.google.android.libraries.feed.feedsessionmanager.internal.HeadAsStructure.TreeNode;
import com.google.android.libraries.feed.feedsessionmanager.internal.HeadSessionImpl;
import com.google.android.libraries.feed.feedsessionmanager.internal.InitializableSession;
import com.google.android.libraries.feed.feedsessionmanager.internal.Session;
import com.google.android.libraries.feed.feedsessionmanager.internal.SessionCache;
import com.google.android.libraries.feed.feedsessionmanager.internal.SessionFactory;
import com.google.android.libraries.feed.feedsessionmanager.internal.SessionManagerMutation;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi.RequestBehavior;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi.SessionManagerState;
import com.google.search.now.feed.client.StreamDataProto.StreamDataOperation;
import com.google.search.now.feed.client.StreamDataProto.StreamPayload;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import com.google.search.now.wire.feed.ContentIdProto.ContentId;
import com.google.search.now.wire.feed.FeedQueryProto.FeedQuery.RequestReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.GuardedBy;

/** Prototype implementation of the SessionManager. All state is kept in memory. */
public final class FeedSessionManager
    implements SessionManager, StoreListener, FeedLifecycleListener, Dumpable {

  private static final String TAG = "FeedSessionManager";

  private static final long TIMEOUT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

  // For the Shared State we will always cache them in the Session Manager
  // Accessed on main thread, updated on Executor task
  private final Map<String, StreamSharedState> sharedStateCache = new HashMap<>();

  // All writes are done on background threads, there are accesses on the main thread.  Leaving
  // the lock back accessibleContentSupplier may eventually run on a background task and not
  // on the executor thread.
  private final SessionCache sessionCache;

  // All access to the content cache happens on the executor thread so there is no need to
  // synchronize access.
  private final ContentCache contentCache;

  @VisibleForTesting final AtomicBoolean initialized = new AtomicBoolean(false);

  private final Object lock = new Object();

  // Keep track of sessions being created which haven't been added to the SessionCache.
  // This is accessed on the main thread and the background thread.
  @GuardedBy("lock")
  private final List<InitializableSession> sessionsUnderConstruction = new ArrayList<>();

  // This captures the NO_CARDS_ERROR when a request fails. The request fails in one task and this
  // is sent to the ModelProvider in the populateSessionTask.
  /*@Nullable*/ @VisibleForTesting ModelError noCardsError;

  private final SessionFactory sessionFactory;
  private final SessionManagerMutation sessionManagerMutation;
  private final Store store;
  private final ThreadUtils threadUtils;
  private final TimingUtils timingUtils;
  private final ProtocolAdapter protocolAdapter;
  private final RequestManager requestManager;
  private final SchedulerApi schedulerApi;
  private final TaskQueue taskQueue;
  private final Clock clock;
  private final long sessionPopulationTimeoutMs;

  @VisibleForTesting final Set<SessionMutationTracker> outstandingMutations = new HashSet<>();

  // operation counts for the dumper
  private int newSessionCount = 0;
  private int existingSessionCount = 0;
  private int handleTokenCount = 0;
  /*@MonotonicNonNull*/ private KnownContentListener knownContentListener;

  @SuppressWarnings("argument.type.incompatible") // ok call to registerObserver
  public FeedSessionManager(
      TaskQueue taskQueue,
      SessionFactory sessionFactory,
      SessionCache sessionCache,
      SessionManagerMutation sessionManagerMutation,
      ContentCache contentCache,
      Store store,
      TimingUtils timingUtils,
      ThreadUtils threadUtils,
      ProtocolAdapter protocolAdapter,
      RequestManager requestManager,
      SchedulerApi schedulerApi,
      Configuration configuration,
      Clock clock,
      FeedObservable<FeedLifecycleListener> lifecycleListenerObservable) {
    this.taskQueue = taskQueue;
    this.sessionFactory = sessionFactory;
    this.sessionCache = sessionCache;
    this.sessionManagerMutation = sessionManagerMutation;
    this.contentCache = contentCache;

    this.store = store;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtils;
    this.protocolAdapter = protocolAdapter;
    this.requestManager = requestManager;
    this.schedulerApi = schedulerApi;
    this.clock = clock;

    sessionPopulationTimeoutMs =
        configuration.getValueOrDefault(ConfigKey.TIMEOUT_TIMEOUT_MS, TIMEOUT_TIMEOUT_MS);
    lifecycleListenerObservable.registerObserver(this);
    Logger.i(TAG, "FeedSessionManager has been created");
  }

  /**
   * Called to initialize the session manager. This creates an executor task which does the actual
   * work of setting up the current state. If this is not called, the session manager will not
   * populate new or existing sessions. There isn't error checking on this since this happens on an
   * executor task.
   */
  public void initialize() {
    boolean init = initialized.getAndSet(true);
    if (init) {
      Logger.w(TAG, "FeedSessionManager has previously been initialized");
      return;
    }
    store.registerObserver(this);
    taskQueue.initialize(this::initializationTask);
  }

  @Override
  public Supplier<Consumer<Result<List<StreamDataOperation>>>> getTriggerRefreshConsumer() {
    return () -> getCommitter("triggerRefresh", MutationContext.EMPTY_CONTEXT);
  }

  // Task which initializes the Session Manager.  This must be the first task run on the
  // Session Manager thread so it's complete before we create any sessions.
  private void initializationTask() {
    threadUtils.checkNotMainThread();
    Thread currentThread = Thread.currentThread();
    currentThread.setName("JardinExecutor");
    timingUtils.pinThread(currentThread, "JardinExecutor");

    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
    // Initialize the Shared States cached here.
    ElapsedTimeTracker sharedStateTimeTracker = timingUtils.getElapsedTimeTracker(TAG);
    Result<List<StreamSharedState>> sharedStatesResult = store.getSharedStates();
    if (sharedStatesResult.isSuccessful()) {
      for (StreamSharedState sharedState : sharedStatesResult.getValue()) {
        sharedStateCache.put(sharedState.getContentId(), sharedState);
      }
    } else {
      // without shared states we need to switch to ephemeral mode
      switchToEphemeralMode("SharedStates failed to load, no shared states are loaded.");
      taskQueue.reset();
      sharedStateTimeTracker.stop("", "sharedStateTimeTracker", "error", "store error");
      timeTracker.stop("task", "Initialization", "error", "switchToEphemeralMode");
      return;
    }
    sharedStateTimeTracker.stop("", "sharedStateTimeTracker");

    // create the head session from the data in the Store
    if (!sessionCache.initialize()) {
      // we failed to initialize the sessionCache, so switch to ephemeral mode.
      switchToEphemeralMode("unable to initialize the sessionCache");
      timeTracker.stop("task", "Initialization", "error", "switchToEphemeralMode");
      return;
    }
    timeTracker.stop("task", "Initialization");
  }

  @Override
  public void getNewSession(
      ModelProvider modelProvider, /*@Nullable*/ ViewDepthProvider viewDepthProvider) {
    threadUtils.checkMainThread();
    if (!initialized.get()) {
      Logger.i(TAG, "Lazy initialization triggered, getNewSession");
      initialize();
    }
    InitializableSession session = sessionFactory.getSession();
    session.bindModelProvider(modelProvider, viewDepthProvider);
    synchronized (lock) {
      sessionsUnderConstruction.add(session);
    }

    if (!sessionCache.isHeadInitialized()) {
      Logger.i(TAG, "Delaying populateSession until initialization is finished");
      taskQueue.execute(
          "postInitialization/populateSession", TaskType.IMMEDIATE, () -> populateSession(session));
    } else {
      populateSession(session);
    }
  }

  // This method can be run either on the main thread or on the background thread. It calls the
  // SchedulerApi to determine how the session is created. It creates a new task to populate the
  // new session.
  private void populateSession(InitializableSession session) {
    HeadSessionImpl headSession = sessionCache.getHead();
    SessionManagerState sessionManagerState =
        new SessionManagerState(
            !headSession.isHeadEmpty(),
            headSession.getStreamSession().getLastAccessed(),
            taskQueue.isMakingRequest());
    @RequestBehavior int behavior = schedulerApi.shouldSessionRequestData(sessionManagerState);

    Runnable timeoutTask = null;
    if (behavior == REQUEST_WITH_TIMEOUT || behavior == NO_REQUEST_WITH_TIMEOUT) {
      timeoutTask = () -> populateSessionTask(session, true);
    }
    boolean legacyHeadContent =
        behavior == REQUEST_WITH_CONTENT || behavior == REQUEST_WITH_TIMEOUT;
    boolean makeRequest =
        (behavior == REQUEST_WITH_TIMEOUT
                || behavior == REQUEST_WITH_WAIT
                || behavior == REQUEST_WITH_CONTENT)
            && !taskQueue.isMakingRequest();
    Logger.i(
        TAG,
        "request behavior: %s, making request %s, timeout task %s",
        behavior,
        makeRequest,
        timeoutTask != null);

    // if we are making a request, there are two orders, request -> populate for all cases except
    // for REQUEST_WITH_CONTENT which is populate -> request
    if (makeRequest && behavior != REQUEST_WITH_CONTENT) {
      // determine if we make the request before or after populating a session
      triggerRefresh(null);
    }

    int taskType = TaskType.USER_FACING;
    if (behavior == RequestBehavior.NO_REQUEST_WITH_CONTENT
        && taskQueue.isMakingRequest()
        && !taskQueue.isDelayed()) {
      // don't wait for the request to finish, go ahead and show the content
      taskType = TaskType.IMMEDIATE;
    }

    // Task which creates and populates the newly created session.  This must be done
    // on the Session Manager thread so it atomic with the mutations.
    taskQueue.execute(
        "populateNewSession",
        taskType,
        () -> populateSessionTask(session, legacyHeadContent),
        timeoutTask,
        sessionPopulationTimeoutMs);
    if (makeRequest && behavior == REQUEST_WITH_CONTENT) {
      triggerRefresh(null);
    }
  }

  private void populateSessionTask(InitializableSession session, boolean legacyHeadContent) {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);

    if (noCardsError != null) {
      ModelProvider modelProvider = session.getModelProvider();
      if (modelProvider == null) {
        Logger.e(TAG, "populateSessionTask - noCardsError, modelProvider not found");
        timeTracker.stop("task", "Create/Populate New Session", "Failure", "noCardsError");
        return;
      }
      Logger.w(TAG, "populateSessionTask - noCardsError %s", modelProvider);

      Result<StreamSession> streamSessionResult = store.createNewSession();
      if (!streamSessionResult.isSuccessful()) {
        switchToEphemeralMode("Unable to create a new session during noCardsError failure");
        timeTracker.stop("task", "Create/Populate New Session", "Failure", "noCardsError");
        return;
      }

      // properly track the session so that it's empty.
      modelProvider.raiseError(Validators.checkNotNull(noCardsError));
      StreamSession streamSession = streamSessionResult.getValue();
      StreamSession recordedSession =
          streamSession.toBuilder().setLastAccessed(clock.currentTimeMillis()).build();
      session.setStreamSession(recordedSession);
      sessionCache.put(session.getStreamSession().getStreamToken(), session);
      synchronized (lock) {
        sessionsUnderConstruction.remove(session);
      }

      // Set the streamSession on the ModelProvider.
      ModelMutation mutation = modelProvider.edit();
      mutation.setStreamSession(streamSession);
      mutation.commit();
      timeTracker.stop("task", "Create/Populate New Session", "Failure", "noCardsError");
      return;
    }

    Result<StreamSession> streamSessionResult = store.createNewSession();
    if (!streamSessionResult.isSuccessful()) {
      switchToEphemeralMode("Unable to create a new session, createNewSession failed");
      timeTracker.stop("task", "Create/Populate New Session", "Failure", "createNewSession");
      return;
    }
    StreamSession streamSession = streamSessionResult.getValue();
    StreamSession recordedSession =
        streamSession.toBuilder().setLastAccessed(clock.currentTimeMillis()).build();

    Result<List<StreamStructure>> streamStructuresResult = store.getStreamStructures(streamSession);
    if (!streamStructuresResult.isSuccessful()) {
      switchToEphemeralMode("Unable to create a new session, getStreamStructures failed");
      timeTracker.stop("task", "Create/Populate New Session", "Failure", "getStreamStructures");
      return;
    }

    boolean cachedBindings;
    cachedBindings = contentCache.size() > 0;
    session.populateModelProvider(
        recordedSession, streamStructuresResult.getValue(), cachedBindings, legacyHeadContent);
    sessionCache.put(session.getStreamSession().getStreamToken(), session);
    synchronized (lock) {
      sessionsUnderConstruction.remove(session);
    }
    newSessionCount++;
    Logger.i(
        TAG,
        "Populate new session: %s, creation time %s",
        session.getStreamSession().getStreamToken(),
        StringFormattingUtils.formatLogDate(session.getStreamSession().getLastAccessed()));
    timeTracker.stop("task", "Create/Populate New Session");
  }

  @VisibleForTesting
  void switchToEphemeralMode(String message) {
    Logger.e(TAG, message);
    store.switchToEphemeralMode();
  }

  @VisibleForTesting
  void modelErrorObserver(/*@Nullable*/ Session session, ModelError error) {
    if (session == null && error.getErrorType() == ErrorType.NO_CARDS_ERROR) {
      Logger.e(TAG, "No Cards Found on TriggerRefresh, setting noCardsError");
      noCardsError = error;
      // queue a clear which will run after all currently delayed tasks.  This allows delayed
      // session population tasks to inform the ModelProvider of errors then we clear the error
      // state.
      taskQueue.execute("clearNoCardsError", TaskType.USER_FACING, () -> noCardsError = null);
      return;
    } else if (session != null && error.getErrorType() == ErrorType.PAGINATION_ERROR) {
      Logger.e(TAG, "Pagination Error found");
      ModelProvider modelProvider = session.getModelProvider();
      if (modelProvider != null) {
        modelProvider.raiseError(error);
      } else {
        Logger.e(TAG, "handling Pagination Error, didn't find Model Provider");
      }
      return;
    }
    Logger.e(
        TAG,
        "unhandled modelErrorObserver: session, %s, error %s",
        session != null,
        error.getErrorType());
  }

  @Override
  public void getExistingSession(String sessionToken, ModelProvider modelProvider) {
    threadUtils.checkMainThread();
    if (!initialized.get()) {
      Logger.i(TAG, "Lazy initialization triggered, getExistingSession");
      initialize();
    }
    InitializableSession session = sessionFactory.getSession();
    session.bindModelProvider(modelProvider, null);

    // Task which populates the newly created session.  This must be done
    // on the Session Manager thread so it atomic with the mutations.
    taskQueue.execute(
        "createExistingSession",
        TaskType.USER_FACING,
        () -> {
          threadUtils.checkNotMainThread();
          Session existingSession = sessionCache.get(sessionToken);
          if (existingSession == null) {
            modelProvider.invalidate();
            return;
          }
          if (!existingSession.getContentInSession().isEmpty()) {
            ModelProvider existingModelProvider = existingSession.getModelProvider();
            if (existingModelProvider != null) {
              existingModelProvider.invalidate();
            }
          }
          StreamSession streamSession = existingSession.getStreamSession();

          Result<List<StreamStructure>> streamStructuresResult =
              store.getStreamStructures(streamSession);
          if (streamStructuresResult.isSuccessful()) {
            session.populateModelProvider(
                streamSession, streamStructuresResult.getValue(), false, false);
            sessionCache.put(session.getStreamSession().getStreamToken(), session);
            existingSessionCount++;
          } else {
            Logger.e(
                TAG,
                "unable to get stream structure for existing session %s",
                session.getStreamSession().getStreamToken());
            switchToEphemeralMode("unable to get stream structure for existing session");
          }
        });
  }

  @Override
  public void invalidatedSession(String sessionToken) {
    if (threadUtils.isMainThread()) {
      taskQueue.execute(
          "invalidatedSession", TaskType.USER_FACING, () -> sessionCache.remove(sessionToken));
    } else {
      sessionCache.remove(sessionToken);
    }
  }

  @Override
  public void detachSession(String sessionToken) {
    if (threadUtils.isMainThread()) {
      taskQueue.execute(
          "detachSession",
          TaskType.USER_FACING,
          () -> sessionCache.detachModelProvider(sessionToken));
    } else {
      sessionCache.detachModelProvider(sessionToken);
    }
  }

  @Override
  public void invalidateHead() {
    sessionManagerMutation.resetHead();
  }

  @Override
  public void handleToken(StreamSession streamSession, StreamToken streamToken) {
    Logger.i(
        TAG,
        "HandleToken on stream %s, token %s",
        streamSession.getStreamToken(),
        streamToken.getContentId());
    threadUtils.checkMainThread();

    // At the moment, this doesn't try to prevent multiple requests with the same Token.
    // We may want to make sure we only make the request a single time.
    handleTokenCount++;
    MutationContext mutationContext =
        new MutationContext.Builder()
            .setContinuationToken(streamToken)
            .setRequestingSession(streamSession)
            .build();
    requestManager.loadMore(streamToken, getCommitter("handleToken", mutationContext));
  }

  @Override
  public void triggerRefresh(/*@Nullable*/ StreamSession streamSession) {
    if (!initialized.get()) {
      Logger.i(TAG, "Lazy initialization triggered, triggerRefresh");
      initialize();
    }
    taskQueue.execute(
        "triggerRefresh(FeedSessionManager)",
        TaskType.HEAD_INVALIDATE, // invalidate because we are requesting a refresh
        () -> triggerRefreshTask(streamSession));
  }

  private void triggerRefreshTask(/*@Nullable*/ StreamSession streamSession) {
    threadUtils.checkNotMainThread();
    requestManager.triggerRefresh(
        RequestReason.MANUAL_REFRESH,
        getCommitter("triggerRefresh", MutationContext.EMPTY_CONTEXT));
    if (streamSession != null) {
      String streamToken = streamSession.getStreamToken();
      Session session = sessionCache.get(streamToken);
      if (session != null) {
        ModelProvider modelProvider = session.getModelProvider();
        if (modelProvider != null) {
          invalidateSession(modelProvider, session);
        } else {
          Logger.w(TAG, "Session didn't have a ModelProvider %s", streamToken);
        }
      } else {
        Logger.w(TAG, "TriggerRefresh didn't find session %s", streamToken);
      }
    } else {
      Logger.i(TAG, "triggerRefreshTask no StreamSession provided");
    }
  }

  @Override
  public void getStreamFeatures(
      List<String> contentIds, Consumer<Result<List<PayloadWithId>>> consumer) {
    threadUtils.checkNotMainThread();
    List<PayloadWithId> results = new ArrayList<>();
    List<String> cacheMisses = new ArrayList<>();
    int contentSize = contentCache.size();
    for (String contentId : contentIds) {
      StreamPayload payload = contentCache.get(contentId);
      if (payload != null) {
        results.add(new PayloadWithId(contentId, payload));
      } else {
        cacheMisses.add(contentId);
      }
    }

    if (!cacheMisses.isEmpty()) {
      Result<List<PayloadWithId>> contentResult = store.getPayloads(cacheMisses);
      if (contentResult.isSuccessful()) {
        results.addAll(contentResult.getValue());
      } else {
        // since we couldn't populate the content, switch to ephemeral mode
        switchToEphemeralMode("Unable to get the payloads in getStreamFeatures");
        consumer.accept(Result.failure());
      }
    }
    Logger.i(
        TAG,
        "Caching getStreamFeatures - items %s, cache misses %s, cache size %s",
        contentIds.size(),
        cacheMisses.size(),
        contentSize);
    consumer.accept(Result.success(results));
  }

  @Override
  /*@Nullable*/
  public StreamSharedState getSharedState(ContentId contentId) {
    threadUtils.checkMainThread();
    String sharedStateId = protocolAdapter.getStreamContentId(contentId);
    StreamSharedState state = sharedStateCache.get(sharedStateId);
    if (state == null) {
      Logger.e(TAG, "Shared State [%s] was not found", sharedStateId);
    }
    return state;
  }

  @Override
  public Consumer<Result<List<StreamDataOperation>>> getUpdateConsumer(
      MutationContext mutationContext) {
    if (!initialized.get()) {
      Logger.i(TAG, "Lazy initialization triggered, getUpdateConsumer");
      initialize();
    }
    return new SessionMutationTracker(mutationContext, "updateConsumer");
  }

  @VisibleForTesting
  class SessionMutationTracker implements Consumer<Result<List<StreamDataOperation>>> {
    private final MutationContext mutationContext;
    private final String taskName;

    @SuppressWarnings("argument.type.incompatible") // ok to add this to the map
    private SessionMutationTracker(MutationContext mutationContext, String taskName) {
      this.mutationContext = mutationContext;
      this.taskName = taskName;
      outstandingMutations.add(this);
    }

    @Override
    public void accept(Result<List<StreamDataOperation>> input) {
      if (outstandingMutations.remove(this)) {
        sessionManagerMutation
            .createCommitter(
                taskName,
                mutationContext,
                FeedSessionManager.this::modelErrorObserver,
                sharedStateCache,
                knownContentListener)
            .accept(input);
      } else {
        Logger.w(TAG, "SessionMutationTracker dropping response due to clear");
      }
    }
  }

  @Override
  public <T> void getStreamFeaturesFromHead(
      Function<StreamPayload, /*@Nullable*/ T> filterPredicate,
      Consumer<Result<List</*@NonNull*/ T>>> consumer) {
    taskQueue.execute(
        "getStreamFeaturesFromHead",
        TaskType.BACKGROUND,
        () -> {
          HeadAsStructure headAsStructure = new HeadAsStructure(store, timingUtils, threadUtils);
          Function<TreeNode, /*@Nullable*/ T> toStreamPayload =
              treeNode -> filterPredicate.apply(treeNode.getStreamPayload());
          headAsStructure.initialize(
              result -> {
                if (!result.isSuccessful()) {
                  consumer.accept(Result.failure());
                  return;
                }
                Result<List</*@NonNull*/ T>> filterResults = headAsStructure.filter(toStreamPayload);
                consumer.accept(
                    filterResults.isSuccessful()
                        ? Result.success(filterResults.getValue())
                        : Result.failure());
              });
        });
  }

  @Override
  public void setKnownContentListener(KnownContentListener knownContentListener) {
    this.knownContentListener = knownContentListener;
  }

  @Override
  public void onSwitchToEphemeralMode() {
    reset();
  }

  private Consumer<Result<List<StreamDataOperation>>> getCommitter(
      String taskName, MutationContext mutationContext) {
    return new SessionMutationTracker(mutationContext, taskName);
  }

  @Override
  public void reset() {
    threadUtils.checkNotMainThread();
    sessionManagerMutation.forceResetHead();
    sessionCache.reset();
    // Invalidate all sessions currently under construction
    List<InitializableSession> invalidateSessions;
    synchronized (lock) {
      invalidateSessions = new ArrayList<>(sessionsUnderConstruction);
      sessionsUnderConstruction.clear();
    }
    for (InitializableSession session : invalidateSessions) {
      ModelProvider modelProvider = session.getModelProvider();
      if (modelProvider != null) {
        modelProvider.invalidate();
      }
    }
    contentCache.reset();
    sharedStateCache.clear();
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    dumper.forKey("newSessionCount").value(newSessionCount).compactPrevious();
    dumper.forKey("existingSessionCount").value(existingSessionCount).compactPrevious();
    dumper.forKey("handleTokenCount").value(handleTokenCount).compactPrevious();
    dumper.forKey("sharedStateCount").value(sharedStateCache.size());
    dumper.dump(contentCache);
    dumper.dump(taskQueue);
    dumper.dump(sessionCache);
    dumper.dump(sessionManagerMutation);
  }

  private void invalidateSession(ModelProvider modelProvider, Session session) {
    threadUtils.checkNotMainThread();
    Logger.i(TAG, "Invalidate session %s", session.getStreamSession().getStreamToken());
    modelProvider.invalidate();
  }

  // This is only used in tests to verify the contents of the shared state cache.
  Map<String, StreamSharedState> getSharedStateCacheForTest() {
    return new HashMap<>(sharedStateCache);
  }

  // This is only used in tests to access a copy of the session state
  Map<String, Session> getSessionsMapForTest() {
    return sessionCache.getSessionsMapForTest();
  }

  // Called in the integration tests
  @VisibleForTesting
  public boolean isDelayed() {
    return taskQueue.isDelayed();
  }

  @Override
  public void onLifecycleEvent(@LifecycleEvent String event) {
    Logger.i(TAG, "onLifecycleEvent %s", event);
    switch (event) {
      case LifecycleEvent.INITIALIZE:
        initialize();
        break;
      case LifecycleEvent.CLEAR_ALL:
        Logger.i(TAG, "CLEAR_ALL will cancel %s mutations", outstandingMutations.size());
        outstandingMutations.clear();
        break;
      case LifecycleEvent.CLEAR_ALL_WITH_REFRESH:
        Logger.i(
            TAG, "CLEAR_ALL_WITH_REFRESH will cancel %s mutations", outstandingMutations.size());
        outstandingMutations.clear();
        break;
      default:
        // Do nothing
    }
  }
}
