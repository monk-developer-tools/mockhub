package io.mockhub.history;

import io.mockhub.playground.Header;
import java.util.List;

final class HistoryFixtures {
  private HistoryFixtures() {}

  static HistoryEntry entry(String id, long at, String method, String url, int status, boolean matched) {
    return new HistoryEntry(id, at, "p1", method, url, "http://localhost:8080" + url, "127.0.0.1",
        List.of(new Header("X-Trace", "trace-" + id), new Header("Content-Type", "application/json")), "{\"id\":\"" + id + "\"}", false,
        status, List.of(new Header("Content-Type", "application/json")), "{\"ok\":true}", false, matched,
        matched ? "s-" + id : null, matched ? "Stub " + id : null, 12);
  }
}
