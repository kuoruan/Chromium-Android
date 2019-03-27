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

package com.google.android.libraries.feed.basicstream.internal.drivers;

import static com.google.android.libraries.feed.common.Validators.checkState;

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionparser.ActionParser;
import com.google.android.libraries.feed.api.actionparser.ActionParserFactory;
import com.google.android.libraries.feed.api.knowncontent.ContentMetadata;
import com.google.android.libraries.feed.api.modelprovider.ModelFeature;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.stream.ContentChangedListener;
import com.google.android.libraries.feed.basicstream.internal.actions.StreamActionApiImpl;
import com.google.android.libraries.feed.basicstream.internal.viewholders.FeedViewHolder;
import com.google.android.libraries.feed.basicstream.internal.viewholders.PietViewHolder;
import com.google.android.libraries.feed.basicstream.internal.viewholders.ViewHolderType;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.action.StreamActionApi;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.logging.ContentLoggingData;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.ContextMenuManager;
import com.google.android.libraries.feed.sharedstream.logging.LoggingListener;
import com.google.android.libraries.feed.sharedstream.logging.OneShotVisibilityLoggingListener;
import com.google.android.libraries.feed.sharedstream.logging.StreamContentLoggingData;
import com.google.android.libraries.feed.sharedstream.offlinemonitor.StreamOfflineMonitor;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.ui.action.FeedActionPayloadProto.FeedActionPayload;
import com.google.search.now.ui.piet.PietProto.Frame;
import com.google.search.now.ui.piet.PietProto.PietSharedState;
import com.google.search.now.ui.stream.StreamStructureProto;
import com.google.search.now.ui.stream.StreamStructureProto.BasicLoggingMetadata;
import com.google.search.now.ui.stream.StreamStructureProto.Content;
import com.google.search.now.ui.stream.StreamStructureProto.PietContent;
import com.google.search.now.ui.stream.StreamStructureProto.RepresentationData;
import com.google.search.now.wire.feed.ContentIdProto.ContentId;
import java.util.ArrayList;
import java.util.List;

/** {@link FeatureDriver} for content. */
public class ContentDriver extends LeafFeatureDriver implements LoggingListener {

  private static final String TAG = "ContentDriver";

  private final BasicLoggingApi basicLoggingApi;
  private final ContentLoggingData contentLoggingData;
  private final List<PietSharedState> pietSharedStates;
  private final Frame frame;
  private final StreamActionApi streamActionApi;
  private final FeedActionPayload swipeAction;
  private final String contentId;
  private final StreamOfflineMonitor streamOfflineMonitor;
  private final OfflineStatusConsumer offlineStatusConsumer;
  private final String contentUrl;
  private final ContentChangedListener contentChangedListener;
  private final ActionParser actionParser;

  private boolean availableOffline;
  /*@Nullable*/ private LoggingListener loggingListener;
  /*@Nullable*/ private PietViewHolder viewHolder;

  ContentDriver(
      ActionApi actionApi,
      ActionManager actionManager,
      ActionParserFactory actionParserFactory,
      BasicLoggingApi basicLoggingApi,
      ModelFeature contentFeatureModel,
      ModelProvider modelProvider,
      int position,
      FeedActionPayload swipeAction,
      StreamOfflineMonitor streamOfflineMonitor,
      ContentChangedListener contentChangedListener,
      ContextMenuManager contextMenuManager) {
    Content content = contentFeatureModel.getStreamFeature().getContent();

    PietContent pietContent = getPietContent(content);
    this.basicLoggingApi = basicLoggingApi;
    frame = pietContent.getFrame();
    pietSharedStates = getPietSharedStates(pietContent, modelProvider);
    contentId = contentFeatureModel.getStreamFeature().getContentId();
    RepresentationData representationData = content.getRepresentationData();
    contentLoggingData =
        createContentLoggingData(content.getBasicLoggingMetadata(), representationData, position);
    actionParser =
        actionParserFactory.build(
            () ->
                ContentMetadata.maybeCreateContentMetadata(
                    content.getOfflineMetadata(), representationData));
    streamActionApi =
        createStreamActionApi(
            actionApi,
            actionParser,
            actionManager,
            basicLoggingApi,
            contentLoggingData,
            modelProvider.getSessionToken(),
            contextMenuManager);
    this.swipeAction = swipeAction;
    this.streamOfflineMonitor = streamOfflineMonitor;
    contentUrl = representationData.getUri();
    availableOffline = streamOfflineMonitor.isAvailableOffline(contentUrl);
    offlineStatusConsumer = new OfflineStatusConsumer();
    streamOfflineMonitor.addOfflineStatusConsumer(contentUrl, offlineStatusConsumer);
    this.contentChangedListener = contentChangedListener;
  }

  @Override
  public void onDestroy() {
    streamOfflineMonitor.removeOfflineStatusConsumer(contentUrl, offlineStatusConsumer);
  }

  @Override
  public LeafFeatureDriver getLeafFeatureDriver() {
    return this;
  }

  private PietContent getPietContent(/*@UnderInitialization*/ ContentDriver this, Content content) {
    checkState(
        content.getType() == StreamStructureProto.Content.Type.PIET,
        "Expected Piet type for feature");

    checkState(
        content.hasExtension(PietContent.pietContentExtension),
        "Expected Piet content for feature");

    return content.getExtension(PietContent.pietContentExtension);
  }

  private List<PietSharedState> getPietSharedStates(
      /*@UnderInitialization*/ ContentDriver this,
      PietContent pietContent,
      ModelProvider modelProvider) {
    List<PietSharedState> sharedStates = new ArrayList<>();
    for (ContentId contentId : pietContent.getPietSharedStatesList()) {
      PietSharedState pietSharedState = extractPietSharedState(contentId, modelProvider);
      if (pietSharedState == null) {
        return new ArrayList<>();
      }

      sharedStates.add(pietSharedState);
    }
    return sharedStates;
  }

  /*@Nullable*/
  private PietSharedState extractPietSharedState(
      /*@UnderInitialization*/ ContentDriver this,
      ContentId pietSharedStateId,
      ModelProvider modelProvider) {
    StreamSharedState sharedState = modelProvider.getSharedState(pietSharedStateId);
    if (sharedState != null) {
      return sharedState.getPietSharedStateItem().getPietSharedState();
    }
    Logger.e(
        TAG,
        "Shared state was null. Stylesheets and templates on PietSharedState "
            + "will not be loaded.");
    return null;
  }

  @Override
  public void bind(FeedViewHolder viewHolder) {
    if (!(viewHolder instanceof PietViewHolder)) {
      throw new AssertionError();
    }
    if (loggingListener == null) {
      loggingListener = new OneShotVisibilityLoggingListener(this);
    }

    this.viewHolder = (PietViewHolder) viewHolder;

    ((PietViewHolder) viewHolder)
        .bind(frame, pietSharedStates, streamActionApi, swipeAction, loggingListener, actionParser);
  }

  @Override
  public void unbind() {
    if (viewHolder == null) {
      return;
    }

    viewHolder.unbind();
    viewHolder = null;
  }

  @Override
  public void maybeRebind() {
    if (viewHolder == null) {
      return;
    }

    // Unbinding clears the viewHolder, so storing to rebind.
    PietViewHolder localViewHolder = viewHolder;
    unbind();
    bind(localViewHolder);
    contentChangedListener.onContentChanged();
  }

  @Override
  public int getItemViewType() {
    return ViewHolderType.TYPE_CARD;
  }

  @Override
  public long itemId() {
    return hashCode();
  }

  @VisibleForTesting
  boolean isBound() {
    return viewHolder != null;
  }

  @Override
  public String getContentId() {
    return contentId;
  }

  @Override
  public void onViewVisible() {
    basicLoggingApi.onContentViewed(contentLoggingData);
  }

  @Override
  public void onContentClicked() {
    basicLoggingApi.onContentClicked(contentLoggingData);
  }

  @Override
  public void onContentSwiped() {
    basicLoggingApi.onContentSwiped(contentLoggingData);
  }

  private ContentLoggingData createContentLoggingData(
      /*@UnderInitialization*/ ContentDriver this,
      BasicLoggingMetadata basicLoggingMetadata,
      RepresentationData representationData,
      int position) {
    return new StreamContentLoggingData(position, basicLoggingMetadata, representationData);
  }

  @VisibleForTesting
  StreamActionApi createStreamActionApi(
      /*@UnknownInitialization*/ ContentDriver this,
      ActionApi actionApi,
      ActionParser actionParser,
      ActionManager actionManager,
      BasicLoggingApi basicLoggingApi,
      ContentLoggingData contentLoggingData,
      /*@Nullable*/ String sessionToken,
      ContextMenuManager contextMenuManager) {
    return new StreamActionApiImpl(
        actionApi,
        actionParser,
        actionManager,
        basicLoggingApi,
        contentLoggingData,
        contextMenuManager,
        sessionToken);
  }

  private class OfflineStatusConsumer implements Consumer<Boolean> {

    @Override
    public void accept(Boolean offlineStatus) {
      if (offlineStatus.equals(availableOffline)) {
        return;
      }

      availableOffline = offlineStatus;
      maybeRebind();
    }
  }
}
