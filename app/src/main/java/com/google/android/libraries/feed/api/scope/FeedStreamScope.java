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
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionparser.ActionParserFactory;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi;
import com.google.android.libraries.feed.api.modelprovider.ModelProviderFactory;
import com.google.android.libraries.feed.api.protocoladapter.ProtocolAdapter;
import com.google.android.libraries.feed.api.sessionmanager.SessionManager;
import com.google.android.libraries.feed.api.stream.Stream;
import com.google.android.libraries.feed.basicstream.BasicStream;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.feedactionparser.FeedActionParserFactory;
import com.google.android.libraries.feed.feedmodelprovider.FeedModelProviderFactory;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.DebugBehavior;
import com.google.android.libraries.feed.host.imageloader.ImageLoaderApi;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.logging.LoggingApi;
import com.google.android.libraries.feed.host.offlineindicator.OfflineIndicatorApi;
import com.google.android.libraries.feed.host.stream.CardConfiguration;
import com.google.android.libraries.feed.host.stream.SnackbarApi;
import com.google.android.libraries.feed.host.stream.StreamConfiguration;
import com.google.android.libraries.feed.piet.host.CustomElementProvider;
import com.google.android.libraries.feed.piet.host.HostBindingProvider;
import java.util.ArrayList;

/** Per-stream instance of the feed library. */
public final class FeedStreamScope {

  public Stream getStream() {
    return stream;
  }

  public ModelProviderFactory getModelProviderFactory() {
    return modelProviderFactory;
  }

  /** A builder that creates a {@link FeedStreamScope}. */
  public static final class Builder {
    /** Construct this builder using {@link FeedProcessScope#createFeedStreamScopeBuilder} */
    Builder(
        Context context,
        ActionApi actionApi,
        ImageLoaderApi imageLoaderApi,
        LoggingApi loggingApi,
        ProtocolAdapter protocolAdapter,
        SessionManager sessionManager,
        ThreadUtils threadUtils,
        TimingUtils timingUtils,
        TaskQueue taskQueue,
        MainThreadRunner mainThreadRunner,
        Clock clock,
        DebugBehavior debugBehavior,
        StreamConfiguration streamConfiguration,
        CardConfiguration cardConfiguration,
        ActionManager actionManager,
        Configuration config,
        SnackbarApi snackbarApi,
        BasicLoggingApi basicLoggingApi,
        OfflineIndicatorApi offlineIndicatorApi,
        KnownContentApi knownContentApi) {
      this.context = context;
      this.actionApi = actionApi;
      this.imageLoaderApi = imageLoaderApi;
      this.loggingApi = loggingApi;
      this.protocolAdapter = protocolAdapter;
      this.sessionManager = sessionManager;
      this.threadUtils = threadUtils;
      this.timingUtils = timingUtils;
      this.taskQueue = taskQueue;
      this.mainThreadRunner = mainThreadRunner;
      this.streamConfiguration = streamConfiguration;
      this.cardConfiguration = cardConfiguration;
      this.clock = clock;
      this.debugBehavior = debugBehavior;
      this.actionManager = actionManager;
      this.config = config;
      this.snackbarApi = snackbarApi;
      this.basicLoggingApi = basicLoggingApi;
      this.offlineIndicatorApi = offlineIndicatorApi;
      this.knownContentApi = knownContentApi;
    }

    public Builder setActionParserFactory(ActionParserFactory actionParserFactory) {
      this.actionParserFactory = actionParserFactory;
      return this;
    }

    public Builder setStream(Stream stream) {
      this.stream = stream;
      return this;
    }

    public Builder setModelProviderFactory(ModelProviderFactory modelProviderFactory) {
      this.modelProviderFactory = modelProviderFactory;
      return this;
    }

    public Builder setCustomElementProvider(CustomElementProvider customElementProvider) {
      this.customElementProvider = customElementProvider;
      return this;
    }

    public Builder setHostBindingProvider(HostBindingProvider hostBindingProvider) {
      this.hostBindingProvider = hostBindingProvider;
      return this;
    }

    public FeedStreamScope build() {
      if (modelProviderFactory == null) {
        modelProviderFactory =
            new FeedModelProviderFactory(
                sessionManager, threadUtils, timingUtils, taskQueue, mainThreadRunner, config);
      }
      if (actionParserFactory == null) {
        actionParserFactory = new FeedActionParserFactory(protocolAdapter);
      }
      if (stream == null) {
        stream =
            new BasicStream(
                context,
                streamConfiguration,
                cardConfiguration,
                imageLoaderApi,
                Validators.checkNotNull(actionParserFactory),
                actionApi,
                customElementProvider,
                debugBehavior,
                threadUtils,
                /* headers = */ new ArrayList<>(0),
                clock,
                Validators.checkNotNull(modelProviderFactory),
                hostBindingProvider,
                actionManager,
                config,
                snackbarApi,
                basicLoggingApi,
                offlineIndicatorApi,
                mainThreadRunner,
                knownContentApi);
      }
      return new FeedStreamScope(
          Validators.checkNotNull(stream), Validators.checkNotNull(modelProviderFactory));
    }

    // Required external dependencies.
    private final Context context;
    private final ActionApi actionApi;
    private final ImageLoaderApi imageLoaderApi;

    @SuppressWarnings("unused")
    private final LoggingApi loggingApi;

    private final ProtocolAdapter protocolAdapter;
    private final SessionManager sessionManager;
    private final ThreadUtils threadUtils;
    private final TimingUtils timingUtils;
    private final TaskQueue taskQueue;
    private final MainThreadRunner mainThreadRunner;
    private final Clock clock;
    private final ActionManager actionManager;
    private CardConfiguration cardConfiguration;
    private StreamConfiguration streamConfiguration;
    private final DebugBehavior debugBehavior;
    private final Configuration config;
    private final SnackbarApi snackbarApi;
    private final BasicLoggingApi basicLoggingApi;
    private final OfflineIndicatorApi offlineIndicatorApi;
    private final KnownContentApi knownContentApi;

    // Optional internal components to override the default implementations.
    /*@MonotonicNonNull*/ private ActionParserFactory actionParserFactory;
    /*@MonotonicNonNull*/ private ModelProviderFactory modelProviderFactory;
    /*@MonotonicNonNull*/ private Stream stream;
    /*@MonotonicNonNull*/ private CustomElementProvider customElementProvider;
    /*@MonotonicNonNull*/ private HostBindingProvider hostBindingProvider;
  }

  private FeedStreamScope(Stream stream, ModelProviderFactory modelProviderFactory) {
    this.stream = stream;
    this.modelProviderFactory = modelProviderFactory;
  }

  private final Stream stream;
  private final ModelProviderFactory modelProviderFactory;
}
