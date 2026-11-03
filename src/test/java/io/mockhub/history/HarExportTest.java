package io.mockhub.history;

import static io.mockhub.history.HistoryFixtures.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarExportTest {

  @Test
  void writesAHar12LogWithQueryBodyAndTimings() {
    // Given
    HistoryEntry e = entry("a", 1793700000000L, "POST", "/pay/a?x=1&name=J%20Doe", 201, true);

    // When
    JsonNode har = HarExport.of(List.of(e), "1.2.3");

    // Then
    assertThat(har.at("/log/version").asText()).isEqualTo("1.2");
    assertThat(har.at("/log/creator/version").asText()).isEqualTo("1.2.3");
    JsonNode entry = har.at("/log/entries/0");
    assertThat(entry.at("/startedDateTime").asText()).isEqualTo("2026-11-03T10:00:00Z");
    assertThat(entry.at("/request/url").asText()).isEqualTo("http://localhost:8080/pay/a?x=1&name=J%20Doe");
    assertThat(entry.at("/request/queryString/1/value").asText()).isEqualTo("J Doe");
    assertThat(entry.at("/request/postData/mimeType").asText()).isEqualTo("application/json");
    assertThat(entry.at("/response/content/text").asText()).isEqualTo("{\"ok\":true}");
    assertThat(entry.at("/timings/wait").asInt()).isEqualTo(12);
    assertThat(entry.at("/comment").asText()).isEqualTo("Matched Stub a");
  }
}
