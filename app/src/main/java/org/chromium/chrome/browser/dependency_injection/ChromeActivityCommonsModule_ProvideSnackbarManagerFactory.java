package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.snackbar.SnackbarManager;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideSnackbarManagerFactory
    implements Factory<SnackbarManager> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideSnackbarManagerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public SnackbarManager get() {
    return provideInstance(module);
  }

  public static SnackbarManager provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideSnackbarManager(module);
  }

  public static ChromeActivityCommonsModule_ProvideSnackbarManagerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideSnackbarManagerFactory(module);
  }

  public static SnackbarManager proxyProvideSnackbarManager(ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideSnackbarManager(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
