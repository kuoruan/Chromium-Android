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
import com.google.android.libraries.feed.api.common.PayloadWithId;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.store.ContentMutation;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.logging.StringFormattingUtils;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.search.now.feed.client.StreamDataProto.StreamPayload;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSessions;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/**
 * For the FeedSessionManager, this class is a cache of the {@link Session} objects which are
 * currently defined.
 */
public final class SessionCache implements Dumpable {
  private static final String TAG = "SessionCache";
  @VisibleForTesting static final String STREAM_SESSION_CONTENT_ID = "FSM::Sessions::0";

  // Used to synchronize the stored data
  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Map<String, Session> sessions = new HashMap<>();

  // Head is in effect final once it's assigned in the initializationTask.
  private final HeadSessionImpl head;

  private boolean initialized = false;

  private final Store store;
  private final TaskQueue taskQueue;
  private final SessionFactory sessionFactory;
  private final long lifetimeMs;
  private final TimingUtils timingUtils;
  private final ThreadUtils threadUtils;
  private final Clock clock;

  // operation counts for the dumper
  private int getCount = 0;
  private int getAllCount = 0;
  private int putCount = 0;
  private int removeCount = 0;
  private int unboundSessionCount = 0;
  private int detachedSessionCount = 0;
  private int expiredSessionsCleared = 0;

  @VisibleForTesting
  final Supplier<Set<String>> accessibleContentSupplier =
      () -> {
        Set<String> accessibleContent = new HashSet<>();
        synchronized (lock) {
          for (Session session : sessions.values()) {
            accessibleContent.addAll(session.getContentInSession());
          }
        }
        return accessibleContent;
      };

  public SessionCache(
      Store store,
      TaskQueue taskQueue,
      SessionFactory sessionFactory,
      long lifetimeMs,
      TimingUtils timingUtils,
      ThreadUtils threadUtil,
      Clock clock) {
    this.store = store;
    this.taskQueue = taskQueue;
    this.sessionFactory = sessionFactory;
    this.lifetimeMs = lifetimeMs;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtil;
    this.clock = clock;

    this.head = sessionFactory.getHeadSession();
  }

  /**
   * Return a {@link Session} for the sessionToken, or {@code null} if the sessionCache doesn't
   * contain the Session.
   */
  /*@Nullable*/
  public Session get(String sessionToken) {
    getCount++;
    synchronized (lock) {
      return sessions.get(sessionToken);
    }
  }

  /** Add a {@link Session} to the SessionCache. */
  public void put(String sessionToken, Session session) {
    threadUtils.checkNotMainThread();
    putCount++;
    synchronized (lock) {
      sessions.put(sessionToken, session);
    }
    updatePersistedSessions();
  }

  public void detachModelProvider(String sessionToken) {
    threadUtils.checkNotMainThread();
    InitializableSession initializableSession;
    synchronized (lock) {
      Session session = sessions.get(sessionToken);
      if (!(session instanceof InitializableSession)) {
        Logger.w(TAG, "Unable to detach session %s", sessionToken);
        return;
      } else {
        initializableSession = (InitializableSession) session;
      }
    }

    initializableSession.bindModelProvider(null, null);
    detachedSessionCount++;
  }

  /**
   * Remove a {@link Session} from the SessionCache. Returns the Session removed or {@code Null} if
   * it wasn't defined.
   */
  /*@Nullable*/
  public Session remove(String sessionToken) {
    threadUtils.checkNotMainThread();
    removeCount++;
    Session session;
    synchronized (lock) {
      session = sessions.remove(sessionToken);
    }
    updatePersistedSessions();
    return session;
  }

  /** Returns {@code true} if HEAD has been initialize, which happens in {@link #initialize()}. */
  public boolean isHeadInitialized() {
    return initialized;
  }

  /**
   * Return {@link HeadSessionImpl} for head. Returns {@code null} if {@link #initialize()} hasn't
   * finished running.
   */
  public HeadSessionImpl getHead() {
    return head;
  }

  /** Returns all the session tracked by the SessionCache. */
  public List<Session> getSessions() {
    getAllCount++;
    synchronized (lock) {
      return new ArrayList<>(sessions.values());
    }
  }

  /** Initialize the SessionCache from Store. */
  public boolean initialize() {
    threadUtils.checkNotMainThread();

    // create the head session from the data in the Store
    ElapsedTimeTracker headTimeTracker = timingUtils.getElapsedTimeTracker(TAG);
    synchronized (lock) {
      sessions.put(head.getStreamSession().getStreamToken(), head);
    }
    Result<List<StreamStructure>> results = store.getStreamStructures(head.getStreamSession());
    if (!results.isSuccessful()) {
      Logger.w(TAG, "unable to get head stream structures");
      return false;
    }
    head.initializeSession(results.getValue());
    initialized = true;
    headTimeTracker.stop("", "createHead");

    initializePersistedSessions();
    return true;
  }

  public void reset() {
    List<Session> sessionList;
    synchronized (lock) {
      sessionList = new ArrayList<>(sessions.values());
    }
    for (Session session : sessionList) {
      ModelProvider modelProvider = session.getModelProvider();
      if (modelProvider != null) {
        modelProvider.invalidate();
      }
    }
    synchronized (lock) {
      sessions.clear();
      head.reset();
      sessions.put(head.getStreamSession().getStreamToken(), head);
      initialized = true;
    }
  }

  @VisibleForTesting
  void initializePersistedSessions() {
    threadUtils.checkNotMainThread();
    StreamSessions persistedSessions = getPersistedSessions();
    List<StreamSession> sessionList;
    if (persistedSessions == null) {
      Logger.w(TAG, "Persisted Sessions were not found, initializing without sessions");
      sessionList = new ArrayList<>();
    } else {
      sessionList = persistedSessions.getStreamSessionsList();
    }

    boolean cleanupSessions = false;
    for (StreamSession session : sessionList) {
      if (!isSessionAlive(session)) {
        Logger.i(
            TAG,
            "Found expired session %s, created %s",
            session.getStreamToken(),
            StringFormattingUtils.formatLogDate(session.getLastAccessed()));
        cleanupSessions = true;
        synchronized (lock) {
          sessions.remove(session.getStreamToken());
        }
        continue;
      }
      HeadSessionImpl headSession = Validators.checkNotNull(head);
      if (session.getStreamToken().equals(headSession.getStreamSession().getStreamToken())) {
        // update the information stored for the $HEAD record we created previously
        Logger.i(
            TAG,
            "Updating $HEAD state, creation %s",
            StringFormattingUtils.formatLogDate(session.getLastAccessed()));
        headSession.updateAccessTime(session.getLastAccessed());
        continue;
      }
      InitializableSession unboundSession;
      synchronized (lock) {
        if (sessions.containsKey(session.getStreamToken())) {
          // Don't replace sessions already found in sessions
          continue;
        }
        // Unbound sessions are sessions that are able to be created through restore
        unboundSession = sessionFactory.getSession();
        unboundSession.setStreamSession(session);
        sessions.put(session.getStreamToken(), unboundSession);
      }

      // Task which populates the newly created unbound session.
      Runnable createUnboundSession =
          () -> {
            Logger.i(TAG, "Task: createUnboundSession %s", session.getStreamToken());
            ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
            Result<List<StreamStructure>> streamStructuresResult =
                store.getStreamStructures(session);
            if (streamStructuresResult.isSuccessful()) {
              unboundSession.populateModelProvider(
                  session, streamStructuresResult.getValue(), false, false);
            } else {
              Logger.e(TAG, "Failed to read unbound session state, ignored");
            }
            timeTracker.stop("task", "createUnboundSession");
            unboundSessionCount++;
          };
      taskQueue.execute("createUnboundSession", TaskType.BACKGROUND, createUnboundSession);
    }

    if (cleanupSessions) {
      // Queue up a task to clear the session journals.
      taskQueue.execute(
          "cleanupSessionJournals", TaskType.BACKGROUND, this::cleanupSessionJournals);
    }
    Set<String> reservedContentIds = new HashSet<>();
    reservedContentIds.add(STREAM_SESSION_CONTENT_ID);
    taskQueue.execute(
        "contentGc",
        TaskType.BACKGROUND,
        store.triggerContentGc(reservedContentIds, accessibleContentSupplier));
  }

  /*@Nullable*/
  @VisibleForTesting
  StreamSessions getPersistedSessions() {
    List<String> sessionIds = new ArrayList<>();
    sessionIds.add(STREAM_SESSION_CONTENT_ID);
    Result<List<PayloadWithId>> sessionPayloadResult = store.getPayloads(sessionIds);
    if (!sessionPayloadResult.isSuccessful()) {
      // If we cant read the persisted sessions, report the error and return null. No sessions will
      // be created, this is as if we deleted all existing sessions.  It should be safe to ignore
      // the error.
      Logger.e(TAG, "getPayloads failed to read the Persisted sessions");
      return null;
    }

    List<PayloadWithId> sessionPayload = sessionPayloadResult.getValue();
    if (sessionPayload.isEmpty()) {
      Logger.w(TAG, "Persisted Sessions were not found");
      return null;
    }
    StreamPayload payload = sessionPayload.get(0).payload;
    if (!payload.hasStreamSessions()) {
      Logger.e(TAG, "Persisted Sessions StreamSessions was not set");
      return null;
    }
    return payload.getStreamSessions();
  }

  @VisibleForTesting
  void updatePersistedSessions() {
    threadUtils.checkNotMainThread();
    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
    StreamSessions.Builder sessionsBuilder = StreamSessions.newBuilder();
    int sessionCount;
    synchronized (lock) {
      sessionCount = sessions.size();
      for (Session s : sessions.values()) {
        sessionsBuilder.addStreamSessions(s.getStreamSession());
      }
    }
    StreamSessions currentSessions = sessionsBuilder.build();
    StreamPayload payload = StreamPayload.newBuilder().setStreamSessions(currentSessions).build();
    ContentMutation contentMutation = store.editContent();
    contentMutation.add(STREAM_SESSION_CONTENT_ID, payload);
    contentMutation.commit();
    timeTracker.stop("task", "updatePersistedSessions(Content)", "sessionCount", sessionCount);
  }

  /**
   * Remove all session journals which are not currently found in {@code sessions} which contains
   * all of the known sessions. This is a garbage collection task.
   */
  @VisibleForTesting
  void cleanupSessionJournals() {
    threadUtils.checkNotMainThread();
    Logger.i(TAG, "Task: cleanupSessionJournals");
    ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
    int sessionCleared = expiredSessionsCleared;

    Result<List<StreamSession>> storedSessionsResult = store.getAllSessions();
    if (storedSessionsResult.isSuccessful()) {
      synchronized (lock) {
        for (StreamSession session : storedSessionsResult.getValue()) {
          if (!sessions.containsKey(session.getStreamToken())) {
            store.removeSession(session);
            expiredSessionsCleared++;
          }
        }
      }
    } else {
      // We were unable to read all the sessions, log an error and then ignore the fact that
      // cleanup failed.
      Logger.e(TAG, "Error reading all sessions, Unable to cleanup session journals");
    }
    timeTracker.stop(
        "task",
        "cleanupSessionJournals",
        "sessionsCleared",
        expiredSessionsCleared - sessionCleared);
  }

  @VisibleForTesting
  boolean isSessionAlive(StreamSession streamSession) {
    // Today HEAD will does not time out
    return (Store.HEAD.getStreamToken().equals(streamSession.getStreamToken()))
        || ((streamSession.getLastAccessed() + lifetimeMs) > clock.currentTimeMillis());
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    synchronized (lock) {
      dumper.forKey("sessions").value(sessions.size());
    }
    dumper.forKey("expiredSessionsCleared").value(expiredSessionsCleared).compactPrevious();
    dumper.forKey("unboundSessionCount").value(unboundSessionCount).compactPrevious();
    dumper.forKey("detachedSessionCount").value(detachedSessionCount).compactPrevious();
    dumper.forKey("get").value(getCount);
    dumper.forKey("getAll").value(getAllCount).compactPrevious();
    dumper.forKey("put").value(putCount).compactPrevious();
    dumper.forKey("remove").value(removeCount).compactPrevious();
  }

  // This is only used in tests to access a copy of the session state
  public Map<String, Session> getSessionsMapForTest() {
    synchronized (lock) {
      return new HashMap<>(sessions);
    }
  }

  void setSessionsForTest(Session... testSessions) {
    for (Session session : testSessions) {
      put(session.getStreamSession().getStreamToken(), session);
    }
  }
}
