package org.chromium.chrome.browser.browserservices;

import android.content.res.Resources;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class TrustedWebActivityDisclosure_Factory
    implements Factory<TrustedWebActivityDisclosure> {
  private final Provider<Resources> resourcesProvider;

  private final Provider<ChromePreferenceManager> preferenceManagerProvider;

  public TrustedWebActivityDisclosure_Factory(
      Provider<Resources> resourcesProvider,
      Provider<ChromePreferenceManager> preferenceManagerProvider) {
    this.resourcesProvider = resourcesProvider;
    this.preferenceManagerProvider = preferenceManagerProvider;
  }

  @Override
  public TrustedWebActivityDisclosure get() {
    return provideInstance(resourcesProvider, preferenceManagerProvider);
  }

  public static TrustedWebActivityDisclosure provideInstance(
      Provider<Resources> resourcesProvider,
      Provider<ChromePreferenceManager> preferenceManagerProvider) {
    return new TrustedWebActivityDisclosure(
        resourcesProvider.get(), preferenceManagerProvider.get());
  }

  public static TrustedWebActivityDisclosure_Factory create(
      Provider<Resources> resourcesProvider,
      Provider<ChromePreferenceManager> preferenceManagerProvider) {
    return new TrustedWebActivityDisclosure_Factory(resourcesProvider, preferenceManagerProvider);
  }

  public static TrustedWebActivityDisclosure newTrustedWebActivityDisclosure(
      Resources resources, ChromePreferenceManager preferenceManager) {
    return new TrustedWebActivityDisclosure(resources, preferenceManager);
  }
}
