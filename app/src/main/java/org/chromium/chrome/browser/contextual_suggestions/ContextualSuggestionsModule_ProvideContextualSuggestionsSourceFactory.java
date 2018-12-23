package org.chromium.chrome.browser.contextual_suggestions;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.chromium.chrome.browser.profiles.Profile;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory
    implements Factory<ContextualSuggestionsSource> {
  private final ContextualSuggestionsModule module;

  private final Provider<Profile> profileProvider;

  public ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory(
      ContextualSuggestionsModule module, Provider<Profile> profileProvider) {
    this.module = module;
    this.profileProvider = profileProvider;
  }

  @Override
  public ContextualSuggestionsSource get() {
    return provideInstance(module, profileProvider);
  }

  public static ContextualSuggestionsSource provideInstance(
      ContextualSuggestionsModule module, Provider<Profile> profileProvider) {
    return proxyProvideContextualSuggestionsSource(module, profileProvider.get());
  }

  public static ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory create(
      ContextualSuggestionsModule module, Provider<Profile> profileProvider) {
    return new ContextualSuggestionsModule_ProvideContextualSuggestionsSourceFactory(
        module, profileProvider);
  }

  public static ContextualSuggestionsSource proxyProvideContextualSuggestionsSource(
      ContextualSuggestionsModule instance, Profile profile) {
    return Preconditions.checkNotNull(
        instance.provideContextualSuggestionsSource(profile),
        "Cannot return null from a non-@Nullable @Provides method");
  }
}
