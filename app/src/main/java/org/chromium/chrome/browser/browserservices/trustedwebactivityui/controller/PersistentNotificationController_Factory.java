package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class PersistentNotificationController_Factory
    implements Factory<PersistentNotificationController> {
  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  private final Provider<TrustedWebActivityModel> modelProvider;

  private final Provider<TrustedWebActivityVerifier> verifierProvider;

  public PersistentNotificationController_Factory(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider) {
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
    this.intentDataProvider = intentDataProvider;
    this.modelProvider = modelProvider;
    this.verifierProvider = verifierProvider;
  }

  @Override
  public PersistentNotificationController get() {
    return provideInstance(
        lifecycleDispatcherProvider, intentDataProvider, modelProvider, verifierProvider);
  }

  public static PersistentNotificationController provideInstance(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider) {
    return new PersistentNotificationController(
        lifecycleDispatcherProvider.get(),
        intentDataProvider.get(),
        modelProvider.get(),
        verifierProvider.get());
  }

  public static PersistentNotificationController_Factory create(
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider) {
    return new PersistentNotificationController_Factory(
        lifecycleDispatcherProvider, intentDataProvider, modelProvider, verifierProvider);
  }

  public static PersistentNotificationController newPersistentNotificationController(
      ActivityLifecycleDispatcher lifecycleDispatcher,
      CustomTabIntentDataProvider intentDataProvider,
      TrustedWebActivityModel model,
      TrustedWebActivityVerifier verifier) {
    return new PersistentNotificationController(
        lifecycleDispatcher, intentDataProvider, model, verifier);
  }
}
