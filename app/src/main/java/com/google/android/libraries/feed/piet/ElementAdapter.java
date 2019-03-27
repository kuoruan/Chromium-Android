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

package com.google.android.libraries.feed.piet;

import static com.google.android.libraries.feed.common.Validators.checkNotNull;
import static com.google.android.libraries.feed.piet.StyleProvider.DIMENSION_NOT_SET;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.piet.DebugLogger.MessageType;
import com.google.search.now.ui.piet.ActionsProto.Actions;
import com.google.search.now.ui.piet.ActionsProto.VisibilityAction;
import com.google.search.now.ui.piet.ElementsProto.Element;
import com.google.search.now.ui.piet.ElementsProto.ElementStack;
import com.google.search.now.ui.piet.ElementsProto.Visibility;
import com.google.search.now.ui.piet.ElementsProto.VisibilityState;
import com.google.search.now.ui.piet.ErrorsProto.ErrorCode;
import com.google.search.now.ui.piet.StylesProto.StyleIdsStack;
import java.util.HashSet;
import java.util.Set;

/**
 * ElementAdapter provides a base class for the various adapters created by UiElement, and provides
 * handling for overlays and any other general UiElement features. This class enforces the life
 * cycle of the BoundViewAdapter.
 *
 * @param <V> A subclass of an Android View. This is the root level view the adapter is responsible
 *     for creating. Once created, it should have the same lifetime as the adapter.
 * @param <M> The model. This is the model type which is bound to the view by the adapter.
 */
abstract class ElementAdapter<V extends View, M> {
  private static final String TAG = "ElementAdapter";
  private final Context context;
  private final AdapterParameters parameters;
  private final V view;
  /*@Nullable*/ private M model;
  private Element baseElement = Element.getDefaultInstance();
  /*@Nullable*/ private RecyclerKey key;
  private StyleProvider elementStyle;
  private boolean created = false;
  private boolean binding = false;

  /*@Nullable*/ private FrameLayout wrapperView = null;

  // TODO: Remove support for overlays on Element
  @Deprecated @VisibleForTesting /*@Nullable*/ ElementStackAdapter overlayAdapter = null;

  Actions actions = Actions.getDefaultInstance();
  /** Set of actions that are currently active / triggered so they only get called once. */
  private final Set<VisibilityAction> activeVisibilityActions = new HashSet<>();

  /**
   * Desired width of this element. {@link StyleProvider#DIMENSION_NOT_SET} indicates no explicit
   * width; let the parent view decide.
   */
  int widthPx = DIMENSION_NOT_SET;
  /**
   * Desired height of this element. {@link StyleProvider#DIMENSION_NOT_SET} indicates no explicit
   * height; let the parent view decide.
   */
  int heightPx = DIMENSION_NOT_SET;

  ElementAdapter(Context context, AdapterParameters parameters, V view) {
    this.context = context;
    this.parameters = parameters;
    this.view = view;
    elementStyle = parameters.defaultStyleProvider;
  }

  ElementAdapter(Context context, AdapterParameters parameters, V view, RecyclerKey key) {
    this(context, parameters, view);
    this.key = key;
  }

  /**
   * Returns the View bound to the Adapter, for external use, such as adding to a layout. This is
   * not a valid call until the first Model has been bound to the Adapter.
   */
  public View getView() {
    return wrapperView != null ? wrapperView : view;
  }

  /**
   * Returns the base view of the adapter; the view typed to the subclass's content. This returns a
   * different result than getView when there is a wrapper FrameLayout to support overlays.
   */
  protected V getBaseView() {
    return view;
  }

  /**
   * Sets up an adapter, but does not bind data. After this, the {@link #getView()} and {@link
   * #getKey()} can be called. This method must not be called if a Model is currently bound to the
   * Adapter; call {@link #releaseAdapter()} first. Do not override; override {@link
   * #onCreateAdapter} instead.
   */
  public void createAdapter(Element baseElement, FrameContext frameContext) {
    M model = getModelFromElement(baseElement);
    createAdapter(model, baseElement, frameContext);
  }

  /**
   * Sets up an adapter, but does not bind data. After this, the {@link #getView()} and {@link
   * #getKey()} can be called. This method must not be called if a Model is currently bound to the
   * Adapter; call {@link #releaseAdapter()} first. Do not override; override {@link
   * #onCreateAdapter} instead.
   */
  public void createAdapter(M model, Element baseElement, FrameContext frameContext) {
    this.model = model;
    this.baseElement = baseElement;

    Visibility visibility = getVisibilityForElement(baseElement, frameContext);
    setVisibilityOnView(visibility);
    if (visibility == Visibility.GONE) {
      return;
    }

    elementStyle = frameContext.makeStyleFor(getElementStyleIdsStack());
    setDesiredDimensions();

    onCreateAdapter(model, baseElement, frameContext);

    initializeWrapperViewDependentFeatures(baseElement, frameContext);

    initializeOverflow(baseElement);

    elementStyle.applyElementStyles(this);

    created = true;
  }

  private void initializeWrapperViewDependentFeatures(
      Element baseElement, FrameContext frameContext) {
    if (!elementStyle.hasRoundedCorners(getContext())
        && baseElement.getOverlaysCount() == 0
        && !elementStyle.hasBorders()) {
      return;
    }

    FrameLayout wrapper = elementStyle.createWrapperView(context);
    wrapper.addView(getBaseView());

    if (baseElement.getOverlaysCount() > 0) {
      overlayAdapter =
          getParameters()
              .elementAdapterFactory
              .createOverlayAdapter(baseElement.getOverlaysList(), frameContext);
      overlayAdapter.setLayoutParams(
          new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      wrapper.addView(overlayAdapter.getView());
    }

    elementStyle.addBorders(wrapper, getContext());

    wrapperView = wrapper;
  }

  /** Performs child-class-specific adapter creation; to be overridden. */
  void onCreateAdapter(M model, Element baseElement, FrameContext frameContext) {}

  /**
   * Binds data from a Model to the Adapter, without changing child views or styles. Do not
   * override; override {@link #onBindModel} instead. Binds to an Element, allowing the subclass to
   * pick out the relevant model from the oneof.
   */
  public void bindModel(Element baseElement, FrameContext frameContext) {
    bindModel(getModelFromElement(baseElement), baseElement, frameContext);
  }

  /** Bind the model and initialize overlays. Calls child {@link #onBindModel} methods. */
  void bindModel(M model, Element baseElement, FrameContext frameContext) {
    this.model = model;
    this.baseElement = baseElement;
    binding = true;
    if (getElementStyleIdsStack().hasStyleBinding()) {
      elementStyle = frameContext.makeStyleFor(getElementStyleIdsStack());
    }

    Visibility visibility = getVisibilityForElement(baseElement, frameContext);
    setVisibilityOnView(visibility);
    if (visibility == Visibility.GONE) {
      return;
    }
    if (!created) {
      createAdapter(model, baseElement, frameContext);
    }
    if (!created) {
      // This should not happen; indicates a problem in the logic if createAdapter here runs without
      // successfully creating the adapter.
      throw new PietFatalException(
          ErrorCode.ERR_UNSPECIFIED,
          frameContext.reportMessage(MessageType.ERROR, "Binding uncreated adapter"));
    }

    onBindModel(model, baseElement, frameContext);
    // Sometimes, onBindModel will short-circuit and set the visibility to GONE (ex. when an
    // optional binding is not found). In that case, we don't want to continue binding.
    if (getView().getVisibility() == View.GONE) {
      return;
    }

    bindOverlays(frameContext);

    bindActions(frameContext);

    // Reapply styles if we have style bindings
    if (getElementStyleIdsStack().hasStyleBinding()) {
      elementStyle.applyElementStyles(this);
    }
  }

  /** Extracts the relevant model from the Element oneof. Throws IllegalArgument if not found. */
  abstract M getModelFromElement(Element baseElement);

  /** Performs child-adapter-specific binding logic; to be overridden. */
  void onBindModel(M model, Element baseElement, FrameContext frameContext) {}

  // TODO: Remove this hacky method when overlays are transitioned to ElementStack.
  private void bindOverlays(FrameContext frameContext) {
    if (baseElement.getOverlaysCount() > 0) {
      checkNotNull(overlayAdapter, "Overlays not initialized")
          .bindModel(
              Element.newBuilder()
                  .setElementStack(
                      ElementStack.newBuilder().addAllContents(baseElement.getOverlaysList()))
                  .build(),
              frameContext);
    }
  }

  void bindActions(FrameContext frameContext) {
    // Set up actions. On the server, they are defined either as the actual action, or an
    // "actions_binding". That actions_binding is defined in a template, and the binding value is
    // then looked up in the template invocation.
    switch (baseElement.getActionsDataCase()) {
      case ACTIONS:
        actions = baseElement.getActions();
        ViewUtils.setOnClickActions(actions, getBaseView(), frameContext);
        break;
      case ACTIONS_BINDING:
        // Get the actions from the actions_binding in the template
        actions = frameContext.getActionsFromBinding(baseElement.getActionsBinding());
        ViewUtils.setOnClickActions(actions, getBaseView(), frameContext);
        break;
      default: // No actions defined.
        actions = Actions.getDefaultInstance();
        ViewUtils.clearOnClickActions(getBaseView());
        ViewUtils.clearOnLongClickActions(getBaseView());
    }
    setHideActionsActive();
  }

  /** Intended to be called in onCreate when view is offscreen; sets hide actions to active. */
  void setHideActionsActive() {
    activeVisibilityActions.clear();
    // Set all "hide" actions as active, since the view is currently off screen.
    activeVisibilityActions.addAll(actions.getOnHideActionsList());
  }

  private void initializeOverflow(Element baseElement) {
    switch (baseElement.getHorizontalOverflow()) {
      case OVERFLOW_HIDDEN:
      case OVERFLOW_UNSPECIFIED:
        // These are the default behavior
        break;
      default:
        // TODO: Use the standard Piet error codes: ERR_UNSUPPORTED_FEATURE
        Logger.w(TAG, "Unsupported overflow behavior: %s", baseElement.getHorizontalOverflow());
    }
  }

  /**
   * Gets the style of the Element's content (ex. TextElement). Only valid after model has been
   * bound.
   */
  StyleProvider getElementStyle() {
    if (model == null) {
      Logger.wtf(TAG, "getElementStyle called when adapter is not bound");
    }
    return elementStyle;
  }

  /** Returns any styles associated with the bound Element. */
  StyleIdsStack getElementStyleIdsStack() {
    if (baseElement.hasStyleReferences()) {
      return baseElement.getStyleReferences();
    } else {
      // TODO: Remove this branch.
      return getSubElementStyleIdsStack();
    }
  }

  /**
   * Returns any styles associated with the bound sub-Element.
   *
   * <p>To be overridden by sub-Elements.
   *
   * @deprecated We are removing the sub-Element style.
   */
  // TODO: Remove this method.
  @Deprecated
  abstract StyleIdsStack getSubElementStyleIdsStack();

  /**
   * Unbinds the current Model from the Adapter. The Adapter will unbind all contained Adapters
   * recursively. Do not override; override {@link #onUnbindModel} instead.
   */
  public void unbindModel() {
    onUnbindModel();
    model = null;
    baseElement = Element.getDefaultInstance();
    binding = false;

    // Unset actions
    ViewUtils.clearOnLongClickActions(getBaseView());
    ViewUtils.clearOnClickActions(getBaseView());

    if (overlayAdapter != null) {
      overlayAdapter.unbindModel();
    }
  }

  /** Performs child-adapter-specific unbind logic; to be overridden. */
  void onUnbindModel() {}

  /**
   * Releases all child adapters to recycler pools. Do not override; override {@link
   * #onReleaseAdapter} instead.
   */
  public void releaseAdapter() {
    if (!created) {
      return;
    }

    onReleaseAdapter();

    // Destroy overlays
    if (wrapperView != null) {
      wrapperView.removeAllViews();
      wrapperView = null;
    }
    ElementAdapterFactory factory = getParameters().elementAdapterFactory;
    if (overlayAdapter != null) {
      factory.releaseAdapter(overlayAdapter);
      overlayAdapter = null;
    }

    setVisibilityOnView(Visibility.VISIBLE);

    widthPx = DIMENSION_NOT_SET;
    heightPx = DIMENSION_NOT_SET;

    created = false;
  }

  /** Performs child-adapter-specific release logic; to be overridden. */
  void onReleaseAdapter() {}

  /**
   * Subclasses will call this method when they are first bound to a Model to set the Key based upon
   * the Model type.
   */
  void setKey(RecyclerKey key) {
    this.key = key;
  }

  /** Returns a {@link RecyclerKey} which represents an instance of a Model based upon a type. */
  /*@Nullable*/
  public RecyclerKey getKey() {
    // There are Adapters which don't hold on to their views.  How do we want to mark them?
    return key;
  }

  /**
   * Returns the desired width of the adapter, computed from the model at bind time, or {@link
   * StyleProvider#DIMENSION_NOT_SET} which implies that the parent can choose the width.
   */
  int getComputedWidthPx() {
    return widthPx;
  }

  /**
   * Returns the desired height of the adapter, computed from the model at bind time, or {@link
   * StyleProvider#DIMENSION_NOT_SET} which implies that the parent can choose the height.
   */
  int getComputedHeightPx() {
    return heightPx;
  }

  /** Sets the adapter's desired dimensions based on the style. */
  private void setDesiredDimensions() {
    widthPx = elementStyle.getWidthSpecPx(context);
    heightPx = elementStyle.getHeightSpecPx(context);
  }

  /** Sets the layout parameters on this adapter's view. */
  public void setLayoutParams(LayoutParams layoutParams) {
    View view = getView();
    if (view == null) {
      return;
    }
    view.setLayoutParams(layoutParams);
    View baseView = getBaseView();
    if (baseView != null && baseView != view) {
      int height =
          layoutParams.height == LayoutParams.WRAP_CONTENT
              ? LayoutParams.WRAP_CONTENT
              : LayoutParams.MATCH_PARENT;
      int width =
          layoutParams.width == LayoutParams.WRAP_CONTENT
              ? LayoutParams.WRAP_CONTENT
              : LayoutParams.MATCH_PARENT;
      baseView.setLayoutParams(new FrameLayout.LayoutParams(width, height));
    }
  }

  AdapterParameters getParameters() {
    return parameters;
  }

  Context getContext() {
    return context;
  }

  /** Returns the {@code model}, but is only legal to call when the Adapter is bound to a model */
  M getModel() {
    return checkNotNull(model);
  }

  /** Returns the {@code model}, or null if the adapter is not bound. */
  /*@Nullable*/
  M getRawModel() {
    return model;
  }

  /** Return the Element's desired vertical placement within its container. */
  int getVerticalGravity(int defaultGravity) {
    if (model == null) {
      return defaultGravity;
    }
    int gravity = getElementStyle().getGravityVertical(defaultGravity);
    // TODO: Remove this fallback to legacy behavior
    if (!getElementStyle().hasGravityVertical() && baseElement.hasGravityVertical()) {
      gravity = ViewUtils.gravityVerticalToGravity(baseElement.getGravityVertical());
    }
    return gravity;
  }

  /** Return the Element's desired horizontal placement within its container. */
  int getHorizontalGravity(int defaultGravity) {
    if (model == null) {
      return defaultGravity;
    }
    int gravity = getElementStyle().getGravityHorizontal(defaultGravity);
    // TODO: Remove this fallback to legacy behavior
    if (!getElementStyle().hasGravityHorizontal() && baseElement.hasGravityHorizontal()) {
      gravity = ViewUtils.gravityHorizontalToGravity(baseElement.getGravityHorizontal());
    }
    return gravity;
  }

  /** Return the Element's desired horizontal and vertical placement within its container. */
  int getGravity(int defaultGravity) {
    if (model == null) {
      return defaultGravity;
    }
    int gravity = getElementStyle().getGravity(defaultGravity);
    // TODO: Remove this fallback to legacy behavior
    if (!getElementStyle().hasGravityHorizontal() && !getElementStyle().hasGravityVertical()) {
      Integer deprecatedGravity = getDeprecatedElementGravity();
      if (deprecatedGravity != null) {
        gravity = deprecatedGravity;
      }
    }
    return gravity;
  }

  /**
   * In a previous implementation, TextElement used the Element gravity for text alignment; this was
   * messy as Element gravity was used for both text positioning and Element positioning. The
   * Element gravity is now deprecated, but we're going to use it for backwards-compatibility on
   * text to avoid breaking teams that currently rely on the old behavior. Going forward, no one
   * will look at Element gravity - this is replaced by gravity on Style and text alignment on
   * Style.
   */
  // TODO: Remove this method
  @Deprecated
  /*@Nullable*/
  Integer getDeprecatedElementGravity() {
    // Only return something if there's actually gravity on the Element.
    if (!baseElement.hasGravityHorizontal() && !baseElement.hasGravityVertical()) {
      return null;
    }
    return ViewUtils.pietGravityToGravity(
        baseElement.getGravityHorizontal(), baseElement.getGravityVertical());
  }

  ParameterizedTextEvaluator getTemplatedStringEvaluator() {
    return parameters.templatedStringEvaluator;
  }

  @VisibleForTesting
  Visibility getVisibilityForElement(Element element, FrameContext frameContext) {
    VisibilityState visibilityState = element.getVisibilityState();
    if (binding && visibilityState.hasOverridingBoundVisibility()) {
      /*@Nullable*/
      Visibility overridingVisibility =
          frameContext.getVisibilityFromBinding(visibilityState.getOverridingBoundVisibility());
      if (overridingVisibility != null) {
        return overridingVisibility;
      }
    }
    return visibilityState.getDefaultVisibility();
  }

  void setVisibilityOnView(Visibility visibility) {
    switch (visibility) {
      case INVISIBLE:
        view.setVisibility(View.INVISIBLE);
        break;
      case GONE:
        view.setVisibility(View.GONE);
        break;
      case VISIBILITY_UNSPECIFIED:
      case VISIBLE:
        view.setVisibility(View.VISIBLE);
        break;
      default:
        throw new PietFatalException(
            ErrorCode.ERR_MISSING_OR_UNHANDLED_CONTENT,
            String.format("Unhandled visibility: %s", visibility));
    }
  }

  void triggerViewActions(View viewport, FrameContext frameContext) {
    ViewUtils.maybeTriggerViewActions(
        getView(),
        viewport,
        actions,
        frameContext.getActionHandler(),
        frameContext.getFrame(),
        activeVisibilityActions);
  }
}
