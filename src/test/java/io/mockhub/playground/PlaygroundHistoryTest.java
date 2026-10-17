package io.mockhub.playground;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaygroundHistoryTest {

  @TempDir
  Path dir;

  private static PlaygroundEntry entry(String id, String body) {
    PlaygroundRequest request = new PlaygroundRequest("GET", "https://a.example/x",
        List.of(new Header("Authorization", "Bearer secret"), new Header("Accept", "*/*")), "");
    PlaygroundResponse response = new PlaygroundResponse(200, List.of(new Header("Set-Cookie", "sid=1")), body, body.length(), false, 12);
    return new PlaygroundEntry(id, "2026-10-17T10:00:00Z", request, response, null, false);
  }

  @Test
  void keepsTheNewestEntriesFirstAndHidesSecrets() {
    // Given
    PlaygroundHistory history = new PlaygroundHistory(dir);

    // When
    history.add("u1", entry("a", "{}"));
    history.add("u1", entry("b", "{}"));
    List<PlaygroundEntry> entries = history.list("u1");

    // Then
    assertThat(entries).extracting(PlaygroundEntry::id).containsExactly("b", "a");
    assertThat(entries.get(0).request().headers()).containsExactly(new Header("Authorization", PlaygroundHistory.REDACTED), new Header("Accept", "*/*"));
    assertThat(entries.get(0).response().headers()).containsExactly(new Header("Set-Cookie", PlaygroundHistory.REDACTED));
  }

  @Test
  void capsTheNumberOfEntriesAndLargeBodies() {
    // Given
    PlaygroundHistory history = new PlaygroundHistory(dir);
    String big = "x".repeat(PlaygroundHistory.MAX_STORED_BODY + 10);

    // When
    for (int i = 0; i < PlaygroundHistory.MAX_ENTRIES + 5; i++) history.add("u1", entry("e" + i, i == PlaygroundHistory.MAX_ENTRIES + 4 ? big : "{}"));
    List<PlaygroundEntry> entries = history.list("u1");

    // Then
    assertThat(entries).hasSize(PlaygroundHistory.MAX_ENTRIES);
    assertThat(entries.get(0).response().body()).hasSize(PlaygroundHistory.MAX_STORED_BODY);
    assertThat(entries.get(0).response().truncated()).isTrue();
  }

  @Test
  void deletesOneEntryOrEverythingPerUser() {
    // Given
    PlaygroundHistory history = new PlaygroundHistory(dir);
    history.add("u1", entry("a", "{}"));
    history.add("u1", entry("b", "{}"));
    history.add("u2", entry("c", "{}"));

    // When
    boolean deleted = history.delete("u1", "a");
    boolean missing = history.delete("u1", "zzz");
    history.clear("u1");

    // Then
    assertThat(deleted).isTrue();
    assertThat(missing).isFalse();
    assertThat(history.list("u1")).isEmpty();
    assertThat(history.list("u2")).hasSize(1);
  }
}
