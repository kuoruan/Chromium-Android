package org.chromium.chrome.browser.customtabs.dynamicmodule;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.customtabs.CloseButtonNavigator;
import org.chromium.chrome.browser.customtabs.CustomTabBottomBarDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabTopBarDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabController;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class DynamicModuleCoordinator_Factory implements Factory<DynamicModuleCoordinator> {
  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  private final Provider<CloseButtonNavigator> closeButtonNavigatorProvider;

  private final Provider<TabObserverRegistrar> tabObserverRegistrarProvider;

  private final Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider;

  private final Provider<ActivityDelegate> activityDelegateProvider;

  private final Provider<CustomTabTopBarDelegate> topBarDelegateProvider;

  private final Provider<CustomTabBottomBarDelegate> bottomBarDelegateProvider;

  private final Provider<ChromeFullscreenManager> fullscreenManagerProvider;

  private final Provider<CustomTabsConnection> connectionProvider;

  private final Provider<ChromeActivity> activityProvider;

  private final Provider<CustomTabActivityTabController> tabControllerProvider;

  private final Provider<DynamicModulePageLoadObserver> pageLoadObserverProvider;

  public DynamicModuleCoordinator_Factory(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider,
      Provider<ActivityDelegate> activityDelegateProvider,
      Provider<CustomTabTopBarDelegate> topBarDelegateProvider,
      Provider<CustomTabBottomBarDelegate> bottomBarDelegateProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabActivityTabController> tabControllerProvider,
      Provider<DynamicModulePageLoadObserver> pageLoadObserverProvider) {
    this.intentDataProvider = intentDataProvider;
    this.closeButtonNavigatorProvider = closeButtonNavigatorProvider;
    this.tabObserverRegistrarProvider = tabObserverRegistrarProvider;
    this.activityLifecycleDispatcherProvider = activityLifecycleDispatcherProvider;
    this.activityDelegateProvider = activityDelegateProvider;
    this.topBarDelegateProvider = topBarDelegateProvider;
    this.bottomBarDelegateProvider = bottomBarDelegateProvider;
    this.fullscreenManagerProvider = fullscreenManagerProvider;
    this.connectionProvider = connectionProvider;
    this.activityProvider = activityProvider;
    this.tabControllerProvider = tabControllerProvider;
    this.pageLoadObserverProvider = pageLoadObserverProvider;
  }

  @Override
  public DynamicModuleCoordinator get() {
    return provideInstance(
        intentDataProvider,
        closeButtonNavigatorProvider,
        tabObserverRegistrarProvider,
        activityLifecycleDispatcherProvider,
        activityDelegateProvider,
        topBarDelegateProvider,
        bottomBarDelegateProvider,
        fullscreenManagerProvider,
        connectionProvider,
        activityProvider,
        tabControllerProvider,
        pageLoadObserverProvider);
  }

  public static DynamicModuleCoordinator provideInstance(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider,
      Provider<ActivityDelegate> activityDelegateProvider,
      Provider<CustomTabTopBarDelegate> topBarDelegateProvider,
      Provider<CustomTabBottomBarDelegate> bottomBarDelegateProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabActivityTabController> tabControllerProvider,
      Provider<DynamicModulePageLoadObserver> pageLoadObserverProvider) {
    return new DynamicModuleCoordinator(
        intentDataProvider.get(),
        closeButtonNavigatorProvider.get(),
        tabObserverRegistrarProvider.get(),
        activityLifecycleDispatcherProvider.get(),
        activityDelegateProvider.get(),
        DoubleCheck.lazy(topBarDelegateProvider),
        DoubleCheck.lazy(bottomBarDelegateProvider),
        DoubleCheck.lazy(fullscreenManagerProvider),
        connectionProvider.get(),
        activityProvider.get(),
        tabControllerProvider.get(),
        pageLoadObserverProvider.get());
  }

  public static DynamicModuleCoordinator_Factory create(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityLifecycleDispatcher> activityLifecycleDispatcherProvider,
      Provider<ActivityDelegate> activityDelegateProvider,
      Provider<CustomTabTopBarDelegate> topBarDelegateProvider,
      Provider<CustomTabBottomBarDelegate> bottomBarDelegateProvider,
      Provider<ChromeFullscreenManager> fullscreenManagerProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabActivityTabController> tabControllerProvider,
      Provider<DynamicModulePageLoadObserver> pageLoadObserverProvider) {
    return new DynamicModuleCoordinator_Factory(
        intentDataProvider,
        closeButtonNavigatorProvider,
        tabObserverRegistrarProvider,
        activityLifecycleDispatcherProvider,
        activityDelegateProvider,
        topBarDelegateProvider,
        bottomBarDelegateProvider,
        fullscreenManagerProvider,
        connectionProvider,
        activityProvider,
        tabControllerProvider,
        pageLoadObserverProvider);
  }

  public static DynamicModuleCoordinator newDynamicModuleCoordinator(
      CustomTabIntentDataProvider intentDataProvider,
      CloseButtonNavigator closeButtonNavigator,
      TabObserverRegistrar tabObserverRegistrar,
      ActivityLifecycleDispatcher activityLifecycleDispatcher,
      ActivityDelegate activityDelegate,
      Lazy<CustomTabTopBarDelegate> topBarDelegate,
      Lazy<CustomTabBottomBarDelegate> bottomBarDelegate,
      Lazy<ChromeFullscreenManager> fullscreenManager,
      CustomTabsConnection connection,
      ChromeActivity activity,
      CustomTabActivityTabController tabController,
      DynamicModulePageLoadObserver pageLoadObserver) {
    return new DynamicModuleCoordinator(
        intentDataProvider,
        closeButtonNavigator,
        tabObserverRegistrar,
        activityLifecycleDispatcher,
        activityDelegate,
        topBarDelegate,
        bottomBarDelegate,
        fullscreenManager,
        connection,
        activity,
        tabController,
        pageLoadObserver);
  }
}
