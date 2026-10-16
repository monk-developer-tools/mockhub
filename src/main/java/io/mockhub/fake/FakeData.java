package io.mockhub.fake;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.datafaker.Faker;

public final class FakeData {
  private final String defaultLocale;
  private final Map<String, Faker> fakers = new ConcurrentHashMap<>();

  public FakeData(String defaultLocale) {
    this.defaultLocale = defaultLocale == null || defaultLocale.isBlank() ? "en" : defaultLocale.trim();
  }

  public String generate(String expression, String locale) {
    String expr = expression == null ? "" : expression.trim();
    if (expr.isEmpty()) throw new IllegalArgumentException("Say what to generate, e.g. {{fake 'Name.fullName'}}");
    if (!expr.contains("#{")) expr = "#{" + expr + "}";
    Faker faker = faker(locale);
    synchronized (faker) {
      return faker.expression(expr);
    }
  }

  private Faker faker(String locale) {
    String tag = locale == null || locale.isBlank() ? defaultLocale : locale.trim();
    return fakers.computeIfAbsent(tag, t -> new Faker(Locale.forLanguageTag(t.replace('_', '-'))));
  }
}
