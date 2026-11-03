package io.mockhub.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private Instant now = Instant.parse("2026-11-03T10:00:00Z");
  private final Clock clock = new Clock() {
    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  };

  @Test
  void refusesCallsOverTheLimitUntilTheWindowEnds() {
    // Given
    RateLimiter limiter = new RateLimiter(clock);
    RateLimit limit = new RateLimit(2, "minute");
    limiter.take("p", limit);
    limiter.take("p", limit);

    // When
    RateLimiter.Decision refused = limiter.take("p", limit);
    now = now.plus(Duration.ofSeconds(61));
    RateLimiter.Decision afterWindow = limiter.take("p", limit);

    // Then
    assertThat(refused.allowed()).isFalse();
    assertThat(refused.resetSeconds()).isEqualTo(60);
    assertThat(afterWindow.allowed()).isTrue();
    assertThat(afterWindow.remaining()).isEqualTo(1);
  }

  @Test
  void addsDelayOnlyAboveTheLoadThreshold() {
    // Given
    LoadMeter meter = new LoadMeter(clock);
    LoadLatency load = new LoadLatency(3, 50);
    for (int i = 0; i < 5; i++) meter.record("p");

    // When
    int extra = load.extraMs(meter.requestsPerSecond("p"));
    int quiet = load.extraMs(meter.requestsPerSecond("other"));

    // Then
    assertThat(extra).isEqualTo(100);
    assertThat(quiet).isZero();
  }
}
