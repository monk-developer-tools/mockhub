package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.fake.DataGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

final class FakeDrafter {
  static final int MAX_DESCRIPTION = 500;
  static final List<String> LOCALES = List.of("", "en-GB", "de", "fr", "es", "it", "nl", "pt-BR", "pl", "sv", "ja", "en-IND");
  private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,39}(\\.[A-Za-z_][A-Za-z0-9_]{0,39}){0,2}");
  private static final String FAILED = "The AI didn't return any fields. Try describing the data differently.";
  static final String SYSTEM = """
      You design test data for a fake data generator. Reply with one JSON object and nothing else:
      {"count": 10, "locale": "", "fields": [{"name": "id", "expression": "sequence"}]}
      count: how many records, 1 to 100; 10 when the description doesn't say.
      locale: the language and country of the data, one of "" (US English), "en-GB", "de", "fr", "es", "it", "nl", "pt-BR", "pl", "sv", "ja", "en-IND".
      Pick it from the description: German customers are "de", shops in Brazil are "pt-BR", UK addresses are "en-GB".
      fields: at most 20. name is camelCase; use dots to nest, like address.city. expression is one of:
      sequence (1, 2, 3…), uuid, Name.fullName, Name.firstName, Name.lastName, Internet.emailAddress, Internet.username,
      PhoneNumber.phoneNumber, Job.title, Address.streetAddress, Address.city, Address.zipCode, Address.state, Address.country,
      Address.latitude, Address.longitude, Company.name, Commerce.productName, Commerce.price, Commerce.department,
      Finance.iban, Money.currencyCode, Color.name, Internet.url, Bool.bool, Lorem.word, Lorem.sentence, Book.title,
      Number.numberBetween '1','100' (whole numbers; change the bounds), Number.randomDouble '2','1','1000' (decimals),
      Date.birthday 'yyyy-MM-dd' (dates), TimeAndDate.past '365','DAYS' (timestamps), or another Datafaker expression.
      """;

  record Draft(List<DataGenerator.Field> fields, int count, String locale, List<String> skipped) {}

  private final BooleanSupplier enabled;
  private final BinDrafter.Llm llm;
  private final DataGenerator generator;

  FakeDrafter(BooleanSupplier enabled, BinDrafter.Llm llm, DataGenerator generator) {
    this.enabled = enabled;
    this.llm = llm;
    this.generator = generator;
  }

  boolean available() {
    return enabled.getAsBoolean();
  }

  Draft draft(String description) {
    if (!available()) throw new BinDrafter.Unavailable("This server has no AI provider set up.");
    String text = description == null ? "" : description.strip();
    if (text.isEmpty()) throw new IllegalArgumentException("Describe the records, for example: 20 German customers with an IBAN");
    if (text.length() > MAX_DESCRIPTION) throw new IllegalArgumentException("Keep the description under " + MAX_DESCRIPTION + " characters");
    return read(LlmJson.parse(llm.complete(SYSTEM, text), FAILED));
  }

  Draft read(JsonNode node) {
    List<String> skipped = new ArrayList<>();
    String locale = node.path("locale").asText("").trim();
    if (!LOCALES.contains(locale)) {
      skipped.add("locale " + locale + ": not one of the languages here, so English is used");
      locale = "";
    }
    int count = node.path("count").asInt(10);
    count = Math.max(1, Math.min(FakeTool.MAX_COUNT, count));
    List<DataGenerator.Field> fields = new ArrayList<>();
    for (JsonNode f : node.path("fields")) {
      if (fields.size() >= FakeTool.MAX_FIELDS) break;
      String name = f.path("name").asText("").trim();
      String expression = f.path("expression").asText("").trim();
      if (!NAME.matcher(name).matches()) {
        skipped.add((name.isEmpty() ? "A field" : name) + ": field names are letters, digits and underscores, with dots to nest");
        continue;
      }
      if (fields.stream().anyMatch(existing -> existing.name().equals(name))) continue;
      DataGenerator.Field field = new DataGenerator.Field(name, expression);
      if (works(field, locale)) fields.add(field);
      else skipped.add(name + ": " + (expression.isEmpty() ? "no expression" : expression + " didn't generate a value"));
    }
    if (fields.isEmpty()) throw new IllegalArgumentException(skipped.isEmpty() ? FAILED : "None of the AI's fields worked: " + skipped.get(0));
    return new Draft(List.copyOf(fields), count, locale, List.copyOf(skipped));
  }

  private boolean works(DataGenerator.Field field, String locale) {
    if (field.expression().isEmpty() || RenderBudget.largestNumber(field.expression()) > FakeTool.MAX_SIZE) return false;
    try {
      generator.generate(List.of(field), 1, locale.isEmpty() ? null : locale);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
