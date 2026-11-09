package io.mockhub.environment;

import com.github.jknack.handlebars.Helper;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.Map;

public final class EnvHelpers implements TemplateHelperProviderExtension {
  private final EnvironmentScope scope;

  public EnvHelpers(EnvironmentScope scope) {
    this.scope = scope;
  }

  @Override
  public String getName() {
    return "mockhub-environments";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of("env", (Helper<Object>) (name, options) -> scope.current()
        .map(e -> name instanceof CharSequence text && !text.toString().isBlank() ? e.variables().getOrDefault(text.toString(), "") : e.name())
        .orElse(""));
  }
}
