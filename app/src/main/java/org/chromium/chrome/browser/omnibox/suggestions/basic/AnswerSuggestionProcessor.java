// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions.basic;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.omnibox.suggestions.AnswersImageFetcher;
import org.chromium.chrome.browser.omnibox.suggestions.AutocompleteCoordinator.SuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.OmniboxSuggestion;
import org.chromium.chrome.browser.omnibox.suggestions.OmniboxSuggestionUiType;
import org.chromium.chrome.browser.omnibox.suggestions.SuggestionCommonProperties;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionViewProperties.SuggestionIcon;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionViewProperties.SuggestionTextContainer;
import org.chromium.components.omnibox.AnswerType;
import org.chromium.components.omnibox.SuggestionAnswer;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A class that handles model and view creation for the most commonly used omnibox suggestion. */
public class AnswerSuggestionProcessor implements SuggestionProcessor {
    private final Map<String, List<PropertyModel>> mPendingAnswerRequestUrls;
    private final Context mContext;
    private final SuggestionHost mSuggestionHost;
    private final AnswersImageFetcher mImageFetcher;
    private boolean mEnableNewAnswerLayout;

    /**
     * @param context An Android context.
     * @param suggestionHost A handle to the object using the suggestions.
     */
    public AnswerSuggestionProcessor(Context context, SuggestionHost suggestionHost) {
        mContext = context;
        mSuggestionHost = suggestionHost;
        mPendingAnswerRequestUrls = new HashMap<>();
        mImageFetcher = new AnswersImageFetcher();
    }

    @Override
    public boolean doesProcessSuggestion(OmniboxSuggestion suggestion) {
        return suggestion.hasAnswer();
    }

    @Override
    public void onNativeInitialized() {
        // Experiment: controls presence of certain answer icon types.
        mEnableNewAnswerLayout =
                ChromeFeatureList.isEnabled(ChromeFeatureList.OMNIBOX_NEW_ANSWER_LAYOUT);
    }

    @Override
    public int getViewTypeId() {
        return OmniboxSuggestionUiType.DEFAULT;
    }

    @Override
    public PropertyModel createModelForSuggestion(OmniboxSuggestion suggestion) {
        return new PropertyModel(SuggestionViewProperties.ALL_KEYS);
    }

    @Override
    public void populateModel(OmniboxSuggestion suggestion, PropertyModel model, int position) {
        maybeFetchAnswerIcon(suggestion, model);

        model.set(SuggestionViewProperties.SUGGESTION_ICON_TYPE,
                SuggestionViewProperties.SuggestionIcon.UNDEFINED);
        model.set(SuggestionViewProperties.DELEGATE,
                mSuggestionHost.createSuggestionViewDelegate(suggestion, position));

        setStateForSuggestion(model, suggestion.getAnswer());
    }

    @Override
    public void onUrlFocusChange(boolean hasFocus) {
        if (!hasFocus) mImageFetcher.clearCache();
    }

    private void maybeFetchAnswerIcon(OmniboxSuggestion suggestion, PropertyModel model) {
        ThreadUtils.assertOnUiThread();

        // Attempting to fetch answer data before we have a profile to request it for.
        if (mSuggestionHost.getCurrentProfile() == null) return;

        if (!suggestion.hasAnswer()) return;
        final String url = suggestion.getAnswer().getSecondLine().getImage();
        if (url == null) return;

        // Do not make duplicate answer image requests for the same URL (to avoid generating
        // duplicate bitmaps for the same image).
        if (mPendingAnswerRequestUrls.containsKey(url)) {
            mPendingAnswerRequestUrls.get(url).add(model);
            return;
        }

        List<PropertyModel> models = new ArrayList<>();
        models.add(model);
        mPendingAnswerRequestUrls.put(url, models);
        mImageFetcher.requestAnswersImage(mSuggestionHost.getCurrentProfile(), url,
                new AnswersImageFetcher.AnswersImageObserver() {
                    @Override
                    public void onAnswersImageChanged(Bitmap bitmap) {
                        ThreadUtils.assertOnUiThread();

                        List<PropertyModel> models = mPendingAnswerRequestUrls.remove(url);
                        boolean didUpdate = false;
                        for (int i = 0; i < models.size(); i++) {
                            PropertyModel model = models.get(i);
                            if (!mSuggestionHost.isActiveModel(model)) continue;
                            model.set(SuggestionViewProperties.ANSWER_IMAGE, bitmap);
                            didUpdate = true;
                        }
                        if (didUpdate) mSuggestionHost.notifyPropertyModelsChanged();
                    }
                });
    }

    /**
     * Sets both lines of the Omnibox suggestion based on an Answers in Suggest result.
     */
    private void setStateForSuggestion(PropertyModel model, SuggestionAnswer answer) {
        float density = mContext.getResources().getDisplayMetrics().density;
        SuggestionAnswer.ImageLine firstLine = answer.getFirstLine();
        SuggestionAnswer.ImageLine secondLine = answer.getSecondLine();
        int numAnswerLines = parseNumAnswerLines(secondLine.getTextFields());
        if (numAnswerLines == -1) numAnswerLines = 1;
        model.set(SuggestionViewProperties.IS_ANSWER, true);

        // New answer layout is specific to all definitions except dictionary.
        // There are few changes when compared to classic layout, most notably the order
        // of the items on the card is reverse.
        boolean applyNewLayout =
                mEnableNewAnswerLayout && answer.getType() != AnswerType.DICTIONARY;
        if (applyNewLayout) {
            SuggestionAnswer.ImageLine temp = firstLine;
            firstLine = secondLine;
            secondLine = temp;
        }

        model.set(SuggestionViewProperties.TEXT_LINE_1_SIZING,
                Pair.create(TypedValue.COMPLEX_UNIT_SP,
                        (float) AnswerTextBuilder.getMaxTextHeightSp(firstLine)));
        model.set(SuggestionViewProperties.TEXT_LINE_2_SIZING,
                Pair.create(TypedValue.COMPLEX_UNIT_SP,
                        (float) AnswerTextBuilder.getMaxTextHeightSp(secondLine)));

        model.set(SuggestionViewProperties.TEXT_LINE_1_TEXT,
                new SuggestionTextContainer(
                        AnswerTextBuilder.buildSpannable(firstLine, density, applyNewLayout)));
        model.set(SuggestionViewProperties.TEXT_LINE_2_TEXT,
                new SuggestionTextContainer(
                        AnswerTextBuilder.buildSpannable(secondLine, density, applyNewLayout)));

        model.set(SuggestionViewProperties.TEXT_LINE_1_MAX_LINES,
                applyNewLayout ? 1 : numAnswerLines);
        model.set(SuggestionViewProperties.TEXT_LINE_2_MAX_LINES,
                applyNewLayout ? numAnswerLines : 1);

        model.set(SuggestionViewProperties.TEXT_LINE_1_TEXT_COLOR,
                SuggestionViewViewBinder.getStandardFontColor(
                        mContext, model.get(SuggestionCommonProperties.USE_DARK_COLORS)));
        model.set(SuggestionViewProperties.TEXT_LINE_2_TEXT_COLOR,
                SuggestionViewViewBinder.getStandardFontColor(
                        mContext, model.get(SuggestionCommonProperties.USE_DARK_COLORS)));

        model.set(SuggestionViewProperties.TEXT_LINE_1_TEXT_DIRECTION, View.TEXT_DIRECTION_INHERIT);
        model.set(SuggestionViewProperties.TEXT_LINE_2_TEXT_DIRECTION, View.TEXT_DIRECTION_INHERIT);

        model.set(SuggestionViewProperties.HAS_ANSWER_IMAGE, secondLine.hasImage());

        model.set(SuggestionViewProperties.REFINABLE, true);

        @SuggestionIcon
        int icon = SuggestionIcon.MAGNIFIER;
        if (mEnableNewAnswerLayout) {
            switch (answer.getType()) {
                case AnswerType.DICTIONARY:
                    icon = SuggestionIcon.DICTIONARY;
                    break;
                case AnswerType.FINANCE:
                    icon = SuggestionIcon.FINANCE;
                    break;
                case AnswerType.KNOWLEDGE_GRAPH:
                    icon = SuggestionIcon.KNOWLEDGE;
                    break;
                case AnswerType.SUNRISE:
                    icon = SuggestionIcon.SUNRISE;
                    break;
                case AnswerType.TRANSLATION:
                    icon = SuggestionIcon.TRANSLATION;
                    break;
                case AnswerType.WEATHER:
                    icon = SuggestionIcon.WEATHER;
                    break;
                case AnswerType.WHEN_IS:
                    icon = SuggestionIcon.EVENT;
                    break;
                case AnswerType.CURRENCY:
                    icon = SuggestionIcon.CURRENCY;
                    break;
                case AnswerType.SPORTS:
                    icon = SuggestionIcon.SPORTS;
            }
        }

        model.set(SuggestionViewProperties.SUGGESTION_ICON_TYPE, icon);
    }

    private static int parseNumAnswerLines(List<SuggestionAnswer.TextField> textFields) {
        for (int i = 0; i < textFields.size(); i++) {
            if (textFields.get(i).hasNumLines()) {
                return Math.min(3, textFields.get(i).getNumLines());
            }
        }
        return -1;
    }
}
