package io.mockhub.runtime;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  public record Decision(boolean allowed, int limit, int remaining, long resetSeconds) {}

  public Decision take(String key, RateLimit limit) {
    long now = clock.millis();
    long length = limit.window().toMillis();
    String id = key + "|" + limit.requests() + "/" + limit.per();
    Window window = windows.compute(id, (k, w) -> w == null || now >= w.start + length ? new Window(now, 1) : new Window(w.start, w.count + 1));
    long reset = Math.max(1, (window.start + length - now + 999) / 1000);
    boolean allowed = window.count <= limit.requests();
    return new Decision(allowed, limit.requests(), Math.max(0, limit.requests() - window.count), reset);
  }

  public void forget(String keyPrefix) {
    windows.keySet().removeIf(k -> k.startsWith(keyPrefix + "|") || k.startsWith(keyPrefix + ":"));
  }

  private record Window(long start, int count) {}
}
