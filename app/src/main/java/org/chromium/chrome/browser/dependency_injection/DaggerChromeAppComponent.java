package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.DoubleCheck;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityDisclosure;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityDisclosure_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsCoordinator;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsCoordinator_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsMediator_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModel_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory;
import org.chromium.chrome.browser.contextual_suggestions.EnabledStateMonitor;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class DaggerChromeAppComponent implements ChromeAppComponent {
  private Provider<EnabledStateMonitor> provideEnabledStateMonitorProvider;

  private ChromeAppModule_ProvideLastUsedProfileFactory provideLastUsedProfileProvider;

  private ChromeAppModule_ProvidesChromePreferenceManagerFactory
      providesChromePreferenceManagerProvider;

  private DaggerChromeAppComponent(Builder builder) {
    initialize(builder);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ChromeAppComponent create() {
    return new Builder().build();
  }

  @SuppressWarnings("unchecked")
  private void initialize(final Builder builder) {
    this.provideEnabledStateMonitorProvider =
        DoubleCheck.provider(
            ChromeAppModule_ProvideEnabledStateMonitorFactory.create(builder.chromeAppModule));
    this.provideLastUsedProfileProvider =
        ChromeAppModule_ProvideLastUsedProfileFactory.create(builder.chromeAppModule);
    this.providesChromePreferenceManagerProvider =
        ChromeAppModule_ProvidesChromePreferenceManagerFactory.create(builder.chromeAppModule);
  }

  @Override
  public EnabledStateMonitor getContextualSuggestionsEnabledStateMonitor() {
    return provideEnabledStateMonitorProvider.get();
  }

  @Override
  public ChromeActivityComponent createChromeActivityComponent(
      ChromeActivityCommonsModule module, ContextualSuggestionsModule contextualSuggestionsModule) {
    return new ChromeActivityComponentImpl(module, contextualSuggestionsModule);
  }

  @Override
  public CustomTabActivityComponent createCustomTabActivityComponent(
      ChromeActivityCommonsModule module, ContextualSuggestionsModule contextualSuggestionsModule) {
    return new CustomTabActivityComponentImpl(module, contextualSuggestionsModule);
  }

  public static final class Builder {
    private ChromeAppModule chromeAppModule;

    private Builder() {}

    public ChromeAppComponent build() {
      if (chromeAppModule == null) {
        this.chromeAppModule = new ChromeAppModule();
      }
      return new DaggerChromeAppComponent(this);
    }

    public Builder chromeAppModule(ChromeAppModule chromeAppModule) {
      this.chromeAppModule = Preconditions.checkNotNull(chromeAppModule);
      return this;
    }
  }

  private final class ChromeActivityComponentImpl implements ChromeActivityComponent {
    private ChromeActivityCommonsModule chromeActivityCommonsModule;

    private ContextualSuggestionsModule contextualSuggestionsModule;

    private ChromeActivityCommonsModule_ProvideChromeActivityFactory provideChromeActivityProvider;

    private ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory
        provideBottomSheetControllerProvider;

    private ChromeActivityCommonsModule_ProvideTabModelSelectorFactory
        provideTabModelSelectorProvider;

    @SuppressWarnings("rawtypes")
    private Provider contextualSuggestionsModelProvider;

    private ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory
        provideChromeFullscreenManagerProvider;

    private ChromeActivityCommonsModule_ProvideToolbarManagerFactory provideToolbarManagerProvider;

    private ChromeActivityCommonsModule_ProvideLayoutManagerFactory provideLayoutManagerProvider;

    private ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory
        provideContextualSuggestionsSourceProvider;

    @SuppressWarnings("rawtypes")
    private Provider contextualSuggestionsMediatorProvider;

    private Provider<ContextualSuggestionsCoordinator> contextualSuggestionsCoordinatorProvider;

    private ChromeActivityComponentImpl(
        ChromeActivityCommonsModule module,
        ContextualSuggestionsModule contextualSuggestionsModule) {
      initialize(module, contextualSuggestionsModule);
    }

    @SuppressWarnings("unchecked")
    private void initialize(
        final ChromeActivityCommonsModule module,
        final ContextualSuggestionsModule contextualSuggestionsModule) {
      this.chromeActivityCommonsModule = Preconditions.checkNotNull(module);
      this.provideChromeActivityProvider =
          ChromeActivityCommonsModule_ProvideChromeActivityFactory.create(
              chromeActivityCommonsModule);
      this.provideBottomSheetControllerProvider =
          ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory.create(
              chromeActivityCommonsModule);
      this.provideTabModelSelectorProvider =
          ChromeActivityCommonsModule_ProvideTabModelSelectorFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsModelProvider =
          DoubleCheck.provider(ContextualSuggestionsModel_Factory.create());
      this.provideChromeFullscreenManagerProvider =
          ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory.create(
              chromeActivityCommonsModule);
      this.provideToolbarManagerProvider =
          ChromeActivityCommonsModule_ProvideToolbarManagerFactory.create(
              chromeActivityCommonsModule);
      this.provideLayoutManagerProvider =
          ChromeActivityCommonsModule_ProvideLayoutManagerFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsModule = Preconditions.checkNotNull(contextualSuggestionsModule);
      this.provideContextualSuggestionsSourceProvider =
          ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory.create(
              contextualSuggestionsModule,
              DaggerChromeAppComponent.this.provideLastUsedProfileProvider);
      this.contextualSuggestionsMediatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsMediator_Factory.create(
                  DaggerChromeAppComponent.this.provideLastUsedProfileProvider,
                  provideTabModelSelectorProvider,
                  provideChromeFullscreenManagerProvider,
                  contextualSuggestionsModelProvider,
                  provideToolbarManagerProvider,
                  provideLayoutManagerProvider,
                  DaggerChromeAppComponent.this.provideEnabledStateMonitorProvider,
                  provideContextualSuggestionsSourceProvider));
      this.contextualSuggestionsCoordinatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsCoordinator_Factory.create(
                  provideChromeActivityProvider,
                  provideBottomSheetControllerProvider,
                  provideTabModelSelectorProvider,
                  contextualSuggestionsModelProvider,
                  contextualSuggestionsMediatorProvider));
    }

    @Override
    public ContextualSuggestionsCoordinator getContextualSuggestionsCoordinator() {
      return contextualSuggestionsCoordinatorProvider.get();
    }
  }

  private final class CustomTabActivityComponentImpl implements CustomTabActivityComponent {
    private ChromeActivityCommonsModule chromeActivityCommonsModule;

    private ContextualSuggestionsModule contextualSuggestionsModule;

    private ChromeActivityCommonsModule_ProvideChromeActivityFactory provideChromeActivityProvider;

    private ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory
        provideBottomSheetControllerProvider;

    private ChromeActivityCommonsModule_ProvideTabModelSelectorFactory
        provideTabModelSelectorProvider;

    @SuppressWarnings("rawtypes")
    private Provider contextualSuggestionsModelProvider;

    private ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory
        provideChromeFullscreenManagerProvider;

    private ChromeActivityCommonsModule_ProvideToolbarManagerFactory provideToolbarManagerProvider;

    private ChromeActivityCommonsModule_ProvideLayoutManagerFactory provideLayoutManagerProvider;

    private ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory
        provideContextualSuggestionsSourceProvider;

    @SuppressWarnings("rawtypes")
    private Provider contextualSuggestionsMediatorProvider;

    private Provider<ContextualSuggestionsCoordinator> contextualSuggestionsCoordinatorProvider;

    private ChromeActivityCommonsModule_ProvideResourcesFactory provideResourcesProvider;

    private Provider<TrustedWebActivityDisclosure> trustedWebActivityDisclosureProvider;

    private CustomTabActivityComponentImpl(
        ChromeActivityCommonsModule module,
        ContextualSuggestionsModule contextualSuggestionsModule) {
      initialize(module, contextualSuggestionsModule);
    }

    @SuppressWarnings("unchecked")
    private void initialize(
        final ChromeActivityCommonsModule module,
        final ContextualSuggestionsModule contextualSuggestionsModule) {
      this.chromeActivityCommonsModule = Preconditions.checkNotNull(module);
      this.provideChromeActivityProvider =
          ChromeActivityCommonsModule_ProvideChromeActivityFactory.create(
              chromeActivityCommonsModule);
      this.provideBottomSheetControllerProvider =
          ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory.create(
              chromeActivityCommonsModule);
      this.provideTabModelSelectorProvider =
          ChromeActivityCommonsModule_ProvideTabModelSelectorFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsModelProvider =
          DoubleCheck.provider(ContextualSuggestionsModel_Factory.create());
      this.provideChromeFullscreenManagerProvider =
          ChromeActivityCommonsModule_ProvideChromeFullscreenManagerFactory.create(
              chromeActivityCommonsModule);
      this.provideToolbarManagerProvider =
          ChromeActivityCommonsModule_ProvideToolbarManagerFactory.create(
              chromeActivityCommonsModule);
      this.provideLayoutManagerProvider =
          ChromeActivityCommonsModule_ProvideLayoutManagerFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsModule = Preconditions.checkNotNull(contextualSuggestionsModule);
      this.provideContextualSuggestionsSourceProvider =
          ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory.create(
              contextualSuggestionsModule,
              DaggerChromeAppComponent.this.provideLastUsedProfileProvider);
      this.contextualSuggestionsMediatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsMediator_Factory.create(
                  DaggerChromeAppComponent.this.provideLastUsedProfileProvider,
                  provideTabModelSelectorProvider,
                  provideChromeFullscreenManagerProvider,
                  contextualSuggestionsModelProvider,
                  provideToolbarManagerProvider,
                  provideLayoutManagerProvider,
                  DaggerChromeAppComponent.this.provideEnabledStateMonitorProvider,
                  provideContextualSuggestionsSourceProvider));
      this.contextualSuggestionsCoordinatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsCoordinator_Factory.create(
                  provideChromeActivityProvider,
                  provideBottomSheetControllerProvider,
                  provideTabModelSelectorProvider,
                  contextualSuggestionsModelProvider,
                  contextualSuggestionsMediatorProvider));
      this.provideResourcesProvider =
          ChromeActivityCommonsModule_ProvideResourcesFactory.create(chromeActivityCommonsModule);
      this.trustedWebActivityDisclosureProvider =
          DoubleCheck.provider(
              TrustedWebActivityDisclosure_Factory.create(
                  provideResourcesProvider,
                  DaggerChromeAppComponent.this.providesChromePreferenceManagerProvider));
    }

    @Override
    public ContextualSuggestionsCoordinator getContextualSuggestionsCoordinator() {
      return contextualSuggestionsCoordinatorProvider.get();
    }

    @Override
    public TrustedWebActivityDisclosure getTrustedWebActivityDisclosure() {
      return trustedWebActivityDisclosureProvider.get();
    }
  }
}
