package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record RuntimeSettings(DefaultResponse defaultResponse, RateLimit rateLimit, Latency latency, CorsRules cors, List<String> ipAllowList) {
  public static final RuntimeSettings NONE = new RuntimeSettings(null, null, null, null, List.of());

  public RuntimeSettings {
    ipAllowList = ipAllowList == null ? List.of() : List.copyOf(ipAllowList);
  }

  public static RuntimeSettings of(RuntimeSettings settings) {
    return settings == null ? NONE : settings;
  }

  public static RuntimeSettings parse(JsonNode node) {
    if (node == null || !node.isObject()) return NONE;
    return new RuntimeSettings(
        DefaultResponse.parse(node.get("defaultResponse")),
        RateLimit.parse(node.get("rateLimit"), "rateLimit"),
        Latency.parse(node.get("latency")),
        CorsRules.parse(node.get("cors")),
        IpAllowList.parse(node.get("ipAllowList")).entries());
  }

  public IpAllowList ipRules() {
    return new IpAllowList(ipAllowList);
  }
}
