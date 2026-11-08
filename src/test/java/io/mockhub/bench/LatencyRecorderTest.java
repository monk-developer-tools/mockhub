package io.mockhub.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LatencyRecorderTest {

  @Test
  void summarisesEveryWorkersLatenciesWithNearestRankPercentiles() {
    // Given
    LatencyRecorder first = new LatencyRecorder();
    LatencyRecorder second = new LatencyRecorder();
    for (int i = 1; i <= 500; i++) first.record(i * 1000L);
    for (int i = 501; i <= 1000; i++) second.record(i * 1000L);

    // When
    Latency latency = LatencyRecorder.summarise(List.of(first, second));

    // Then
    assertThat(latency.count()).isEqualTo(1000);
    assertThat(latency.p50Micros()).isEqualTo(500.0);
    assertThat(latency.p90Micros()).isEqualTo(900.0);
    assertThat(latency.p99Micros()).isEqualTo(990.0);
    assertThat(latency.p999Micros()).isEqualTo(999.0);
    assertThat(latency.maxMicros()).isEqualTo(1000.0);
    assertThat(latency.meanMicros()).isEqualTo(500.5);
  }

  @Test
  void growsPastItsFirstBufferAndReportsNothingWhenEmpty() {
    // Given
    LatencyRecorder busy = new LatencyRecorder();
    for (int i = 0; i < 10_000; i++) busy.record(2_000L);

    // When
    Latency many = LatencyRecorder.summarise(List.of(busy));
    Latency none = LatencyRecorder.summarise(List.of(new LatencyRecorder()));

    // Then
    assertThat(many.count()).isEqualTo(10_000);
    assertThat(many.p99Micros()).isEqualTo(2.0);
    assertThat(none.count()).isZero();
    assertThat(none.maxMicros()).isZero();
  }
}
