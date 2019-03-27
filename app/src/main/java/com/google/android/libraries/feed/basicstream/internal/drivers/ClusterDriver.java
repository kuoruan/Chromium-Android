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

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.actionmanager.ActionManager;
import com.google.android.libraries.feed.api.actionparser.ActionParserFactory;
import com.google.android.libraries.feed.api.modelprovider.ModelChild;
import com.google.android.libraries.feed.api.modelprovider.ModelChild.Type;
import com.google.android.libraries.feed.api.modelprovider.ModelCursor;
import com.google.android.libraries.feed.api.modelprovider.ModelFeature;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.stream.ContentChangedListener;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.host.action.ActionApi;
import com.google.android.libraries.feed.host.logging.BasicLoggingApi;
import com.google.android.libraries.feed.sharedstream.contextmenumanager.ContextMenuManager;
import com.google.android.libraries.feed.sharedstream.offlinemonitor.StreamOfflineMonitor;

/** {@link FeatureDriver} for Clusters. */
public class ClusterDriver implements FeatureDriver {

  private static final String TAG = "ClusterDriver";
  private final ActionApi actionApi;
  private final ActionManager actionManager;
  private final ActionParserFactory actionParserFactory;
  private final BasicLoggingApi basicLoggingApi;
  private final ModelFeature clusterModel;
  private final ModelProvider modelProvider;
  private final int position;
  private final StreamOfflineMonitor streamOfflineMonitor;
  private final ContentChangedListener contentChangedListener;
  private final ContextMenuManager contextMenuManager;
  /*@Nullable*/ private CardDriver cardDriver;

  ClusterDriver(
      ActionApi actionApi,
      ActionManager actionManager,
      ActionParserFactory actionParserFactory,
      BasicLoggingApi basicLoggingApi,
      ModelFeature clusterModel,
      ModelProvider modelProvider,
      int position,
      StreamOfflineMonitor streamOfflineMonitor,
      ContentChangedListener contentChangedListener,
      ContextMenuManager contextMenuManager) {
    this.actionApi = actionApi;
    this.actionManager = actionManager;
    this.actionParserFactory = actionParserFactory;
    this.basicLoggingApi = basicLoggingApi;
    this.clusterModel = clusterModel;
    this.modelProvider = modelProvider;
    this.position = position;
    this.streamOfflineMonitor = streamOfflineMonitor;
    this.contentChangedListener = contentChangedListener;
    this.contextMenuManager = contextMenuManager;
  }

  @Override
  public void onDestroy() {
    if (cardDriver != null) {
      cardDriver.onDestroy();
    }
  }

  @Override
  /*@Nullable*/
  public LeafFeatureDriver getLeafFeatureDriver() {
    if (cardDriver == null) {
      cardDriver = createCardChild(clusterModel);
    }

    if (cardDriver != null) {
      return cardDriver.getLeafFeatureDriver();
    }

    return null;
  }

  /*@Nullable*/
  private CardDriver createCardChild(ModelFeature clusterFeature) {
    ModelCursor cursor = clusterFeature.getCursor();
    // TODO: add change listener to clusterCursor.
    ModelChild child;
    while ((child = cursor.getNextItem()) != null) {
      if (child.getType() != Type.FEATURE) {
        Logger.e(TAG, "Child of cursor is not a feature");
        continue;
      }

      ModelFeature content = child.getModelFeature();

      if (content == null) {
        Logger.e(TAG, "Content not found");
        continue;
      }

      if (!content.getStreamFeature().hasCard()) {
        Logger.e(TAG, "Content not card.");
        continue;
      }

      return createCardDriver(content);
    }

    return null;
  }

  @VisibleForTesting
  CardDriver createCardDriver(ModelFeature content) {
    return new CardDriver(
        actionApi,
        actionManager,
        actionParserFactory,
        basicLoggingApi,
        content,
        modelProvider,
        position,
        streamOfflineMonitor,
        contentChangedListener,
        contextMenuManager);
  }
}
