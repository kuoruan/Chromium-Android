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

package com.google.android.libraries.feed.feedrequestmanager;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import com.google.android.libraries.feed.api.actionmanager.ActionReader;
import com.google.android.libraries.feed.api.common.DismissActionWithSemanticProperties;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.protocoladapter.ProtocolAdapter;
import com.google.android.libraries.feed.api.requestmanager.RequestManager;
import com.google.android.libraries.feed.api.store.ActionMutation.ActionType;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.locale.LocaleUtils;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.protoextensions.FeedExtensionRegistry;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.feedrequestmanager.internal.Utils;
import com.google.android.libraries.feed.host.config.ApplicationInfo;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.android.libraries.feed.host.network.HttpRequest;
import com.google.android.libraries.feed.host.network.HttpRequest.HttpMethod;
import com.google.android.libraries.feed.host.network.NetworkClient;
import com.google.android.libraries.feed.host.scheduler.SchedulerApi;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.search.now.feed.client.StreamDataProto.StreamDataOperation;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import com.google.search.now.wire.feed.ActionTypeProto;
import com.google.search.now.wire.feed.CapabilityProto.Capability;
import com.google.search.now.wire.feed.ClientInfoProto.ClientInfo;
import com.google.search.now.wire.feed.ClientInfoProto.ClientInfo.PlatformType;
import com.google.search.now.wire.feed.ContentIdProto.ContentId;
import com.google.search.now.wire.feed.FeedActionQueryDataProto.Action;
import com.google.search.now.wire.feed.FeedActionQueryDataProto.FeedActionQueryData;
import com.google.search.now.wire.feed.FeedActionQueryDataProto.FeedActionQueryDataItem;
import com.google.search.now.wire.feed.FeedQueryProto.FeedQuery;
import com.google.search.now.wire.feed.FeedQueryProto.FeedQuery.RequestReason;
import com.google.search.now.wire.feed.FeedRequestProto.FeedRequest;
import com.google.search.now.wire.feed.FeedRequestProto.FeedRequest.Builder;
import com.google.search.now.wire.feed.RequestProto.Request;
import com.google.search.now.wire.feed.RequestProto.Request.RequestVersion;
import com.google.search.now.wire.feed.ResponseProto.Response;
import com.google.search.now.wire.feed.SemanticPropertiesProto.SemanticProperties;
import com.google.search.now.wire.feed.VersionProto.Version;
import com.google.search.now.wire.feed.VersionProto.Version.Architecture;
import com.google.search.now.wire.feed.VersionProto.Version.BuildType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Default implementation of RequestManager. */
public final class FeedRequestManager implements RequestManager {
  private static final String TAG = "FeedRequestManager";

  public static final String MOTHERSHIP_PARAM_PAYLOAD = "reqpld";
  public static final String LOCALE_PARAM = "hl";
  private static final String MOTHERSHIP_PARAM_FORMAT = "fmt";
  private static final String MOTHERSHIP_VALUE_BINARY = "bin";

  private final Configuration configuration;
  private final NetworkClient networkClient;
  private final ProtocolAdapter protocolAdapter;
  private final FeedExtensionRegistry extensionRegistry;
  private final SchedulerApi scheduler;
  private final TaskQueue taskQueue;
  private final TimingUtils timingUtils;
  private final ThreadUtils threadUtils;
  private final ActionReader actionReader;
  private final Context context;
  private final MainThreadRunner mainThreadRunner;
  private ApplicationInfo applicationInfo;

  /*@Nullable*/
  private Supplier<Consumer<Result<List<StreamDataOperation>>>>
      defaultTriggerRefreshConsumerSupplier;

  public FeedRequestManager(
      Configuration configuration,
      NetworkClient networkClient,
      ProtocolAdapter protocolAdapter,
      FeedExtensionRegistry extensionRegistry,
      SchedulerApi scheduler,
      TaskQueue taskQueue,
      TimingUtils timingUtils,
      ThreadUtils threadUtils,
      ActionReader actionReader,
      Context context,
      ApplicationInfo applicationInfo,
      MainThreadRunner mainThreadRunner) {
    this.configuration = configuration;
    this.networkClient = networkClient;
    this.protocolAdapter = protocolAdapter;
    this.extensionRegistry = extensionRegistry;
    this.scheduler = scheduler;
    this.taskQueue = taskQueue;
    this.timingUtils = timingUtils;
    this.threadUtils = threadUtils;
    this.actionReader = actionReader;
    this.context = context;
    this.applicationInfo = applicationInfo;
    this.mainThreadRunner = mainThreadRunner;
  }

  @Override
  public void loadMore(
      StreamToken streamToken, Consumer<Result<List<StreamDataOperation>>> consumer) {
    threadUtils.checkMainThread();
    taskQueue.execute(
        "requestManagerLoadMore",
        TaskType.IMMEDIATE, // always run requests as soon as possible
        () -> {
          Logger.i(TAG, "Task: FeedRequestManager LoadMore");
          ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
          RequestBuilder request = newDefaultRequest().setPageToken(streamToken.getNextPageToken());
          executeRequest(request, consumer);
          timeTracker.stop(
              "task", "FeedRequestManager LoadMore", "token", streamToken.getNextPageToken());
        });
  }

  @Override
  public void triggerRefresh(
      RequestReason reason, Consumer<Result<List<StreamDataOperation>>> consumer) {
    Logger.i(TAG, "trigger refresh %s", reason);
    RequestBuilder request = newDefaultRequest();

    if (threadUtils.isMainThread()) {
      // This will make a new request, it should invalidate the existing head to delay everything
      // until the response is obtained.
      taskQueue.execute(
          "requestManager.TriggerRefresh",
          TaskType.HEAD_INVALIDATE,
          () -> executeRequest(request, consumer));
    } else {
      executeRequest(request, consumer);
    }
  }

  @Override
  public void triggerRefresh(RequestReason reason) {
    if (defaultTriggerRefreshConsumerSupplier == null) {
      throw new IllegalStateException(
          "Must set defaultTriggerRefreshConsumerSupplier before calling "
              + "triggerRefresh(RequestReason).");
    }
    triggerRefresh(reason, defaultTriggerRefreshConsumerSupplier.get());
  }

  @Override
  public void setDefaultTriggerRefreshConsumerSupplier(
      Supplier<Consumer<Result<List<StreamDataOperation>>>> consumerSupplier) {
    this.defaultTriggerRefreshConsumerSupplier = consumerSupplier;
  }

  private RequestBuilder newDefaultRequest() {
    return new RequestBuilder(getLocale(), applicationInfo, configuration);
  }

  private void executeRequest(
      RequestBuilder requestBuilder, Consumer<Result<List<StreamDataOperation>>> consumer) {
    threadUtils.checkNotMainThread();
    Result<List<DismissActionWithSemanticProperties>> dismissActionsResult =
        actionReader.getDismissActionsWithSemanticProperties();
    if (dismissActionsResult.isSuccessful()) {
      requestBuilder.setActions(dismissActionsResult.getValue());
      Logger.i(TAG, "Dismiss actions in request: %s", dismissActionsResult.getValue());
    } else {
      Logger.e(TAG, "Error fetching dismiss actions");
    }

    String endpoint = configuration.getValueOrDefault(ConfigKey.FEED_SERVER_ENDPOINT, "");
    Uri.Builder uriBuilder = Uri.parse(endpoint).buildUpon();
    uriBuilder.appendQueryParameter(
        MOTHERSHIP_PARAM_PAYLOAD,
        Base64.encodeToString(
            requestBuilder.build().toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP));
    uriBuilder.appendQueryParameter(MOTHERSHIP_PARAM_FORMAT, MOTHERSHIP_VALUE_BINARY);
    uriBuilder.appendQueryParameter(LOCALE_PARAM, getLocale().toString());

    @HttpMethod
    String httpMethod =
        configuration.getValueOrDefault(ConfigKey.FEED_SERVER_METHOD, HttpMethod.GET);
    HttpRequest httpRequest =
        new HttpRequest(uriBuilder.build(), httpMethod, Collections.emptyList(), new byte[] {});
    Logger.i(TAG, "Making Request: %s", httpRequest.getUri().getPath());
    networkClient.send(
        httpRequest,
        input -> {
          Logger.i(
              TAG,
              "Request: %s completed with response code: %s",
              httpRequest.getUri().getPath(),
              input.getResponseCode());
          if (input.getResponseCode() != 200) {
            String errorBody = null;
            try {
              errorBody = new String(input.getResponseBody(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
              Logger.e(TAG, "Error handling http error logging", e);
            }
            Logger.e(TAG, "errorCode: %d", input.getResponseCode());
            Logger.e(TAG, "errorResponse: %s", errorBody);
            if (!requestBuilder.hasPageToken()) {
              scheduler.onRequestError(input.getResponseCode());
            }
            consumer.accept(Result.failure());
            return;
          }

          handleResponseBytes(input.getResponseBody(), consumer);
        });
  }

  private Locale getLocale() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ? context.getResources().getConfiguration().getLocales().get(0)
        : context.getResources().getConfiguration().locale;
  }

  private void handleResponseBytes(
      final byte[] responseBytes, final Consumer<Result<List<StreamDataOperation>>> consumer) {
    taskQueue.execute(
        "handleResponseBytes",
        TaskType.IMMEDIATE,
        () -> {
          Response response;
          boolean isLengthPrefixed =
              configuration.getValueOrDefault(ConfigKey.FEED_SERVER_RESPONSE_LENGTH_PREFIXED, true);
          try {
            response =
                Response.parseFrom(
                    isLengthPrefixed ? getLengthPrefixedValue(responseBytes) : responseBytes,
                    extensionRegistry.getExtensionRegistry());
          } catch (IOException e) {
            Logger.e(TAG, e, "Response parse failed");
            consumer.accept(Result.failure());
            return;
          }
          Result<List<StreamDataOperation>> result = protocolAdapter.createModel(response);
          final Result<List<StreamDataOperation>> contextResult;
          if (result.isSuccessful()) {
            contextResult = Result.success(result.getValue());
          } else {
            contextResult = Result.failure();
          }
          mainThreadRunner.execute(
              "FeedRequestManager consumer", () -> consumer.accept(contextResult));
        });
  }

  /**
   * Returns the first length-prefixed value from {@code input}. The first bytes of input are
   * assumed to be a varint32 encoding the length of the rest of the message. If input contains more
   * than one message, only the first message is returned.i w
   *
   * @throws IOException if input cannot be parsed
   */
  private byte[] getLengthPrefixedValue(byte[] input) throws IOException {
    CodedInputStream codedInputStream = CodedInputStream.newInstance(input);
    if (codedInputStream.isAtEnd()) {
      throw new IOException("Empty length-prefixed response");
    } else {
      int length = codedInputStream.readRawVarint32();
      return codedInputStream.readRawBytes(length);
    }
  }

  private static final class RequestBuilder {

    private ByteString token;
    private List<DismissActionWithSemanticProperties> dismissActionsWithSemanticProperties;
    private Locale locale;
    private ApplicationInfo applicationInfo;
    private Configuration configuration;

    RequestBuilder(Locale locale, ApplicationInfo applicationInfo, Configuration configuration) {
      this.locale = locale;
      this.applicationInfo = applicationInfo;
      this.configuration = configuration;
    }

    /**
     * Sets the token used to tell the server which page of results we want in the response.
     *
     * @param token the token copied from FeedResponse.next_page_token.
     */
    RequestBuilder setPageToken(ByteString token) {
      this.token = token;
      return this;
    }

    boolean hasPageToken() {
      return token != null;
    }

    RequestBuilder setActions(
        List<DismissActionWithSemanticProperties> dismissActionsWithSemanticProperties) {
      this.dismissActionsWithSemanticProperties = dismissActionsWithSemanticProperties;
      return this;
    }

    public Request build() {
      Request.Builder requestBuilder =
          Request.newBuilder().setRequestVersion(RequestVersion.FEED_QUERY);

      FeedQuery.Builder feedQuery = FeedQuery.newBuilder();
      if (token != null) {
        feedQuery.setPageToken(token);
      }
      Builder feedRequestBuilder = FeedRequest.newBuilder().setFeedQuery(feedQuery);
      if (dismissActionsWithSemanticProperties != null
          && !dismissActionsWithSemanticProperties.isEmpty()) {
        feedRequestBuilder.addFeedActionQueryData(buildFeedActionQueryData());
      }
      feedRequestBuilder.setClientInfo(buildClientInfo());
      if (configuration.getValueOrDefault(ConfigKey.FEED_UI_ENABLED, false)) {
        feedRequestBuilder.addClientCapability(Capability.FEED_UI);
      }
      if (configuration.getValueOrDefault(ConfigKey.USE_SECONDARY_PAGE_REQUEST, false)) {
        feedRequestBuilder.addClientCapability(Capability.USE_SECONDARY_PAGE_REQUEST);
      }
      requestBuilder.setExtension(FeedRequest.feedRequest, feedRequestBuilder.build());

      return requestBuilder.build();
    }

    private FeedActionQueryData buildFeedActionQueryData() {
      Map<Long, Integer> ids = new LinkedHashMap<>(dismissActionsWithSemanticProperties.size());
      Map<String, Integer> tables =
          new LinkedHashMap<>(dismissActionsWithSemanticProperties.size());
      Map<String, Integer> contentDomains =
          new LinkedHashMap<>(dismissActionsWithSemanticProperties.size());
      Map<SemanticProperties, Integer> semanticProperties =
          new LinkedHashMap<>(dismissActionsWithSemanticProperties.size());
      ArrayList<FeedActionQueryDataItem> actionDataItems =
          new ArrayList<>(dismissActionsWithSemanticProperties.size());

      for (DismissActionWithSemanticProperties action : dismissActionsWithSemanticProperties) {
        ContentId contentId = action.getContentId();
        byte /*@Nullable*/ [] semanticPropertiesBytes = action.getSemanticProperties();

        FeedActionQueryDataItem.Builder actionDataItemBuilder =
            FeedActionQueryDataItem.newBuilder();

        actionDataItemBuilder.setIdIndex(getIndexForItem(ids, contentId.getId()));
        actionDataItemBuilder.setTableIndex(getIndexForItem(tables, contentId.getTable()));
        actionDataItemBuilder.setContentDomainIndex(
            getIndexForItem(contentDomains, contentId.getContentDomain()));
        if (semanticPropertiesBytes != null) {
          actionDataItemBuilder.setSemanticPropertiesIndex(
              getIndexForItem(
                  semanticProperties,
                  SemanticProperties.newBuilder()
                      .setSemanticPropertiesData(ByteString.copyFrom(semanticPropertiesBytes))
                      .build()));
        }

        actionDataItems.add(actionDataItemBuilder.build());
      }
      return FeedActionQueryData.newBuilder()
          .setAction(
              Action.newBuilder()
                  .setActionType(ActionTypeProto.ActionType.forNumber(ActionType.DISMISS)))
          .addAllUniqueId(ids.keySet())
          .addAllUniqueTable(tables.keySet())
          .addAllUniqueContentDomain(contentDomains.keySet())
          .addAllUniqueSemanticProperties(semanticProperties.keySet())
          .addAllFeedActionQueryDataItem(actionDataItems)
          .build();
    }

    private static <T> int getIndexForItem(Map<T, Integer> objectMap, T object) {
      if (!objectMap.containsKey(object)) {
        int newIndex = objectMap.size();
        objectMap.put(object, newIndex);
        return newIndex;
      } else {
        return objectMap.get(object);
      }
    }

    private ClientInfo buildClientInfo() {
      ClientInfo.Builder clientInfoBuilder = ClientInfo.newBuilder();
      clientInfoBuilder.setPlatformType(PlatformType.ANDROID);
      clientInfoBuilder.setPlatformVersion(getPlatformVersion());
      clientInfoBuilder.setLocale(LocaleUtils.getLanguageTag(locale));
      clientInfoBuilder.setAppType(Utils.convertAppType(applicationInfo.getAppType()));
      clientInfoBuilder.setAppVersion(getAppVersion());
      return clientInfoBuilder.build();
    }

    private static Version getPlatformVersion() {
      Version.Builder builder = Version.newBuilder();
      Utils.fillVersionsFromString(builder, Build.VERSION.RELEASE);
      builder.setArchitecture(getPlatformArchitecture());
      builder.setBuildType(getPlatformBuildType());
      builder.setApiVersion(Build.VERSION.SDK_INT);
      return builder.build();
    }

    private static Architecture getPlatformArchitecture() {
      String androidAbi =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
              ? Build.SUPPORTED_ABIS[0]
              : Build.CPU_ABI;
      return Utils.convertArchitectureString(androidAbi);
    }

    private static BuildType getPlatformBuildType() {
      if (Build.TAGS != null) {
        if (Build.TAGS.contains("dev-keys") || Build.TAGS.contains("test-keys")) {
          return BuildType.DEV;
        } else if (Build.TAGS.contains("release-keys")) {
          return BuildType.RELEASE;
        }
      }
      return BuildType.UNKNOWN_BUILD_TYPE;
    }

    private Version getAppVersion() {
      Version.Builder builder = Version.newBuilder();
      Utils.fillVersionsFromString(builder, applicationInfo.getVersionString());
      builder.setArchitecture(Utils.convertArchitecture(applicationInfo.getArchitecture()));
      builder.setBuildType(Utils.convertBuildType(applicationInfo.getBuildType()));
      return builder.build();
    }
  }
}
