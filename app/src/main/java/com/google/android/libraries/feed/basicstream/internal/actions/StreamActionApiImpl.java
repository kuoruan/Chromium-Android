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

package com.google.android.libraries.feed.basicstream.internal.actions;

import android.view.View;
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionparser.ActionParser;
import com.google.android.libraries.feed.api.knowncontent.ContentMetadata;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.action.StreamActionApi;
import com.google.android.libraries.feed.host.logging.ActionType;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.host.logging.ContentLoggingData;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.ContextMenuManager;
import com.google.search.now.feed.client.StreamDataProto.StreamDataOperation;
import com.google.search.now.ui.action.FeedActionProto.LabelledFeedActionData;
import com.google.search.now.ui.action.FeedActionProto.OpenContextMenuData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Action handler for Stream. */
public class StreamActionApiImpl implements StreamActionApi {

  private static final String TAG = "StreamActionApiImpl";


  private final ActionApi actionApi;
  private final ActionParser actionParser;
  private final ActionManager actionManager;
  private final BasicLoggingApi basicLoggingApi;
  private final ContentLoggingData contentLoggingData;
  private final ContextMenuManager contextMenuManager;

  /*@Nullable*/ private final String sessionToken;

  public StreamActionApiImpl(
      ActionApi actionApi,
      ActionParser actionParser,
      ActionManager actionManager,
      BasicLoggingApi basicLoggingApi,
      ContentLoggingData contentLoggingData,
      ContextMenuManager contextMenuManager,
      /*@Nullable*/ String sessionToken) {
    this.actionApi = actionApi;
    this.actionParser = actionParser;
    this.actionManager = actionManager;
    this.basicLoggingApi = basicLoggingApi;
    this.contentLoggingData = contentLoggingData;
    this.contextMenuManager = contextMenuManager;
    this.sessionToken = sessionToken;
  }

  @Override
  public void openContextMenu(OpenContextMenuData openContextMenuData, View anchorView) {
    List<String> actionLabels = new ArrayList<>();
    List<LabelledFeedActionData> enabledActions = new ArrayList<>();
    for (LabelledFeedActionData labelledFeedActionData :
        openContextMenuData.getContextMenuDataList()) {
      if (actionParser.canPerformAction(labelledFeedActionData.getFeedActionPayload(), this)) {
        actionLabels.add(labelledFeedActionData.getLabel());
        enabledActions.add(labelledFeedActionData);
      }
    }

    boolean menuOpened =
        contextMenuManager.openContextMenu(
            anchorView,
            actionLabels,
            (int position) ->
                actionParser.parseFeedActionPayload(
                    enabledActions.get(position).getFeedActionPayload(),
                    StreamActionApiImpl.this,
                    anchorView));

    if (menuOpened) {
      basicLoggingApi.onContentContextMenuOpened(contentLoggingData);
    }
  }


  @Override
  public boolean canOpenContextMenu() {
    return true;
  }

  @Override
  public boolean canDismiss() {
    return true;
  }

  @Override
  public void dismiss(String contentId, List<StreamDataOperation> dataOperations) {
    actionManager.dismiss(Collections.singletonList(contentId), dataOperations, sessionToken);
    basicLoggingApi.onContentDismissed(contentLoggingData);
  }

  @Override
  public void onClientAction(@ActionType int actionType) {
    basicLoggingApi.onClientAction(contentLoggingData, actionType);
  }

  @Override
  public void openUrl(String url) {
    actionApi.openUrl(url);
  }

  @Override
  public boolean canOpenUrl() {
    return actionApi.canOpenUrl();
  }

  @Override
  public void openUrlInIncognitoMode(String url) {
    actionApi.openUrlInIncognitoMode(url);
  }

  @Override
  public boolean canOpenUrlInIncognitoMode() {
    return actionApi.canOpenUrlInIncognitoMode();
  }

  @Override
  public void openUrlInNewTab(String url) {
    actionApi.openUrlInNewTab(url);
  }

  @Override
  public boolean canOpenUrlInNewTab() {
    return actionApi.canOpenUrlInNewTab();
  }

  @Override
  public void openUrlInNewWindow(String url) {
    actionApi.openUrlInNewWindow(url);
  }

  @Override
  public boolean canOpenUrlInNewWindow() {
    return actionApi.canOpenUrlInNewWindow();
  }

  @Override
  public void downloadUrl(ContentMetadata contentMetadata) {
    actionApi.downloadUrl(contentMetadata);
  }

  @Override
  public boolean canDownloadUrl() {
    return actionApi.canDownloadUrl();
  }

  @Override
  public void learnMore() {
    actionApi.learnMore();
  }

  @Override
  public boolean canLearnMore() {
    return actionApi.canLearnMore();
  }
}
