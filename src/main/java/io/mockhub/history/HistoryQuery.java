package io.mockhub.history;

import io.mockhub.api.ApiException;
import io.mockhub.playground.Header;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;

public record HistoryQuery(String text, String method, int statusFrom, int statusTo, Boolean matched, String stubId,
                           String headerName, String headerValue, long from, long to, int limit, int offset) {

  public static final int MAX_LIMIT = 500;

  public static HistoryQuery parse(Function<String, String> param) {
    String status = blank(param.apply("status"));
    int[] range = status == null ? new int[] {0, 999} : statusRange(status);
    String header = blank(param.apply("header"));
    String headerName = null;
    String headerValue = null;
    if (header != null) {
      int colon = header.indexOf(':');
      headerName = (colon < 0 ? header : header.substring(0, colon)).trim();
      headerValue = colon < 0 ? null : blank(header.substring(colon + 1));
    }
    String matched = blank(param.apply("matched"));
    return new HistoryQuery(
        lower(blank(param.apply("q"))),
        upper(blank(param.apply("method"))),
        range[0], range[1],
        matched == null ? null : Boolean.parseBoolean(matched),
        blank(param.apply("stub")),
        headerName == null ? null : headerName.toLowerCase(Locale.ROOT),
        lower(headerValue),
        time(param.apply("from"), 0),
        time(param.apply("to"), Long.MAX_VALUE),
        Math.max(1, Math.min(MAX_LIMIT, number(param.apply("limit"), 100))),
        Math.max(0, number(param.apply("offset"), 0)));
  }

  public static HistoryQuery recent(int limit) {
    return new HistoryQuery(null, null, 0, 999, null, null, null, null, 0, Long.MAX_VALUE, limit, 0);
  }

  public boolean matches(HistoryEntry e) {
    if (e.at() < from || e.at() > to) return false;
    if (method != null && !method.equals(e.method())) return false;
    if (e.status() < statusFrom || e.status() > statusTo) return false;
    if (matched != null && matched != e.matched()) return false;
    if (stubId != null && !stubId.equals(e.stubId())) return false;
    if (headerName != null && e.requestHeaders().stream().noneMatch(this::headerMatches)) return false;
    return text == null || contains(e.url(), text) || contains(e.requestBody(), text) || contains(e.responseBody(), text)
        || contains(e.stubName(), text);
  }

  private boolean headerMatches(Header h) {
    return h.name().toLowerCase(Locale.ROOT).equals(headerName) && (headerValue == null || contains(h.value(), headerValue));
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  static int[] statusRange(String status) {
    String s = status.trim().toLowerCase(Locale.ROOT);
    try {
      if (s.matches("[1-5]xx")) {
        int base = (s.charAt(0) - '0') * 100;
        return new int[] {base, base + 99};
      }
      if (s.matches("\\d{3}-\\d{3}")) return new int[] {Integer.parseInt(s.substring(0, 3)), Integer.parseInt(s.substring(4))};
      if (s.matches("\\d{3}")) return new int[] {Integer.parseInt(s), Integer.parseInt(s)};
    } catch (NumberFormatException ignored) {
      throw ApiException.badRequest("The status filter must look like 404, 4xx or 400-499");
    }
    throw ApiException.badRequest("The status filter must look like 404, 4xx or 400-499");
  }

  private static long time(String value, long fallback) {
    String v = blank(value);
    if (v == null) return fallback;
    try {
      return v.matches("\\d+") ? Long.parseLong(v) : Instant.parse(v).toEpochMilli();
    } catch (DateTimeParseException | NumberFormatException e) {
      throw ApiException.badRequest("Times must be ISO-8601, like 2026-11-03T10:00:00Z, or epoch milliseconds");
    }
  }

  private static int number(String value, int fallback) {
    String v = blank(value);
    if (v == null) return fallback;
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("'" + v + "' isn't a whole number");
    }
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
}
