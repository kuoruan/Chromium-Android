package org.chromium.chrome.browser.dependency_injection;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory
    implements Factory<BottomSheetController> {
  private final ChromeActivityCommonsModule module;

  public ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory(
      ChromeActivityCommonsModule module) {
    this.module = module;
  }

  @Override
  public BottomSheetController get() {
    return provideInstance(module);
  }

  public static BottomSheetController provideInstance(ChromeActivityCommonsModule module) {
    return proxyProvideBottomSheetController(module);
  }

  public static ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory create(
      ChromeActivityCommonsModule module) {
    return new ChromeActivityCommonsModule_ProvideBottomSheetControllerFactory(module);
  }

  public static BottomSheetController proxyProvideBottomSheetController(
      ChromeActivityCommonsModule instance) {
    return Preconditions.checkNotNull(
        instance.provideBottomSheetController(),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
