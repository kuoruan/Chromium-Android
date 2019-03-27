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

import android.content.Context;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.piet.DebugLogger.MessageType;
import com.google.android.libraries.feed.piet.host.AssetProvider;
import com.google.search.now.ui.piet.ErrorsProto.ErrorCode;
import com.google.search.now.ui.piet.MediaQueriesProto.MediaQueryCondition;
import com.google.search.now.ui.piet.PietProto.PietSharedState;
import com.google.search.now.ui.piet.PietProto.Stylesheet;
import com.google.search.now.ui.piet.PietProto.Template;
import com.google.search.now.ui.piet.StylesProto.BoundStyle;
import com.google.search.now.ui.piet.StylesProto.Style;
import com.google.search.now.ui.piet.StylesProto.StyleIdsStack;
import java.util.List;
import java.util.Map;

/** This class provides support helpers methods for managing styles in Piet. */
public class PietStylesHelper {
  private static final String TAG = "PietStylesHelper";

  private final Map<String, NoKeyOverwriteHashMap<String, Style>> stylesheetScopes =
      new NoKeyOverwriteHashMap<>("Style", ErrorCode.ERR_DUPLICATE_STYLE);
  private final Map<String, Stylesheet> stylesheets =
      new NoKeyOverwriteHashMap<>("Stylesheet", ErrorCode.ERR_DUPLICATE_STYLESHEET);
  private final Map<String, Template> templates =
      new NoKeyOverwriteHashMap<>("Template", ErrorCode.ERR_DUPLICATE_TEMPLATE);

  private final MediaQueryHelper mediaQueryHelper;

  PietStylesHelper(
      List<PietSharedState> pietSharedStates,
      int frameWidthPx,
      AssetProvider assetProvider,
      Context context) {
    mediaQueryHelper = new MediaQueryHelper(frameWidthPx, assetProvider, context);
    for (PietSharedState sharedState : pietSharedStates) {
      if (sharedState.getStylesheetsCount() > 0) {
        for (Stylesheet stylesheet : sharedState.getStylesheetsList()) {
          if (mediaQueryHelper.areMediaQueriesMet(stylesheet.getConditionsList())) {
            stylesheets.put(stylesheet.getStylesheetId(), stylesheet);
          }
        }
      }

      for (Template template : sharedState.getTemplatesList()) {
        if (mediaQueryHelper.areMediaQueriesMet(template.getConditionsList())) {
          templates.put(template.getTemplateId(), template);
        }
      }
    }
  }

  boolean areMediaQueriesMet(List<MediaQueryCondition> conditions) {
    return mediaQueryHelper.areMediaQueriesMet(conditions);
  }

  /*@Nullable*/
  Stylesheet getStylesheet(String stylesheetId) {
    return stylesheets.get(stylesheetId);
  }

  /** Returns a Map of style_id to Style. This represents the Stylesheet. */
  NoKeyOverwriteHashMap<String, Style> getStylesheetMap(String stylesheetId) {
    if (stylesheetScopes.containsKey(stylesheetId)) {
      return stylesheetScopes.get(stylesheetId);
    }
    Stylesheet stylesheet = getStylesheet(stylesheetId);
    if (stylesheet != null) {
      NoKeyOverwriteHashMap<String, Style> styleMap = createMapFromStylesheet(stylesheet);
      stylesheetScopes.put(stylesheet.getStylesheetId(), styleMap);
      return styleMap;
    }
    Logger.w(TAG, "Stylesheet [%s] was not found in the Stylesheet", stylesheetId);
    return createMapFromStylesheet(Stylesheet.getDefaultInstance());
  }

  NoKeyOverwriteHashMap<String, Style> createMapFromStylesheet(Stylesheet stylesheet) {
    NoKeyOverwriteHashMap<String, Style> styleMap =
        new NoKeyOverwriteHashMap<>("Style", ErrorCode.ERR_DUPLICATE_STYLE);
    for (Style style : stylesheet.getStylesList()) {
      if (mediaQueryHelper.areMediaQueriesMet(style.getConditionsList())) {
        styleMap.put(style.getStyleId(), style);
      }
    }
    return styleMap;
  }

  /** Returns a {@link Template} for the template */
  /*@Nullable*/
  public Template getTemplate(String templateId) {
    return templates.get(templateId);
  }

  void addSharedStateTemplatesToFrame(Map<String, Template> frameTemplates) {
    frameTemplates.putAll(templates);
  }

  /**
   * Given a StyleIdsStack, a base style, and a styleMap that contains the styles definition,
   * returns a Style that is the proto-merge of all the styles in the stack, starting with the base.
   */
  static Style mergeStyleIdsStack(
      Style baseStyle,
      StyleIdsStack stack,
      Map<String, Style> styleMap,
      /*@Nullable*/ FrameContext frameContext) {
    Style.Builder mergedStyle = baseStyle.toBuilder();
    for (String style : stack.getStyleIdsList()) {
      if (styleMap.containsKey(style)) {
        mergedStyle.mergeFrom(styleMap.get(style)).build();
      } else {
        String error =
            String.format("Unable to bind style [%s], style not found in Stylesheet", style);
        if (frameContext != null) {
          frameContext.reportMessage(MessageType.ERROR, ErrorCode.ERR_MISSING_STYLE, error);
        }
        Logger.w(TAG, error);
      }
    }
    if (stack.hasStyleBinding()) {
      // LINT.IfChange
      FrameContext localFrameContext =
          checkNotNull(frameContext, "Binding styles not supported when frameContext is null");
      BoundStyle boundStyle = localFrameContext.getStyleFromBinding(stack.getStyleBinding());
      if (boundStyle.hasBackground()) {
        mergedStyle.setBackground(
            mergedStyle.getBackground().toBuilder().mergeFrom(boundStyle.getBackground()));
      }
      if (boundStyle.hasColor()) {
        mergedStyle.setColor(boundStyle.getColor());
      }
      if (boundStyle.hasImageLoadingSettings()) {
        mergedStyle.setImageLoadingSettings(
            mergedStyle
                .getImageLoadingSettings()
                .toBuilder()
                .mergeFrom(boundStyle.getImageLoadingSettings()));
      }
      if (boundStyle.hasScaleType()) {
        mergedStyle.setScaleType(boundStyle.getScaleType());
      }
      // LINT.ThenChange
    }
    return mergedStyle.build();
  }
}
