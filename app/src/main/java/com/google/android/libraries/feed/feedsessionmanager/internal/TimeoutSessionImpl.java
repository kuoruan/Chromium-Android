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
import com.google.android.libraries.feed.api.common.MutationContext;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.modelprovider.ModelChild;
import com.google.android.libraries.feed.api.modelprovider.ModelChild.Type;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.store.Store;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure.Operation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** SessionImpl which implements the TimeoutScheduler specific behaviors need within the session. */
public final class TimeoutSessionImpl extends SessionImpl {
  private static final String TAG = "TimeoutSessionImpl";

  TimeoutSessionImpl(
      Store store,
      boolean limitPagingUpdates,
      TaskQueue taskQueue,
      TimingUtils timingUtils,
      ThreadUtils threadUtils) {
    super(store, limitPagingUpdates, taskQueue, timingUtils, threadUtils);
    Logger.i(TAG, "Using TimeoutSessionImpl");
  }

  @Override
  public void populateModelProvider(
      StreamSession streamSession,
      List<StreamStructure> head,
      boolean cachedBindings,
      boolean legacyHeadContent) {
    Logger.i(TAG, "TimeoutSession.populateModelProvider, legacyHeadContent %s", legacyHeadContent);
    super.populateModelProvider(streamSession, head, cachedBindings, legacyHeadContent);
  }

  @Override
  public void updateSession(
      boolean clearHead,
      List<StreamStructure> streamStructures,
      /*@Nullable*/ MutationContext mutationContext) {
    StreamSession ss = Validators.checkNotNull(streamSession);
    Logger.i(
        TAG,
        "TimeoutSession.updateSession, clearHead %s, legacyHeadContent %s",
        clearHead,
        legacyHeadContent);
    if (clearHead) {
      if (!legacyHeadContent) {
        // Without having legacy HEAD content, don't update the session,
        Logger.i(TAG, "Session %s not updated due to clearHead", ss.getStreamToken());
        return;
      }

      if (viewDepthProvider != null) {
        // Append the new items to the existing copy of HEAD, removing the existing items which
        // have not yet been seen by the user.
        List<ModelChild> rootChildren = captureRootContent();
        if (!rootChildren.isEmpty()) {

          // Calculate the children to remove and append StreamStructure remove operations
          String lowestChild = Validators.checkNotNull(viewDepthProvider).getChildViewDepth();
          if (lowestChild != null) {
            List<StreamStructure> removeOperations = removeItems(lowestChild, rootChildren);
            if (!removeOperations.isEmpty()) {
              removeOperations.addAll(streamStructures);
              streamStructures = removeOperations;
            }
          }
        }
      }
      // Only do this once
      legacyHeadContent = false;
    }

    updateCount++;
    updateSessionInternal(streamStructures, mutationContext);
  }

  @Override
  public boolean invalidateOnResetHead() {
    return false;
  }

  @VisibleForTesting
  List<StreamStructure> removeItems(String lowestChild, List<ModelChild> rootChildren) {
    boolean remove = false;
    List<StreamStructure> removeOperations = new ArrayList<>();
    for (ModelChild child : rootChildren) {
      if (remove || child.getType() == Type.TOKEN) {
        removeOperations.add(createRemoveFeature(child.getContentId(), child.getParentId()));
      } else if (child.getContentId().equals(lowestChild)) {
        remove = true;
      }
    }
    return removeOperations;
  }

  @VisibleForTesting
  List<ModelChild> captureRootContent() {
    ModelProvider modelProvider = getModelProvider();
    if (modelProvider == null) {
      Logger.w(TAG, "ModelProvider was not found");
      return Collections.emptyList();
    }
    return modelProvider.getAllRootChildren();
  }

  @VisibleForTesting
  StreamStructure createRemoveFeature(String contentId, /*@Nullable*/ String parentId) {
    StreamStructure.Builder builder =
        StreamStructure.newBuilder().setOperation(Operation.REMOVE).setContentId(contentId);
    if (parentId != null) {
      builder.setParentContentId(parentId);
    }
    return builder.build();
  }
}
