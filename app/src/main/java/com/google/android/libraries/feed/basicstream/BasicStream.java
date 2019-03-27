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

package com.google.android.libraries.feed.basicstream;

import static com.google.android.libraries.feed.common.Validators.checkNotNull;
import static com.google.android.libraries.feed.common.Validators.checkState;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Base64;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionparser.ActionParserFactory;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.knowncontent.KnownContentApi;
import com.google.android.libraries.feed.api.modelprovider.ModelError;
import com.google.android.libraries.feed.api.modelprovider.ModelError.ErrorType;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider.State;
import com.google.android.libraries.feed.api.modelprovider.ModelProviderFactory;
import com.google.android.libraries.feed.api.modelprovider.ModelProviderObserver;
import com.google.android.libraries.feed.api.stream.ContentChangedListener;
import com.google.android.libraries.feed.api.stream.Header;
import com.google.android.libraries.feed.api.stream.ScrollListener;
import com.google.android.libraries.feed.api.stream.Stream;
import com.google.android.libraries.feed.basicstream.internal.StreamItemAnimator;
import com.google.android.libraries.feed.basicstream.internal.StreamItemTouchCallbacks;
import com.google.android.libraries.feed.basicstream.internal.StreamRecyclerViewAdapter;
import com.google.android.libraries.feed.basicstream.internal.StreamSavedInstanceStateProto.StreamSavedInstanceState;
import com.google.android.libraries.feed.basicstream.internal.drivers.StreamDriver;
import com.google.android.libraries.feed.basicstream.internal.scroll.ScrollRestorer;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.Clock;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.android.libraries.feed.host.config.DebugBehavior;
import com.google.android.libraries.feed.host.imageloader.ImageLoaderApi;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.offlineindicator.OfflineIndicatorApi;
import com.google.android.libraries.feed.host.stream.CardConfiguration;
import com.google.android.libraries.feed.host.stream.SnackbarApi;
import com.google.android.libraries.feed.host.stream.StreamConfiguration;
import com.google.android.libraries.feed.piet.PietManager;
import com.google.android.libraries.feed.piet.host.CustomElementProvider;
import com.google.android.libraries.feed.piet.host.HostBindingProvider;
import com.google.android.libraries.feed.sharedstream.contentchanged.StreamContentChangedListener;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.ContextMenuManager;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.ContextMenuManagerImpl;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.FloatingContextMenuManager;
import com.google.android.libraries.feed.sharedstream.deepestcontenttracker.DeepestContentTracker;
import com.google.android.libraries.feed.sharedstream.offlinemonitor.StreamOfflineMonitor;
import com.google.android.libraries.feed.sharedstream.piet.PietAssetProvider;
import com.google.android.libraries.feed.sharedstream.piet.PietCustomElementProvider;
import com.google.android.libraries.feed.sharedstream.piet.PietHostBindingProvider;
import com.google.android.libraries.feed.sharedstream.proto.ScrollStateProto.ScrollState;
import com.google.android.libraries.feed.sharedstream.publicapi.menumeasurer.MenuMeasurer;
import com.google.android.libraries.feed.sharedstream.scroll.StreamScrollMonitor;
import com.google.protobuf.InvalidProtocolBufferException;

import org.chromium.chrome.R;

import java.util.List;

/**
 * A basic implementation of a Feed {@link Stream} that is just able to render a vertical stream of
 * cards.
 */
public class BasicStream implements Stream, ModelProviderObserver, OnLayoutChangeListener {

  private static final String TAG = "BasicStream";

  @VisibleForTesting static final String KEY_STREAM_STATE = "stream-state";
  @VisibleForTesting static final long DEFAULT_LOGGING_IMMEDIATE_CONTENT_THRESHOLD_MS = 1000;
  @VisibleForTesting static final long MINIMUM_SPINNER_SHOW_TIME = 500;
  @VisibleForTesting static final long MINIMUM_TIME_BEFORE_SHOWING_SPINNER = 500;

  private final CardConfiguration cardConfiguration;
  private final Clock clock;
  private final ThreadUtils threadUtils;
  private final PietManager pietManager;
  private final ModelProviderFactory modelProviderFactory;
  private final ActionParserFactory actionParserFactory;
  private final ActionApi actionApi;
  private final ActionManager actionManager;
  private final Configuration configuration;
  private final SnackbarApi snackbarApi;
  private final StreamContentChangedListener streamContentChangedListener;
  private final DeepestContentTracker deepestContentTracker;
  private final BasicLoggingApi basicLoggingApi;
  private final long immediateContentThreshold;
  private final StreamOfflineMonitor streamOfflineMonitor;
  private final MainThreadRunner mainThreadRunner;
  private final KnownContentApi knownContentApi;

  private RecyclerView recyclerView;
  private ContextMenuManager contextMenuManager;
  private Context context;
  private List<Header> headers;
  private StreamConfiguration streamConfiguration;
  private StreamRecyclerViewAdapter adapter;
  private StreamScrollMonitor streamScrollMonitor;
  private ScrollRestorer scrollRestorer;
  private long sessionStartTimestamp;
  private long initialLoadingSpinnerStartTime;
  private boolean isInitialLoad = true;
  private boolean isRestoring;
  private boolean isDestroyed;
  private boolean isStreamContentVisible = true;

  @LoggingState private int loggingState = LoggingState.STARTING;

  /*@MonotonicNonNull*/ private ModelProvider modelProvider;
  /*@MonotonicNonNull*/ private StreamDriver streamDriver;

  /*@Nullable*/ private String savedSessionToken;
  private StreamItemAnimator itemAnimator;

  public BasicStream(
      Context context,
      StreamConfiguration streamConfiguration,
      CardConfiguration cardConfiguration,
      ImageLoaderApi imageLoaderApi,
      ActionParserFactory actionParserFactory,
      ActionApi actionApi,
      /*@Nullable*/ CustomElementProvider customElementProvider,
      DebugBehavior debugBehavior,
      ThreadUtils threadUtils,
      List<Header> headers,
      Clock clock,
      ModelProviderFactory modelProviderFactory,
      /*@Nullable*/ HostBindingProvider hostBindingProvider,
      ActionManager actionManager,
      Configuration configuration,
      SnackbarApi snackbarApi,
      BasicLoggingApi basicLoggingApi,
      OfflineIndicatorApi offlineIndicatorApi,
      MainThreadRunner mainThreadRunner,
      KnownContentApi knownContentApi) {
    this.cardConfiguration = cardConfiguration;
    this.clock = clock;
    this.threadUtils = threadUtils;
    this.streamOfflineMonitor = new StreamOfflineMonitor(offlineIndicatorApi);
    this.context = context;
    this.headers = headers;
    this.modelProviderFactory = modelProviderFactory;
    this.streamConfiguration = streamConfiguration;
    this.actionParserFactory = actionParserFactory;
    this.actionApi = actionApi;
    this.actionManager = actionManager;
    this.configuration = configuration;
    this.snackbarApi = snackbarApi;
    this.mainThreadRunner = mainThreadRunner;
    this.streamContentChangedListener = createStreamContentChangedListener();
    this.deepestContentTracker = new DeepestContentTracker();
    this.basicLoggingApi = basicLoggingApi;
    this.immediateContentThreshold =
        configuration.getValueOrDefault(
            ConfigKey.LOGGING_IMMEDIATE_CONTENT_THRESHOLD_MS,
            DEFAULT_LOGGING_IMMEDIATE_CONTENT_THRESHOLD_MS);
    this.knownContentApi = knownContentApi;

    this.pietManager =
        createPietManager(
            context,
            cardConfiguration,
            imageLoaderApi,
            customElementProvider,
            debugBehavior,
            clock,
            hostBindingProvider,
            streamOfflineMonitor,
            configuration);
  }

  @VisibleForTesting
  PietManager createPietManager(
      /*@UnderInitialization*/ BasicStream this,
      Context context,
      CardConfiguration cardConfiguration,
      ImageLoaderApi imageLoaderApi,
      /*@Nullable*/ CustomElementProvider customElementProvider,
      DebugBehavior debugBehavior,
      Clock clock,
      /*@Nullable*/ HostBindingProvider hostBindingProvider,
      StreamOfflineMonitor streamOfflineMonitor,
      Configuration configuration) {
    return new PietManager(
        debugBehavior,
        new PietAssetProvider(
            imageLoaderApi,
            cardConfiguration,
            clock,
            configuration.getValueOrDefault(ConfigKey.FADE_IMAGE_THRESHOLD_MS, 80)),
        new PietCustomElementProvider(context, customElementProvider),
        new PietHostBindingProvider(hostBindingProvider, streamOfflineMonitor),
        clock);
  }

  @Override
  public void onCreate(/*@Nullable*/ Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      onCreate((String) null);
      return;
    }

    onCreate(savedInstanceState.getString(KEY_STREAM_STATE));
  }

  @Override
  public void onCreate(/*@Nullable*/ String savedInstanceState) {
    checkState(recyclerView == null, "Can't call onCreate() multiple times.");
    setupRecyclerView();
    streamScrollMonitor =
        createStreamScrollMonitor(recyclerView, streamContentChangedListener, mainThreadRunner);

    if (savedInstanceState == null) {
      scrollRestorer = createScrollRestorer(configuration, recyclerView, streamScrollMonitor, null);
      return;
    }

    try {
      StreamSavedInstanceState streamSavedInstanceState =
          StreamSavedInstanceState.parseFrom(Base64.decode(savedInstanceState, Base64.DEFAULT));

      if (streamSavedInstanceState.hasSessionToken()) {
        savedSessionToken = streamSavedInstanceState.getSessionToken();
      }

      scrollRestorer =
          createScrollRestorer(
              configuration,
              recyclerView,
              streamScrollMonitor,
              streamSavedInstanceState.getScrollState());
    } catch (IllegalArgumentException | InvalidProtocolBufferException e) {
      Logger.wtf(TAG, "Could not parse saved instance state String.");
      scrollRestorer = createScrollRestorer(configuration, recyclerView, streamScrollMonitor, null);
    }
  }

  @Override
  public void onShow() {
    // Only create model provider if Stream content is visible.
    if (isStreamContentVisible) {
      createModelProviderAndStreamDriver();
    } else {
      // If Stream content is not visible, we will not create the StreamDriver and restore the
      // scroll position automatically. So we try to restore the scroll position before.
      scrollRestorer.maybeRestoreScroll();
    }
    adapter.setShown(true);
  }

  @Override
  public void onActive() {}

  @Override
  public void onInactive() {}

  @Override
  public void onHide() {
    adapter.setShown(false);
    contextMenuManager.dismissPopup();
  }

  @Override
  public void onDestroy() {
    if (isDestroyed) {
      Logger.e(TAG, "onDestroy() called multiple times.");
      return;
    }
    adapter.onDestroy();
    recyclerView.removeOnLayoutChangeListener(this);
    if (modelProvider != null) {
      modelProvider.unregisterObserver(this);
      modelProvider.detachModelProvider();
    }
    if (streamDriver != null) {
      streamDriver.onDestroy();
    }

    isDestroyed = true;
  }

  @Override
  public Bundle getSavedInstanceState() {
    Bundle bundle = new Bundle();
    bundle.putString(KEY_STREAM_STATE, getSavedInstanceStateString());
    return bundle;
  }

  @Override
  public String getSavedInstanceStateString() {
    StreamSavedInstanceState.Builder builder = StreamSavedInstanceState.newBuilder();
    if (modelProvider != null && modelProvider.getSessionToken() != null) {
      builder.setSessionToken(checkNotNull(modelProvider.getSessionToken()));
    }

    ScrollState scrollState =
        scrollRestorer.getScrollStateForScrollRestore(adapter.getHeaderCount());
    if (scrollState != null) {
      builder.setScrollState(scrollState);
    }

    return convertStreamSavedInstanceStateToString(builder.build());
  }

  @Override
  public View getView() {
    checkState(recyclerView != null, "Must call onCreate() before getView()");
    return recyclerView;
  }

  @VisibleForTesting
  StreamRecyclerViewAdapter getAdapter() {
    return adapter;
  }

  @Override
  public void setHeaderViews(List<Header> headers) {
    Logger.i(
        TAG,
        "Setting %s header views, currently have %s headers",
        headers.size(),
        this.headers.size());

    this.headers = headers;
    adapter.setHeaders(headers);
  }

  @Override
  public void setStreamContentVisibility(boolean visible) {
    checkNotNull(adapter, "onCreate must be called before setStreamContentVisibility");
    // If Stream content was previously not visible, ModelProvider might need to be created.
    if (!isStreamContentVisible && visible && modelProvider == null) {
      createModelProviderAndStreamDriver();
    }

    isStreamContentVisible = visible;

    itemAnimator.setStreamVisibility(visible);
    adapter.setStreamContentVisible(visible);
  }

  @Override
  public void trim() {
    pietManager.purgeRecyclerPools();
    recyclerView.getRecycledViewPool().clear();
  }

  @Override
  public void smoothScrollBy(int dx, int dy) {
    recyclerView.smoothScrollBy(dx, dy);
  }

  @Override
  public int getChildTopAt(int position) {
    if (!isChildAtPositionVisible(position)) {
      return POSITION_NOT_KNOWN;
    }

    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (layoutManager == null) {
      return POSITION_NOT_KNOWN;
    }

    View view = layoutManager.findViewByPosition(position);
    if (view == null) {
      return POSITION_NOT_KNOWN;
    }

    return view.getTop();
  }

  @Override
  public boolean isChildAtPositionVisible(int position) {
    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (layoutManager == null) {
      return false;
    }

    int firstItemPosition = layoutManager.findFirstVisibleItemPosition();
    int lastItemPosition = layoutManager.findLastVisibleItemPosition();
    if (firstItemPosition == RecyclerView.NO_POSITION
        || lastItemPosition == RecyclerView.NO_POSITION) {
      return false;
    }

    if (position < firstItemPosition || position > lastItemPosition) {
      return false;
    }

    return true;
  }

  @Override
  public void addScrollListener(ScrollListener listener) {
    streamScrollMonitor.addScrollListener(listener);
  }

  @Override
  public void removeScrollListener(ScrollListener listener) {
    streamScrollMonitor.removeScrollListener(listener);
  }

  @Override
  public void addOnContentChangedListener(ContentChangedListener listener) {
    streamContentChangedListener.addContentChangedListener(listener);
  }

  @Override
  public void removeOnContentChangedListener(ContentChangedListener listener) {
    streamContentChangedListener.removeContentChangedListener(listener);
  }

  @Override
  public void triggerRefresh() {
    if (streamDriver == null || modelProvider == null) {
      Logger.w(
          TAG,
          "Refresh requested before Stream was shown.  Scheduler should be used instead "
              + "in this instance.");
      return;
    }

    // This invalidates the modelProvider, which results in onSessionFinished() then
    // onSessionStart() being called, leading to recreating the entire stream.
    streamDriver.showZeroState(/* showSpinner= */ true);
    modelProvider.triggerRefresh();
  }

  @Override
  public void onLayoutChange(
      View v,
      int left,
      int top,
      int right,
      int bottom,
      int oldLeft,
      int oldTop,
      int oldRight,
      int oldBottom) {
    if ((oldLeft != 0 && left != oldLeft) || (oldRight != 0 && right != oldRight)) {
      checkNotNull(adapter, "onCreate must be called before so that adapter is set.").rebind();
    }

    contextMenuManager.dismissPopup();
  }

  private void setupRecyclerView() {
    adapter =
        createRecyclerViewAdapter(
            context,
            cardConfiguration,
            pietManager,
            deepestContentTracker,
            streamContentChangedListener,
            configuration);
    adapter.setHeaders(headers);
    recyclerView = new RecyclerView(context);
    recyclerView.setId(R.id.feed_stream_recycler_view);
    recyclerView.setLayoutManager(createRecyclerViewLayoutManager(context));
    contextMenuManager = createContextMenuManager(recyclerView, new MenuMeasurer(context));
    new ItemTouchHelper(new StreamItemTouchCallbacks()).attachToRecyclerView(recyclerView);
    recyclerView.setAdapter(adapter);
    recyclerView.setClipToPadding(false);
    if (VERSION.SDK_INT > VERSION_CODES.JELLY_BEAN) {
      recyclerView.setPaddingRelative(
          streamConfiguration.getPaddingStart(),
          streamConfiguration.getPaddingTop(),
          streamConfiguration.getPaddingEnd(),
          streamConfiguration.getPaddingBottom());
    } else {
      recyclerView.setPadding(
          streamConfiguration.getPaddingStart(),
          streamConfiguration.getPaddingTop(),
          streamConfiguration.getPaddingEnd(),
          streamConfiguration.getPaddingBottom());
    }

    itemAnimator = new StreamItemAnimator(streamContentChangedListener);
    itemAnimator.setStreamVisibility(isStreamContentVisible);

    recyclerView.setItemAnimator(itemAnimator);
    recyclerView.addOnLayoutChangeListener(this);
  }

  private void updateAdapterAfterSessionStart(ModelProvider modelProvider) {
    if (streamDriver != null) {
      streamDriver.onDestroy();
    }

    streamDriver =
        createStreamDriver(
            actionApi,
            actionManager,
            actionParserFactory,
            modelProvider,
            threadUtils,
            clock,
            configuration,
            context,
            snackbarApi,
            streamContentChangedListener,
            scrollRestorer,
            basicLoggingApi,
            streamOfflineMonitor,
            knownContentApi,
            contextMenuManager,
            isRestoring,
            /* isInitialLoad= */ false);

    // If after starting a new session the Stream is still empty, we should show the zero state.
    if (streamDriver.getLeafFeatureDrivers().isEmpty()) {
      streamDriver.showZeroState(/* showSpinner= */ false);
    }
    adapter.setDriver(streamDriver);
    if (loggingState == LoggingState.STARTING
        && modelProvider.getCurrentState() == State.READY
        && modelProvider.getRootFeature() == null) {
      basicLoggingApi.onOpenedWithNoContent();
      loggingState = LoggingState.LOGGED_NO_CONTENT;
    }

    deepestContentTracker.reset();
  }

  @Override
  public void onSessionStart() {
    threadUtils.checkMainThread();
    ModelProvider localModelProvider =
        checkNotNull(modelProvider, "Model Provider must be set if a session is active");
    // On initial load, if a loading spinner is currently being shown, the spinner must be shown for
    // at least the time specified in MINIMUM_SPINNER_SHOW_TIME.
    if (isInitialLoad && initialLoadingSpinnerStartTime != 0L) {
      long spinnerDisplayTime = clock.currentTimeMillis() - initialLoadingSpinnerStartTime;
      // If MINIMUM_SPINNER_SHOW_TIME has elapsed, the new content can be shown immediately.
      if (spinnerDisplayTime >= MINIMUM_SPINNER_SHOW_TIME) {
        updateAdapterAfterSessionStart(localModelProvider);
        logContent();
      } else {
        // If MINIMUM_SPINNER_SHOW_TIME has not elapsed, the new content should only be shown once
        // the remaining time has been fulfilled.
        mainThreadRunner.executeWithDelay(
            TAG + " onSessionStart",
            () -> {
              // Only show content if model providers are the same. If they are different, this
              // indicates that the session finished before the spinner show time elapsed.
              if (modelProvider == localModelProvider) {
                updateAdapterAfterSessionStart(localModelProvider);
                logContent();
              }
            },
            MINIMUM_SPINNER_SHOW_TIME - spinnerDisplayTime);
      }
    } else {
      updateAdapterAfterSessionStart(localModelProvider);
      logContent();
    }
    isInitialLoad = false;
  }

  private void logContent() {
    if (loggingState == LoggingState.STARTING) {
      if (!checkNotNull(streamDriver).hasContent()
          && clock.currentTimeMillis() - sessionStartTimestamp > immediateContentThreshold) {
        basicLoggingApi.onOpenedWithNoImmediateContent();
      }

      long timeToPopulateMs = clock.currentTimeMillis() - sessionStartTimestamp;
      basicLoggingApi.onOpenedWithContent(
          (int) timeToPopulateMs, checkNotNull(streamDriver).getLeafFeatureDrivers().size());
      // onOpenedWithContent should only be logged the first time the Stream is opened up.
      loggingState = LoggingState.LOGGED_CONTENT_SHOWN;
    }
  }

  @Override
  public void onSessionFinished() {
    if (isDestroyed) {
      // This seems to be getting called after onDestroy(), resulting in unregistering from the
      // ModelProvider twice, which causes a crash.
      Logger.e(TAG, "onSessionFinished called after onDestroy()");
      return;
    }

    // Our previous session isn't valid anymore.  There are some circumstances we could probably
    // restore our scroll (say if scroll was in headers), other times, if we were to restore scroll
    // it would be to a card which is no longer present.  For simplicity just abandon scroll
    // restoring for now.  We can improve logic if this doesn't prove to be sufficient enough.
    scrollRestorer.abandonRestoringScroll();

    if (streamDriver != null) {
      streamDriver.showZeroState(/* showSpinner= */ true);
    }
    isRestoring = false;

    if (modelProvider != null) {
      modelProvider.unregisterObserver(this);
    }
    modelProvider = modelProviderFactory.createNew(deepestContentTracker);
    modelProvider.registerObserver(this);
  }

  @Override
  public void onError(ModelError modelError) {
    if (modelError.getErrorType() != ErrorType.NO_CARDS_ERROR) {
      Logger.wtf(TAG, "Not expecting non NO_CARDS_ERROR type.");
    }

    if (loggingState == LoggingState.STARTING) {
      basicLoggingApi.onOpenedWithNoContent();
      loggingState = LoggingState.LOGGED_NO_CONTENT;
    }

    scrollRestorer.abandonRestoringScroll();
    if (streamDriver != null) {
      streamDriver.showZeroState(/* showSpinner= */ false);
    }
  }

  private void createModelProviderAndStreamDriver() {
    if (modelProvider == null) {

      // For nullness checker
      ModelProvider localModelProvider = null;
      String localSavedSessionToken = savedSessionToken;
      if (localSavedSessionToken != null) {
        isRestoring = true;
        Logger.d(TAG, "Attempting to restoring session token: %s.", localSavedSessionToken);
        localModelProvider = modelProviderFactory.create(localSavedSessionToken);
      }

      if (localModelProvider == null) {
        // If a session is no longer valid then a ModelProvider will not have been created above.
        Logger.d(TAG, "Creating new session for showing.");
        localModelProvider = modelProviderFactory.createNew(deepestContentTracker);
        sessionStartTimestamp = clock.currentTimeMillis();
      }

      modelProvider = localModelProvider;
      modelProvider.registerObserver(this);
    }

    if (streamDriver == null) {
      // If the ModelProvider is not ready we don't want to restore the Stream at all.  Instead we
      // need to wait for it to become active and we can reset the StreamDriver with the correct
      // scroll restorer in order to finally restore scroll position.
      ScrollRestorer initialScrollRestorer =
          modelProvider.getCurrentState() == State.READY
              ? scrollRestorer
              : createNonRestoringScrollRestorer(configuration, recyclerView, streamScrollMonitor);

      streamDriver =
          createStreamDriver(
              actionApi,
              actionManager,
              actionParserFactory,
              modelProvider,
              threadUtils,
              clock,
              configuration,
              context,
              snackbarApi,
              streamContentChangedListener,
              initialScrollRestorer,
              basicLoggingApi,
              streamOfflineMonitor,
              knownContentApi,
              contextMenuManager,
              isRestoring,
              isInitialLoad);
      if (isInitialLoad && !isRestoring) {
        mainThreadRunner.executeWithDelay(
            TAG + " onShow",
            () -> {
              if (isInitialLoad) {
                checkNotNull(streamDriver).showZeroState(/* showSpinner= */ true);
                initialLoadingSpinnerStartTime = clock.currentTimeMillis();
              }
            },
            MINIMUM_TIME_BEFORE_SHOWING_SPINNER);
      } else if (!isRestoring) {
        streamDriver.showZeroState(/* showSpinner= */ true);
      }
      adapter.setDriver(streamDriver);
    }
  }

  private String convertStreamSavedInstanceStateToString(
      StreamSavedInstanceState savedInstanceState) {
    return Base64.encodeToString(savedInstanceState.toByteArray(), Base64.DEFAULT);
  }

  @VisibleForTesting
  StreamDriver createStreamDriver(
      ActionApi actionApi,
      ActionManager actionManager,
      ActionParserFactory actionParserFactory,
      ModelProvider modelProvider,
      ThreadUtils threadUtils,
      Clock clock,
      Configuration configuration,
      Context context,
      SnackbarApi snackbarApi,
      ContentChangedListener contentChangedListener,
      ScrollRestorer scrollRestorer,
      BasicLoggingApi basicLoggingApi,
      StreamOfflineMonitor streamOfflineMonitor,
      KnownContentApi knownContentApi,
      ContextMenuManager contextMenuManager,
      boolean restoring,
      boolean isInitialLoad) {
    return new StreamDriver(
        actionApi,
        actionManager,
        actionParserFactory,
        modelProvider,
        threadUtils,
        clock,
        configuration,
        context,
        snackbarApi,
        contentChangedListener,
        scrollRestorer,
        basicLoggingApi,
        streamOfflineMonitor,
        knownContentApi,
        contextMenuManager,
        restoring,
        isInitialLoad);
  }

  @VisibleForTesting
  StreamRecyclerViewAdapter createRecyclerViewAdapter(
      Context context,
      CardConfiguration cardConfiguration,
      PietManager pietManager,
      DeepestContentTracker deepestContentTracker,
      StreamContentChangedListener streamContentChangedListener,
      Configuration configuration) {
    return new StreamRecyclerViewAdapter(
        context,
        cardConfiguration,
        pietManager,
        deepestContentTracker,
        streamContentChangedListener,
        configuration);
  }

  @VisibleForTesting
  StreamScrollMonitor createStreamScrollMonitor(
      RecyclerView recyclerView,
      ContentChangedListener contentChangedListener,
      MainThreadRunner mainThreadRunner) {
    return new StreamScrollMonitor(recyclerView, contentChangedListener, mainThreadRunner);
  }

  @VisibleForTesting
  LinearLayoutManager createRecyclerViewLayoutManager(Context context) {
    return new LinearLayoutManager(context);
  }

  @VisibleForTesting
  StreamContentChangedListener createStreamContentChangedListener(
      /*@UnderInitialization*/ BasicStream this) {
    return new StreamContentChangedListener();
  }

  @VisibleForTesting
  ScrollRestorer createScrollRestorer(
      Configuration configuration,
      RecyclerView recyclerView,
      StreamScrollMonitor streamScrollMonitor,
      /*@Nullable*/ ScrollState scrollState) {
    return new ScrollRestorer(configuration, recyclerView, streamScrollMonitor, scrollState);
  }

  @VisibleForTesting
  ScrollRestorer createNonRestoringScrollRestorer(
      Configuration configuration,
      RecyclerView recyclerView,
      StreamScrollMonitor streamScrollMonitor) {
    return ScrollRestorer.nonRestoringRestorer(configuration, recyclerView, streamScrollMonitor);
  }

  @VisibleForTesting
  ContextMenuManager createContextMenuManager(
      RecyclerView recyclerView, MenuMeasurer menuMeasurer) {
    ContextMenuManager manager;
    if (VERSION.SDK_INT > VERSION_CODES.M) {
      manager = new ContextMenuManagerImpl(menuMeasurer, context);
    } else {
      manager = new FloatingContextMenuManager(context);
    }
    manager.setView(recyclerView);
    return manager;
  }

  @IntDef({
    LoggingState.STARTING,
    LoggingState.LOGGED_NO_CONTENT,
    LoggingState.LOGGED_CONTENT_SHOWN
  })
  @interface LoggingState {
    int STARTING = 0;
    int LOGGED_NO_CONTENT = 1;
    int LOGGED_CONTENT_SHOWN = 2;
  }
}
