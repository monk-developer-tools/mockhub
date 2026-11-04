package io.mockhub.publictools;

import io.mockhub.runtime.RateLimit;
import io.mockhub.runtime.RateLimiter;
import java.util.Optional;

public final class PublicLimits {
  public enum Kind {
    ANY(new RateLimit(120, "minute")),
    CREATE(new RateLimit(10, "hour")),
    WORK(new RateLimit(30, "minute"));

    final RateLimit limit;

    Kind(RateLimit limit) {
      this.limit = limit;
    }
  }

  private final RateLimiter limiter;

  public PublicLimits(RateLimiter limiter) {
    this.limiter = limiter;
  }

  public Optional<Long> take(String clientIp, Kind kind) {
    RateLimiter.Decision decision = limiter.take("public:" + kind.name() + ":" + clientIp, kind.limit);
    return decision.allowed() ? Optional.empty() : Optional.of(decision.resetSeconds());
  }
}
