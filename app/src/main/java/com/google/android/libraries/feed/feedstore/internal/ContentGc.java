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

package com.google.android.libraries.feed.feedstore.internal;

import static com.google.android.libraries.feed.feedstore.internal.FeedStoreConstants.SEMANTIC_PROPERTIES_PREFIX;
import static com.google.android.libraries.feed.feedstore.internal.FeedStoreConstants.SHARED_STATE_PREFIX;

import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.functional.Supplier;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.host.storage.CommitResult;
import com.google.android.libraries.feed.host.storage.ContentMutation;
import com.google.android.libraries.feed.host.storage.ContentStorageDirect;
import com.google.search.now.feed.client.StreamDataProto.StreamAction;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Storage Content Garbage Collector. */
public final class ContentGc {
  private static final String TAG = "ContentGc";

  private final Supplier<Set<String>> accessibleContentSupplier;
  private final Set<String> reservedContentIds;
  private final Supplier<Set<StreamAction>> actionsSupplier;
  private final ContentStorageDirect contentStorageDirect;
  private final TimingUtils timingUtils;

  ContentGc(
      Supplier<Set<String>> accessibleContentSupplier,
      Set<String> reservedContentIds,
      Supplier<Set<StreamAction>> actionsSupplier,
      ContentStorageDirect contentStorageDirect,
      TimingUtils timingUtils) {
    this.accessibleContentSupplier = accessibleContentSupplier;
    this.reservedContentIds = reservedContentIds;
    this.actionsSupplier = actionsSupplier;
    this.contentStorageDirect = contentStorageDirect;
    this.timingUtils = timingUtils;
  }

  void gc() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<String> population = getPopulation();
    // remove the items in the population that are accessible, reserved, or semantic properties
    // either accessible or associated with an action
    Set<String> accessibleContent = getAccessible();
    population.removeAll(accessibleContent);
    population.removeAll(reservedContentIds);
    population.removeAll(getAccessibleSemanticProperties(accessibleContent));
    population.removeAll(getActionSemanticProperties(getActions()));
    filterPrefixed(population);

    // Population now contains only un-accessible items
    removeUnAccessible(population);
    tracker.stop("task", "ContentGc", "contentItemsRemoved", population.size());
  }

  private void removeUnAccessible(Set<String> unAccessible) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    ContentMutation.Builder mutationBuilder = new ContentMutation.Builder();
    for (String key : unAccessible) {
      mutationBuilder.delete(key);
    }
    CommitResult result = contentStorageDirect.commit(mutationBuilder.build());
    if (result == CommitResult.FAILURE) {
      Logger.e(TAG, "Content Modification failed removing unaccessible items.");
    }
    tracker.stop("", "removeUnAccessible", "mutations", unAccessible.size());
  }

  private void filterPrefixed(Set<String> population) {
    int size = population.size();
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Iterator<String> i = population.iterator();
    while (i.hasNext()) {
      String key = i.next();
      if (key.startsWith(SHARED_STATE_PREFIX)) {
        i.remove();
      }
    }
    tracker.stop("", "filterPrefixed", population.size() - size);
  }

  private Set<String> getAccessible() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<String> accessibleContent = accessibleContentSupplier.get();
    tracker.stop("", "getAccessible", "accessableContent", accessibleContent.size());
    return accessibleContent;
  }

  private Set<StreamAction> getActions() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<StreamAction> actions = actionsSupplier.get();
    tracker.stop("", "getActions", "actionCount", actions.size());
    return actions;
  }

  private Set<String> getPopulation() {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<String> population = new HashSet<>();
    Result<List<String>> result = contentStorageDirect.getAllKeys();
    if (result.isSuccessful()) {
      population.addAll(result.getValue());
    } else {
      Logger.e(TAG, "Unable to get all content, getAll failed");
    }
    tracker.stop("", "getPopulation", "contentPopulation", population.size());
    return population;
  }

  private Set<String> getAccessibleSemanticProperties(Set<String> accessibleContent) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<String> semanticPropertiesKeys = new HashSet<>();
    for (String accessibleContentId : accessibleContent) {
      String semanticPropertyKey = SEMANTIC_PROPERTIES_PREFIX + accessibleContentId;
      semanticPropertiesKeys.add(semanticPropertyKey);
    }
    tracker.stop(
        "",
        "getAccessibleSemanticProperties",
        "accessibleSemanticPropertiesSize",
        semanticPropertiesKeys.size());
    return semanticPropertiesKeys;
  }

  private Set<String> getActionSemanticProperties(Set<StreamAction> actions) {
    ElapsedTimeTracker tracker = timingUtils.getElapsedTimeTracker(TAG);
    Set<String> semanticPropertiesKeys = new HashSet<>();
    for (StreamAction action : actions) {
      String semanticPropertyKey = SEMANTIC_PROPERTIES_PREFIX + action.getFeatureContentId();
      semanticPropertiesKeys.add(semanticPropertyKey);
    }
    tracker.stop(
        "",
        "getActionSemanticProperties",
        "actionSemanticPropertiesSize",
        semanticPropertiesKeys.size());
    return semanticPropertiesKeys;
  }
}
