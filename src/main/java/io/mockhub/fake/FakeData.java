package io.mockhub.fake;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.datafaker.Faker;

public final class FakeData {
  private final String defaultLocale;
  static final int MAX_LOCALES = 32;
  private final Map<String, Faker> fakers = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Faker> eldest) {
      return size() > MAX_LOCALES;
    }
  };

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

  int cachedLocales() {
    synchronized (fakers) {
      return fakers.size();
    }
  }

  private Faker faker(String locale) {
    String tag = locale == null || locale.isBlank() ? defaultLocale : locale.trim();
    Locale chosen = Locale.forLanguageTag(tag.replace('_', '-'));
    synchronized (fakers) {
      return fakers.computeIfAbsent(chosen.toLanguageTag(), t -> new Faker(chosen));
    }
  }
}
