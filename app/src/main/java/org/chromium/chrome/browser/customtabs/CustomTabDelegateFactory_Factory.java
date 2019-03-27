package org.chromium.chrome.browser.customtabs;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabDelegateFactory_Factory implements Factory<CustomTabDelegateFactory> {
  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  private final Provider<CustomTabBrowserControlsVisibilityDelegate> visibilityDelegateProvider;

  private final Provider<ExternalAuthUtils> authUtilsProvider;

  private final Provider<MultiWindowUtils> multiWindowUtilsProvider;

  public CustomTabDelegateFactory_Factory(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabBrowserControlsVisibilityDelegate> visibilityDelegateProvider,
      Provider<ExternalAuthUtils> authUtilsProvider,
      Provider<MultiWindowUtils> multiWindowUtilsProvider) {
    this.intentDataProvider = intentDataProvider;
    this.visibilityDelegateProvider = visibilityDelegateProvider;
    this.authUtilsProvider = authUtilsProvider;
    this.multiWindowUtilsProvider = multiWindowUtilsProvider;
  }

  @Override
  public CustomTabDelegateFactory get() {
    return provideInstance(
        intentDataProvider,
        visibilityDelegateProvider,
        authUtilsProvider,
        multiWindowUtilsProvider);
  }

  public static CustomTabDelegateFactory provideInstance(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabBrowserControlsVisibilityDelegate> visibilityDelegateProvider,
      Provider<ExternalAuthUtils> authUtilsProvider,
      Provider<MultiWindowUtils> multiWindowUtilsProvider) {
    return new CustomTabDelegateFactory(
        intentDataProvider.get(),
        visibilityDelegateProvider.get(),
        authUtilsProvider.get(),
        multiWindowUtilsProvider.get());
  }

  public static CustomTabDelegateFactory_Factory create(
      Provider<CustomTabIntentDataProvider> intentDataProvider,
      Provider<CustomTabBrowserControlsVisibilityDelegate> visibilityDelegateProvider,
      Provider<ExternalAuthUtils> authUtilsProvider,
      Provider<MultiWindowUtils> multiWindowUtilsProvider) {
    return new CustomTabDelegateFactory_Factory(
        intentDataProvider,
        visibilityDelegateProvider,
        authUtilsProvider,
        multiWindowUtilsProvider);
  }

  public static CustomTabDelegateFactory newCustomTabDelegateFactory(
      CustomTabIntentDataProvider intentDataProvider,
      CustomTabBrowserControlsVisibilityDelegate visibilityDelegate,
      ExternalAuthUtils authUtils,
      MultiWindowUtils multiWindowUtils) {
    return new CustomTabDelegateFactory(
        intentDataProvider, visibilityDelegate, authUtils, multiWindowUtils);
  }
}
