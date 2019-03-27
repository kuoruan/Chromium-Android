package org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityUmaRecorder;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityDisclosureController_Factory
    implements Factory<TrustedWebActivityDisclosureController> {
  private final Provider<ChromePreferenceManager> preferenceManagerProvider;

  private final Provider<TrustedWebActivityModel> modelProvider;

  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  private final Provider<TrustedWebActivityVerifier> verifierProvider;

  private final Provider<TrustedWebActivityUmaRecorder> recorderProvider;

  public TrustedWebActivityDisclosureController_Factory(
      Provider<ChromePreferenceManager> preferenceManagerProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<TrustedWebActivityUmaRecorder> recorderProvider) {
    this.preferenceManagerProvider = preferenceManagerProvider;
    this.modelProvider = modelProvider;
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
    this.verifierProvider = verifierProvider;
    this.recorderProvider = recorderProvider;
  }

  @Override
  public TrustedWebActivityDisclosureController get() {
    return provideInstance(
        preferenceManagerProvider,
        modelProvider,
        lifecycleDispatcherProvider,
        verifierProvider,
        recorderProvider);
  }

  public static TrustedWebActivityDisclosureController provideInstance(
      Provider<ChromePreferenceManager> preferenceManagerProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<TrustedWebActivityUmaRecorder> recorderProvider) {
    return new TrustedWebActivityDisclosureController(
        preferenceManagerProvider.get(),
        modelProvider.get(),
        lifecycleDispatcherProvider.get(),
        verifierProvider.get(),
        recorderProvider.get());
  }

  public static TrustedWebActivityDisclosureController_Factory create(
      Provider<ChromePreferenceManager> preferenceManagerProvider,
      Provider<TrustedWebActivityModel> modelProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<TrustedWebActivityUmaRecorder> recorderProvider) {
    return new TrustedWebActivityDisclosureController_Factory(
        preferenceManagerProvider,
        modelProvider,
        lifecycleDispatcherProvider,
        verifierProvider,
        recorderProvider);
  }

  public static TrustedWebActivityDisclosureController newTrustedWebActivityDisclosureController(
      ChromePreferenceManager preferenceManager,
      TrustedWebActivityModel model,
      ActivityLifecycleDispatcher lifecycleDispatcher,
      TrustedWebActivityVerifier verifier,
      TrustedWebActivityUmaRecorder recorder) {
    return new TrustedWebActivityDisclosureController(
        preferenceManager, model, lifecycleDispatcher, verifier, recorder);
  }
}
