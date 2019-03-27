package org.chromium.chrome.browser.contextual_suggestions;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.ToolbarManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ContextualSuggestionsMediator_Factory
    implements Factory<ContextualSuggestionsMediator> {
  private final Provider<Profile> profileProvider;

  private final Provider<TabModelSelector> tabModelSelectorProvider;

  private final Provider<ChromeFullscreenManager> fullscreenManagerProvider;

  private final Provider<ContextualSuggestionsModel> modelProvider;

  private final Provider<ToolbarManager> toolbarManagerProvider;

  private final Provider<LayoutManager> layoutManagerProvider;

  private final Provider<EnabledStateMonitor> enabledStateMonitorProvider;

  private final Provider<ContextualSuggestionsSource> suggestionSourceProvider;

  public ContextualSuggestionsMediator_Factory(
      Provider<Profile> profileProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ToolbarManager> toolbarManagerProvider,
      Provider<LayoutManager> layoutManagerProvider,
      Provider<EnabledStateMonitor> enabledStateMonitorProvider,
      Provider<ContextualSuggestionsSource> suggestionSourceProvider) {
    this.profileProvider = profileProvider;
    this.tabModelSelectorProvider = tabModelSelectorProvider;
    this.fullscreenManagerProvider = fullscreenManagerProvider;
    this.modelProvider = modelProvider;
    this.toolbarManagerProvider = toolbarManagerProvider;
    this.layoutManagerProvider = layoutManagerProvider;
    this.enabledStateMonitorProvider = enabledStateMonitorProvider;
    this.suggestionSourceProvider = suggestionSourceProvider;
  }

  @Override
  public ContextualSuggestionsMediator get() {
    return provideInstance(
        profileProvider,
        tabModelSelectorProvider,
        fullscreenManagerProvider,
        modelProvider,
        toolbarManagerProvider,
        layoutManagerProvider,
        enabledStateMonitorProvider,
        suggestionSourceProvider);
  }

  public static ContextualSuggestionsMediator provideInstance(
      Provider<Profile> profileProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ToolbarManager> toolbarManagerProvider,
      Provider<LayoutManager> layoutManagerProvider,
      Provider<EnabledStateMonitor> enabledStateMonitorProvider,
      Provider<ContextualSuggestionsSource> suggestionSourceProvider) {
    return new ContextualSuggestionsMediator(
        profileProvider.get(),
        tabModelSelectorProvider.get(),
        fullscreenManagerProvider.get(),
        modelProvider.get(),
        toolbarManagerProvider.get(),
        layoutManagerProvider.get(),
        enabledStateMonitorProvider.get(),
        suggestionSourceProvider);
  }

  public static ContextualSuggestionsMediator_Factory create(
      Provider<Profile> profileProvider,
      Provider<TabModelSelector> tabModelSelectorProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<ContextualSuggestionsModel> modelProvider,
      Provider<ToolbarManager> toolbarManagerProvider,
      Provider<LayoutManager> layoutManagerProvider,
      Provider<EnabledStateMonitor> enabledStateMonitorProvider,
      Provider<ContextualSuggestionsSource> suggestionSourceProvider) {
    return new ContextualSuggestionsMediator_Factory(
        profileProvider,
        tabModelSelectorProvider,
        fullscreenManagerProvider,
        modelProvider,
        toolbarManagerProvider,
        layoutManagerProvider,
        enabledStateMonitorProvider,
        suggestionSourceProvider);
  }

  public static ContextualSuggestionsMediator newContextualSuggestionsMediator(
      Profile profile,
      TabModelSelector tabModelSelector,
      ChromeFullscreenManager fullscreenManager,
      Object model,
      ToolbarManager toolbarManager,
      LayoutManager layoutManager,
      EnabledStateMonitor enabledStateMonitor,
      Provider<ContextualSuggestionsSource> suggestionSourceProvider) {
    return new ContextualSuggestionsMediator(
        profile,
        tabModelSelector,
        fullscreenManager,
        (ContextualSuggestionsModel) model,
        toolbarManager,
        layoutManager,
        enabledStateMonitor,
        suggestionSourceProvider);
  }
}
