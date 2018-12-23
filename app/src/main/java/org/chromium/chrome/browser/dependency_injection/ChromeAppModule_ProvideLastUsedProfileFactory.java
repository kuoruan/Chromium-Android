package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.profiles.Profile;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeAppModule_ProvideLastUsedProfileFactory implements Factory<Profile> {
  private final ChromeAppModule module;

  public ChromeAppModule_ProvideLastUsedProfileFactory(ChromeAppModule module) {
    this.module = module;
  }

  @Override
  public Profile get() {
    return provideInstance(module);
  }

  public static Profile provideInstance(ChromeAppModule module) {
    return proxyProvideLastUsedProfile(module);
  }

  public static ChromeAppModule_ProvideLastUsedProfileFactory create(ChromeAppModule module) {
    return new ChromeAppModule_ProvideLastUsedProfileFactory(module);
  }

  public static Profile proxyProvideLastUsedProfile(ChromeAppModule instance) {
    return Preconditions.checkNotNull(
        instance.provideLastUsedProfile(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
