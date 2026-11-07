package io.mockhub.template;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ValueHelpers implements TemplateHelperProviderExtension {

  @Override
  public String getName() {
    return "mockhub-values";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "len", (Helper<Object>) (value, options) -> length(value),
        "object", (Helper<Object>) (value, options) -> object(options),
        "json", (Helper<Object>) (value, options) -> JsonValue.stringify(value, Boolean.TRUE.equals(options.hash("pretty"))),
        "toNumber", (Helper<Object>) (value, options) -> number(value, options.hash("default")),
        "default", (Helper<Object>) (value, options) -> options.tagType == TagType.SECTION ? FlowHelpers.defaultBlock(options)
            : empty(value) && options.params.length > 0 ? options.param(0) : value);
  }

  static int length(Object value) {
    if (value == null) return 0;
    if (value instanceof CharSequence text) return text.length();
    if (value instanceof Collection<?> items) return items.size();
    if (value instanceof Map<?, ?> fields) return fields.size();
    if (value.getClass().isArray()) return Array.getLength(value);
    return value.toString().length();
  }

  private static Object object(Options options) {
    Map<String, Object> fields = new LinkedHashMap<>();
    options.hash.forEach(fields::put);
    return new JsonValue(fields);
  }

  static Object number(Object value, Object fallback) {
    if (value instanceof Number n) return n;
    String text = value == null ? "" : value.toString().trim();
    try {
      BigDecimal parsed = new BigDecimal(text);
      if (parsed.stripTrailingZeros().scale() <= 0 && parsed.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) return parsed.longValueExact();
      return parsed.doubleValue();
    } catch (NumberFormatException | ArithmeticException e) {
      return fallback;
    }
  }

  static Long whole(Object value) {
    Object n = value instanceof Number given ? given : number(value, null);
    if (!(n instanceof Number num)) return null;
    double d = num.doubleValue();
    if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || Math.abs(d) > 9.0e15) return null;
    return num.longValue();
  }

  static boolean empty(Object value) {
    return value == null || value instanceof CharSequence text && text.toString().isBlank();
  }
}
