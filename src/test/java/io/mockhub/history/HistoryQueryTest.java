package io.mockhub.history;

import static io.mockhub.history.HistoryFixtures.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryQueryTest {

  private static HistoryQuery query(Map<String, String> params) {
    return HistoryQuery.parse(params::get);
  }

  @Test
  void combinesEveryFilter() {
    // Given
    HistoryQuery q = query(Map.of("method", "post", "status", "2xx", "matched", "true", "header", "x-trace: TRACE-a", "q", "ID\":\"A"));

    // When
    boolean hit = q.matches(entry("a", 1000, "POST", "/pay/a", 201, true));
    boolean wrongMethod = q.matches(entry("a", 1000, "GET", "/pay/a", 201, true));
    boolean wrongStatus = q.matches(entry("a", 1000, "POST", "/pay/a", 500, true));
    boolean wrongHeader = q.matches(entry("b", 1000, "POST", "/pay/b", 201, true));

    // Then
    assertThat(hit).isTrue();
    assertThat(wrongMethod).isFalse();
    assertThat(wrongStatus).isFalse();
    assertThat(wrongHeader).isFalse();
  }

  @Test
  void readsStatusRangesAndTimes() {
    // Given
    HistoryQuery range = query(Map.of("status", "400-404", "from", "2026-11-03T10:00:00Z", "to", "1793750400000"));
    long tenOClock = 1793700000000L;

    // When / Then
    assertThat(range.statusFrom()).isEqualTo(400);
    assertThat(range.statusTo()).isEqualTo(404);
    assertThat(range.from()).isEqualTo(tenOClock);
    assertThat(range.matches(entry("a", tenOClock - 1, "GET", "/a", 404, false))).isFalse();
    assertThat(range.matches(entry("a", tenOClock, "GET", "/a", 404, false))).isTrue();
    assertThat(range.matches(entry("a", tenOClock, "GET", "/a", 405, false))).isFalse();
  }

  @Test
  void capsTheLimitAndRejectsWhatItCannotRead() {
    // When
    HistoryQuery big = query(Map.of("limit", "100000"));

    // Then
    assertThat(big.limit()).isEqualTo(HistoryQuery.MAX_LIMIT);
    assertThatThrownBy(() -> query(Map.of("status", "teapot"))).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> query(Map.of("from", "yesterday"))).isInstanceOf(ApiException.class).hasMessageContaining("ISO-8601");
  }
}
