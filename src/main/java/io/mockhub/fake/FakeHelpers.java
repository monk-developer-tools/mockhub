package io.mockhub.fake;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FakeHelpers implements TemplateHelperProviderExtension {
  private final FakeData fake;

  public FakeHelpers(FakeData fake) {
    this.fake = fake;
  }

  @Override
  public String getName() {
    return "mockhub-fake";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of("fake", (Helper<Object>) this::apply);
  }

  private Object apply(Object context, Options options) {
    String expression = context == null ? "" : context.toString();
    if (options.params.length > 0 && !expression.contains("#{")) {
      List<String> args = new ArrayList<>();
      for (Object p : options.params) args.add("'" + String.valueOf(p).replace("'", "") + "'");
      expression = "#{" + expression + " " + String.join(",", args) + "}";
    }
    Object locale = options.hash("locale");
    try {
      return fake.generate(expression, locale == null ? null : locale.toString());
    } catch (RuntimeException e) {
      return "[fake: can't generate '" + expression + "': " + e.getMessage() + "]";
    }
  }
}
