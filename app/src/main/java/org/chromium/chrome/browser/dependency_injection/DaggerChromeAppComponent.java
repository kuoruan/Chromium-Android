package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.DoubleCheck;
import dagger.internal.Preconditions;
import dagger.internal.SingleCheck;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.AppHooksModule;
import org.chromium.chrome.browser.AppHooksModule_ProvideCustomTabsConnectionFactory;
import org.chromium.chrome.browser.AppHooksModule_ProvideExternalAuthUtilsFactory;
import org.chromium.chrome.browser.AppHooksModule_ProvideMultiWindowUtilsFactory;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.WebContentsFactory_Factory;
import org.chromium.chrome.browser.browserservices.ClearDataDialogResultRecorder;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityUmaRecorder;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityUmaRecorder_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityCoordinator;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityCoordinator_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.ClientAppDataRecorder;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.ClientAppDataRecorder_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityDisclosureController_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityOpenTimeRecorder;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityOpenTimeRecorder_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityToolbarController;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityToolbarController_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityDisclosureView;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityDisclosureView_Factory;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityToolbarView;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityToolbarView_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsCoordinator;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsCoordinator_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsMediator_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModel_Factory;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule;
import org.chromium.chrome.browser.contextual_suggestions.ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory;
import org.chromium.chrome.browser.contextual_suggestions.EnabledStateMonitor;
import org.chromium.chrome.browser.customtabs.CloseButtonNavigator;
import org.chromium.chrome.browser.customtabs.CloseButtonNavigator_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabBottomBarDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabBottomBarDelegate_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabBrowserControlsVisibilityDelegate_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabDelegateFactory;
import org.chromium.chrome.browser.customtabs.CustomTabDelegateFactory_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabObserver;
import org.chromium.chrome.browser.customtabs.CustomTabObserver_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabTabPersistencePolicy;
import org.chromium.chrome.browser.customtabs.CustomTabTabPersistencePolicy_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabTopBarDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabTopBarDelegate_Factory;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar_Factory;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabController;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabController_Factory;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabFactory;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabFactory_Factory;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityComponent;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityModule;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityModule_ProvideClientAppDataRegisterFactory;
import org.chromium.chrome.browser.customtabs.dependency_injection.CustomTabActivityModule_ProvideIntentDataProviderFactory;
import org.chromium.chrome.browser.customtabs.dynamicmodule.ActivityDelegate_Factory;
import org.chromium.chrome.browser.customtabs.dynamicmodule.DynamicModuleCoordinator;
import org.chromium.chrome.browser.customtabs.dynamicmodule.DynamicModuleCoordinator_Factory;
import org.chromium.chrome.browser.customtabs.dynamicmodule.DynamicModulePageLoadObserver_Factory;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class DaggerChromeAppComponent implements ChromeAppComponent {
  private ChromeAppModule chromeAppModule;

  private AppHooksModule appHooksModule;

  private ChromeAppModule_ProvidesChromePreferenceManagerFactory
      providesChromePreferenceManagerProvider;

  private Provider<EnabledStateMonitor> provideEnabledStateMonitorProvider;

  private ChromeAppModule_ProvideLastUsedProfileFactory provideLastUsedProfileProvider;

  private ChromeAppModule_ProvideContextFactory provideContextProvider;

  private AppHooksModule_ProvideExternalAuthUtilsFactory provideExternalAuthUtilsProvider;

  private AppHooksModule_ProvideMultiWindowUtilsFactory provideMultiWindowUtilsProvider;

  private ChromeAppModule_ProvideWarmupManagerFactory provideWarmupManagerProvider;

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
    this.chromeAppModule = builder.chromeAppModule;
    this.providesChromePreferenceManagerProvider =
        ChromeAppModule_ProvidesChromePreferenceManagerFactory.create(builder.chromeAppModule);
    this.provideEnabledStateMonitorProvider =
        DoubleCheck.provider(
            ChromeAppModule_ProvideEnabledStateMonitorFactory.create(builder.chromeAppModule));
    this.appHooksModule = builder.appHooksModule;
    this.provideLastUsedProfileProvider =
        ChromeAppModule_ProvideLastUsedProfileFactory.create(builder.chromeAppModule);
    this.provideContextProvider =
        ChromeAppModule_ProvideContextFactory.create(builder.chromeAppModule);
    this.provideExternalAuthUtilsProvider =
        AppHooksModule_ProvideExternalAuthUtilsFactory.create(builder.appHooksModule);
    this.provideMultiWindowUtilsProvider =
        AppHooksModule_ProvideMultiWindowUtilsFactory.create(builder.appHooksModule);
    this.provideWarmupManagerProvider =
        ChromeAppModule_ProvideWarmupManagerFactory.create(builder.chromeAppModule);
  }

  @Override
  public CustomTabsConnection resolveCustomTabsConnection() {
    return AppHooksModule_ProvideCustomTabsConnectionFactory.proxyProvideCustomTabsConnection();
  }

  @Override
  public ChromePreferenceManager resolvePreferenceManager() {
    return ChromeAppModule_ProvidesChromePreferenceManagerFactory
        .proxyProvidesChromePreferenceManager(chromeAppModule);
  }

  @Override
  public ClearDataDialogResultRecorder resolveTwaClearDataDialogRecorder() {
    return new ClearDataDialogResultRecorder(
        DoubleCheck.lazy(providesChromePreferenceManagerProvider),
        ChromeAppModule_ProvideChromeBrowserInitializerFactory.proxyProvideChromeBrowserInitializer(
            chromeAppModule),
        new TrustedWebActivityUmaRecorder());
  }

  @Override
  public EnabledStateMonitor resolveContextualSuggestionsEnabledStateMonitor() {
    return provideEnabledStateMonitorProvider.get();
  }

  @Override
  public ExternalAuthUtils resolveExternalAuthUtils() {
    return AppHooksModule_ProvideExternalAuthUtilsFactory.proxyProvideExternalAuthUtils(
        appHooksModule);
  }

  @Override
  public ChromeActivityComponent createChromeActivityComponent(
      ChromeActivityCommonsModule module, ContextualSuggestionsModule contextualSuggestionsModule) {
    return new ChromeActivityComponentImpl(module, contextualSuggestionsModule);
  }

  @Override
  public CustomTabActivityComponent createCustomTabActivityComponent(
      ChromeActivityCommonsModule module,
      ContextualSuggestionsModule contextualSuggestionsModule,
      CustomTabActivityModule customTabActivityModule) {
    return new CustomTabActivityComponentImpl(
        module, contextualSuggestionsModule, customTabActivityModule);
  }

  public static final class Builder {
    private ChromeAppModule chromeAppModule;

    private AppHooksModule appHooksModule;

    private Builder() {}

    public ChromeAppComponent build() {
      if (chromeAppModule == null) {
        this.chromeAppModule = new ChromeAppModule();
      }
      if (appHooksModule == null) {
        this.appHooksModule = new AppHooksModule();
      }
      return new DaggerChromeAppComponent(this);
    }

    public Builder chromeAppModule(ChromeAppModule chromeAppModule) {
      this.chromeAppModule = Preconditions.checkNotNull(chromeAppModule);
      return this;
    }

    public Builder appHooksModule(AppHooksModule appHooksModule) {
      this.appHooksModule = Preconditions.checkNotNull(appHooksModule);
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

    private ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory
        provideLifecycleDispatcherProvider;

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
      this.provideLifecycleDispatcherProvider =
          ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsCoordinatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsCoordinator_Factory.create(
                  provideChromeActivityProvider,
                  provideBottomSheetControllerProvider,
                  provideTabModelSelectorProvider,
                  contextualSuggestionsModelProvider,
                  contextualSuggestionsMediatorProvider,
                  provideLifecycleDispatcherProvider));
    }

    @Override
    public ChromeAppComponent getParent() {
      return DaggerChromeAppComponent.this;
    }

    @Override
    public ContextualSuggestionsCoordinator resolveContextualSuggestionsCoordinator() {
      return contextualSuggestionsCoordinatorProvider.get();
    }
  }

  private final class CustomTabActivityComponentImpl implements CustomTabActivityComponent {
    private ChromeActivityCommonsModule chromeActivityCommonsModule;

    private ContextualSuggestionsModule contextualSuggestionsModule;

    private CustomTabActivityModule customTabActivityModule;

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

    private ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory
        provideLifecycleDispatcherProvider;

    private Provider<ContextualSuggestionsCoordinator> contextualSuggestionsCoordinatorProvider;

    private Provider<TrustedWebActivityModel> trustedWebActivityModelProvider;

    private CustomTabActivityModule_ProvideClientAppDataRegisterFactory
        provideClientAppDataRegisterProvider;

    private Provider<ClientAppDataRecorder> clientAppDataRecorderProvider;

    private CustomTabActivityModule_ProvideIntentDataProviderFactory provideIntentDataProvider;

    private Provider<TabObserverRegistrar> tabObserverRegistrarProvider;

    private ChromeActivityCommonsModule_ProvideActivityTabProviderFactory
        provideActivityTabProvider;

    private Provider<TrustedWebActivityVerifier> trustedWebActivityVerifierProvider;

    private TrustedWebActivityDisclosureController_Factory
        trustedWebActivityDisclosureControllerProvider;

    private Provider<TrustedWebActivityToolbarController>
        trustedWebActivityToolbarControllerProvider;

    private Provider<CustomTabBrowserControlsVisibilityDelegate>
        customTabBrowserControlsVisibilityDelegateProvider;

    private Provider<TrustedWebActivityToolbarView> trustedWebActivityToolbarViewProvider;

    private ChromeActivityCommonsModule_ProvideResourcesFactory provideResourcesProvider;

    private ChromeActivityCommonsModule_ProvideSnackbarManagerFactory
        provideSnackbarManagerProvider;

    private Provider<TrustedWebActivityDisclosureView> trustedWebActivityDisclosureViewProvider;

    private Provider<TrustedWebActivityOpenTimeRecorder> trustedWebActivityOpenTimeRecorderProvider;

    private Provider<CloseButtonNavigator> closeButtonNavigatorProvider;

    private Provider<TrustedWebActivityCoordinator> trustedWebActivityCoordinatorProvider;

    private ActivityDelegate_Factory activityDelegateProvider;

    private Provider<CustomTabTopBarDelegate> customTabTopBarDelegateProvider;

    private Provider<CustomTabBottomBarDelegate> customTabBottomBarDelegateProvider;

    private Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider;

    private ChromeActivityCommonsModule_ProvideTabContentManagerFactory
        provideTabContentManagerProvider;

    private ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory
        provideCompositorViewHolderProvider;

    private Provider<CustomTabTabPersistencePolicy> customTabTabPersistencePolicyProvider;

    private ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory
        provideActivityWindowAndroidProvider;

    private Provider<CustomTabActivityTabFactory> customTabActivityTabFactoryProvider;

    private Provider<CustomTabObserver> customTabObserverProvider;

    private Provider<WebContentsFactory> webContentsFactoryProvider;

    private Provider<CustomTabActivityTabController> customTabActivityTabControllerProvider;

    private DynamicModulePageLoadObserver_Factory dynamicModulePageLoadObserverProvider;

    private Provider<DynamicModuleCoordinator> dynamicModuleCoordinatorProvider;

    private CustomTabActivityComponentImpl(
        ChromeActivityCommonsModule module,
        ContextualSuggestionsModule contextualSuggestionsModule,
        CustomTabActivityModule customTabActivityModule) {
      initialize(module, contextualSuggestionsModule, customTabActivityModule);
    }

    @SuppressWarnings("unchecked")
    private void initialize(
        final ChromeActivityCommonsModule module,
        final ContextualSuggestionsModule contextualSuggestionsModule,
        final CustomTabActivityModule customTabActivityModule) {
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
      this.provideLifecycleDispatcherProvider =
          ChromeActivityCommonsModule_ProvideLifecycleDispatcherFactory.create(
              chromeActivityCommonsModule);
      this.contextualSuggestionsCoordinatorProvider =
          DoubleCheck.provider(
              ContextualSuggestionsCoordinator_Factory.create(
                  provideChromeActivityProvider,
                  provideBottomSheetControllerProvider,
                  provideTabModelSelectorProvider,
                  contextualSuggestionsModelProvider,
                  contextualSuggestionsMediatorProvider,
                  provideLifecycleDispatcherProvider));
      this.trustedWebActivityModelProvider =
          DoubleCheck.provider(TrustedWebActivityModel_Factory.create());
      this.customTabActivityModule = Preconditions.checkNotNull(customTabActivityModule);
      this.provideClientAppDataRegisterProvider =
          CustomTabActivityModule_ProvideClientAppDataRegisterFactory.create(
              customTabActivityModule);
      this.clientAppDataRecorderProvider =
          DoubleCheck.provider(
              ClientAppDataRecorder_Factory.create(
                  DaggerChromeAppComponent.this.provideContextProvider,
                  provideClientAppDataRegisterProvider));
      this.provideIntentDataProvider =
          CustomTabActivityModule_ProvideIntentDataProviderFactory.create(customTabActivityModule);
      this.tabObserverRegistrarProvider =
          DoubleCheck.provider(
              TabObserverRegistrar_Factory.create(provideLifecycleDispatcherProvider));
      this.provideActivityTabProvider =
          ChromeActivityCommonsModule_ProvideActivityTabProviderFactory.create(
              chromeActivityCommonsModule);
      this.trustedWebActivityVerifierProvider =
          DoubleCheck.provider(
              TrustedWebActivityVerifier_Factory.create(
                  clientAppDataRecorderProvider,
                  provideIntentDataProvider,
                  AppHooksModule_ProvideCustomTabsConnectionFactory.create(),
                  provideLifecycleDispatcherProvider,
                  tabObserverRegistrarProvider,
                  provideActivityTabProvider));
      this.trustedWebActivityDisclosureControllerProvider =
          TrustedWebActivityDisclosureController_Factory.create(
              DaggerChromeAppComponent.this.providesChromePreferenceManagerProvider,
              trustedWebActivityModelProvider,
              provideLifecycleDispatcherProvider,
              trustedWebActivityVerifierProvider,
              TrustedWebActivityUmaRecorder_Factory.create());
      this.trustedWebActivityToolbarControllerProvider =
          DoubleCheck.provider(
              TrustedWebActivityToolbarController_Factory.create(
                  trustedWebActivityModelProvider,
                  trustedWebActivityVerifierProvider,
                  provideLifecycleDispatcherProvider));
      this.customTabBrowserControlsVisibilityDelegateProvider =
          DoubleCheck.provider(
              CustomTabBrowserControlsVisibilityDelegate_Factory.create(
                  provideChromeFullscreenManagerProvider, provideActivityTabProvider));
      this.trustedWebActivityToolbarViewProvider =
          DoubleCheck.provider(
              TrustedWebActivityToolbarView_Factory.create(
                  provideChromeFullscreenManagerProvider,
                  customTabBrowserControlsVisibilityDelegateProvider,
                  trustedWebActivityModelProvider));
      this.provideResourcesProvider =
          ChromeActivityCommonsModule_ProvideResourcesFactory.create(chromeActivityCommonsModule);
      this.provideSnackbarManagerProvider =
          ChromeActivityCommonsModule_ProvideSnackbarManagerFactory.create(
              chromeActivityCommonsModule);
      this.trustedWebActivityDisclosureViewProvider =
          DoubleCheck.provider(
              TrustedWebActivityDisclosureView_Factory.create(
                  provideResourcesProvider,
                  provideSnackbarManagerProvider,
                  trustedWebActivityModelProvider,
                  provideLifecycleDispatcherProvider));
      this.trustedWebActivityOpenTimeRecorderProvider =
          DoubleCheck.provider(
              TrustedWebActivityOpenTimeRecorder_Factory.create(
                  provideLifecycleDispatcherProvider,
                  trustedWebActivityVerifierProvider,
                  TrustedWebActivityUmaRecorder_Factory.create(),
                  provideActivityTabProvider));
      this.closeButtonNavigatorProvider =
          DoubleCheck.provider(CloseButtonNavigator_Factory.create());
      this.trustedWebActivityCoordinatorProvider =
          DoubleCheck.provider(
              TrustedWebActivityCoordinator_Factory.create(
                  trustedWebActivityDisclosureControllerProvider,
                  trustedWebActivityToolbarControllerProvider,
                  trustedWebActivityToolbarViewProvider,
                  trustedWebActivityDisclosureViewProvider,
                  trustedWebActivityOpenTimeRecorderProvider,
                  trustedWebActivityVerifierProvider,
                  closeButtonNavigatorProvider));
      this.activityDelegateProvider =
          ActivityDelegate_Factory.create(
              provideChromeActivityProvider, provideLifecycleDispatcherProvider);
      this.customTabTopBarDelegateProvider =
          DoubleCheck.provider(
              CustomTabTopBarDelegate_Factory.create(provideChromeActivityProvider));
      this.customTabBottomBarDelegateProvider =
          DoubleCheck.provider(
              CustomTabBottomBarDelegate_Factory.create(
                  provideChromeActivityProvider,
                  provideIntentDataProvider,
                  provideChromeFullscreenManagerProvider));
      this.customTabDelegateFactoryProvider =
          DoubleCheck.provider(
              CustomTabDelegateFactory_Factory.create(
                  provideIntentDataProvider,
                  customTabBrowserControlsVisibilityDelegateProvider,
                  DaggerChromeAppComponent.this.provideExternalAuthUtilsProvider,
                  DaggerChromeAppComponent.this.provideMultiWindowUtilsProvider));
      this.provideTabContentManagerProvider =
          ChromeActivityCommonsModule_ProvideTabContentManagerFactory.create(
              chromeActivityCommonsModule);
      this.provideCompositorViewHolderProvider =
          ChromeActivityCommonsModule_ProvideCompositorViewHolderFactory.create(
              chromeActivityCommonsModule);
      this.customTabTabPersistencePolicyProvider =
          DoubleCheck.provider(
              CustomTabTabPersistencePolicy_Factory.create(provideChromeActivityProvider));
      this.provideActivityWindowAndroidProvider =
          ChromeActivityCommonsModule_ProvideActivityWindowAndroidFactory.create(
              chromeActivityCommonsModule);
      this.customTabActivityTabFactoryProvider =
          DoubleCheck.provider(
              CustomTabActivityTabFactory_Factory.create(
                  provideChromeActivityProvider,
                  customTabTabPersistencePolicyProvider,
                  provideActivityWindowAndroidProvider,
                  customTabDelegateFactoryProvider,
                  provideIntentDataProvider));
      this.customTabObserverProvider =
          DoubleCheck.provider(
              CustomTabObserver_Factory.create(
                  DaggerChromeAppComponent.this.provideContextProvider,
                  provideIntentDataProvider,
                  AppHooksModule_ProvideCustomTabsConnectionFactory.create()));
      this.webContentsFactoryProvider = SingleCheck.provider(WebContentsFactory_Factory.create());
      this.customTabActivityTabControllerProvider =
          DoubleCheck.provider(
              CustomTabActivityTabController_Factory.create(
                  provideChromeActivityProvider,
                  customTabDelegateFactoryProvider,
                  AppHooksModule_ProvideCustomTabsConnectionFactory.create(),
                  provideIntentDataProvider,
                  provideTabContentManagerProvider,
                  provideActivityTabProvider,
                  tabObserverRegistrarProvider,
                  provideCompositorViewHolderProvider,
                  provideLifecycleDispatcherProvider,
                  DaggerChromeAppComponent.this.provideWarmupManagerProvider,
                  customTabTabPersistencePolicyProvider,
                  customTabActivityTabFactoryProvider,
                  customTabObserverProvider,
                  webContentsFactoryProvider));
      this.dynamicModulePageLoadObserverProvider =
          DynamicModulePageLoadObserver_Factory.create(provideActivityTabProvider);
      this.dynamicModuleCoordinatorProvider =
          DoubleCheck.provider(
              DynamicModuleCoordinator_Factory.create(
                  provideIntentDataProvider,
                  closeButtonNavigatorProvider,
                  tabObserverRegistrarProvider,
                  provideLifecycleDispatcherProvider,
                  activityDelegateProvider,
                  customTabTopBarDelegateProvider,
                  customTabBottomBarDelegateProvider,
                  provideChromeFullscreenManagerProvider,
                  AppHooksModule_ProvideCustomTabsConnectionFactory.create(),
                  provideChromeActivityProvider,
                  customTabActivityTabControllerProvider,
                  dynamicModulePageLoadObserverProvider));
    }

    @Override
    public ChromeAppComponent getParent() {
      return DaggerChromeAppComponent.this;
    }

    @Override
    public ContextualSuggestionsCoordinator resolveContextualSuggestionsCoordinator() {
      return contextualSuggestionsCoordinatorProvider.get();
    }

    @Override
    public TrustedWebActivityCoordinator resolveTrustedWebActivityCoordinator() {
      return trustedWebActivityCoordinatorProvider.get();
    }

    @Override
    public DynamicModuleCoordinator resolveDynamicModuleCoordinator() {
      return dynamicModuleCoordinatorProvider.get();
    }

    @Override
    public CloseButtonNavigator resolveCloseButtonNavigator() {
      return closeButtonNavigatorProvider.get();
    }

    @Override
    public TabObserverRegistrar resolveTabObserverRegistrar() {
      return tabObserverRegistrarProvider.get();
    }

    @Override
    public CustomTabTopBarDelegate resolveTobBarDelegate() {
      return customTabTopBarDelegateProvider.get();
    }

    @Override
    public CustomTabBottomBarDelegate resolveBottomBarDelegate() {
      return customTabBottomBarDelegateProvider.get();
    }

    @Override
    public CustomTabActivityTabController resolveTabController() {
      return customTabActivityTabControllerProvider.get();
    }

    @Override
    public CustomTabActivityTabFactory resolveTabFactory() {
      return customTabActivityTabFactoryProvider.get();
    }

    @Override
    public CustomTabTabPersistencePolicy resolveTabPersistencePolicy() {
      return customTabTabPersistencePolicyProvider.get();
    }
  }
}
