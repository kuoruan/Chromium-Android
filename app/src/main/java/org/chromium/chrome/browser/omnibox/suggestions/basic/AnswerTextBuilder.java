// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions.basic;

import android.content.res.Resources;
import android.graphics.Paint;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.components.omnibox.AnswerTextType;
import org.chromium.components.omnibox.SuggestionAnswer;

import java.util.List;

/**
 * Helper class that builds Spannables to represent the styled text in answers from Answers in
 * Suggest.
 */
class AnswerTextBuilder {
    private static final String TAG = "AnswerTextBuilder";

    private static final int ANSWERS_TOP_ALIGNED_TEXT_SIZE_SP = 12;
    private static final int ANSWERS_DESCRIPTION_TEXT_NEGATIVE_SIZE_SP = 16;
    private static final int ANSWERS_DESCRIPTION_TEXT_POSITIVE_SIZE_SP = 16;
    private static final int ANSWERS_SUGGESTION_TEXT_SIZE_SP = 16;
    private static final int ANSWERS_PERSONALIZED_SUGGESTION_TEXT_SIZE_SP = 15;
    private static final int ANSWERS_ANSWER_TEXT_MEDIUM_SIZE_SP = 20;
    private static final int ANSWERS_ANSWER_TEXT_LARGE_SIZE_SP = 24;
    private static final int ANSWERS_SECONDARY_TEXT_SMALL_SIZE_SP = 12;
    private static final int ANSWERS_SECONDARY_TEXT_MEDIUM_SIZE_SP = 14;

    /**
     * Builds a Spannable containing all of the styled text in the supplied ImageLine.
     *
     * @param line All text fields within this line will be added to the returned Spannable.
     *             types.
     * @param density Screen density which will be used to properly size and layout images and top-
     *                aligned text.
     */
    static Spannable buildSpannable(
            SuggestionAnswer.ImageLine line, float density, boolean useNewAnswerLayout) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // Determine the height of the largest text element in the line.  This
        // will be used to top-align text and scale images.
        int maxTextHeightSp = getMaxTextHeightSp(line);

        List<SuggestionAnswer.TextField> textFields = line.getTextFields();
        for (int i = 0; i < textFields.size(); i++) {
            appendAndStyleText(
                    builder, textFields.get(i), maxTextHeightSp, density, useNewAnswerLayout);
        }
        if (line.hasAdditionalText()) {
            builder.append("  ");
            SuggestionAnswer.TextField additionalText = line.getAdditionalText();
            appendAndStyleText(
                    builder, additionalText, maxTextHeightSp, density, useNewAnswerLayout);
        }
        if (line.hasStatusText()) {
            builder.append("  ");
            SuggestionAnswer.TextField statusText = line.getStatusText();
            appendAndStyleText(builder, statusText, maxTextHeightSp, density, useNewAnswerLayout);
        }

        return builder;
    }

    /**
     * Determine the height of the largest text field in the entire line.
     *
     * @param line An ImageLine containing the text fields.
     * @return The height in SP.
     */
    static int getMaxTextHeightSp(SuggestionAnswer.ImageLine line) {
        int maxHeightSp = 0;

        List<SuggestionAnswer.TextField> textFields = line.getTextFields();
        for (int i = 0; i < textFields.size(); i++) {
            int height = getAnswerTextSizeSp(textFields.get(i).getType());
            if (height > maxHeightSp) {
                maxHeightSp = height;
            }
        }
        if (line.hasAdditionalText()) {
            int height = getAnswerTextSizeSp(line.getAdditionalText().getType());
            if (height > maxHeightSp) {
                maxHeightSp = height;
            }
        }
        if (line.hasStatusText()) {
            int height = getAnswerTextSizeSp(line.getStatusText().getType());
            if (height > maxHeightSp) {
                maxHeightSp = height;
            }
        }

        return maxHeightSp;
    }

    /**
     * Append the styled text in textField to the supplied builder.
     *
     * @param builder The builder to append the text to.
     * @param textField The text field (with text and type) to append.
     * @param maxTextHeightSp The height in SP of the largest text field in the entire line. Used to
     *                        top-align text when specified.
     * @param density Screen density which will be used to properly size and layout images and top-
     *                aligned text.
     */
    @SuppressWarnings("deprecation") // Update usage of Html.fromHtml when API min is 24
    private static void appendAndStyleText(SpannableStringBuilder builder,
            SuggestionAnswer.TextField textField, int maxTextHeightSp, float density,
            boolean useNewAnswerLayout) {
        String text = textField.getText();
        int type = textField.getType();

        // Unescape HTML entities (e.g. "&quot;", "&gt;").
        text = Html.fromHtml(text).toString();

        // Append as HTML (answer responses contain simple markup).
        int start = builder.length();
        builder.append(Html.fromHtml(text));
        int end = builder.length();

        // Apply styles according to the type.
        AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(getAnswerTextSizeSp(type), true);
        builder.setSpan(sizeSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        ForegroundColorSpan colorSpan =
                new ForegroundColorSpan(getAnswerTextColor(type, useNewAnswerLayout));
        builder.setSpan(colorSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (type == AnswerTextType.TOP_ALIGNED) {
            TopAlignedSpan topAlignedSpan =
                    new TopAlignedSpan(ANSWERS_TOP_ALIGNED_TEXT_SIZE_SP, maxTextHeightSp, density);
            builder.setSpan(topAlignedSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Return the SP text height for the specified answer text type.
     *
     * @param type The answer type as specified at http://goto.google.com/ais_api.
     */
    private static int getAnswerTextSizeSp(@AnswerTextType int type) {
        switch (type) {
            case AnswerTextType.TOP_ALIGNED:
                return ANSWERS_TOP_ALIGNED_TEXT_SIZE_SP;
            case AnswerTextType.DESCRIPTION_NEGATIVE:
                return ANSWERS_DESCRIPTION_TEXT_NEGATIVE_SIZE_SP;
            case AnswerTextType.DESCRIPTION_POSITIVE:
                return ANSWERS_DESCRIPTION_TEXT_POSITIVE_SIZE_SP;
            case AnswerTextType.SUGGESTION:
                return ANSWERS_SUGGESTION_TEXT_SIZE_SP;
            case AnswerTextType.PERSONALIZED_SUGGESTION:
                return ANSWERS_PERSONALIZED_SUGGESTION_TEXT_SIZE_SP;
            case AnswerTextType.ANSWER_TEXT_MEDIUM:
                return ANSWERS_ANSWER_TEXT_MEDIUM_SIZE_SP;
            case AnswerTextType.ANSWER_TEXT_LARGE:
                return ANSWERS_ANSWER_TEXT_LARGE_SIZE_SP;
            case AnswerTextType.SUGGESTION_SECONDARY_TEXT_SMALL:
                return ANSWERS_SECONDARY_TEXT_SMALL_SIZE_SP;
            case AnswerTextType.SUGGESTION_SECONDARY_TEXT_MEDIUM:
                return ANSWERS_SECONDARY_TEXT_MEDIUM_SIZE_SP;
            default:
                Log.w(TAG, "Unknown answer type: " + type);
                return ANSWERS_SUGGESTION_TEXT_SIZE_SP;
        }
    }

    /**
     * Return the color code for the specified answer text type.
     *
     * @param type The answer type as specified at http://goto.google.com/ais_api.
     */
    private static int getAnswerTextColor(@AnswerTextType int type, boolean useNewAnswerLayout) {
        Resources resources = ContextUtils.getApplicationContext().getResources();
        switch (type) {
            case AnswerTextType.DESCRIPTION_NEGATIVE:
                return ApiCompatibilityUtils.getColor(
                        resources, R.color.answers_description_text_negative);

            case AnswerTextType.DESCRIPTION_POSITIVE:
                return ApiCompatibilityUtils.getColor(
                        resources, R.color.answers_description_text_positive);

            case AnswerTextType.SUGGESTION:
            case AnswerTextType.PERSONALIZED_SUGGESTION:
                if (useNewAnswerLayout) {
                    return ApiCompatibilityUtils.getColor(resources, R.color.answers_answer_text);
                } else {
                    return ApiCompatibilityUtils.getColor(
                            resources, R.color.url_emphasis_default_text);
                }

            case AnswerTextType.TOP_ALIGNED:
            case AnswerTextType.ANSWER_TEXT_MEDIUM:
            case AnswerTextType.ANSWER_TEXT_LARGE:
            case AnswerTextType.SUGGESTION_SECONDARY_TEXT_SMALL:
            case AnswerTextType.SUGGESTION_SECONDARY_TEXT_MEDIUM:
                if (useNewAnswerLayout) {
                    return ApiCompatibilityUtils.getColor(
                            resources, R.color.url_emphasis_default_text);
                } else {
                    return ApiCompatibilityUtils.getColor(resources, R.color.answers_answer_text);
                }

            default:
                Log.w(TAG, "Unknown answer type: " + type);
                return ApiCompatibilityUtils.getColor(resources, R.color.url_emphasis_default_text);
        }
    }

    /**
     * Aligns the top of the spanned text with the top of some other specified text height. This is
     * done by calculating the ascent of both text heights and shifting the baseline of the spanned
     * text by the difference.  As a result, "top aligned" means the top of the ascents are
     * aligned, which looks as expected in most cases (some glyphs in some fonts are drawn above
     * the top of the ascent).
     */
    private static class TopAlignedSpan extends MetricAffectingSpan {
        private final int mTextHeightSp;
        private final int mMaxTextHeightSp;
        private final float mDensity;

        private Integer mBaselineShift;

        /**
         * Constructor for TopAlignedSpan.
         *
         * @param textHeightSp The total height in SP of the text covered by this span.
         * @param maxTextHeightSp The total height in SP of the text we wish to top-align with.
         * @param density The display density.
         */
        public TopAlignedSpan(int textHeightSp, int maxTextHeightSp, float density) {
            mTextHeightSp = textHeightSp;
            mMaxTextHeightSp = maxTextHeightSp;
            mDensity = density;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            initBaselineShift(tp);
            tp.baselineShift += mBaselineShift;
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            initBaselineShift(tp);
            tp.baselineShift += mBaselineShift;
        }

        private void initBaselineShift(TextPaint tp) {
            if (mBaselineShift != null) return;
            Paint.FontMetrics metrics = tp.getFontMetrics();
            float ascentProportion = metrics.ascent / (metrics.top - metrics.bottom);

            int textAscentPx = (int) (mTextHeightSp * ascentProportion * mDensity);
            int maxTextAscentPx = (int) (mMaxTextHeightSp * ascentProportion * mDensity);

            mBaselineShift = -(maxTextAscentPx - textAscentPx); // Up is -y.
        }
    }
}
