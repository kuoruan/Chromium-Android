package org.chromium.chrome.browser.browserservices.trustedwebactivityui.view;

import android.content.Context;
import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.browserservices.trustedwebactivityui.TrustedWebActivityModel;
import org.chromium.chrome.browser.init.ActivityLifecycleDispatcher;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class PersistentNotificationView_Factory
    implements Factory<PersistentNotificationView> {
  private final Provider<Context> contextProvider;

  private final Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider;

  private final Provider<TrustedWebActivityModel> modelProvider;

  public PersistentNotificationView_Factory(
      Provider<Context> contextProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityModel> modelProvider) {
    this.contextProvider = contextProvider;
    this.lifecycleDispatcherProvider = lifecycleDispatcherProvider;
    this.modelProvider = modelProvider;
  }

  @Override
  public PersistentNotificationView get() {
    return provideInstance(contextProvider, lifecycleDispatcherProvider, modelProvider);
  }

  public static PersistentNotificationView provideInstance(
      Provider<Context> contextProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityModel> modelProvider) {
    return new PersistentNotificationView(
        contextProvider.get(), lifecycleDispatcherProvider.get(), modelProvider.get());
  }

  public static PersistentNotificationView_Factory create(
      Provider<Context> contextProvider,
      Provider<ActivityLifecycleDispatcher> lifecycleDispatcherProvider,
      Provider<TrustedWebActivityModel> modelProvider) {
    return new PersistentNotificationView_Factory(
        contextProvider, lifecycleDispatcherProvider, modelProvider);
  }

  public static PersistentNotificationView newPersistentNotificationView(
      Context context,
      ActivityLifecycleDispatcher lifecycleDispatcher,
      TrustedWebActivityModel model) {
    return new PersistentNotificationView(context, lifecycleDispatcher, model);
  }
}
