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

package com.google.android.libraries.feed.feedmodelprovider;

import android.support.annotation.VisibleForTesting;
import com.google.android.libraries.feed.api.common.MutationContext;
import com.google.android.libraries.feed.api.common.ThreadUtils;
import com.google.android.libraries.feed.api.modelprovider.FeatureChange;
import com.google.android.libraries.feed.api.modelprovider.FeatureChangeObserver;
import com.google.android.libraries.feed.api.modelprovider.ModelChild;
import com.google.android.libraries.feed.api.modelprovider.ModelChild.Type;
import com.google.android.libraries.feed.api.modelprovider.ModelError;
import com.google.android.libraries.feed.api.modelprovider.ModelError.ErrorType;
import com.google.android.libraries.feed.api.modelprovider.ModelFeature;
import com.google.android.libraries.feed.api.modelprovider.ModelMutation;
import com.google.android.libraries.feed.api.modelprovider.ModelProvider;
import com.google.android.libraries.feed.api.modelprovider.ModelProviderObserver;
import com.google.android.libraries.feed.api.modelprovider.ModelToken;
import com.google.android.libraries.feed.api.modelprovider.RemoveTracking;
import com.google.android.libraries.feed.api.modelprovider.TokenCompleted;
import com.google.android.libraries.feed.api.modelprovider.TokenCompletedObserver;
import com.google.android.libraries.feed.api.sessionmanager.SessionManager;
import com.google.android.libraries.feed.common.Result;
import com.google.android.libraries.feed.common.Validators;
import com.google.android.libraries.feed.common.concurrent.MainThreadRunner;
import com.google.android.libraries.feed.common.concurrent.TaskQueue;
import com.google.android.libraries.feed.common.concurrent.TaskQueue.TaskType;
import com.google.android.libraries.feed.common.feedobservable.FeedObservable;
import com.google.android.libraries.feed.common.functional.Committer;
import com.google.android.libraries.feed.common.functional.Consumer;
import com.google.android.libraries.feed.common.functional.Predicate;
import com.google.android.libraries.feed.common.logging.Dumpable;
import com.google.android.libraries.feed.common.logging.Dumper;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.time.TimingUtils;
import com.google.android.libraries.feed.common.time.TimingUtils.ElapsedTimeTracker;
import com.google.android.libraries.feed.feedmodelprovider.internal.CursorProvider;
import com.google.android.libraries.feed.feedmodelprovider.internal.FeatureChangeImpl;
import com.google.android.libraries.feed.feedmodelprovider.internal.ModelChildBinder;
import com.google.android.libraries.feed.feedmodelprovider.internal.ModelCursorImpl;
import com.google.android.libraries.feed.feedmodelprovider.internal.ModelMutationImpl;
import com.google.android.libraries.feed.feedmodelprovider.internal.ModelMutationImpl.Change;
import com.google.android.libraries.feed.feedmodelprovider.internal.UpdatableModelChild;
import com.google.android.libraries.feed.feedmodelprovider.internal.UpdatableModelFeature;
import com.google.android.libraries.feed.feedmodelprovider.internal.UpdatableModelToken;
import com.google.android.libraries.feed.host.config.Configuration;
import com.google.android.libraries.feed.host.config.Configuration.ConfigKey;
import com.google.protobuf.ByteString;
import com.google.search.now.feed.client.StreamDataProto.StreamSession;
import com.google.search.now.feed.client.StreamDataProto.StreamSharedState;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure;
import com.google.search.now.feed.client.StreamDataProto.StreamStructure.Operation;
import com.google.search.now.feed.client.StreamDataProto.StreamToken;
import com.google.search.now.wire.feed.ContentIdProto.ContentId;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.annotation.concurrent.GuardedBy;

/** An implementation of {@link ModelProvider}. This will represent the Stream tree in memory. */
public final class FeedModelProvider extends FeedObservable<ModelProviderObserver>
    implements ModelProvider, Dumpable {

  private static final String TAG = "FeedModelProvider";
  private static final List<UpdatableModelChild> EMPTY_LIST =
      Collections.unmodifiableList(new ArrayList<>());
  private static final String SYNTHETIC_TOKEN_PREFIX = "_token:";

  private final Object lock = new Object();

  @GuardedBy("lock")
  /*@Nullable*/
  private UpdatableModelChild root = null;

  // The tree is model as a parent with an list of children.  A container is created for every
  // ModelChild with a child.
  @GuardedBy("lock")
  private final Map<String, ArrayList<UpdatableModelChild>> containers = new HashMap<>();

  @GuardedBy("lock")
  private final Map<String, UpdatableModelChild> contents = new HashMap<>();

  @GuardedBy("lock")
  private final Map<ByteString, TokenTracking> tokens = new HashMap<>();

  @GuardedBy("lock")
  private final Map<String, SyntheticTokenTracker> syntheticTokens = new HashMap<>();

  // TODO: Tiktok doesn't like WeakReference and will report uses as conformance errors
  @GuardedBy("lock")
  private final List<WeakReference<ModelCursorImpl>> cursors = new ArrayList<>();

  @GuardedBy("lock")
  private @State int currentState = State.INITIALIZING;

  // #dump() operation counts
  private int removedChildrenCount = 0;
  private int removeScanCount = 0;
  private int commitCount = 0;
  private int commitTokenCount = 0;
  private int commitUpdateCount = 0;
  private int cursorsRemoved = 0;

  private final SessionManager sessionManager;
  private final ThreadUtils threadUtils;
  private final TaskQueue taskQueue;
  private final MainThreadRunner mainThreadRunner;
  private final ModelChildBinder modelChildBinder;
  private final TimingUtils timingUtils;

  /*@Nullable*/ private final Predicate<StreamStructure> filterPredicate;
  /*@Nullable*/ private RemoveTrackingFactory<?> removeTrackingFactory;

  private final int initialPageSize;
  private final int pageSize;
  private final int minPageSize;

  /*@Nullable*/ @VisibleForTesting StreamSession streamSession;

  @GuardedBy("lock")
  private boolean delayedTriggerRefresh = false;

  FeedModelProvider(
      SessionManager sessionManager,
      ThreadUtils threadUtils,
      TimingUtils timingUtils,
      TaskQueue taskQueue,
      MainThreadRunner mainThreadRunner,
      /*@Nullable*/ Predicate<StreamStructure> filterPredicate,
      Configuration config) {
    this.sessionManager = sessionManager;
    this.threadUtils = threadUtils;
    this.timingUtils = timingUtils;
    this.taskQueue = taskQueue;
    this.mainThreadRunner = mainThreadRunner;
    this.initialPageSize = config.getValueOrDefault(ConfigKey.INITIAL_NON_CACHED_PAGE_SIZE, 0);
    this.pageSize = config.getValueOrDefault(ConfigKey.NON_CACHED_PAGE_SIZE, 0);
    this.minPageSize = config.getValueOrDefault(ConfigKey.NON_CACHED_MIN_PAGE_SIZE, 0);
    this.filterPredicate = filterPredicate;

    CursorProvider cursorProvider =
        parentId -> {
          synchronized (lock) {
            ArrayList<UpdatableModelChild> children = containers.get(parentId);
            if (children == null) {
              Logger.i(TAG, "No children found for Cursor");
              ModelCursorImpl cursor = new ModelCursorImpl(parentId, EMPTY_LIST);
              cursors.add(new WeakReference<>(cursor));
              return cursor;
            }
            ModelCursorImpl cursor = new ModelCursorImpl(parentId, new ArrayList<>(children));
            cursors.add(new WeakReference<>(cursor));
            return cursor;
          }
        };
    modelChildBinder = new ModelChildBinder(sessionManager, cursorProvider, timingUtils);
  }

  @Override
  /*@Nullable*/
  public ModelFeature getRootFeature() {
    synchronized (lock) {
      if (root == null) {
        Logger.i(TAG, "Found Empty Stream");
        return null;
      }
      if (root.getType() != Type.FEATURE) {
        Logger.e(TAG, "Root is bound to the wrong type %s", root.getType());
        return null;
      }
      return root.getModelFeature();
    }
  }

  @Override
  /*@Nullable*/
  public ModelChild getModelChild(String contentId) {
    synchronized (lock) {
      return contents.get(contentId);
    }
  }

  @Override
  /*@Nullable*/
  public StreamSharedState getSharedState(ContentId contentId) {
    return sessionManager.getSharedState(contentId);
  }

  @Override
  public void handleToken(ModelToken modelToken) {
    if (modelToken instanceof UpdatableModelToken) {
      UpdatableModelToken token = (UpdatableModelToken) modelToken;
      if (token.isSynthetic()) {
        SyntheticTokenTracker tokenTracker;
        synchronized (lock) {
          tokenTracker = syntheticTokens.get(token.getStreamToken().getContentId());
        }
        if (tokenTracker == null) {
          Logger.e(TAG, "Unable to find the SyntheticTokenTracker");
          return;
        }
        // The nullness checker fails to understand tokenTracker can't be null in the Lambda usage
        SyntheticTokenTracker tt = Validators.checkNotNull(tokenTracker);
        taskQueue.execute(
            "FeedModelProvider.handleSyntheticToken",
            TaskType.USER_FACING,
            () -> tt.handleSyntheticToken(token));
        return;
      }
    }
    StreamSession streamSession = Validators.checkNotNull(this.streamSession);
    sessionManager.handleToken(streamSession, modelToken.getStreamToken());
  }

  @Override
  public void triggerRefresh() {
    threadUtils.checkMainThread();
    if (streamSession == null) {
      synchronized (lock) {
        delayedTriggerRefresh = true;
      }
      return;
    }
    sessionManager.triggerRefresh(streamSession);
  }

  @Override
  public void registerObserver(ModelProviderObserver observer) {
    super.registerObserver(observer);
    synchronized (lock) {
      // If we are in the ready state, then call the Observer to inform it things are ready.
      if (currentState == State.READY) {
        observer.onSessionStart();
      } else if (currentState == State.INVALIDATED) {
        observer.onSessionFinished();
      }
    }
  }

  @Override
  public @State int getCurrentState() {
    synchronized (lock) {
      return currentState;
    }
  }

  @Override
  /*@Nullable*/
  public String getSessionToken() {
    if (streamSession == null) {
      Logger.w(TAG, "streamSession is null, this should have been set during population");
      return null;
    }
    return streamSession.getStreamToken();
  }

  @Override
  public List<ModelChild> getAllRootChildren() {
    synchronized (lock) {
      if (root == null) {
        return Collections.emptyList();
      }
      List<UpdatableModelChild> rootChildren = containers.get(root.getContentId());
      return (rootChildren != null) ? new ArrayList<>(rootChildren) : Collections.emptyList();
    }
  }

  @Override
  public void enableRemoveTracking(RemoveTrackingFactory<?> removeTrackingFactory) {
    this.removeTrackingFactory = removeTrackingFactory;
  }

  @Override
  public ModelMutation edit() {
    return new ModelMutationImpl(committer);
  }

  @Override
  public void detachModelProvider() {
    if (!moveToInvalidateState()) {
      Logger.e(TAG, "unable to detach FeedModelProvider");
      return;
    }
    String sessionToken = getSessionToken();
    if (sessionToken != null) {
      Logger.i(TAG, "Detach the current ModelProvider: session %s", sessionToken);
      sessionManager.detachSession(sessionToken);
    }
  }

  @Override
  public void invalidate() {
    if (!moveToInvalidateState()) {
      Logger.e(TAG, "unable to invalidate FeedModelProvider");
      return;
    }
    String sessionToken = getSessionToken();
    if (sessionToken != null) {
      Logger.i(TAG, "Invalidating the current ModelProvider: session %s", sessionToken);
      sessionManager.invalidatedSession(sessionToken);
    }

    // Always run the observers on the UI Thread
    mainThreadRunner.execute(
        TAG + " onSessionFinished",
        () -> {
          List<ModelProviderObserver> observerList = getObserversToNotify();
          for (ModelProviderObserver observer : observerList) {
            observer.onSessionFinished();
          }
        });
  }

  private boolean moveToInvalidateState() {
    synchronized (lock) {
      if (currentState == State.INVALIDATED) {
        Logger.i(TAG, "Invalidated an already invalid ModelProvider");
        return false;
      }
      Logger.i(
          TAG,
          "Moving %s to INVALIDATED",
          (streamSession != null) ? streamSession.getStreamToken() : "No streamSession");
      currentState = State.INVALIDATED;
      for (WeakReference<ModelCursorImpl> cursorRef : cursors) {
        ModelCursorImpl cursor = cursorRef.get();
        if (cursor != null) {
          cursor.release();
          cursorRef.clear();
        }
      }
      cursors.clear();
      tokens.clear();
      containers.clear();
    }
    return true;
  }

  @Override
  public void raiseError(ModelError error) {
    if (error.getErrorType() == ErrorType.NO_CARDS_ERROR) {
      mainThreadRunner.execute(
          TAG + " onError",
          () -> {
            List<ModelProviderObserver> observerList = getObserversToNotify();
            for (ModelProviderObserver observer : observerList) {
              observer.onError(error);
            }
          });
    } else if (error.getErrorType() == ErrorType.PAGINATION_ERROR) {
      Logger.i(TAG, "handling Pagination error");
      TokenTracking token;
      synchronized (lock) {
        token = tokens.get(error.getContinuationToken());
      }
      if (token != null) {
        mainThreadRunner.execute(
            TAG + " onTokenChange",
            () -> {
              List<TokenCompletedObserver> observerList = token.tokenChild.getObserversToNotify();
              for (TokenCompletedObserver observer : observerList) {
                observer.onError(error);
              }
            });
      } else {
        Logger.e(TAG, "The Token Observer was not found during pagination error");
      }
    }
  }

  /**
   * This wraps the ViewDepthProvider provided by the UI. It does this so it can verify that the
   * returned contentId is one with the Root as the parent.
   */
  /*@Nullable*/
  ViewDepthProvider getViewDepthProvider(/*@Nullable*/ ViewDepthProvider delegate) {
    if (delegate == null) {
      return null;
    }
    return new ViewDepthProvider() {
      @Override
      public /*@Nullable*/ String getChildViewDepth() {
        String cid = Validators.checkNotNull(delegate).getChildViewDepth();
        synchronized (lock) {
          if (cid == null || root == null) {
            return null;
          }
          String rootId = root.getContentId();
          UpdatableModelChild child = contents.get(cid);
          while (child != null) {
            if (child.getParentId() == null) {
              return null;
            }
            if (rootId.equals(child.getParentId())) {
              return child.getContentId();
            }
            child = contents.get(child.getParentId());
          }
        }
        return null;
      }
    };
  }

  @Override
  public void dump(Dumper dumper) {
    synchronized (lock) {
      dumper.title(TAG);
      dumper.forKey("currentState").value(currentState);
      dumper.forKey("contentCount").value(contents.size()).compactPrevious();
      dumper.forKey("containers").value(containers.size()).compactPrevious();
      dumper.forKey("tokens").value(tokens.size()).compactPrevious();
      dumper.forKey("syntheticTokens").value(syntheticTokens.size()).compactPrevious();
      dumper.forKey("observers").value(observers.size()).compactPrevious();
      dumper.forKey("commitCount").value(commitCount);
      dumper.forKey("commitTokenCount").value(commitTokenCount).compactPrevious();
      dumper.forKey("commitUpdateCount").value(commitUpdateCount).compactPrevious();
      dumper.forKey("removeCount").value(removedChildrenCount);
      dumper.forKey("removeScanCount").value(removeScanCount).compactPrevious();
      if (root != null) {
        // This is here to satisfy the nullness checker.
        UpdatableModelChild nonNullRoot = Validators.checkNotNull(root);
        if (nonNullRoot.getType() != Type.FEATURE) {
          dumper.forKey("root").value("[ROOT NOT A FEATURE]");
          dumper.forKey("type").value(nonNullRoot.getType()).compactPrevious();
        } else if (nonNullRoot.getModelFeature().getStreamFeature() != null
            && nonNullRoot.getModelFeature().getStreamFeature().hasContentId()) {
          dumper
              .forKey("root")
              .value(nonNullRoot.getModelFeature().getStreamFeature().getContentId());
        } else {
          dumper.forKey("root").value("[FEATURE NOT DEFINED]");
        }
      } else {
        dumper.forKey("root").value("[UNDEFINED]");
      }
      int singleChild = 0;
      Dumper childDumper = dumper.getChildDumper();
      childDumper.title("Containers With Multiple Children");
      for (Entry<String, ArrayList<UpdatableModelChild>> entry : containers.entrySet()) {
        if (entry.getValue().size() > 1) {
          childDumper.forKey("Container").value(entry.getKey());
          childDumper.forKey("childrenCount").value(entry.getValue().size()).compactPrevious();
        } else {
          singleChild++;
        }
      }
      dumper.forKey("singleChildContainers").value(singleChild);
      dumper.forKey("cursors").value(cursors.size());
      int atEnd = 0;
      int cursorEmptyRefs = 0;
      for (WeakReference<ModelCursorImpl> cursorRef : cursors) {
        ModelCursorImpl cursor = cursorRef.get();
        if (cursor == null) {
          cursorEmptyRefs++;
        } else if (cursor.isAtEnd()) {
          atEnd++;
        }
      }
      dumper.forKey("cursorsRemoved").value(cursorsRemoved).compactPrevious();
      dumper.forKey("reclaimedWeakReferences").value(cursorEmptyRefs).compactPrevious();
      dumper.forKey("cursorsAtEnd").value(atEnd).compactPrevious();

      for (WeakReference<ModelCursorImpl> cursorRef : cursors) {
        ModelCursorImpl cursor = cursorRef.get();
        if (cursor != null && !cursor.isAtEnd()) {
          dumper.dump(cursor);
        }
      }
    }
  }

  @VisibleForTesting
  List<ModelProviderObserver> getObserversToNotify() {
    // Make a copy of the observers, so the observers are not mutated while invoking callbacks.
    // mObservers is locked when adding or removing observers. Also, release the lock before
    // invoking callbacks to avoid deadlocks. ([INTERNAL LINK])
    synchronized (observers) {
      return new ArrayList<>(observers);
    }
  }

  /**
   * Abstract class used by the {@code ModelMutatorCommitter} to modify the model state based upon
   * the current model state and the contents of the mutation. We define mutation handlers for the
   * initialization, for a mutation based upon a continuation token response, and then a standard
   * update mutation. The default implementation is a no-op.
   */
  abstract static class MutationHandler {
    private MutationHandler() {}

    /**
     * Called before processing the children of the mutation. This allows the model to be cleaned up
     * before new children are added.
     */
    void preMutation() {}

    /** Append a child to a parent */
    void appendChild(String parentKey, UpdatableModelChild child) {}

    /** Remove a child from a parent */
    void removeChild(String parentKey, UpdatableModelChild child) {}

    /**
     * This is called after the model has been updated. Typically this will notify observers of the
     * changes made during the mutation.
     */
    void postMutation() {}
  }

  /** This is the {@code ModelMutatorCommitter} which updates the model. */
  private final Committer<Void, Change> committer =
      new Committer<Void, Change>() {
        @Override
        public Void commit(Change change) {
          Logger.i(
              TAG,
              "FeedModelProvider - committer, structure changes %s, update changes %s",
              change.structureChanges.size(),
              change.updateChanges.size());
          threadUtils.checkNotMainThread();
          ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
          commitCount++;

          if (change.streamSession != null) {
            streamSession = change.streamSession;
            synchronized (lock) {
              if (delayedTriggerRefresh) {
                delayedTriggerRefresh = false;
                mainThreadRunner.execute(TAG + " TriggerRefresh", () -> triggerRefresh());
              }
            }
          }

          int tokenStart = 0;
          int tokenPageSize = initialPageSize;
          synchronized (lock) {
            if (root != null) {
              ArrayList<UpdatableModelChild> rootChildren = containers.get(root.getContentId());
              if (rootChildren != null) {
                tokenStart = rootChildren.size() - 1;
                tokenPageSize = pageSize;
              }
            }
          }

          // All appends and updates are considered unbound (childrenToBind) and will need to be
          // sent to the ModelChildBinder.
          Map<String, UpdatableModelChild> appendedChildren = new HashMap<>();
          List<UpdatableModelChild> childrenToBind = new ArrayList<>();
          boolean removedChildren = false;
          for (StreamStructure structureChange : change.structureChanges) {
            if (filterPredicate != null && !filterPredicate.test(structureChange)) {
              continue;
            }
            if (structureChange.getOperation() == Operation.UPDATE_OR_APPEND) {
              String contentId = structureChange.getContentId();
              UpdatableModelChild child =
                  new UpdatableModelChild(contentId, structureChange.getParentContentId());
              appendedChildren.put(contentId, child);
              childrenToBind.add(child);
            } else if (structureChange.getOperation() == Operation.REMOVE) {
              removedChildren = true;
            }
          }

          RemoveTracking<?> removeTracking = null;
          if (removeTrackingFactory != null && change.mutationContext != null && removedChildren) {
            removeTracking = removeTrackingFactory.create(change.mutationContext);
          }

          synchronized (lock) {
            // Add the updates to the childrenToBind
            for (StreamStructure updatedChild : change.updateChanges) {
              UpdatableModelChild child = contents.get(updatedChild.getContentId());
              if (child != null) {
                childrenToBind.add(child);
              } else {
                Logger.w(TAG, "child %s was not found for updating", updatedChild.getContentId());
              }
            }
          }

          // Mutate the Model
          MutationHandler mutationHandler =
              getMutationHandler(change.updateChanges, change.mutationContext);
          processMutation(
              mutationHandler, change.structureChanges, appendedChildren, removeTracking);

          if (removeTracking != null) {
            // Update the UI on the main thread.
            mainThreadRunner.execute(
                TAG + " removeTracking", removeTracking::triggerConsumerUpdate);
          }

          synchronized (lock) {
            if (shouldInsertSyntheticToken()) {
              SyntheticTokenTracker tokenTracker =
                  new SyntheticTokenTracker(
                      Validators.checkNotNull(root), tokenStart, tokenPageSize);
              childrenToBind = tokenTracker.insertToken();
            }
          }

          bindChildrenAndTokens(
              childrenToBind,
              (result) -> {
                if (result.isSuccessful()) {
                  mutationHandler.postMutation();
                } else {
                  Logger.e(TAG, "bindChildrenAndTokens failed, not processing mutation");
                  invalidate();
                }
              });
          timeTracker.stop("", "modelProviderCommit");
          StreamToken token =
              (change.mutationContext != null)
                  ? change.mutationContext.getContinuationToken()
                  : null;
          Logger.i(
              TAG,
              "ModelProvider Mutation committed - structure changes %s, childrenToBind %s, "
                  + "removedChildren %s, Token %s",
              change.structureChanges.size(),
              childrenToBind.size(),
              removedChildren,
              token != null);
          return null;
        }

        /** Returns a MutationHandler for processing the mutation */
        private MutationHandler getMutationHandler(
            List<StreamStructure> updatedChildren, /*@Nullable*/ MutationContext mutationContext) {
          synchronized (lock) {
            StreamToken mutationSourceToken =
                mutationContext != null ? mutationContext.getContinuationToken() : null;
            MutationHandler mutationHandler;
            if (currentState == State.INITIALIZING) {
              Validators.checkState(
                  mutationSourceToken == null,
                  "Initializing the Model Provider from a Continuation Token");
              mutationHandler = new InitializeModel();
            } else if (mutationSourceToken != null) {
              mutationHandler = new TokenMutation(mutationSourceToken);
              commitTokenCount++;
            } else {
              mutationHandler = new UpdateMutation(updatedChildren);
              commitUpdateCount++;
            }
            return mutationHandler;
          }
        }

        /** Process the structure changes to update the model. */
        void processMutation(
            MutationHandler mutationHandler,
            List<StreamStructure> structureChanges,
            Map<String, UpdatableModelChild> appendedChildren,
            /*@Nullable*/ RemoveTracking<?> removeTracking) {

          ElapsedTimeTracker timeTracker = timingUtils.getElapsedTimeTracker(TAG);
          int appends = 0;
          int removes = 0;
          synchronized (lock) {
            // Processing before the structural mutation
            mutationHandler.preMutation();

            // process the structure changes
            String currentParentKey = null;
            ArrayList<UpdatableModelChild> childrenList = null;
            for (StreamStructure structure : structureChanges) {
              if (structure.getOperation() == Operation.UPDATE_OR_APPEND) {
                UpdatableModelChild modelChild = appendedChildren.get(structure.getContentId());
                if (modelChild == null) {
                  Logger.w(TAG, "Didn't find update child for %s", structure.getContentId());
                  continue;
                }
                if (!modelChild.hasParentId()) {
                  if (!createRoot(modelChild)) {
                    Logger.e(TAG, "Root update failed, invalidating model");
                    Logger.i(
                        TAG,
                        "Moving %s to INVALIDATED",
                        (streamSession != null)
                            ? streamSession.getStreamToken()
                            : "No streamSession");
                    currentState = State.INVALIDATED;
                    mainThreadRunner.execute(
                        TAG + " multipleRootInvalidation",
                        () -> {
                          List<ModelProviderObserver> observerList = getObserversToNotify();
                          for (ModelProviderObserver observer : observerList) {
                            observer.onSessionFinished();
                          }
                        });
                    return;
                  }
                  contents.put(modelChild.getContentId(), modelChild);
                  appends++;
                  continue;
                }

                String parentKey = Validators.checkNotNull(modelChild.getParentId());
                if (!parentKey.equals(currentParentKey)) {
                  childrenList = getChildList(parentKey);
                  currentParentKey = parentKey;
                }
                if (childrenList == null) {
                  Logger.e(TAG, "childrenList was not set");
                  continue;
                }
                childrenList.add(modelChild);
                contents.put(modelChild.getContentId(), modelChild);
                appends++;

                mutationHandler.appendChild(parentKey, modelChild);
              } else if (structure.getOperation() == Operation.REMOVE) {
                handleRemoveOperation(mutationHandler, structure, removeTracking);
                removes++;
                contents.remove(structure.getContentId());
              }
            }
          }
          timeTracker.stop("", "modelMutation", "appends", appends, "removes", removes);
        }
      };

  private void bindChildrenAndTokens(
      List<UpdatableModelChild> childrenToBind, Consumer<Result<Void>> consumer) {
    // Bind the unbound children
    modelChildBinder.bindChildren(childrenToBind, consumer);

    synchronized (lock) {
      // Track any tokens we added to the tree
      for (UpdatableModelChild child : childrenToBind) {
        if (child.getType() == Type.TOKEN) {
          String parent = child.getParentId();
          if (parent == null) {
            Logger.w(
                TAG,
                "Found a token for a child %s without a parent, ignoring",
                child.getContentId());
            continue;
          }
          ArrayList<UpdatableModelChild> childrenList = getChildList(parent);
          TokenTracking tokenTracking =
              new TokenTracking(child.getUpdatableModelToken(), parent, childrenList);
          tokens.put(child.getModelToken().getStreamToken().getNextPageToken(), tokenTracking);
        }
      }
    }
  }

  private boolean shouldInsertSyntheticToken() {
    synchronized (lock) {
      return (root != null && initialPageSize > 0);
    }
  }

  /** Class which handles Synthetic tokens within the root children list. */
  @VisibleForTesting
  final class SyntheticTokenTracker {
    private final List<UpdatableModelChild> childrenToBind = new ArrayList<>();
    private final UpdatableModelChild pagingChild;
    private final int startingPosition;
    private final int endPosition;
    private final boolean insertToken;

    private UpdatableModelChild tokenChild;

    SyntheticTokenTracker(UpdatableModelChild pagingChild, int startingPosition, int pageSize) {
      this.pagingChild = pagingChild;

      if (startingPosition < 0) {
        startingPosition = 0;
      }
      List<UpdatableModelChild> children = containers.get(pagingChild.getContentId());
      if (children == null) {
        Logger.e(TAG, "Paging child doesn't not have children");
        this.startingPosition = 0;
        this.endPosition = 0;
        this.insertToken = false;
        return;
      }
      int start = startingPosition;
      int end = startingPosition + pageSize;
      if (children.size() <= start) {
        Logger.e(
            TAG,
            "SyntheticTokenTrack to start track beyond child count, start %s, child length %s",
            startingPosition,
            children.size());
        // Bind everything
        start = 0;
        end = children.size();
      } else if (start + pageSize > children.size()
          || start + pageSize + minPageSize > children.size()) {
        end = children.size();
      }
      this.startingPosition = start;
      this.endPosition = end;
      this.insertToken = end < children.size();
    }

    /** Returns the UpdatableModelChild which represents the synthetic token added to the model. */
    UpdatableModelChild getTokenChild() {
      return tokenChild;
    }

    /** Insert a synthetic token into the tree. */
    List<UpdatableModelChild> insertToken() {
      ElapsedTimeTracker tt = timingUtils.getElapsedTimeTracker(TAG);
      traverse(pagingChild, startingPosition, endPosition);
      if (insertToken) {
        synchronized (lock) {
          ArrayList<UpdatableModelChild> rootChildren = containers.get(pagingChild.getContentId());
          if (rootChildren != null) {
            tokenChild = getSyntheticToken();
            rootChildren.add(endPosition, tokenChild);
            syntheticTokens.put(tokenChild.getContentId(), this);
            Logger.i(
                TAG,
                "Inserting a Synthetic Token %s at %s",
                tokenChild.getContentId(),
                endPosition);
          } else {
            Logger.e(TAG, "Unable to find paging node's children");
          }
        }
      }
      tt.stop("", "syntheticTokens");
      return childrenToBind;
    }

    /** Handle the synthetic token */
    void handleSyntheticToken(UpdatableModelToken token) {
      StreamToken streamToken = token.getStreamToken();
      SyntheticTokenTracker tracker;
      synchronized (lock) {
        tracker = syntheticTokens.remove(streamToken.getContentId());
      }
      if (tracker == null) {
        Logger.e(TAG, "SyntheticTokenTracker was not found");
        return;
      }

      UpdatableModelChild tokenChild = tracker.getTokenChild();
      Logger.i(TAG, "Found Token %s", tokenChild != null);
      UpdatableModelChild currentRoot;
      synchronized (lock) {
        currentRoot = root;
      }
      if (tokenChild != null && currentRoot != null) {
        List<UpdatableModelChild> rootChildren;
        synchronized (lock) {
          rootChildren = containers.get(currentRoot.getContentId());
        }
        if (rootChildren != null) {
          int pos = rootChildren.indexOf(tokenChild);
          if (pos > 0) {
            rootChildren.remove(pos);
            SyntheticTokenTracker tokenTracker =
                new SyntheticTokenTracker(currentRoot, pos, pageSize);
            List<UpdatableModelChild> childrenToBind = tokenTracker.insertToken();
            List<UpdatableModelChild> cursorSublist =
                rootChildren.subList(pos, rootChildren.size());

            // Bind the unbound children
            bindChildrenAndTokens(
                childrenToBind,
                (bindingCount) -> {
                  ModelCursorImpl cursor =
                      new ModelCursorImpl(streamToken.getParentId(), cursorSublist);

                  TokenCompleted tokenCompleted = new TokenCompleted(cursor);
                  mainThreadRunner.execute(
                      TAG + " onTokenChange",
                      () -> {
                        List<TokenCompletedObserver> observerList = token.getObserversToNotify();
                        for (TokenCompletedObserver observer : observerList) {
                          observer.onTokenCompleted(tokenCompleted);
                        }
                      });
                });
          }
        }
      }
    }

    private void traverse(UpdatableModelChild node, int start, int end) {
      synchronized (lock) {
        if (node.getType() == Type.UNBOUND) {
          childrenToBind.add(node);
        }
        String nodeId = node.getContentId();
        List<UpdatableModelChild> children = containers.get(nodeId);
        if (children != null && !children.isEmpty()) {
          int maxChildren = Math.min(end, children.size());
          for (int i = start; i < maxChildren; i++) {
            UpdatableModelChild child = children.get(i);
            traverse(child, 0, Integer.MAX_VALUE);
          }
        }
      }
    }

    private UpdatableModelChild getSyntheticToken() {
      synchronized (lock) {
        UpdatableModelChild r = Validators.checkNotNull(root);
        String contentId = SYNTHETIC_TOKEN_PREFIX + UUID.randomUUID();
        StreamToken streamToken = StreamToken.newBuilder().setContentId(contentId).build();
        UpdatableModelChild modelChild = new UpdatableModelChild(contentId, r.getContentId());
        modelChild.bindToken(new UpdatableModelToken(streamToken, true));
        return modelChild;
      }
    }
  }

  private void handleRemoveOperation(
      MutationHandler mutationHandler,
      StreamStructure removeChild,
      /*@Nullable*/ RemoveTracking<?> removeTracking) {
    if (!removeChild.hasParentContentId()) {
      // It shouldn't be legal to remove the root, that is what CLEAR_HEAD is for.
      Logger.e(TAG, "** Unable to remove the root element");
      return;
    }

    if (removeTracking != null) {
      synchronized (lock) {
        UpdatableModelChild child = contents.get(removeChild.getContentId());
        if (child != null) {
          traverseNode(child, removeTracking);
        } else {
          Logger.w(TAG, "Didn't find child %s to do RemoveTracking", removeChild.getContentId());
        }
      }
    }
    synchronized (lock) {
      String parentKey = removeChild.getParentContentId();
      List<UpdatableModelChild> childList = containers.get(parentKey);
      if (childList == null) {
        if (!removeChild.hasParentContentId()) {
          Logger.w(TAG, "Remove of root is not yet supported");
        } else {
          Logger.w(TAG, "Parent of removed item is not found");
        }
        return;
      }

      // For FEATURE children, add the remove to the mutation handler to create the
      // StreamFeatureChange.  We skip this for TOKENS.
      String childKey = removeChild.getContentId();
      UpdatableModelChild targetChild = contents.get(childKey);
      if (targetChild == null) {
        if (childKey.startsWith(SYNTHETIC_TOKEN_PREFIX)) {
          Logger.i(TAG, "Remove Synthetic Token");
          SyntheticTokenTracker token = syntheticTokens.get(childKey);
          if (token != null) {
            targetChild = token.getTokenChild();
            mutationHandler.removeChild(parentKey, targetChild);
            syntheticTokens.remove(childKey);
          } else {
            Logger.e(TAG, "Unable to find synthetic token %s", childKey);
            return;
          }
        } else {
          Logger.e(TAG, "Child %s not found in the ModelProvider contents", childKey);
          return;
        }
      }
      if (targetChild.getType() == Type.FEATURE) {
        mutationHandler.removeChild(parentKey, targetChild);
      } else if (targetChild.getType() == Type.TOKEN) {
        mutationHandler.removeChild(parentKey, targetChild);
      }

      // This walks the child list backwards because the most common removal item is a
      // token which is always the last item in the list.  removeScanCount tracks if we are
      // walking the list too much
      ListIterator<UpdatableModelChild> li = childList.listIterator(childList.size());
      UpdatableModelChild removed = null;
      while (li.hasPrevious()) {
        removeScanCount++;
        UpdatableModelChild child = li.previous();
        if (child.getContentId().equals(childKey)) {
          removed = child;
          break;
        }
      }

      if (removed != null) {
        childList.remove(removed);
        removedChildrenCount++;
      } else {
        Logger.w(TAG, "Child to be removed was not found");
      }
    }
  }

  /**
   * This {@link MutationHandler} handles the initial mutation populating the model. No update
   * events are triggered. When the model is updated, we trigger a Session Started event.
   */
  @VisibleForTesting
  final class InitializeModel extends MutationHandler {
    @Override
    public void postMutation() {
      Logger.i(
          TAG,
          "Moving %s to READY",
          (streamSession != null) ? streamSession.getStreamToken() : "No streamSession");
      synchronized (lock) {
        currentState = State.READY;
      }
      mainThreadRunner.execute(
          TAG + " onSessionStart",
          () -> {
            List<ModelProviderObserver> observerList = getObserversToNotify();
            for (ModelProviderObserver observer : observerList) {
              observer.onSessionStart();
            }
          });
    }
  }

  /**
   * This {@link MutationHandler} handles a mutation based upon a continuation token. For a token we
   * will not generate changes for the parent updated by the token. Instead, the new children are
   * appended and a {@link TokenCompleted} will be triggered.
   */
  @VisibleForTesting
  final class TokenMutation extends MutationHandler {
    private final StreamToken mutationSourceToken;
    /*@Nullable*/ TokenTracking token = null;
    int newCursorStart = -1;

    TokenMutation(StreamToken mutationSourceToken) {
      this.mutationSourceToken = mutationSourceToken;
    }

    @VisibleForTesting
    TokenTracking getTokenTrackingForTest() {
      synchronized (lock) {
        return Validators.checkNotNull(tokens.get(mutationSourceToken.getNextPageToken()));
      }
    }

    @Override
    public void preMutation() {
      synchronized (lock) {
        token = tokens.remove(mutationSourceToken.getNextPageToken());
        if (token == null) {
          Logger.e(TAG, "Token was not found, positioning to end of list");
          return;
        }
        // adjust the location because we will remove the token
        newCursorStart = token.location.size() - 1;
      }
    }

    @Override
    public void postMutation() {
      if (token == null) {
        Logger.e(TAG, "Token was not found, mutation is being ignored");
        return;
      }
      ModelCursorImpl cursor =
          new ModelCursorImpl(
              token.parentContentId, token.location.subList(newCursorStart, token.location.size()));
      TokenCompleted tokenCompleted = new TokenCompleted(cursor);
      mainThreadRunner.execute(
          TAG + " onTokenChange",
          () -> {
            if (token != null) {
              List<TokenCompletedObserver> observerList = token.tokenChild.getObserversToNotify();
              for (TokenCompletedObserver observer : observerList) {
                observer.onTokenCompleted(tokenCompleted);
              }
            }
          });
    }
  }

  /**
   * {@code MutationHandler} which handles updates. All changes are tracked for the UI through
   * {@link FeatureChange}. One will be created for each {@link ModelFeature} that changed. There
   * are two types of changes, the content and changes to the children (structure).
   */
  @VisibleForTesting
  final class UpdateMutation extends MutationHandler {

    private final List<StreamStructure> updates;
    private final Map<String, FeatureChangeImpl> changes = new HashMap<>();
    private final Set<String> newParents = new HashSet<>();

    UpdateMutation(List<StreamStructure> updates) {
      this.updates = updates;
    }

    @Override
    public void preMutation() {
      Logger.i(TAG, "Updating %s items", updates.size());
      // Walk all the updates and update the values, creating changes to track these
      for (StreamStructure update : updates) {
        FeatureChangeImpl change = getChange(update.getContentId());
        if (change != null) {
          change.setFeatureChanged(true);
        }
      }
    }

    @Override
    public void removeChild(String parentKey, UpdatableModelChild child) {
      FeatureChangeImpl change = getChange(parentKey);
      if (change != null) {
        change.getChildChangesImpl().removeChild(child);
      }
    }

    @Override
    public void appendChild(String parentKey, UpdatableModelChild child) {
      // Is this a child of a node that is new to the model?  We only report changes
      // to existing ModelFeatures.
      String childKey = child.getContentId();
      if (newParents.contains(parentKey)) {
        // Don't create a change the child of a new child
        newParents.add(childKey);
        return;
      }

      newParents.add(childKey);
      FeatureChangeImpl change = getChange(parentKey);
      if (change != null) {
        change.getChildChangesImpl().addAppendChild(child);
      }
    }

    @Override
    public void postMutation() {
      synchronized (lock) {
        // Update the cursors before we notify the UI
        List<WeakReference<ModelCursorImpl>> removeList = new ArrayList<>();
        for (WeakReference<ModelCursorImpl> cursorRef : cursors) {
          ModelCursorImpl cursor = cursorRef.get();
          if (cursor != null) {
            FeatureChange change = changes.get(cursor.getParentContentId());
            if (change != null) {
              cursor.updateIterator(change);
            }
          } else {
            removeList.add(cursorRef);
          }
        }
        cursorsRemoved += removeList.size();
        cursors.removeAll(removeList);
      }

      // Update the Observers on the UI Thread
      mainThreadRunner.execute(
          TAG + " onFeatureChange",
          () -> {
            for (FeatureChangeImpl change : changes.values()) {
              List<FeatureChangeObserver> observerList =
                  ((UpdatableModelFeature) change.getModelFeature()).getObserversToNotify();
              for (FeatureChangeObserver observer : observerList) {
                observer.onChange(change);
              }
            }
          });
    }

    /*@Nullable*/
    private FeatureChangeImpl getChange(String contentIdKey) {
      FeatureChangeImpl change = changes.get(contentIdKey);
      if (change == null) {
        UpdatableModelChild modelChild;
        synchronized (lock) {
          modelChild = contents.get(contentIdKey);
        }
        if (modelChild == null) {
          Logger.e(TAG, "Didn't find '%s' in content", contentIdKey);
          return null;
        }
        if (modelChild.getType() == Type.UNBOUND) {
          Logger.e(TAG, "Looking for unbound child %s, ignore child", modelChild.getContentId());
          return null;
        }
        change = new FeatureChangeImpl(modelChild.getModelFeature());
        changes.put(contentIdKey, change);
      }
      return change;
    }
  }

  // This method will return true if it sets/updates root
  private boolean createRoot(UpdatableModelChild child) {
    synchronized (lock) {
      // this must be a root
      if (child.getType() == Type.FEATURE || child.getType() == Type.UNBOUND) {
        if (root != null) {
          // For multiple roots, check to see if they have the same content id, if so then ignore
          // the new root.  Otherwise, invalidate the model because we don't support multiple roots
          if (root.getContentId().equals(child.getContentId())) {
            Logger.w(TAG, "Multiple Roots - duplicate root is ignored");
            return true;
          } else {
            Logger.e(
                TAG,
                "Found multiple roots [%s, %s] which is not supported.  Invalidating model",
                Validators.checkNotNull(root).getContentId(),
                child.getContentId());
            return false;
          }
        }
        root = child;
      } else {
        // continuation tokens can not be roots.
        Logger.e(TAG, "Invalid Root, type %s", child.getType());
        return false;
      }
      return true;
    }
  }

  // Lazy creation of containers
  private ArrayList<UpdatableModelChild> getChildList(String parentKey) {
    synchronized (lock) {
      if (!containers.containsKey(parentKey)) {
        containers.put(parentKey, new ArrayList<>());
      }
      return containers.get(parentKey);
    }
  }

  private void traverseNode(UpdatableModelChild node, RemoveTracking<?> removeTracking) {
    if (node.getType() == Type.FEATURE) {
      removeTracking.filterStreamFeature(node.getModelFeature().getStreamFeature());
      synchronized (lock) {
        List<UpdatableModelChild> children = containers.get(node.getContentId());
        if (children != null) {
          for (UpdatableModelChild child : children) {
            traverseNode(child, removeTracking);
          }
        }
      }
    }
  }

  /** Track the continuation token location and model */
  @VisibleForTesting
  static final class TokenTracking {
    final UpdatableModelToken tokenChild;
    final String parentContentId;
    final ArrayList<UpdatableModelChild> location;

    TokenTracking(
        UpdatableModelToken tokenChild,
        String parentContentId,
        ArrayList<UpdatableModelChild> location) {
      this.tokenChild = tokenChild;
      this.parentContentId = parentContentId;
      this.location = location;
    }
  }

  // test only method for returning a copy of the tokens map
  @VisibleForTesting
  Map<ByteString, TokenTracking> getTokensForTest() {
    synchronized (lock) {
      return new HashMap<>(tokens);
    }
  }

  @VisibleForTesting
  boolean getDelayedTriggerRefreshForTest() {
    synchronized (lock) {
      return delayedTriggerRefresh;
    }
  }

  // test only method for verifying the synthetic tokens
  Map<String, SyntheticTokenTracker> getSyntheticTokensForTest() {
    synchronized (lock) {
      return new HashMap<>(syntheticTokens);
    }
  }
}
