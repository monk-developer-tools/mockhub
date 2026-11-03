package io.mockhub.runtime;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoadMeter {
  private final Clock clock;
  private final Map<String, Second> seconds = new ConcurrentHashMap<>();

  public LoadMeter(Clock clock) {
    this.clock = clock;
  }

  public void record(String projectId) {
    long now = clock.millis() / 1000;
    seconds.compute(projectId, (k, s) -> {
      if (s == null || now > s.second + 1) return new Second(now, 1, 0);
      if (now == s.second + 1) return new Second(now, 1, s.count);
      return new Second(s.second, s.count + 1, s.previous);
    });
  }

  public int requestsPerSecond(String projectId) {
    long now = clock.millis() / 1000;
    Second s = seconds.get(projectId);
    if (s == null || now > s.second + 1) return 0;
    if (now == s.second + 1) return s.count;
    return Math.max(s.count, s.previous);
  }

  private record Second(long second, int count, int previous) {}
}
