package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public record RateLimit(int requests, String per) {
  private static final Map<String, Duration> WINDOWS = Map.of("second", Duration.ofSeconds(1), "minute", Duration.ofMinutes(1), "hour", Duration.ofHours(1));

  public static RateLimit parse(JsonNode node, String label) {
    if (Fields.absent(node)) return null;
    int requests = Fields.integer(node, "requests", 0, 1, 1_000_000, "The rate limit");
    String per = node.path("per").asText("minute").trim().toLowerCase(Locale.ROOT);
    if (!WINDOWS.containsKey(per)) throw ApiException.badRequest("The rate limit must be per second, minute or hour");
    return new RateLimit(requests, per);
  }

  public static RateLimit fromMetadata(Object value) {
    if (!(value instanceof Map<?, ?> m)) return null;
    Object requests = m.get("requests");
    Object per = m.get("per");
    int count;
    try {
      count = requests instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(requests).trim());
    } catch (NumberFormatException e) {
      return null;
    }
    String window = per == null ? "minute" : per.toString().toLowerCase(Locale.ROOT);
    return count > 0 && WINDOWS.containsKey(window) ? new RateLimit(count, window) : null;
  }

  public Duration window() {
    return WINDOWS.get(per);
  }

  public String describe() {
    return requests + (requests == 1 ? " request" : " requests") + " per " + per;
  }
}
