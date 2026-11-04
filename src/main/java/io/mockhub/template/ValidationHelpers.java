package io.mockhub.template;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ValidationHelpers implements TemplateHelperProviderExtension {
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  @Override
  public String getName() {
    return "mockhub-validation";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "isEmail", check(v -> v != null && EMAIL.matcher(v.toString().trim()).matches()),
        "isUuid", check(v -> v != null && UUID.matcher(v.toString().trim()).matches()),
        "isNumber", check(v -> ValueHelpers.number(v, null) != null),
        "required", (Helper<Object>) (value, options) -> assertThat(!ValueHelpers.empty(value), options, "A required value is missing"),
        "check", (Helper<Object>) (value, options) -> assertThat(truthy(value), options, "The request is not valid"));
  }

  private static Helper<Object> check(Predicate<Object> rule) {
    return (value, options) -> {
      boolean valid = rule.test(value);
      return options.params.length == 0 ? valid : assertThat(valid, options, null);
    };
  }

  private static Object assertThat(boolean valid, Options options, String fallback) {
    if (valid) return "";
    Object given = options.params.length > 0 ? options.param(0) : fallback;
    String message = String.valueOf(given);
    throw new ValidationFailure(status(options.hash("status")), message);
  }

  private static int status(Object value) {
    Object n = ValueHelpers.number(value, 400);
    int status = n instanceof Number number ? number.intValue() : 400;
    return status >= 400 && status <= 599 ? status : 400;
  }

  private static boolean truthy(Object value) {
    if (value instanceof Boolean b) return b;
    return !ValueHelpers.empty(value) && !"false".equalsIgnoreCase(value.toString());
  }
}
