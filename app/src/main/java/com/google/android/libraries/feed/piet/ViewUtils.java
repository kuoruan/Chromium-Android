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

import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.v4.view.ViewCompat;
import android.view.Gravity;
import android.view.View;
import com.google.android.libraries.feed.piet.host.ActionHandler;
import com.google.android.libraries.feed.piet.host.ActionHandler.ActionType;
import com.google.search.now.ui.piet.ActionsProto.Actions;
import com.google.search.now.ui.piet.ActionsProto.VisibilityAction;
import com.google.search.now.ui.piet.PietProto.Frame;
import com.google.search.now.ui.piet.StylesProto.GravityHorizontal;
import com.google.search.now.ui.piet.StylesProto.GravityVertical;

import org.chromium.chrome.R;

import java.util.Set;

/** Utility class, providing useful methods to interact with Views. */
public class ViewUtils {
  private static final String TAG = "ViewUtils";

  // TODO: Remove this method; it is only used by
  // ElementAdapter.getDeprecatedElementGravity
  @Deprecated
  static int pietGravityToGravity(
      GravityHorizontal gravityHorizontal, GravityVertical gravityVertical) {
    return gravityHorizontalToGravity(gravityHorizontal)
        | gravityVerticalToGravity(gravityVertical);
  }

  // TODO: Remove this method; it is only used by
  // ElementAdapter.getDeprecatedElementGravity
  @Deprecated
  @SuppressWarnings("UnnecessaryDefaultInEnumSwitch")
  static int gravityHorizontalToGravity(GravityHorizontal gravityHorizontal) {
    switch (gravityHorizontal) {
      case GRAVITY_START:
        return Gravity.START;
      case GRAVITY_CENTER:
        return Gravity.CENTER_HORIZONTAL;
      case GRAVITY_END:
        return Gravity.END;
      case GRAVITY_HORIZONTAL_UNSPECIFIED:
      default:
        return Gravity.START;
    }
  }

  // TODO: Remove this method; it is only used by
  // ElementAdapter.getDeprecatedElementGravity
  @Deprecated
  @SuppressWarnings("UnnecessaryDefaultInEnumSwitch")
  static int gravityVerticalToGravity(GravityVertical gravityVertical) {
    switch (gravityVertical) {
      case GRAVITY_TOP:
        return Gravity.TOP;
      case GRAVITY_MIDDLE:
        return Gravity.CENTER_VERTICAL;
      case GRAVITY_BOTTOM:
        return Gravity.BOTTOM;
      case GRAVITY_VERTICAL_UNSPECIFIED:
      default:
        return Gravity.NO_GRAVITY;
    }
  }

  /**
   * Attaches the onClick action from actions to the view, executed by the handler. In Android M+, a
   * RippleDrawable is added to the foreground of the view, so that a ripple animation happens on
   * each click.
   */
  static void setOnClickActions(Actions actions, View view, FrameContext frameContext) {
    ActionHandler handler = frameContext.getActionHandler();
    if (actions.hasOnLongClickAction()) {
      view.setOnLongClickListener(
          v -> {
            handler.handleAction(
                actions.getOnLongClickAction(),
                ActionType.LONG_CLICK,
                frameContext.getFrame(),
                view,
                null);
            return true;
          });
    } else {
      clearOnLongClickActions(view);
    }
    if (actions.hasOnClickAction()) {
      view.setOnClickListener(
          v -> {
            handler.handleAction(
                actions.getOnClickAction(), ActionType.CLICK, frameContext.getFrame(), view, null);
          });
    } else {
      clearOnClickActions(view);
    }
    // TODO: Implement alternative support for older versions
    if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
      if (actions.hasOnClickAction() || actions.hasOnLongClickAction()) {
        // CAUTION: View.setForeground() is only available in L+
        view.setForeground(view.getContext().getDrawable(R.drawable.piet_clickable_ripple));
      } else {
        view.setForeground(null);
      }
    }
  }

  static void clearOnLongClickActions(View view) {
    view.setOnLongClickListener(null);
    view.setLongClickable(false);
  }

  /** Sets clickability to false. */
  static void clearOnClickActions(View view) {
    if (view.hasOnClickListeners()) {
      view.setOnClickListener(null);
    }

    view.setClickable(false);
  }

  /**
   * Check if this view is visible, trigger actions accordingly, and update set of active actions.
   *
   * <p>Actions are added to activeActions when they trigger, and removed when the condition that
   * caused them to trigger is no longer true. (Ex. a view action will be removed when the view goes
   * off screen)
   *
   * @param view this adapter's view
   * @param viewport the visible viewport
   * @param actions this element's actions, which might be triggered
   * @param actionHandler host-provided handler to execute actions
   * @param frame the parent frame
   * @param activeActions mutable set of currently-triggered actions; this will get updated by this
   *     method as new actions are triggered and old actions are reset.
   */
  static void maybeTriggerViewActions(
      View view,
      View viewport,
      Actions actions,
      ActionHandler actionHandler,
      Frame frame,
      Set<VisibilityAction> activeActions) {
    if (actions.getOnViewActionsCount() == 0 && actions.getOnHideActionsCount() == 0) {
      return;
    }
    // For invisible views, short-cut triggering of hide/show actions.
    if (view.getVisibility() != View.VISIBLE || !ViewCompat.isAttachedToWindow(view)) {
      activeActions.removeAll(actions.getOnViewActionsList());
      for (VisibilityAction visibilityAction : actions.getOnHideActionsList()) {
        if (activeActions.add(visibilityAction)) {
          actionHandler.handleAction(
              visibilityAction.getAction(), ActionType.VIEW, frame, view, null);
        }
      }
      return;
    }

    // Figure out overlap of viewport and view, and trigger based on proportion overlap.
    Rect viewRect = getViewRectOnScreen(view);
    Rect viewportRect = getViewRectOnScreen(viewport);

    if (viewportRect.intersect(viewRect)) {
      int viewArea = viewRect.height() * viewRect.width();
      int visibleArea = viewportRect.height() * viewportRect.width();
      float proportionVisible = ((float) visibleArea) / viewArea;

      for (VisibilityAction visibilityAction : actions.getOnViewActionsList()) {
        if (proportionVisible >= visibilityAction.getProportionVisible()) {
          if (activeActions.add(visibilityAction)) {
            actionHandler.handleAction(
                visibilityAction.getAction(), ActionType.VIEW, frame, view, null);
          }
        } else {
          activeActions.remove(visibilityAction);
        }
      }

      for (VisibilityAction visibilityAction : actions.getOnHideActionsList()) {
        if (proportionVisible < visibilityAction.getProportionVisible()) {
          if (activeActions.add(visibilityAction)) {
            actionHandler.handleAction(
                visibilityAction.getAction(), ActionType.VIEW, frame, view, null);
          }
        } else {
          activeActions.remove(visibilityAction);
        }
      }
    }
  }

  private static Rect getViewRectOnScreen(View view) {
    int[] viewLocation = new int[2];
    view.getLocationOnScreen(viewLocation);

    return new Rect(
        viewLocation[0],
        viewLocation[1],
        viewLocation[0] + view.getWidth(),
        viewLocation[1] + view.getHeight());
  }

  /** Private constructor to prevent instantiation. */
  private ViewUtils() {}
}
