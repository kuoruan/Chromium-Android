package org.chromium.chrome.browser.browserservices.trustedwebactivityui;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityDisclosureController;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityOpenTimeRecorder;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityToolbarController;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.controller.TrustedWebActivityVerifier;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityDisclosureView;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.view.TrustedWebActivityToolbarView;
import org.chromium.chrome.browser.customtabs.CloseButtonNavigator;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityCoordinator_Factory
    implements Factory<TrustedWebActivityCoordinator> {
  private final Provider<TrustedWebActivityDisclosureController> disclosureControllerProvider;

  private final Provider<TrustedWebActivityToolbarController> toolbarControllerProvider;

  private final Provider<TrustedWebActivityToolbarView> toolbarViewProvider;

  private final Provider<TrustedWebActivityDisclosureView> disclosureViewProvider;

  private final Provider<TrustedWebActivityOpenTimeRecorder> openTimeRecorderProvider;

  private final Provider<TrustedWebActivityVerifier> verifierProvider;

  private final Provider<CloseButtonNavigator> closeButtonNavigatorProvider;

  public TrustedWebActivityCoordinator_Factory(
      Provider<TrustedWebActivityDisclosureController> disclosureControllerProvider,
      Provider<TrustedWebActivityToolbarController> toolbarControllerProvider,
      Provider<TrustedWebActivityToolbarView> toolbarViewProvider,
      Provider<TrustedWebActivityDisclosureView> disclosureViewProvider,
      Provider<TrustedWebActivityOpenTimeRecorder> openTimeRecorderProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider) {
    this.disclosureControllerProvider = disclosureControllerProvider;
    this.toolbarControllerProvider = toolbarControllerProvider;
    this.toolbarViewProvider = toolbarViewProvider;
    this.disclosureViewProvider = disclosureViewProvider;
    this.openTimeRecorderProvider = openTimeRecorderProvider;
    this.verifierProvider = verifierProvider;
    this.closeButtonNavigatorProvider = closeButtonNavigatorProvider;
  }

  @Override
  public TrustedWebActivityCoordinator get() {
    return provideInstance(
        disclosureControllerProvider,
        toolbarControllerProvider,
        toolbarViewProvider,
        disclosureViewProvider,
        openTimeRecorderProvider,
        verifierProvider,
        closeButtonNavigatorProvider);
  }

  public static TrustedWebActivityCoordinator provideInstance(
      Provider<TrustedWebActivityDisclosureController> disclosureControllerProvider,
      Provider<TrustedWebActivityToolbarController> toolbarControllerProvider,
      Provider<TrustedWebActivityToolbarView> toolbarViewProvider,
      Provider<TrustedWebActivityDisclosureView> disclosureViewProvider,
      Provider<TrustedWebActivityOpenTimeRecorder> openTimeRecorderProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider) {
    return new TrustedWebActivityCoordinator(
        disclosureControllerProvider.get(),
        toolbarControllerProvider.get(),
        toolbarViewProvider.get(),
        disclosureViewProvider.get(),
        openTimeRecorderProvider.get(),
        verifierProvider.get(),
        closeButtonNavigatorProvider.get());
  }

  public static TrustedWebActivityCoordinator_Factory create(
      Provider<TrustedWebActivityDisclosureController> disclosureControllerProvider,
      Provider<TrustedWebActivityToolbarController> toolbarControllerProvider,
      Provider<TrustedWebActivityToolbarView> toolbarViewProvider,
      Provider<TrustedWebActivityDisclosureView> disclosureViewProvider,
      Provider<TrustedWebActivityOpenTimeRecorder> openTimeRecorderProvider,
      Provider<TrustedWebActivityVerifier> verifierProvider,
      Provider<CloseButtonNavigator> closeButtonNavigatorProvider) {
    return new TrustedWebActivityCoordinator_Factory(
        disclosureControllerProvider,
        toolbarControllerProvider,
        toolbarViewProvider,
        disclosureViewProvider,
        openTimeRecorderProvider,
        verifierProvider,
        closeButtonNavigatorProvider);
  }

  public static TrustedWebActivityCoordinator newTrustedWebActivityCoordinator(
      TrustedWebActivityDisclosureController disclosureController,
      TrustedWebActivityToolbarController toolbarController,
      TrustedWebActivityToolbarView toolbarView,
      TrustedWebActivityDisclosureView disclosureView,
      TrustedWebActivityOpenTimeRecorder openTimeRecorder,
      TrustedWebActivityVerifier verifier,
      CloseButtonNavigator closeButtonNavigator) {
    return new TrustedWebActivityCoordinator(
        disclosureController,
        toolbarController,
        toolbarView,
        disclosureView,
        openTimeRecorder,
        verifier,
        closeButtonNavigator);
  }
}
