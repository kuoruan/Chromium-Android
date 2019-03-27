package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityToolbarController_Factory
    implements Factory<TrustedWebActivityToolbarController> {
  private final Provider<TrustedWebActivityModel> modelProvider;

  private final Provider<TrustedWebActivityVerifier> verifierProvider;

  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  public TrustedWebActivityToolbarController_Factory(
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    this.modelProvider = modelProvider;
    this.verifierProvider = verifierProvider;
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
  }

  @Override
  public TrustedWebActivityToolbarController get() {
    return provideInstance(modelProvider, verifierProvider, lifecycleDispatcherProvider);
  }

  public static TrustedWebActivityToolbarController provideInstance(
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    return new TrustedWebActivityToolbarController(
        modelProvider.get(), verifierProvider.get(), lifecycleDispatcherProvider.get());
  }

  public static TrustedWebActivityToolbarController_Factory create(
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider) {
    return new TrustedWebActivityToolbarController_Factory(
        modelProvider, verifierProvider, lifecycleDispatcherProvider);
  }

  public static TrustedWebActivityToolbarController newTrustedWebActivityToolbarController(
      TrustedWebActivityModel model,
      TrustedWebActivityVerifier verifier,
      ActivityLifecycleDispatcher lifecycleDispatcher) {
    return new TrustedWebActivityToolbarController(model, verifier, lifecycleDispatcher);
  }
}
