package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public record LoadLatency(int afterRps, int msPerRequest) {
  static final int MAX_EXTRA_MS = 30_000;

  static LoadLatency parse(JsonNode node) {
    if (Fields.absent(node)) return null;
    return new LoadLatency(
        Fields.integer(node, "afterRps", 10, 1, 100_000, "The load threshold"),
        Fields.integer(node, "msPerRequest", 10, 1, 10_000, "The extra delay per request"));
  }

  public int extraMs(int requestsPerSecond) {
    long extra = (long) Math.max(0, requestsPerSecond - afterRps) * msPerRequest;
    return (int) Math.min(MAX_EXTRA_MS, extra);
  }
}
