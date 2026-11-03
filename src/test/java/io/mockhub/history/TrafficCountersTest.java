package io.mockhub.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrafficCountersTest {

  private static final Instant NOW = Instant.parse("2026-11-03T10:30:00Z");

  @TempDir
  Path dir;

  @Test
  void summarisesAWindowWithItsPeakSecond() {
    // Given
    TrafficCounters traffic = new TrafficCounters(dir, Clock.fixed(NOW, ZoneOffset.UTC));
    long t = NOW.toEpochMilli();
    for (int i = 0; i < 5; i++) traffic.record("p1", t - 10_000, true);
    traffic.record("p1", t - 9_000, false);
    traffic.record("p1", t, true);

    // When
    TrafficCounters.View view = traffic.view("p1", "5m");
    TrafficCounters.View day = traffic.view("p1", "24h");

    // Then
    assertThat(view.points()).hasSize(300);
    assertThat(view.total()).isEqualTo(7);
    assertThat(view.unmatched()).isEqualTo(1);
    assertThat(view.peakRps()).isEqualTo(5);
    assertThat(view.lastAt()).isEqualTo(t + 1000);
    assertThat(view.averageRps()).isEqualTo(0.6);
    assertThat(day.bucketSeconds()).isEqualTo(300);
    assertThat(day.total()).isEqualTo(7);
    assertThat(day.peakRps()).isEqualTo(5);
  }

  @Test
  void keepsDailyTotalsAcrossARestart() {
    // Given
    TrafficCounters first = new TrafficCounters(dir, Clock.fixed(NOW, ZoneOffset.UTC));
    first.record("p1", NOW.toEpochMilli(), true);
    first.record("p1", NOW.toEpochMilli(), false);
    first.save();

    // When
    TrafficCounters second = new TrafficCounters(dir, Clock.fixed(NOW, ZoneOffset.UTC));

    // Then
    assertThat(second.daily("p1")).hasSize(30).last()
        .isEqualTo(new TrafficCounters.Day("2026-11-03", 2, 1));
  }

  @Test
  void rejectsUnknownWindows() {
    // When / Then
    assertThatThrownBy(() -> new TrafficCounters(dir, Clock.systemUTC()).view("p1", "2d")).isInstanceOf(ApiException.class);
  }
}
