package io.mockhub.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryStoreCloseTest {

  @TempDir
  Path dir;

  @Test
  void closingTwiceReturnsAtOnce() {
    // Given
    HistoryStore store = new HistoryStore(dir, Clock.systemUTC(), Duration.ofDays(1), Long.MAX_VALUE);
    store.close();

    // When
    long started = System.nanoTime();
    store.close();
    store.flush();
    long tookMs = (System.nanoTime() - started) / 1_000_000;

    // Then
    assertThat(tookMs).isLessThan(1000);
  }
}
