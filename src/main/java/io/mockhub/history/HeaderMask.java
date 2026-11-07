package io.mockhub.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.playground.Header;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HeaderMask {
  public static final String MASKED = "••••••";

  private HeaderMask() {}

  public static HistoryEntry apply(HistoryEntry entry, Set<String> names) {
    if (names.isEmpty() || entry == null) return entry;
    return entry.withHeaders(mask(entry.requestHeaders(), names), mask(entry.responseHeaders(), names));
  }

  public static ObjectNode applyToEvent(ObjectNode event, Set<String> names) {
    if (names.isEmpty()) return event;
    for (String part : List.of("request", "response")) {
      JsonNode headers = event.path(part).path("headers");
      if (!(headers instanceof ObjectNode object)) continue;
      for (String name : new ArrayList<>(iterable(object))) {
        if (names.contains(name.toLowerCase(Locale.ROOT))) object.put(name, MASKED);
      }
    }
    return event;
  }

  private static List<String> iterable(ObjectNode object) {
    List<String> out = new ArrayList<>();
    object.fieldNames().forEachRemaining(out::add);
    return out;
  }

  static List<Header> mask(List<Header> headers, Set<String> names) {
    return headers.stream().map(h -> names.contains(h.name().toLowerCase(Locale.ROOT)) ? new Header(h.name(), MASKED) : h).toList();
  }
}
