package io.mockhub.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CrudQuery(List<Filter> filters, List<SortKey> sort, int offset, int limit) {
  public static final CrudQuery ALL = new CrudQuery(List.of(), List.of(), 0, Integer.MAX_VALUE);

  private static final Set<String> CONTROL = Set.of("_sort", "_order", "_limit", "_offset", "sort", "limit", "offset", "page", "size");
  private static final Pattern OPERATOR = Pattern.compile("^(.+)\\[([A-Za-z]+)]$");

  public enum Op {
    EQ, NE, GT, GTE, LT, LTE;

    static Optional<Op> parse(String name) {
      try {
        return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
  }

  public record Filter(String field, Op op, List<String> values) implements Predicate<ObjectNode> {
    @Override
    public boolean test(ObjectNode record) {
      List<JsonNode> found = Fields.values(record, field);
      return switch (op) {
        case EQ -> found.stream().anyMatch(node -> values.stream().anyMatch(v -> Fields.equal(node, v)));
        case NE -> found.stream().noneMatch(node -> values.stream().anyMatch(v -> Fields.equal(node, v)));
        case GT -> found.stream().anyMatch(node -> values.stream().allMatch(v -> Fields.compare(node, v) > 0));
        case GTE -> found.stream().anyMatch(node -> values.stream().allMatch(v -> Fields.compare(node, v) >= 0));
        case LT -> found.stream().anyMatch(node -> values.stream().allMatch(v -> Fields.compare(node, v) < 0));
        case LTE -> found.stream().anyMatch(node -> values.stream().allMatch(v -> Fields.compare(node, v) <= 0));
      };
    }
  }

  public record SortKey(String field, boolean descending) {}

  public sealed interface Parsed permits Ok, Invalid {}

  public record Ok(CrudQuery query) implements Parsed {}

  public record Invalid(String message) implements Parsed {}

  public static Parsed parse(Map<String, List<String>> params) {
    List<Filter> filters = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : params.entrySet()) {
      if (CONTROL.contains(e.getKey())) continue;
      Matcher m = OPERATOR.matcher(e.getKey());
      if (!m.matches()) {
        filters.add(new Filter(e.getKey(), Op.EQ, e.getValue()));
        continue;
      }
      Optional<Op> op = Op.parse(m.group(2));
      if (op.isEmpty()) return new Invalid("Unknown filter " + e.getKey() + ". Use eq, ne, gt, gte, lt or lte, e.g. price[gte]=10");
      filters.add(new Filter(m.group(1), op.get(), e.getValue()));
    }
    List<SortKey> sort = sort(params);
    try {
      int[] window = window(params);
      return new Ok(new CrudQuery(List.copyOf(filters), sort, window[0], window[1]));
    } catch (IllegalArgumentException e) {
      return new Invalid(e.getMessage());
    }
  }

  public List<ObjectNode> apply(List<ObjectNode> records) {
    List<ObjectNode> matching = records.stream().filter(r -> filters.stream().allMatch(f -> f.test(r))).toList();
    return sort.isEmpty() ? matching : matching.stream().sorted(comparator()).toList();
  }

  private Comparator<ObjectNode> comparator() {
    Comparator<ObjectNode> by = (a, b) -> 0;
    for (SortKey key : sort) {
      Comparator<ObjectNode> one = (a, b) -> Fields.compare(Fields.first(a, key.field()), Fields.first(b, key.field()));
      by = by.thenComparing(key.descending() ? one.reversed() : one);
    }
    return by;
  }

  private static List<SortKey> sort(Map<String, List<String>> params) {
    List<SortKey> keys = new ArrayList<>();
    for (String field : list(first(params, "sort"))) {
      boolean descending = field.startsWith("-");
      keys.add(new SortKey(descending ? field.substring(1) : field.startsWith("+") ? field.substring(1) : field, descending));
    }
    List<String> legacy = list(first(params, "_sort"));
    List<String> orders = list(first(params, "_order"));
    for (int i = 0; i < legacy.size(); i++) {
      String order = orders.isEmpty() ? "asc" : orders.get(Math.min(i, orders.size() - 1));
      keys.add(new SortKey(legacy.get(i), "desc".equalsIgnoreCase(order)));
    }
    return List.copyOf(keys);
  }

  private static int[] window(Map<String, List<String>> params) {
    String page = first(params, "page");
    String size = first(params, "size");
    if (page != null || size != null) {
      int pageNumber = whole(page, 1, 1, "page");
      int pageSize = whole(size, 20, 1, "size");
      long offset = (long) (pageNumber - 1) * pageSize;
      return new int[] {offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset, pageSize};
    }
    String limit = Optional.ofNullable(first(params, "limit")).orElse(first(params, "_limit"));
    String offset = Optional.ofNullable(first(params, "offset")).orElse(first(params, "_offset"));
    return new int[] {whole(offset, 0, 0, "offset"), whole(limit, Integer.MAX_VALUE, 0, "limit")};
  }

  private static int whole(String value, int fallback, int min, String name) {
    if (value == null || value.isBlank()) return fallback;
    try {
      int n = Integer.parseInt(value.trim());
      if (n >= min) return n;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException(name + " must be a whole number" + (min > 0 ? " from " + min : " from 0") + ", not \"" + value + "\"");
  }

  private static String first(Map<String, List<String>> params, String name) {
    List<String> values = params.get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static List<String> list(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  static final class Fields {
    private Fields() {}

    static List<JsonNode> values(JsonNode record, String field) {
      List<JsonNode> current = List.of(record);
      for (String part : field.split("\\.")) {
        List<JsonNode> next = new ArrayList<>();
        for (JsonNode node : current) {
          JsonNode child = node.isArray() ? null : node.get(part);
          if (node.isArray()) node.forEach(item -> Optional.ofNullable(item.get(part)).ifPresent(next::add));
          else if (child != null) next.add(child);
        }
        current = next;
      }
      List<JsonNode> flat = new ArrayList<>();
      current.forEach(node -> {
        if (node.isArray()) node.forEach(flat::add);
        else flat.add(node);
      });
      return flat.stream().filter(n -> !n.isNull() && !n.isMissingNode()).toList();
    }

    static JsonNode first(JsonNode record, String field) {
      List<JsonNode> found = values(record, field);
      return found.isEmpty() ? null : found.get(0);
    }

    static boolean equal(JsonNode node, String value) {
      if (node.isBoolean()) return node.asText().equalsIgnoreCase(value);
      Optional<BigDecimal> left = number(node);
      Optional<BigDecimal> right = number(value);
      if (left.isPresent() && right.isPresent()) return left.get().compareTo(right.get()) == 0;
      return node.asText().equals(value);
    }

    static int compare(JsonNode node, String value) {
      Optional<BigDecimal> left = number(node);
      Optional<BigDecimal> right = number(value);
      if (left.isPresent() && right.isPresent()) return left.get().compareTo(right.get());
      return node.asText().compareToIgnoreCase(value);
    }

    static int compare(JsonNode a, JsonNode b) {
      if (a == null || b == null) return a == null ? (b == null ? 0 : 1) : -1;
      return compare(a, b.asText());
    }

    private static Optional<BigDecimal> number(JsonNode node) {
      return node.isBoolean() || node.isContainerNode() ? Optional.empty() : number(node.asText());
    }

    private static Optional<BigDecimal> number(String text) {
      if (text == null || text.isBlank()) return Optional.empty();
      try {
        return Optional.of(new BigDecimal(text.trim()));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
  }
}
