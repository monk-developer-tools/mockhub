package io.mockhub.publictools;

import java.util.Locale;
import java.util.function.Predicate;

final class BinLogFilter {
  private BinLogFilter() {}

  static Predicate<Bins.Captured> of(PublicRequest request) {
    return of(request.firstQuery("method"), request.firstQuery("status"), request.firstQuery("q"));
  }

  static Predicate<Bins.Captured> of(String method, String status, String text) {
    Predicate<Bins.Captured> out = c -> true;
    if (method != null && !method.isBlank()) {
      String m = method.trim().toUpperCase(Locale.ROOT);
      out = out.and(c -> c.method().equals(m));
    }
    if (status != null && !status.isBlank()) out = out.and(status(status.trim().toLowerCase(Locale.ROOT)));
    if (text != null && !text.isBlank()) {
      String needle = text.trim().toLowerCase(Locale.ROOT);
      out = out.and(c -> haystack(c).contains(needle));
    }
    return out;
  }

  private static Predicate<Bins.Captured> status(String status) {
    if (status.matches("[1-5]xx")) {
      int band = status.charAt(0) - '0';
      return c -> c.outcome() != null && c.outcome().status() / 100 == band;
    }
    if (status.matches("\\d{3}")) {
      int exact = Integer.parseInt(status);
      return c -> c.outcome() != null && c.outcome().status() == exact;
    }
    throw new IllegalArgumentException("Filter the status with a code like 404 or a class like 4xx");
  }

  private static String haystack(Bins.Captured c) {
    StringBuilder text = new StringBuilder(c.path()).append(' ').append(c.query()).append(' ').append(c.event()).append(' ').append(c.body());
    c.headers().forEach((k, v) -> text.append(' ').append(k).append(": ").append(v));
    if (c.outcome() != null) text.append(' ').append(c.outcome().rule());
    return text.toString().toLowerCase(Locale.ROOT);
  }
}
