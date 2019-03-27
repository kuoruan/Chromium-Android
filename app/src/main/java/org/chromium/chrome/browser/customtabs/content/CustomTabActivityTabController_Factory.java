package org.chromium.chrome.browser.customtabs.content;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.customtabs.CustomTabDelegateFactory;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabObserver;
import org.chromium.chrome.browser.customtabs.CustomTabTabPersistencePolicy;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabActivityTabController_Factory
    implements Factory<CustomTabActivityTabController> {
  private final Provider<ChromeActivity> activityProvider;

  private final Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider;

  private final Provider<CustomTabsConnection> connectionProvider;

  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  private final Provider<TabContentManager> tabContentManagerProvider;

  private final Provider<ActivityTabProvider> tabProvider;

  private final Provider<TabObserverRegistrar> tabObserverRegistrarProvider;

  private final Provider<CompositorViewHolder> compositorViewHolderProvider;

  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  private final Provider<WarmupManager> warmupManagerProvider;

  private final Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider;

  private final Provider<CustomTabActivityTabFactory> tabFactoryProvider;

  private final Provider<CustomTabObserver> customTabObserverProvider;

  private final Provider<WebContentsFactory> webContentsFactoryProvider;

  public CustomTabActivityTabController_Factory(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TabContentManager> tabContentManagerProvider,
      Provider<ActivityTabProvider> tabProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<CompositorViewHolder> compositorViewHolderProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<WarmupManager> warmupManagerProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<CustomTabActivityTabFactory> tabFactoryProvider,
      Provider<CustomTabObserver> customTabObserverProvider,
      Provider<WebContentsFactory> webContentsFactoryProvider) {
    this.activityProvider = activityProvider;
    this.customTabDelegateFactoryProvider = customTabDelegateFactoryProvider;
    this.connectionProvider = connectionProvider;
    this.intentDataProvider = intentDataProvider;
    this.tabContentManagerProvider = tabContentManagerProvider;
    this.tabProvider = tabProvider;
    this.tabObserverRegistrarProvider = tabObserverRegistrarProvider;
    this.compositorViewHolderProvider = compositorViewHolderProvider;
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
    this.warmupManagerProvider = warmupManagerProvider;
    this.persistencePolicyProvider = persistencePolicyProvider;
    this.tabFactoryProvider = tabFactoryProvider;
    this.customTabObserverProvider = customTabObserverProvider;
    this.webContentsFactoryProvider = webContentsFactoryProvider;
  }

  @Override
  public CustomTabActivityTabController get() {
    return provideInstance(
        activityProvider,
        customTabDelegateFactoryProvider,
        connectionProvider,
        intentDataProvider,
        tabContentManagerProvider,
        tabProvider,
        tabObserverRegistrarProvider,
        compositorViewHolderProvider,
        lifecycleDispatcherProvider,
        warmupManagerProvider,
        persistencePolicyProvider,
        tabFactoryProvider,
        customTabObserverProvider,
        webContentsFactoryProvider);
  }

  public static CustomTabActivityTabController provideInstance(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TabContentManager> tabContentManagerProvider,
      Provider<ActivityTabProvider> tabProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<CompositorViewHolder> compositorViewHolderProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<WarmupManager> warmupManagerProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<CustomTabActivityTabFactory> tabFactoryProvider,
      Provider<CustomTabObserver> customTabObserverProvider,
      Provider<WebContentsFactory> webContentsFactoryProvider) {
    return new CustomTabActivityTabController(
        activityProvider.get(),
        DoubleCheck.lazy(customTabDelegateFactoryProvider),
        connectionProvider.get(),
        intentDataProvider.get(),
        DoubleCheck.lazy(tabContentManagerProvider),
        tabProvider.get(),
        tabObserverRegistrarProvider.get(),
        DoubleCheck.lazy(compositorViewHolderProvider),
        lifecycleDispatcherProvider.get(),
        warmupManagerProvider.get(),
        persistencePolicyProvider.get(),
        tabFactoryProvider.get(),
        DoubleCheck.lazy(customTabObserverProvider),
        webContentsFactoryProvider.get());
  }

  public static CustomTabActivityTabController_Factory create(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabsConnection> connectionProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TabContentManager> tabContentManagerProvider,
      Provider<ActivityTabProvider> tabProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<CompositorViewHolder> compositorViewHolderProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<WarmupManager> warmupManagerProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<CustomTabActivityTabFactory> tabFactoryProvider,
      Provider<CustomTabObserver> customTabObserverProvider,
      Provider<WebContentsFactory> webContentsFactoryProvider) {
    return new CustomTabActivityTabController_Factory(
        activityProvider,
        customTabDelegateFactoryProvider,
        connectionProvider,
        intentDataProvider,
        tabContentManagerProvider,
        tabProvider,
        tabObserverRegistrarProvider,
        compositorViewHolderProvider,
        lifecycleDispatcherProvider,
        warmupManagerProvider,
        persistencePolicyProvider,
        tabFactoryProvider,
        customTabObserverProvider,
        webContentsFactoryProvider);
  }

  public static CustomTabActivityTabController newCustomTabActivityTabController(
      ChromeActivity activity,
      Lazy<CustomTabDelegateFactory> customTabDelegateFactory,
      CustomTabsConnection connection,
      CustomTabIntentDataProvider intentDataProvider,
      Lazy<TabContentManager> tabContentManager,
      ActivityTabProvider tabProvider,
      TabObserverRegistrar tabObserverRegistrar,
      Lazy<CompositorViewHolder> compositorViewHolder,
      ActivityLifecycleDispatcher lifecycleDispatcher,
      WarmupManager warmupManager,
      CustomTabTabPersistencePolicy persistencePolicy,
      CustomTabActivityTabFactory tabFactory,
      Lazy<CustomTabObserver> customTabObserver,
      WebContentsFactory webContentsFactory) {
    return new CustomTabActivityTabController(
        activity,
        customTabDelegateFactory,
        connection,
        intentDataProvider,
        tabContentManager,
        tabProvider,
        tabObserverRegistrar,
        compositorViewHolder,
        lifecycleDispatcher,
        warmupManager,
        persistencePolicy,
        tabFactory,
        customTabObserver,
        webContentsFactory);
  }
}
