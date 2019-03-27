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

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.VisibleForTesting;
import android.support.v4.widget.TextViewCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.ui.LayoutUtils;
import com.google.android.libraries.feed.piet.AdapterFactory.AdapterKeySupplier;
import com.google.android.libraries.feed.piet.DebugLogger.MessageType;
import com.google.android.libraries.feed.piet.host.AssetProvider.GoogleSansTypeface;
import com.google.search.now.ui.piet.ElementsProto.Element;
import com.google.search.now.ui.piet.ElementsProto.TextElement;
import com.google.search.now.ui.piet.ErrorsProto.ErrorCode;
import com.google.search.now.ui.piet.StylesProto;
import com.google.search.now.ui.piet.StylesProto.Font;
import com.google.search.now.ui.piet.StylesProto.StyleIdsStack;
import com.google.search.now.ui.piet.StylesProto.Typeface.CommonTypeface;

import org.chromium.chrome.R;

import java.util.List;

/**
 * Base {@link ElementAdapter} to extend to manage {@code ChunkedText} and {@code ParameterizedText}
 * elements.
 */
abstract class TextElementAdapter extends ElementAdapter<TextView, TextElement> {
  private static final String TAG = "TextElementAdapter";
  private ExtraLineHeight extraLineHeight = ExtraLineHeight.builder().build();

  TextElementAdapter(Context context, AdapterParameters parameters) {
    super(context, parameters, new TextView(context));
  }

  @Override
  protected TextElement getModelFromElement(Element baseElement) {
    if (!baseElement.hasTextElement()) {
      throw new PietFatalException(
          ErrorCode.ERR_MISSING_ELEMENT_CONTENTS,
          String.format("Missing TextElement; has %s", baseElement.getElementsCase()));
    }
    return baseElement.getTextElement();
  }

  @Override
  void onCreateAdapter(TextElement textLine, Element baseElement, FrameContext frameContext) {
    if (getKey() == null) {
      TextElementKey key = createKey(getElementStyle().getFont());
      setKey(key);
      setValuesUsedInRecyclerKey(key, frameContext);
    }

    // Setup the layout of the text lines, including all properties not in the recycler key.
    updateTextStyle();
  }

  private void updateTextStyle() {
    TextView textView = getBaseView();
    StyleProvider textStyle = getElementStyle();
    textView.setTextColor(textStyle.getColor());

    if (textStyle.getFont().hasLineHeight()) {
      textView.setIncludeFontPadding(false);
      textView.setLineSpacing(
          /* add= */ getExtraLineHeight().betweenLinesExtraPx(), /* mult= */ 1.0f);
    } else if (textStyle.getFont().hasLineHeightRatio()) {
      // TODO Remove this code once transition to line height is complete.
      textView.setIncludeFontPadding(false);
      textView.setLineSpacing(/* add= */ 0, textStyle.getFont().getLineHeightRatio());
    }
    setLetterSpacing(textView, textStyle);
    if (textStyle.getMaxLines() > 0) {
      textView.setMaxLines(textStyle.getMaxLines());
      textView.setEllipsize(TextUtils.TruncateAt.END);
    } else {
      // MAX_VALUE is the value used in the Android implementation for the default
      textView.setMaxLines(Integer.MAX_VALUE);
    }

    // TODO: Remove this if statement and always call this first method.
    if (textStyle.getTextAlignment() != (Gravity.START | Gravity.TOP)) {
      getBaseView().setGravity(textStyle.getTextAlignment());
    } else {
      // TODO: Remove this branch
      Integer legacyGravity = getDeprecatedElementGravity();
      if (legacyGravity != null) {
        getBaseView().setGravity(legacyGravity);
      } else {
        getBaseView().setGravity(textStyle.getTextAlignment());
      }
    }
  }

  private void setLetterSpacing(TextView textView, StyleProvider textStyle) {
    if (textStyle.getFont().hasLetterSpacingDp()) {
      float textSize;
      if (textStyle.getFont().hasSize()) {
        textSize = textStyle.getFont().getSize();
      } else {
        textSize = LayoutUtils.pxToSp(textView.getTextSize(), textView.getContext());
      }
      float letterSpacingDp = textStyle.getFont().getLetterSpacingDp();
      float letterSpacingEm = letterSpacingDp / textSize;
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        textView.setLetterSpacing(letterSpacingEm);
        Logger.e(TAG, "got letter spacing: %s", textView.getLetterSpacing());
      } else {
        // Letter spacing wasn't supported before L. We substitute SetTextScaleX, which actually
        // stretches the letters, rather than just adding space between them. It won't look exactly
        // the same, but we can use it to get close to the same width for a set of characters.
        float extraLetterSpaceDp = letterSpacingEm * textSize;
        // It can vary by font and character, but typically letter width is about half of height.
        float approximateLetterwidth = textSize / 2;
        float textScale = (approximateLetterwidth + extraLetterSpaceDp) / approximateLetterwidth;
        textView.setTextScaleX(textScale);
      }
    } else if (textStyle.getFont().hasLetterSpacing()) {
      // TODO Remove this code after Android Piet Gestalt experiment finishes.
      float letterSpacingEm = textStyle.getFont().getLetterSpacing();
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        textView.setLetterSpacing(letterSpacingEm);
      } else {
        // Letter spacing wasn't supported before L. We substitute SetTextScaleX, which actually
        // stretches the letters, rather than just adding space between them. It won't look exactly
        // the same, but we can use it to get close to the same width for a set of characters.
        float textHeight;
        if (textStyle.getFont().hasSize()) {
          textHeight = textStyle.getFont().getSize();
        } else {
          textHeight = LayoutUtils.pxToSp(textView.getTextSize(), textView.getContext());
        }
        float extraLetterSpaceDp = letterSpacingEm * textHeight;
        // It can vary by font and character, but typically letter width is about half of height.
        float approximateLetterwidth = textHeight / 2;
        float textScale = (approximateLetterwidth + extraLetterSpaceDp) / approximateLetterwidth;
        textView.setTextScaleX(textScale);
      }
    }
  }

  @Override
  void onBindModel(TextElement textLine, Element baseElement, FrameContext frameContext) {
    // Set the initial state for the TextView
    // No bindings found, so use the inlined value (or empty if not set)
    setTextOnView(frameContext, textLine);

    if (textLine.getStyleReferences().hasStyleBinding()) {
      updateTextStyle();
    }
  }

  @Override
  StyleIdsStack getSubElementStyleIdsStack() {
    return getModel().getStyleReferences();
  }

  abstract void setTextOnView(FrameContext frameContext, TextElement textElement);

  @Override
  void onUnbindModel() {
    TextView textView = getBaseView();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      textView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
    }
    textView.setText("");
  }

  private float calculateOriginalAndExpectedLineHeightDifference() {
    TextView textView = getBaseView();
    StyleProvider textStyle = getElementStyle();

    float lineHeightGoalSp = textStyle.getFont().getLineHeight();
    float lineHeightGoalPx = LayoutUtils.spToPx(lineHeightGoalSp, textView.getContext());
    float currentHeight = textView.getLineHeight();

    return (lineHeightGoalPx - currentHeight);
  }

  /**
   * Returns a line height object which contains the number of pixels that need to be added between
   * each line, as well as the number of pixels that need to be added to the top and bottom padding
   * of the element in order to match css line height behavior.
   */
  ExtraLineHeight getExtraLineHeight() {
    Font font = getElementStyle().getFont();

    // The line height cannot change in the same text element adapter, so there is no need to
    // calculate this more than once. In fact, it should not be calculated more than once, because
    // if calculateOriginalAndExpectedLineHeightDifference() is called again after adjusting line
    // spacing, it will return 0, even though we still need the original calculation for padding. If
    // it was already calculated or there is no line height set, return the saved object.
    if (extraLineHeight.betweenLinesExtraPx() != 0
        || (!font.hasLineHeight() && !font.hasLineHeightRatio())) {
      return extraLineHeight;
    }

    float extraLineHeightBetweenLinesFloat = calculateOriginalAndExpectedLineHeightDifference();
    int extraLineHeightBetweenLines = Math.round(extraLineHeightBetweenLinesFloat);

    int totalExtraPadding = 0;
    if (font.hasLineHeight()) {
      // Adjust the rounding for the extra top and bottom padding, to make the total height of the
      // text element a little more exact.
      totalExtraPadding = adjustRounding(extraLineHeightBetweenLinesFloat);
    } else if (font.hasLineHeightRatio()) {
      // TODO Remove this code once transition to line height is complete.
      float textSize = getBaseView().getTextSize();
      float extraLineHeightRatio = (font.getLineHeightRatio() - 1.0f);
      totalExtraPadding = (int) (textSize * extraLineHeightRatio);
    }
    int extraPaddingForLineHeightTop = totalExtraPadding / 2;
    int extraPaddingForLineHeightBottom = totalExtraPadding - extraPaddingForLineHeightTop;
    // In API version 21 (Lollipop), the implementation of lineSpacingMultiplier() changed to add
    // no extra space beneath a block of text. Before API 21, we need to subtract the extra
    // padding (so that only half the padding is on the bottom). That means
    // extraPaddingForLineHeightBottom needs to be negative.
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      if (font.hasLineHeight()) {
        int currentBottomPixelsAdded = extraLineHeightBetweenLines;
        extraPaddingForLineHeightBottom =
            -(currentBottomPixelsAdded - extraPaddingForLineHeightBottom);
      } else if (font.hasLineHeightRatio()) {
        // TODO Remove this code once transition to line height is complete.
        extraPaddingForLineHeightBottom = -extraPaddingForLineHeightBottom;
      }
    }

    extraLineHeight =
        ExtraLineHeight.builder()
            .setTopPaddingPx(extraPaddingForLineHeightTop)
            .setBottomPaddingPx(extraPaddingForLineHeightBottom)
            .setBetweenLinesExtraPx(extraLineHeightBetweenLines)
            .build();

    return extraLineHeight;
  }

  /**
   * Rounds the float value away from the nearest integer, i.e. 4.75 rounds to 4, and 7.2 rounds to
   * 8.
   */
  private int adjustRounding(float floatValueToRound) {
    int intWithRegularRounding = Math.round(floatValueToRound);
    // If the regular rounding rounded up, round down with adjusted rounding.
    if (floatValueToRound - (float) intWithRegularRounding < 0) {
      return intWithRegularRounding - 1;
    }
    // If the regular rounding rounded down, round up with adjusted rounding.
    if (floatValueToRound - (float) intWithRegularRounding > 0) {
      return intWithRegularRounding + 1;
    }
    return intWithRegularRounding;
  }

  static class ExtraLineHeight {
    private final int topPaddingPx;
    private final int bottomPaddingPx;
    private final int betweenLinesExtraPx;

    int topPaddingPx() {
      return topPaddingPx;
    }

    int bottomPaddingPx() {
      return bottomPaddingPx;
    }

    int betweenLinesExtraPx() {
      return betweenLinesExtraPx;
    }

    private ExtraLineHeight(Builder builder) {
      this.topPaddingPx = builder.topPaddingPx;
      this.bottomPaddingPx = builder.bottomPaddingPx;
      this.betweenLinesExtraPx = builder.betweenLinesExtraPx;
    }

    static Builder builder() {
      return new ExtraLineHeight.Builder();
    }

    static class Builder {
      private int topPaddingPx;
      private int bottomPaddingPx;
      private int betweenLinesExtraPx;

      Builder setTopPaddingPx(int value) {
        topPaddingPx = value;
        return this;
      }

      Builder setBottomPaddingPx(int value) {
        bottomPaddingPx = value;
        return this;
      }

      Builder setBetweenLinesExtraPx(int value) {
        betweenLinesExtraPx = value;
        return this;
      }

      ExtraLineHeight build() {
        return new ExtraLineHeight(this);
      }
    }
  }

  @VisibleForTesting
  // LINT.IfChange
  void setValuesUsedInRecyclerKey(TextElementKey fontKey, FrameContext frameContext) {
    // TODO: Implement typefaces
    TextView textView = getBaseView();
    textView.setTextSize(fontKey.getSize());

    for (StylesProto.Typeface typeface : fontKey.typefaces) {
      switch (typeface.getTypefaceSpecifierCase()) {
        case COMMON_TYPEFACE:
          if (loadCommonTypeface(
              typeface.getCommonTypeface(), textView, fontKey.isItalic(), frameContext)) {
            return;
          }
          break;
        case CUSTOM_TYPEFACE:
          if (loadCustomTypeface(typeface.getCustomTypeface(), textView, fontKey.isItalic())) {
            return;
          }
          break;
        default:
          // do nothing
      }
    }
    // We didn't load a font, but we can at least respect italicization.
    makeFontItalic(textView, fontKey.isItalic());
  }

  /**
   * Load one of the typefaces from the {@link CommonTypeface} enum.
   *
   * @return true for success, false for failure
   */
  private boolean loadCommonTypeface(
      CommonTypeface commonTypeface,
      TextView textView,
      boolean isItalic,
      FrameContext frameContext) {
    switch (commonTypeface) {
      case PLATFORM_DEFAULT_LIGHT:
        TextViewCompat.setTextAppearance(textView, R.style.gm_font_weight_light);
        break;
      case PLATFORM_DEFAULT_REGULAR:
        TextViewCompat.setTextAppearance(textView, R.style.gm_font_weight_regular);
        break;
      case PLATFORM_DEFAULT_MEDIUM:
        TextViewCompat.setTextAppearance(textView, R.style.gm_font_weight_medium);
        break;
      case GOOGLE_SANS_MEDIUM:
      case GOOGLE_SANS_REGULAR:
        return loadGoogleSans(commonTypeface, textView, isItalic, frameContext);
      default:
        return false;
    }

    makeFontItalic(textView, isItalic);
    return true;
  }

  /**
   * Ask the host to load a typeface by string identifier.
   *
   * @return true for success, false for failure
   */
  private boolean loadCustomTypeface(String customTypeface, TextView textView, boolean isItalic) {
    Typeface hostTypeface =
        getParameters().hostProviders.getAssetProvider().getTypeface(customTypeface, isItalic);
    if (hostTypeface != null) {
      textView.setTypeface(hostTypeface);
      return true;
    }
    return false;
  }

  /**
   * Ask the host to load a Google Sans variant. These typefaces are expected to be present, but
   * can't be included in the Piet library.
   *
   * @return true for success, false for failure
   */
  private boolean loadGoogleSans(
      CommonTypeface googleSansType,
      TextView textView,
      boolean isItalic,
      FrameContext frameContext) {
    boolean success =
        loadCustomTypeface(googleSansEnumToStringDef(googleSansType), textView, isItalic);
    if (!success) {
      frameContext.reportMessage(
          MessageType.ERROR, ErrorCode.ERR_MISSING_FONTS, "Could not load Google Sans");
    }
    return success;
  }
  // LINT.ThenChange

  /**
   * Conversion method to avoid version skew issues if we would ever change the enum names in the
   * CommonTypeface proto, so we don't need to change all the hosts or old clients.
   */
  @VisibleForTesting
  @GoogleSansTypeface
  static String googleSansEnumToStringDef(CommonTypeface googleSansType) {
    switch (googleSansType) {
      case GOOGLE_SANS_MEDIUM:
        return GoogleSansTypeface.GOOGLE_SANS_MEDIUM;
      case GOOGLE_SANS_REGULAR:
        return GoogleSansTypeface.GOOGLE_SANS_REGULAR;
      default:
        return GoogleSansTypeface.UNDEFINED;
    }
  }

  private static void makeFontItalic(TextView textView, boolean isItalic) {
    if (isItalic) {
      textView.setTypeface(textView.getTypeface(), Typeface.ITALIC);
    } else {
      textView.setTypeface(Typeface.create(textView.getTypeface(), Typeface.NORMAL));
    }
  }

  TextElementKey createKey(Font font) {
    return new TextElementKey(font);
  }

  abstract static class TextElementKeySupplier<A extends TextElementAdapter>
      implements AdapterKeySupplier<A, TextElement> {
    @Override
    public TextElementKey getKey(FrameContext frameContext, TextElement model) {
      StyleProvider styleProvider = frameContext.makeStyleFor(model.getStyleReferences());
      return new TextElementKey(styleProvider.getFont());
    }
  }

  /** We will Key TextViews off of the Ellipsizing, Font Size and FontWeight, and Italics. */
  // LINT.IfChange
  static class TextElementKey extends RecyclerKey {
    private final int size;
    private final boolean italic;
    private final List<StylesProto.Typeface> typefaces;

    TextElementKey(Font font) {
      size = font.getSize();
      italic = font.getItalic();
      typefaces = font.getTypefaceList();
    }

    public int getSize() {
      return size;
    }

    public boolean isItalic() {
      return italic;
    }

    @Override
    public int hashCode() {
      // Can't use Objects.hash() as it is only available in KK+ and can't use Guava's impl either.
      int result = size;
      result = 31 * result + (italic ? 1 : 0);
      result = 31 * result + typefaces.hashCode();
      return result;
    }

    @Override
    public boolean equals(/*@Nullable*/ Object obj) {
      if (obj == this) {
        return true;
      }

      if (obj == null) {
        return false;
      }

      if (!(obj instanceof TextElementKey)) {
        return false;
      }

      TextElementKey key = (TextElementKey) obj;
      return key.size == size && key.italic == italic && typefaces.equals(key.typefaces);
    }
  }
  // LINT.ThenChange
}
