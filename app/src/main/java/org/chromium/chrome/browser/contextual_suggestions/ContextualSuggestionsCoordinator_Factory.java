package org.chromium.chrome.browser.contextual_suggestions;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ContextualSuggestionsCoordinator_Factory
    implements Factory<ContextualSuggestionsCoordinator> {
  private final Provider<ChromeActivity> activityProvider;

  private final Provider<BottomSheetController> bottomSheetControllerProvider;

  private final Provider<TabModelSelector> tabModelSelectorProvider;

  private final Provider<ContextualSuggestionsModel> modelProvider;

  private final Provider<ContextualSuggestionsMediator> mediatorProvider;

  public ContextualSuggestionsCoordinator_Factory(
      Provider<ChromeActivity> activityProvider,
      Provider<BottomSheetController> bottomSheetControllerProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ContextualSuggestionsMediator> mediatorProvider) {
    this.activityProvider = activityProvider;
    this.bottomSheetControllerProvider = bottomSheetControllerProvider;
    this.tabModelSelectorProvider = tabModelSelectorProvider;
    this.modelProvider = modelProvider;
    this.mediatorProvider = mediatorProvider;
  }

  @Override
  public ContextualSuggestionsCoordinator get() {
    return provideInstance(
        activityProvider,
        bottomSheetControllerProvider,
        tabModelSelectorProvider,
        modelProvider,
        mediatorProvider);
  }

  public static ContextualSuggestionsCoordinator provideInstance(
      Provider<ChromeActivity> activityProvider,
      Provider<BottomSheetController> bottomSheetControllerProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ContextualSuggestionsMediator> mediatorProvider) {
    return new ContextualSuggestionsCoordinator(
        activityProvider.get(),
        bottomSheetControllerProvider.get(),
        tabModelSelectorProvider.get(),
        modelProvider.get(),
        mediatorProvider.get());
  }

  public static ContextualSuggestionsCoordinator_Factory create(
      Provider<ChromeActivity> activityProvider,
      Provider<BottomSheetController> bottomSheetControllerProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ContextualSuggestionsMediator> mediatorProvider) {
    return new ContextualSuggestionsCoordinator_Factory(
        activityProvider,
        bottomSheetControllerProvider,
        tabModelSelectorProvider,
        modelProvider,
        mediatorProvider);
  }

  public static ContextualSuggestionsCoordinator newContextualSuggestionsCoordinator(
      ChromeActivity activity,
      BottomSheetController bottomSheetController,
      TabModelSelector tabModelSelector,
      Object model,
      Object mediator) {
    return new ContextualSuggestionsCoordinator(
        activity,
        bottomSheetController,
        tabModelSelector,
        (ContextualSuggestionsModel) model,
        (ContextualSuggestionsMediator) mediator);
  }
}
