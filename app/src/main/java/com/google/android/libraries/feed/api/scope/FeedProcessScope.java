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

package com.google.android.libraries.feed.api.scope;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionmanager.ActionReader;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi;
import com.google.android.libraries.feed.api.lifecycle.AppLifecycleListener;
import com.google.android.libraries.feed.api.protocoladapter.ProtocolAdapter;
import com.google.android.libraries.feed.api.requestmanager.RequestManager;
import com.google.android.libraries.feed.api.sessionmanager.SessionManager;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.DirectHostSupported;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.protoextensions.FeedExtensionRegistry;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.SystemClockImpl;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.feedactionmanager.FeedActionManagerImpl;
import com.google.android.libraries.feed.feedactionreader.FeedActionReader;
import com.google.android.libraries.feed.feedapplifecyclelistener.FeedAppLifecycleListener;
import com.google.android.libraries.feed.feedknowncontent.FeedKnownContentApi;
import com.google.android.libraries.feed.feedprotocoladapter.FeedProtocolAdapter;
import com.google.android.libraries.feed.feedrequestmanager.FeedRequestManager;
import com.google.android.libraries.feed.feedsessionmanager.FeedSessionManagerFactory;
import com.google.android.libraries.feed.feedstore.ContentStorageDirectImpl;
import com.google.android.libraries.feed.feedstore.FeedStore;
import com.google.android.libraries.feed.feedstore.JournalStorageDirectImpl;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.config.ApplicationInfo;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.android.libraries.feed.host.config.DebugBehavior;
import com.google.android.libraries.feed.host.imageloader.ImageLoaderApi;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.logging.LoggingApi;
import com.google.android.libraries.feed.host.network.NetworkClient;
import com.google.android.libraries.feed.host.offlineindicator.OfflineIndicatorApi;
import com.google.android.libraries.feed.host.proto.ProtoExtensionProvider;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi;
import com.google.android.libraries.feed.host.storage.ContentStorage;
import com.google.android.libraries.feed.host.storage.ContentStorageDirect;
import com.google.android.libraries.feed.host.storage.JournalStorage;
import com.google.android.libraries.feed.host.storage.JournalStorageDirect;
import com.google.android.libraries.feed.host.stream.CardConfiguration;
import com.google.android.libraries.feed.host.stream.SnackbarApi;
import com.google.android.libraries.feed.host.stream.StreamConfiguration;
import com.google.android.libraries.feed.hostimpl.network.NetworkClientWrapper;
import com.google.android.libraries.feed.hostimpl.scheduler.SchedulerApiWrapper;
import com.google.android.libraries.feed.hostimpl.storage.InMemoryContentStorage;
import com.google.android.libraries.feed.hostimpl.storage.InMemoryJournalStorage;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Per-process instance of the feed library.
 *
 * <p>It's the host's responsibility to make sure there's only one instance of this per process, per
 * user.
 */
public final class FeedProcessScope implements Dumpable {
  private static final String TAG = "FeedProcessScope";

  private final LoggingApi loggingApi;
  private final NetworkClient networkClient;
  private final ProtocolAdapter protocolAdapter;
  private final RequestManager requestManager;
  private final SessionManager sessionManager;
  private final Store store;
  private final TimingUtils timingUtils;
  private final ThreadUtils threadUtils;
  private final TaskQueue taskQueue;
  private final MainThreadRunner mainThreadRunner;
  private final AppLifecycleListener appLifecycleListener;
  private final Clock clock;
  private final DebugBehavior debugBehavior;
  private final ActionManager actionManager;
  private final Configuration configuration;
  private final KnownContentApi knownContentApi;
  private final FeedExtensionRegistry feedExtensionRegistry;
  private final ClearAllListener clearAllListener;

  /**
   * Return a {@link Builder} to create a FeedProcessScope
   *
   * <p>This is called by hosts so it must be public
   */
  public FeedStreamScope.Builder createFeedStreamScopeBuilder(
      Context context,
      ImageLoaderApi imageLoaderApi,
      ActionApi actionApi,
      StreamConfiguration streamConfiguration,
      CardConfiguration cardConfiguration,
      SnackbarApi snackbarApi,
      BasicLoggingApi basicLoggingApi,
      OfflineIndicatorApi offlineIndicatorApi) {
    return new FeedStreamScope.Builder(
        context,
        actionApi,
        imageLoaderApi,
        loggingApi,
        protocolAdapter,
        sessionManager,
        threadUtils,
        timingUtils,
        taskQueue,
        mainThreadRunner,
        clock,
        debugBehavior,
        streamConfiguration,
        cardConfiguration,
        actionManager,
        configuration,
        snackbarApi,
        basicLoggingApi,
        offlineIndicatorApi,
        knownContentApi);
  }

  /** Created through the {@link Builder}. */
  private FeedProcessScope(
      LoggingApi loggingApi,
      NetworkClient networkClient,
      ProtocolAdapter protocolAdapter,
      RequestManager requestManager,
      SessionManager sessionManager,
      Store store,
      TimingUtils timingUtils,
      ThreadUtils threadUtils,
      TaskQueue taskQueue,
      MainThreadRunner mainThreadRunner,
      AppLifecycleListener appLifecycleListener,
      Clock clock,
      DebugBehavior debugBehavior,
      ActionManager actionManager,
      Configuration configuration,
      KnownContentApi knownContentApi,
      FeedExtensionRegistry feedExtensionRegistry,
      ClearAllListener clearAllListener) {
    this.loggingApi = loggingApi;
    this.networkClient = networkClient;
    this.protocolAdapter = protocolAdapter;
    this.requestManager = requestManager;
    this.sessionManager = sessionManager;
    this.store = store;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtils;
    this.taskQueue = taskQueue;
    this.mainThreadRunner = mainThreadRunner;
    this.appLifecycleListener = appLifecycleListener;
    this.clock = clock;
    this.debugBehavior = debugBehavior;
    this.actionManager = actionManager;
    this.configuration = configuration;
    this.knownContentApi = knownContentApi;
    this.feedExtensionRegistry = feedExtensionRegistry;
    this.clearAllListener = clearAllListener;
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.title(TAG);
    if (protocolAdapter instanceof Dumpable) {
      dumper.dump((Dumpable) protocolAdapter);
    }
    dumper.dump(timingUtils);
    if (sessionManager instanceof Dumpable) {
      dumper.dump((Dumpable) sessionManager);
    }
    if (store instanceof Dumpable) {
      dumper.dump((Dumpable) store);
    }
    dumper.dump(clearAllListener);
  }

  public void onDestroy() {
    try {
      Logger.i(TAG, "FeedProcessScope onDestroy called");
      networkClient.close();
      taskQueue.reset();
      taskQueue.completeReset();
    } catch (Exception ignored) {
      // Ignore exception when closing.
    }
  }

  public Clock getClock() {
    return clock;
  }

  public ProtocolAdapter getProtocolAdapter() {
    return protocolAdapter;
  }

  public RequestManager getRequestManager() {
    return requestManager;
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public TimingUtils getTimingUtils() {
    return timingUtils;
  }

  public MainThreadRunner getMainThreadRunner() {
    return mainThreadRunner;
  }

  public TaskQueue getTaskQueue() {
    return taskQueue;
  }

  public AppLifecycleListener getAppLifecycleListener() {
    return appLifecycleListener;
  }

  public ActionManager getActionManager() {
    return actionManager;
  }

  public Store getStore() {
    return store;
  }

  public KnownContentApi getKnownContentApi() {
    return knownContentApi;
  }

  public FeedExtensionRegistry getFeedExtensionRegistry() {
    return feedExtensionRegistry;
  }

  /** A builder that creates a {@link FeedProcessScope}. */
  public static final class Builder {

    // Required fields.
    private final Configuration configuration;
    ExecutorService singleThreadExecutor;
    private final LoggingApi loggingApi;
    private final NetworkClient unwrappedNetworkClient;
    private final SchedulerApi unwrappedSchedulerApi;
    private final FeedAppLifecycleListener lifecycleListener;
    private final DebugBehavior debugBehavior;
    private final Context context;
    private final ApplicationInfo applicationInfo;

    // Optional fields - if they are not provided, we will use default implementations.
    /*@MonotonicNonNull*/ private RequestManager requestManager = null;
    /*@MonotonicNonNull*/ private SessionManager sessionManager = null;
    /*@MonotonicNonNull*/ private ProtocolAdapter protocolAdapter = null;
    /*@MonotonicNonNull*/ private ProtoExtensionProvider protoExtensionProvider = null;
    /*@MonotonicNonNull*/ ContentStorageDirect contentStorage = null;
    /*@MonotonicNonNull*/ JournalStorageDirect journalStorage = null;
    /*@MonotonicNonNull*/ ContentStorage rawContentStorage = null;
    /*@MonotonicNonNull*/ JournalStorage rawJournalStorage = null;
    /*@MonotonicNonNull*/ private Clock clock;
    /*@MonotonicNonNull*/ private KnownContentApi knownContentApi;
    /*@MonotonicNonNull*/ private MainThreadRunner mainThreadRunner;

    // This will be overridden in tests.
    private ThreadUtils threadUtils = new ThreadUtils();

    /** The APIs are all required to construct the scope. */
    public Builder(
        Configuration configuration,
        ExecutorService singleThreadExecutor,
        LoggingApi loggingApi,
        NetworkClient networkClient,
        SchedulerApi schedulerApi,
        FeedAppLifecycleListener lifecycleListener,
        DebugBehavior debugBehavior,
        Context context,
        ApplicationInfo applicationInfo) {
      this.configuration = configuration;
      this.singleThreadExecutor = singleThreadExecutor;
      this.loggingApi = loggingApi;
      this.lifecycleListener = lifecycleListener;
      this.debugBehavior = debugBehavior;
      this.context = context;
      this.applicationInfo = applicationInfo;
      this.unwrappedNetworkClient = networkClient;
      this.unwrappedSchedulerApi = schedulerApi;
    }

    public Builder setProtocolAdapter(ProtocolAdapter protocolAdapter) {
      this.protocolAdapter = protocolAdapter;
      return this;
    }

    public Builder setRequestManager(RequestManager requestManager) {
      this.requestManager = requestManager;
      return this;
    }

    public Builder setSessionManager(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
      return this;
    }

    public Builder setProtoExtensionProvider(ProtoExtensionProvider protoExtensionProvider) {
      this.protoExtensionProvider = protoExtensionProvider;
      return this;
    }

    public Builder setContentStorage(ContentStorage contentStorage) {
      rawContentStorage = contentStorage;
      return this;
    }

    public Builder setContentStorageDirect(ContentStorageDirect contentStorage) {
      this.contentStorage = contentStorage;
      return this;
    }

    public Builder setJournalStorage(JournalStorage journalStorage) {
      rawJournalStorage = journalStorage;
      return this;
    }

    public Builder setJournalStorageDirect(JournalStorageDirect journalStorage) {
      this.journalStorage = journalStorage;
      return this;
    }

    public Builder setMainThreadRunner(MainThreadRunner mainThreadRunner) {
      this.mainThreadRunner = mainThreadRunner;
      return this;
    }

    // This is really exposed for tests to override the thread checking
    Builder setThreadUtils(ThreadUtils threadUtils) {
      this.threadUtils = threadUtils;
      return this;
    }

    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    private MainThreadRunner getOrCreateMainThreadRunner() {
      if (mainThreadRunner == null) {
        mainThreadRunner = new MainThreadRunner();
      }
      return mainThreadRunner;
    }

    @VisibleForTesting
    ContentStorageDirect buildContentStorage() {
      if (contentStorage == null) {
        boolean useDirect = configuration.getValueOrDefault(ConfigKey.USE_DIRECT_STORAGE, false);
        if (useDirect
            && rawContentStorage != null
            && rawContentStorage instanceof ContentStorageDirect) {
          contentStorage = (ContentStorageDirect) rawContentStorage;
        } else if (rawContentStorage != null) {
          contentStorage =
              new ContentStorageDirectImpl(rawContentStorage, getOrCreateMainThreadRunner());
        } else {
          contentStorage = new InMemoryContentStorage();
        }
      }
      return contentStorage;
    }

    @VisibleForTesting
    JournalStorageDirect buildJournalStorage() {
      if (journalStorage == null) {
        boolean useDirect = configuration.getValueOrDefault(ConfigKey.USE_DIRECT_STORAGE, false);
        if (useDirect
            && rawJournalStorage != null
            && rawJournalStorage instanceof JournalStorageDirect) {
          journalStorage = (JournalStorageDirect) rawJournalStorage;
        } else if (rawJournalStorage != null) {
          journalStorage =
              new JournalStorageDirectImpl(rawJournalStorage, getOrCreateMainThreadRunner());
        } else {
          journalStorage = new InMemoryJournalStorage();
        }
      }
      return journalStorage;
    }

    public FeedProcessScope build() {
      contentStorage = buildContentStorage();
      journalStorage = buildJournalStorage();

      boolean directHostCallEnabled =
          configuration.getValueOrDefault(ConfigKey.USE_DIRECT_STORAGE, false);
      NetworkClient networkClient;
      SchedulerApi schedulerApi;
      if (unwrappedNetworkClient instanceof DirectHostSupported && directHostCallEnabled) {
        networkClient = unwrappedNetworkClient;
      } else {
        networkClient =
            new NetworkClientWrapper(
                unwrappedNetworkClient, threadUtils, getOrCreateMainThreadRunner());
      }
      if (unwrappedSchedulerApi instanceof DirectHostSupported && directHostCallEnabled) {
        schedulerApi = unwrappedSchedulerApi;
      } else {
        schedulerApi =
            new SchedulerApiWrapper(
                unwrappedSchedulerApi, threadUtils, getOrCreateMainThreadRunner());
      }

      // Build default component instances if necessary.
      if (protoExtensionProvider == null) {
        // Return an empty list of extensions by default.
        protoExtensionProvider = ArrayList::new;
      }
      FeedExtensionRegistry extensionRegistry = new FeedExtensionRegistry(protoExtensionProvider);
      if (clock == null) {
        clock = new SystemClockImpl();
      }
      TimingUtils timingUtils = new TimingUtils();
      TaskQueue taskQueue =
          new TaskQueue(singleThreadExecutor, getOrCreateMainThreadRunner(), clock, true);
      FeedStore store =
          new FeedStore(
              timingUtils,
              extensionRegistry,
              contentStorage,
              journalStorage,
              threadUtils,
              taskQueue,
              clock);

      lifecycleListener.registerObserver(store);

      if (protocolAdapter == null) {
        protocolAdapter = new FeedProtocolAdapter(timingUtils);
      }
      ActionReader actionReader =
          new FeedActionReader(store, clock, protocolAdapter, taskQueue, configuration);
      if (requestManager == null) {
        requestManager =
            new FeedRequestManager(
                configuration,
                networkClient,
                protocolAdapter,
                extensionRegistry,
                schedulerApi,
                taskQueue,
                timingUtils,
                threadUtils,
                actionReader,
                context,
                applicationInfo,
                getOrCreateMainThreadRunner());
      }
      if (sessionManager == null) {
        FeedSessionManagerFactory fsmFactory =
            new FeedSessionManagerFactory(
                taskQueue,
                store,
                timingUtils,
                threadUtils,
                protocolAdapter,
                requestManager,
                schedulerApi,
                configuration,
                clock,
                lifecycleListener);
        sessionManager = fsmFactory.create();
      }
      requestManager.setDefaultTriggerRefreshConsumerSupplier(
          sessionManager.getTriggerRefreshConsumer());
      ActionManager actionManager =
          new FeedActionManagerImpl(sessionManager, store, threadUtils, taskQueue);

      if (knownContentApi == null) {
        knownContentApi =
            new FeedKnownContentApi(sessionManager, getOrCreateMainThreadRunner(), threadUtils);
      }

      ClearAllListener clearAllListener =
          new ClearAllListener(taskQueue, sessionManager, store, threadUtils, lifecycleListener);
      return new FeedProcessScope(
          loggingApi,
          networkClient,
          Validators.checkNotNull(protocolAdapter),
          Validators.checkNotNull(requestManager),
          Validators.checkNotNull(sessionManager),
          store,
          timingUtils,
          threadUtils,
          taskQueue,
          getOrCreateMainThreadRunner(),
          lifecycleListener,
          clock,
          debugBehavior,
          actionManager,
          configuration,
          knownContentApi,
          extensionRegistry,
          clearAllListener);
    }
  }
}
