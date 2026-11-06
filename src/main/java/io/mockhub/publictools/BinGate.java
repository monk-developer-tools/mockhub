package io.mockhub.publictools;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class BinGate {
  private record Window(long start, long count, BinOptions.RateLimit limit) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final LongSupplier millis;

  BinGate(LongSupplier millis) {
    this.millis = millis;
  }

  Optional<Long> take(String binId, BinOptions.RateLimit limit) {
    long now = millis.getAsLong();
    long span = limit.per().millis;
    Window window = windows.compute(binId, (k, w) -> w == null || !w.limit().equals(limit) || now - w.start() >= span
        ? new Window(now, 1, limit)
        : new Window(w.start(), w.count() + 1, limit));
    if (window.count() <= limit.requests()) return Optional.empty();
    return Optional.of(Math.max(1, (window.start() + span - now + 999) / 1000));
  }
}
