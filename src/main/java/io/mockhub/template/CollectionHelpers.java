package io.mockhub.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import io.mockhub.api.Jsons;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class CollectionHelpers implements TemplateHelperProviderExtension {
  private final Supplier<Random> random;

  public CollectionHelpers() {
    this(ThreadLocalRandom::current);
  }

  CollectionHelpers(Supplier<Random> random) {
    this.random = random;
  }

  @Override
  public String getName() {
    return "mockhub-collections";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "sort", (Helper<Object>) (value, options) -> sort(value, options),
        "reverse", (Helper<Object>) (value, options) -> reverse(value),
        "oneOf", (Helper<Object>) (value, options) -> oneOf(arguments(value, options)),
        "someOf", (Helper<Object>) (value, options) -> someOf(value, options),
        "concat", (Helper<Object>) (value, options) -> concat(arguments(value, options)),
        "objectMerge", (Helper<Object>) (value, options) -> objectMerge(arguments(value, options)),
        "coalesce", (Helper<Object>) (value, options) -> coalesce(arguments(value, options)),
        "jsonParse", (Helper<Object>) (value, options) -> jsonParse(value, options.params.length > 0 ? options.param(0) : null));
  }

  static List<Object> arguments(Object first, Options options) {
    if (options.params.length == 0 && first != null && first == options.context.model()) return List.of();
    List<Object> all = new ArrayList<>();
    all.add(first);
    Collections.addAll(all, options.params);
    return all;
  }

  static List<Object> items(Object value) {
    if (value instanceof Collection<?> c) return new ArrayList<>(c);
    if (value != null && value.getClass().isArray()) {
      List<Object> out = new ArrayList<>();
      for (int i = 0; i < Array.getLength(value); i++) out.add(Array.get(value, i));
      return out;
    }
    if (value instanceof CharSequence text && text.toString().trim().startsWith("[")) {
      Object parsed = parse(text.toString());
      if (parsed instanceof List<?> list) return new ArrayList<>(list);
    }
    throw new IllegalArgumentException("Expected a list, like (array 'a' 'b') or a JSON array");
  }

  private Object sort(Object value, Options options) {
    List<Object> list = items(value);
    String key = null;
    String order = "asc";
    if (options.params.length == 1) {
      String only = String.valueOf(options.<Object>param(0));
      if (only.equalsIgnoreCase("asc") || only.equalsIgnoreCase("desc")) order = only;
      else key = only;
    } else if (options.params.length >= 2) {
      key = String.valueOf(options.<Object>param(0));
      order = String.valueOf(options.<Object>param(1));
    }
    String field = key;
    Comparator<Object> comparator = Comparator.comparing(item -> field == null ? item : pick(item, field), CollectionHelpers::compare);
    list.sort("desc".equalsIgnoreCase(order) ? comparator.reversed() : comparator);
    return new JsonList(list);
  }

  private static int compare(Object a, Object b) {
    if (a == null || b == null) return a == null ? (b == null ? 0 : 1) : -1;
    BigDecimal x = decimal(a);
    BigDecimal y = decimal(b);
    if (x != null && y != null) return x.compareTo(y);
    return a.toString().compareTo(b.toString());
  }

  private static BigDecimal decimal(Object value) {
    if (value instanceof Number n) return new BigDecimal(n.toString());
    try {
      return value instanceof CharSequence text ? new BigDecimal(text.toString().trim()) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Object pick(Object value, String path) {
    Object current = value;
    for (String part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) current = map.get(part);
      else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size()) current = list.get(Integer.parseInt(part));
      else return null;
    }
    return current;
  }

  private static Object reverse(Object value) {
    if (value == null) return null;
    if (!(value instanceof Collection<?>) && !value.getClass().isArray() && !(value instanceof CharSequence text && text.toString().trim().startsWith("["))) return value;
    List<Object> list = items(value);
    Collections.reverse(list);
    return new JsonList(list);
  }

  private Object oneOf(List<Object> values) {
    List<Object> present = values.stream().filter(v -> v != null).toList();
    return present.isEmpty() ? "" : present.get(random.get().nextInt(present.size()));
  }

  private Object someOf(Object value, Options options) {
    List<Object> list = items(value);
    int max = options.params.length > 1 ? (int) Math.min(list.size(), whole(options.<Object>param(1))) : list.size();
    int min = options.params.length > 0 ? (int) Math.max(0, Math.min(max, whole(options.<Object>param(0)))) : 0;
    Random rnd = random.get();
    int take = min + (max > min ? rnd.nextInt(max - min + 1) : 0);
    List<Object> shuffled = new ArrayList<>(list);
    Collections.shuffle(shuffled, rnd);
    List<Object> chosen = shuffled.subList(0, take);
    boolean json = options.params.length > 2 && "true".equalsIgnoreCase(String.valueOf(options.<Object>param(2)));
    return json ? new JsonList(chosen) : chosen.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static long whole(Object value) {
    Long n = ValueHelpers.whole(value);
    if (n == null) throw new IllegalArgumentException("Expected a whole number, not " + value);
    return n;
  }

  private static Object concat(List<Object> values) {
    boolean lists = values.stream().anyMatch(v -> v instanceof Collection<?> || v != null && v.getClass().isArray());
    if (lists) {
      List<Object> out = new ArrayList<>();
      for (Object v : values) {
        if (v == null) continue;
        if (v instanceof Collection<?> || v.getClass().isArray()) out.addAll(items(v));
        else out.add(v);
      }
      return new JsonList(out);
    }
    StringBuilder out = new StringBuilder();
    for (Object v : values) {
      if (v != null) out.append(v);
    }
    return out.toString();
  }

  private static Object objectMerge(List<Object> values) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Object v : values) {
      if (v == null) continue;
      Object map = v instanceof CharSequence text ? parse(text.toString()) : v;
      if (!(map instanceof Map<?, ?> fields)) throw new IllegalArgumentException("objectMerge takes objects, like (object a=1) or a JSON object");
      fields.forEach((k, value) -> out.put(String.valueOf(k), value));
    }
    return new JsonValue(out);
  }

  private static Object coalesce(List<Object> values) {
    for (Object v : values) {
      if (v != null) return v;
    }
    return null;
  }

  private static Object jsonParse(Object value, Object path) {
    if (value == null) return null;
    Object parsed = value instanceof CharSequence text ? parse(text.toString()) : value;
    Object picked = path == null || path.toString().isBlank() ? parsed : pick(parsed, path.toString());
    return wrap(picked);
  }

  static Object parse(String text) {
    try {
      return wrap(Jsons.MAPPER.readValue(text, new TypeReference<Object>() {}));
    } catch (Exception e) {
      throw new IllegalArgumentException("That isn't valid JSON: " + (text.length() > 60 ? text.substring(0, 60) + "…" : text));
    }
  }

  @SuppressWarnings("unchecked")
  static Object wrap(Object value) {
    if (value instanceof JsonValue || value instanceof JsonList) return value;
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      ((Map<String, Object>) map).forEach((k, v) -> out.put(k, wrap(v)));
      return new JsonValue(out);
    }
    if (value instanceof List<?> list) return new JsonList(list.stream().map(CollectionHelpers::wrap).toList());
    return value;
  }
}
