package io.mockhub.history;

import static io.mockhub.history.HistoryFixtures.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryStoreTest {

  private static final Instant NOW = Instant.parse("2026-11-03T10:30:00Z");

  @TempDir
  Path dir;
  private HistoryStore store;

  @AfterEach
  void close() {
    if (store != null) store.close();
  }

  @Test
  void searchesNewestFirstWithFacetsAndPages() {
    // Given
    store = new HistoryStore(dir, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7), Long.MAX_VALUE);
    long t = NOW.toEpochMilli();
    store.record(entry("a", t - 2_000, "GET", "/pay/a", 200, true));
    store.record(entry("b", t - 1_000, "POST", "/pay/b", 201, true));
    store.record(entry("c", t, "GET", "/pay/c", 404, false));

    // When
    HistoryPage page = store.search("p1", HistoryQuery.parse(Map.of("limit", "2")::get));
    HistoryPage second = store.search("p1", HistoryQuery.parse(Map.of("limit", "2", "offset", "2")::get));

    // Then
    assertThat(page.items()).extracting(HistoryEntry::id).containsExactly("c", "b");
    assertThat(page.items().get(0).requestBody()).isEmpty();
    assertThat(second.items()).extracting(HistoryEntry::id).containsExactly("a");
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.facets().methods()).containsExactly(Map.entry("GET", 2), Map.entry("POST", 1));
    assertThat(page.facets().unmatched()).isEqualTo(1);
    assertThat(store.get("p1", "b")).hasValueSatisfying(e -> assertThat(e.requestBody()).isEqualTo("{\"id\":\"b\"}"));
  }

  @Test
  void dropsHoursOlderThanTheRetentionAndTheOldestOverTheSizeCap() throws Exception {
    // Given
    long t = NOW.toEpochMilli();
    HistoryStore writer = new HistoryStore(dir, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30), Long.MAX_VALUE);
    writer.record(entry("old", t - Duration.ofDays(8).toMillis(), "GET", "/pay/old", 200, true));
    writer.record(entry("mid", t - Duration.ofHours(5).toMillis(), "GET", "/pay/mid", 200, true));
    writer.record(entry("new", t, "GET", "/pay/new", 200, true));
    writer.close();
    long newest = Files.size(dir.resolve("p1").resolve("2026110310.requests.ndjson"));
    store = new HistoryStore(dir, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7), newest);

    // When
    store.prune();

    // Then
    assertThat(store.search("p1", HistoryQuery.recent(10)).items()).extracting(HistoryEntry::id).containsExactly("new");
  }
}
