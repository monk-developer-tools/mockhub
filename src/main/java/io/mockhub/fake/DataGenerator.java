package io.mockhub.fake;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DataGenerator {
  public static final int MAX_RECORDS = 1000;
  public static final int MAX_FIELDS = 50;
  public static final String SEQUENCE = "sequence";
  public static final String UUID_TYPE = "uuid";
  private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
  private static final Pattern FIELD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$-]*(\\.[A-Za-z_$][A-Za-z0-9_$-]*)*");

  private final FakeData fake;

  public DataGenerator(FakeData fake) {
    this.fake = fake;
  }

  public record Field(String name, String expression) {}

  public ArrayNode generate(List<Field> fields, int count, String locale) {
    if (fields == null || fields.isEmpty()) throw ApiException.badRequest("Add at least one field");
    if (fields.size() > MAX_FIELDS) throw ApiException.badRequest("Use at most " + MAX_FIELDS + " fields");
    if (count < 1 || count > MAX_RECORDS) throw ApiException.badRequest("Generate between 1 and " + MAX_RECORDS + " records");
    for (Field f : fields) {
      if (f.name() == null || !FIELD.matcher(f.name()).matches()) {
        throw ApiException.badRequest("'" + f.name() + "' isn't a valid field name. Use letters, digits and dots for nesting, e.g. address.city");
      }
      if (f.expression() == null || f.expression().isBlank()) throw ApiException.badRequest("Choose what to generate for " + f.name());
    }
    ArrayNode records = Jsons.MAPPER.createArrayNode();
    for (int i = 1; i <= count; i++) {
      ObjectNode record = records.addObject();
      for (Field f : fields) put(record, f.name().split("\\."), 0, value(f.expression().trim(), i, locale));
    }
    return records;
  }

  private Object value(String expression, int index, String locale) {
    if (expression.equals(SEQUENCE)) return index;
    if (expression.equals(UUID_TYPE)) return UUID.randomUUID().toString();
    String raw;
    try {
      raw = fake.generate(expression, locale);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("Can't generate '" + expression + "': " + e.getMessage());
    }
    String category = expression.replace("#{", "").split("[. ]")[0];
    if ((category.equals("Number") || expression.startsWith("Commerce.price")) && NUMBER.matcher(raw).matches()) {
      return raw.contains(".") ? (Object) Double.parseDouble(raw) : (Object) Long.parseLong(raw);
    }
    if (category.equals("Bool") && (raw.equals("true") || raw.equals("false"))) return Boolean.parseBoolean(raw);
    return raw;
  }

  private static void put(ObjectNode node, String[] path, int depth, Object value) {
    String key = path[depth];
    if (depth == path.length - 1) {
      node.set(key, Jsons.MAPPER.valueToTree(value));
      return;
    }
    ObjectNode child = node.get(key) instanceof ObjectNode o ? o : node.putObject(key);
    put(child, path, depth + 1, value);
  }
}
