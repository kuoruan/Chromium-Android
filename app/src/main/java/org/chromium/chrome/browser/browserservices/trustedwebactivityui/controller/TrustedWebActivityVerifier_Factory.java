package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.customtabs.TabObserverRegistrar;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityVerifier_Factory
    implements Factory<TrustedWebActivityVerifier> {
  private final Provider<ClientAppDataRecorder> clientAppDataRecorderProvider;

  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  private final Provider<CustomTabsConnection> customTabsConnectionProvider;

  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  private final Provider<TabObserverRegistrar> tabObserverRegistrarProvider;

  private final Provider<ActivityTabProvider> activityTabProvider;

  public TrustedWebActivityVerifier_Factory(
      Provider<ClientAppDataRecorder> clientAppDataRecorderProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabsConnection> customTabsConnectionProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityTabProvider> activityTabProvider) {
    this.clientAppDataRecorderProvider = clientAppDataRecorderProvider;
    this.intentDataProvider = intentDataProvider;
    this.customTabsConnectionProvider = customTabsConnectionProvider;
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
    this.tabObserverRegistrarProvider = tabObserverRegistrarProvider;
    this.activityTabProvider = activityTabProvider;
  }

  @Override
  public TrustedWebActivityVerifier get() {
    return provideInstance(
        clientAppDataRecorderProvider,
        intentDataProvider,
        customTabsConnectionProvider,
        lifecycleDispatcherProvider,
        tabObserverRegistrarProvider,
        activityTabProvider);
  }

  public static TrustedWebActivityVerifier provideInstance(
      Provider<ClientAppDataRecorder> clientAppDataRecorderProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabsConnection> customTabsConnectionProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityTabProvider> activityTabProvider) {
    return new TrustedWebActivityVerifier(
        DoubleCheck.lazy(clientAppDataRecorderProvider),
        intentDataProvider.get(),
        customTabsConnectionProvider.get(),
        lifecycleDispatcherProvider.get(),
        tabObserverRegistrarProvider.get(),
        activityTabProvider.get());
  }

  public static TrustedWebActivityVerifier_Factory create(
      Provider<ClientAppDataRecorder> clientAppDataRecorderProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabsConnection> customTabsConnectionProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TabObserverRegistrar> tabObserverRegistrarProvider,
      Provider<ActivityTabProvider> activityTabProvider) {
    return new TrustedWebActivityVerifier_Factory(
        clientAppDataRecorderProvider,
        intentDataProvider,
        customTabsConnectionProvider,
        lifecycleDispatcherProvider,
        tabObserverRegistrarProvider,
        activityTabProvider);
  }

  public static TrustedWebActivityVerifier newTrustedWebActivityVerifier(
      Lazy<ClientAppDataRecorder> clientAppDataRecorder,
      CustomTabIntentDataProvider intentDataProvider,
      CustomTabsConnection customTabsConnection,
      ActivityLifecycleDispatcher lifecycleDispatcher,
      TabObserverRegistrar tabObserverRegistrar,
      ActivityTabProvider activityTabProvider) {
    return new TrustedWebActivityVerifier(
        clientAppDataRecorder,
        intentDataProvider,
        customTabsConnection,
        lifecycleDispatcher,
        tabObserverRegistrar,
        activityTabProvider);
  }
}
