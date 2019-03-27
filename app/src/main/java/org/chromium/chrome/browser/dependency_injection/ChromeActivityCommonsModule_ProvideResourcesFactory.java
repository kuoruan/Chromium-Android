package org.chromium.chrome.browser.dependency_injection;

import android.content.res.Resources;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideResourcesFactory
    implements Factory<Resources> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideResourcesFactory(ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public Resources get() {
    return provideInstance(module);
  }

  public static Resources provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideResources(module);
  }

  public static ChromeActivityCommonsModule_ProvideResourcesFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideResourcesFactory(module);
  }

  public static Resources proxyProvideResources(ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideResources(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
