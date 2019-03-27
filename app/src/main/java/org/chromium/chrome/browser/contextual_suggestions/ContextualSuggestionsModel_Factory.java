package org.chromium.chrome.browser.contextual_suggestions;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://google.github.io/dagger"
)
public final class ContextualSuggestionsModel_Factory
    implements Factory<ContextualSuggestionsModel> {
  private static final ContextualSuggestionsModel_Factory INSTANCE =
      new ContextualSuggestionsModel_Factory();

  @Override
  public ContextualSuggestionsModel get() {
    return provideInstance();
  }

  public static ContextualSuggestionsModel provideInstance() {
    return new ContextualSuggestionsModel();
  }

  public static ContextualSuggestionsModel_Factory create() {
    return INSTANCE;
  }

  public static ContextualSuggestionsModel newContextualSuggestionsModel() {
    return new ContextualSuggestionsModel();
  }
}
