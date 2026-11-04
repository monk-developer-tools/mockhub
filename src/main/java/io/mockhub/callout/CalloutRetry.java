package io.mockhub.callout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record CalloutRetry(int count, long backoffMs, String statuses) {
  public static final int MAX_COUNT = 5;
  public static final long DEFAULT_BACKOFF_MS = 1000;
  public static final long MAX_BACKOFF_MS = 30_000;
  public static final String DEFAULT_STATUSES = "429,5xx";
  public static final CalloutRetry NONE = new CalloutRetry(0, DEFAULT_BACKOFF_MS, DEFAULT_STATUSES);

  public CalloutRetry {
    count = Math.max(0, Math.min(MAX_COUNT, count));
    backoffMs = backoffMs <= 0 ? DEFAULT_BACKOFF_MS : Math.min(MAX_BACKOFF_MS, backoffMs);
    statuses = statuses == null || statuses.isBlank() ? DEFAULT_STATUSES : statuses.trim();
  }

  static CalloutRetry from(Object value) {
    if (value instanceof Number n) return new CalloutRetry(n.intValue(), 0, null);
    if (value instanceof String s && s.trim().matches("\\d+")) return new CalloutRetry(Integer.parseInt(s.trim()), 0, null);
    if (!(value instanceof Map<?, ?> map)) return NONE;
    return new CalloutRetry(number(map.get("count")), number(map.get("backoffMs")), statusText(map.get("statuses")));
  }

  public boolean retriesStatus(int status) {
    for (String part : statuses.split(",")) {
      String p = part.trim().toLowerCase(Locale.ROOT);
      if (p.matches("[1-5]xx") && status / 100 == p.charAt(0) - '0') return true;
      if (p.matches("\\d{3}") && Integer.parseInt(p) == status) return true;
    }
    return false;
  }

  public long backoffBefore(int nextAttempt) {
    long wait = backoffMs;
    for (int i = 2; i < nextAttempt && wait < MAX_BACKOFF_MS; i++) wait *= 2;
    return Math.min(MAX_BACKOFF_MS, wait);
  }

  private static int number(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value instanceof String s && s.trim().matches("\\d+")) return Integer.parseInt(s.trim());
    return 0;
  }

  private static String statusText(Object value) {
    if (value instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      list.forEach(v -> out.add(String.valueOf(v)));
      return String.join(",", out);
    }
    return value == null ? null : value.toString();
  }
}
