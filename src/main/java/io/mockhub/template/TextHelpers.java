package io.mockhub.template;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

public final class TextHelpers implements TemplateHelperProviderExtension {
  public static final int MAX_PAD = 10_000;

  @Override
  public String getName() {
    return "mockhub-text";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "padStart", (Helper<Object>) (value, options) -> pad(value, options, true),
        "padEnd", (Helper<Object>) (value, options) -> pad(value, options, false),
        "urlDecode", (Helper<Object>) (value, options) -> URLDecoder.decode(text(value, options).replace("+", "%2B"), StandardCharsets.UTF_8),
        "base64Decode", (Helper<Object>) (value, options) -> base64(text(value, options)),
        "lowercase", (Helper<Object>) (value, options) -> value == null ? "" : value.toString().toLowerCase(Locale.ROOT),
        "uppercase", (Helper<Object>) (value, options) -> value == null ? "" : value.toString().toUpperCase(Locale.ROOT),
        "isInteger", (Helper<Object>) (value, options) -> integer(value),
        "isDate", (Helper<Object>) (value, options) -> TemplateDates.isDate(value));
  }

  private static String text(Object value, Options options) throws IOException {
    if (options.tagType == TagType.SECTION) return options.fn().toString().trim();
    return value == null ? "" : value.toString();
  }

  private static String pad(Object value, Options options, boolean start) {
    String text = value == null ? "" : value.toString();
    Long length = options.params.length > 0 ? ValueHelpers.whole(options.<Object>param(0)) : null;
    if (length == null) throw new IllegalArgumentException("Give the length to pad to, like {{padStart '42' 5 '0'}}");
    if (length > MAX_PAD) throw new IllegalArgumentException("Pad to at most " + MAX_PAD + " characters");
    String filler = options.params.length > 1 && options.param(1) != null ? options.param(1).toString() : " ";
    if (filler.isEmpty() || text.length() >= length) return text;
    StringBuilder padding = new StringBuilder();
    while (padding.length() < length - text.length()) padding.append(filler);
    padding.setLength((int) (length - text.length()));
    return start ? padding + text : text + padding;
  }

  private static String base64(String text) {
    try {
      return new String(Base64.getDecoder().decode(text.trim()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("That isn't valid Base64");
    }
  }

  private static BigDecimal decimal(Object value) {
    if (value instanceof Number n) return new BigDecimal(n.toString());
    if (value == null || value.toString().isBlank()) return null;
    try {
      return new BigDecimal(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean integer(Object value) {
    if (value instanceof Double || value instanceof Float) return false;
    BigDecimal n = decimal(value);
    return n != null && !value.toString().contains(".") && n.stripTrailingZeros().scale() <= 0;
  }
}
