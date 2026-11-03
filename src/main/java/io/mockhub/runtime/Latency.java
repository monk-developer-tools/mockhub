package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.DelayDistribution;
import com.github.tomakehurst.wiremock.http.LogNormal;
import com.github.tomakehurst.wiremock.http.UniformDistribution;
import io.mockhub.api.ApiException;
import java.util.Locale;

public record Latency(String mode, int fixedMs, int minMs, int maxMs, int medianMs, double sigma, LoadLatency load) {
  static final int MAX_MS = 60_000;

  static Latency parse(JsonNode node) {
    if (Fields.absent(node)) return null;
    String mode = node.path("mode").asText("none").trim().toLowerCase(Locale.ROOT);
    LoadLatency load = LoadLatency.parse(node.get("load"));
    Latency latency = switch (mode) {
      case "none" -> new Latency("none", 0, 0, 0, 0, 0, load);
      case "fixed" -> new Latency("fixed", Fields.integer(node, "fixedMs", 0, 0, MAX_MS, "The delay"), 0, 0, 0, 0, load);
      case "uniform" -> uniform(node, load);
      case "lognormal" -> new Latency("lognormal", 0, 0, 0,
          Fields.integer(node, "medianMs", 0, 1, MAX_MS, "The median delay"), Fields.decimal(node, "sigma", 0.25, 0.01, 2, "The spread"), load);
      default -> throw ApiException.badRequest("The delay must be none, fixed, uniform or lognormal");
    };
    return latency.isEmpty() ? null : latency;
  }

  private static Latency uniform(JsonNode node, LoadLatency load) {
    int min = Fields.integer(node, "minMs", 0, 0, MAX_MS, "The shortest delay");
    int max = Fields.integer(node, "maxMs", 0, 0, MAX_MS, "The longest delay");
    if (min > max) throw ApiException.badRequest("The shortest delay can't be longer than the longest");
    return new Latency("uniform", 0, min, max, 0, 0, load);
  }

  boolean isEmpty() {
    return "none".equals(mode) && load == null;
  }

  public DelayDistribution distribution() {
    return switch (mode) {
      case "uniform" -> new UniformDistribution(minMs, maxMs);
      case "lognormal" -> new LogNormal(medianMs, sigma, (double) MAX_MS);
      default -> null;
    };
  }

  public int fixed() {
    return "fixed".equals(mode) ? fixedMs : 0;
  }
}
