package io.mockhub.history;

import java.util.List;
import java.util.Map;

public record HistoryPage(List<HistoryEntry> items, int total, boolean truncated, Facets facets) {

  public record StubCount(String id, String name, int count) {}

  public record Facets(Map<String, Integer> methods, Map<String, Integer> statuses, int matched, int unmatched, List<StubCount> stubs) {}
}
