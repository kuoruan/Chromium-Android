package org.chromium.chrome.browser.customtabs.content;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.customtabs.CustomTabDelegateFactory;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.CustomTabTabPersistencePolicy;
import org.chromium.ui.base.ActivityWindowAndroid;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class CustomTabActivityTabFactory_Factory
    implements Factory<CustomTabActivityTabFactory> {
  private final Provider<ChromeActivity> activityProvider;

  private final Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider;

  private final Provider<ActivityWindowAndroid> activityWindowAndroidProvider;

  private final Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider;

  private final Provider<CustomTabIntentDataProvider> intentDataProvider;

  public CustomTabActivityTabFactory_Factory(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<ActivityWindowAndroid> activityWindowAndroidProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider) {
    this.activityProvider = activityProvider;
    this.persistencePolicyProvider = persistencePolicyProvider;
    this.activityWindowAndroidProvider = activityWindowAndroidProvider;
    this.customTabDelegateFactoryProvider = customTabDelegateFactoryProvider;
    this.intentDataProvider = intentDataProvider;
  }

  @Override
  public CustomTabActivityTabFactory get() {
    return provideInstance(
        activityProvider,
        persistencePolicyProvider,
        activityWindowAndroidProvider,
        customTabDelegateFactoryProvider,
        intentDataProvider);
  }

  public static CustomTabActivityTabFactory provideInstance(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<ActivityWindowAndroid> activityWindowAndroidProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider) {
    return new CustomTabActivityTabFactory(
        activityProvider.get(),
        persistencePolicyProvider.get(),
        DoubleCheck.lazy(activityWindowAndroidProvider),
        DoubleCheck.lazy(customTabDelegateFactoryProvider),
        intentDataProvider.get());
  }

  public static CustomTabActivityTabFactory_Factory create(
      Provider<ChromeActivity> activityProvider,
      Provider<CustomTabTabPersistencePolicy> persistencePolicyProvider,
      Provider<ActivityWindowAndroid> activityWindowAndroidProvider,
      Provider<CustomTabDelegateFactory> customTabDelegateFactoryProvider,
      Provider<CustomTabIntentDataProvider> intentDataProvider) {
    return new CustomTabActivityTabFactory_Factory(
        activityProvider,
        persistencePolicyProvider,
        activityWindowAndroidProvider,
        customTabDelegateFactoryProvider,
        intentDataProvider);
  }

  public static CustomTabActivityTabFactory newCustomTabActivityTabFactory(
      ChromeActivity activity,
      CustomTabTabPersistencePolicy persistencePolicy,
      Lazy<ActivityWindowAndroid> activityWindowAndroid,
      Lazy<CustomTabDelegateFactory> customTabDelegateFactory,
      CustomTabIntentDataProvider intentDataProvider) {
    return new CustomTabActivityTabFactory(
        activity,
        persistencePolicy,
        activityWindowAndroid,
        customTabDelegateFactory,
        intentDataProvider);
  }
}
