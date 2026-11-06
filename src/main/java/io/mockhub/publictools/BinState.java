package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class BinState {
  public static final int MAX_NAMES = 100;
  public static final int MAX_NAME = 60;
  public static final int MAX_VALUE = 4 * 1024;
  public static final int MAX_LIST = 100;
  public static final int MAX_BYTES = 64 * 1024;

  public static final class Full extends RuntimeException {
    Full(String message) {
      super(message, null, false, false);
    }
  }

  private final Map<String, Long> counters = new TreeMap<>();
  private final Map<String, String> values = new TreeMap<>();
  private final Map<String, Deque<String>> lists = new TreeMap<>();
  private boolean changed;

  public synchronized long count(String name, long add, boolean reset) {
    check(name);
    if (!counters.containsKey(name)) room(name, 0);
    long next = (reset ? 0 : counters.getOrDefault(name, 0L)) + add;
    counters.put(name, next);
    changed = true;
    return next;
  }

  public synchronized void save(String name, String value) {
    check(name);
    checkValue(value);
    String before = values.get(name);
    if (before == null) room(name, size(value));
    else if (bytes() - size(before) + size(value) > MAX_BYTES) throw full();
    values.put(name, value);
    changed = true;
  }

  public synchronized Optional<String> saved(String name) {
    return Optional.ofNullable(values.get(name));
  }

  public synchronized void add(String name, String value) {
    check(name);
    checkValue(value);
    Deque<String> list = lists.get(name);
    if (list == null) {
      room(name, size(value));
      list = new ArrayDeque<>();
      lists.put(name, list);
    } else if (bytes() + size(value) > MAX_BYTES) {
      throw full();
    }
    list.addLast(value);
    while (list.size() > MAX_LIST) list.removeFirst();
    changed = true;
  }

  public synchronized List<String> list(String name) {
    Deque<String> list = lists.get(name);
    return list == null ? List.of() : List.copyOf(list);
  }

  public enum Kind {
    COUNTERS, VALUES, LISTS;

    static Kind of(String value) {
      try {
        return valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Saved state is counters, values or lists");
      }
    }
  }

  public synchronized void put(Kind kind, String name, JsonNode value) {
    check(name);
    switch (kind) {
      case COUNTERS -> {
        if (!value.isIntegralNumber()) throw new IllegalArgumentException("A counter is a whole number");
        count(name, value.asLong(), true);
      }
      case VALUES -> {
        if (!value.isTextual() && !value.isNumber() && !value.isBoolean()) throw new IllegalArgumentException("A saved value is text");
        save(name, value.asText());
      }
      case LISTS -> {
        if (!value.isArray()) throw new IllegalArgumentException("A list is an array of text");
        if (value.size() > MAX_LIST) throw new IllegalArgumentException("A list keeps up to " + MAX_LIST + " items");
        Deque<String> before = lists.remove(name);
        try {
          if (value.isEmpty()) {
            room(name, 0);
            lists.put(name, new ArrayDeque<>());
          }
          for (JsonNode item : value) {
            if (item.isContainerNode()) throw new IllegalArgumentException("A list is an array of text");
            add(name, item.asText());
          }
        } catch (RuntimeException e) {
          if (before == null) lists.remove(name);
          else lists.put(name, before);
          throw e;
        }
      }
    }
    changed = true;
  }

  public synchronized boolean remove(Kind kind, String name) {
    boolean removed = switch (kind) {
      case COUNTERS -> counters.remove(name) != null;
      case VALUES -> values.remove(name) != null;
      case LISTS -> lists.remove(name) != null;
    };
    if (removed) changed = true;
    return removed;
  }

  public synchronized void clear() {
    if (counters.isEmpty() && values.isEmpty() && lists.isEmpty()) return;
    counters.clear();
    values.clear();
    lists.clear();
    changed = true;
  }

  public synchronized boolean takeChanged() {
    boolean was = changed;
    changed = false;
    return was;
  }

  public synchronized boolean isEmpty() {
    return counters.isEmpty() && values.isEmpty() && lists.isEmpty();
  }

  public synchronized ObjectNode toJson() {
    ObjectNode out = Jsons.object();
    ObjectNode c = out.putObject("counters");
    counters.forEach(c::put);
    ObjectNode v = out.putObject("values");
    values.forEach(v::put);
    ObjectNode l = out.putObject("lists");
    lists.forEach((name, items) -> {
      ArrayNode a = l.putArray(name);
      items.forEach(a::add);
    });
    return out;
  }

  public static BinState from(JsonNode node) {
    BinState state = new BinState();
    if (node == null || !node.isObject()) return state;
    try {
      node.path("counters").fields().forEachRemaining(e -> state.count(e.getKey(), e.getValue().asLong(), true));
      node.path("values").fields().forEachRemaining(e -> state.save(e.getKey(), e.getValue().asText("")));
      node.path("lists").fields().forEachRemaining(e -> e.getValue().forEach(item -> state.add(e.getKey(), item.asText(""))));
    } catch (IllegalArgumentException | Full e) {
      state.takeChanged();
      return state;
    }
    state.changed = false;
    return state;
  }

  private int names() {
    return counters.size() + values.size() + lists.size();
  }

  private long bytes() {
    long total = 0;
    for (String k : counters.keySet()) total += size(k) + 20;
    for (Map.Entry<String, String> e : values.entrySet()) total += size(e.getKey()) + size(e.getValue());
    for (Map.Entry<String, Deque<String>> e : lists.entrySet()) {
      total += size(e.getKey());
      for (String item : e.getValue()) total += size(item);
    }
    return total;
  }

  private void room(String name, int valueBytes) {
    if (names() >= MAX_NAMES) throw new Full("A bin keeps up to " + MAX_NAMES + " counters, values and lists. Clear its saved state to start again.");
    if (bytes() + size(name) + valueBytes > MAX_BYTES) throw full();
  }

  private static Full full() {
    return new Full("The bin's saved state is full (64 KB). Clear it to start again.");
  }

  private static void check(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_NAME) {
      throw new IllegalArgumentException("Name a counter, value or list with 1 to " + MAX_NAME + " characters");
    }
  }

  private static void checkValue(String value) {
    if (size(value) > MAX_VALUE) throw new IllegalArgumentException("A saved value can be up to 4 KB");
  }

  private static int size(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
