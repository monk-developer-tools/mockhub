package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCalloutLogTest {

  private static CalloutEntry entry(String id, String projectId, String body) {
    return new CalloutEntry(id, "2026-11-03T10:00:00Z", projectId, "s1", "Stub", "async", null,
        new OutboundRequest("POST", "https://partner.test", List.of(), "{}"),
        new CalloutEntry.Result(200, List.of(), body, false), null, 5);
  }

  @Test
  void keepsTheNewestEntriesPerProject() {
    // Given
    InMemoryCalloutLog log = new InMemoryCalloutLog(3);

    // When
    log.add(entry("1", "a", ""));
    log.add(entry("2", "b", ""));
    log.add(entry("3", "a", ""));
    log.add(entry("4", "a", ""));

    // Then
    assertThat(log.list("a", 10)).extracting(CalloutEntry::id).containsExactly("4", "3");
    assertThat(log.list("a", 1)).extracting(CalloutEntry::id).containsExactly("4");
    assertThat(log.list("b", 10)).extracting(CalloutEntry::id).containsExactly("2");
  }

  @Test
  void clearsOneProjectAndCapsLargeBodies() {
    // Given
    InMemoryCalloutLog log = new InMemoryCalloutLog(10);
    log.add(entry("1", "a", "x".repeat(InMemoryCalloutLog.MAX_STORED_BODY + 10)));
    log.add(entry("2", "b", ""));

    // When
    CalloutEntry stored = log.list("a", 10).get(0);
    log.clear("a");

    // Then
    assertThat(stored.response().body()).hasSize(InMemoryCalloutLog.MAX_STORED_BODY);
    assertThat(stored.response().truncated()).isTrue();
    assertThat(log.list("a", 10)).isEmpty();
    assertThat(log.list("b", 10)).hasSize(1);
  }
}
