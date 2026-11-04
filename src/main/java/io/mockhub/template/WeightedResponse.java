package io.mockhub.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WeightedResponse(double weight, int status, String body, Map<String, String> headers) {
  public static final int MAX = 20;

  public static List<WeightedResponse> from(JsonNode parameters) {
    JsonNode list = parameters.path("responses");
    if (!list.isArray()) return List.of();
    List<WeightedResponse> out = new ArrayList<>();
    for (JsonNode item : list) {
      double weight = item.path("weight").asDouble(1);
      if (weight <= 0) continue;
      Map<String, String> headers = new LinkedHashMap<>();
      item.path("headers").properties().forEach(e -> headers.put(e.getKey(), e.getValue().asText()));
      String body = item.has("jsonBody") ? item.get("jsonBody").toString() : item.hasNonNull("body") ? item.get("body").asText() : null;
      out.add(new WeightedResponse(weight, item.path("status").asInt(200), body, headers));
    }
    return out;
  }

  public static int pick(List<WeightedResponse> choices, double random) {
    double total = choices.stream().mapToDouble(WeightedResponse::weight).sum();
    double point = random * total;
    for (int i = 0; i < choices.size(); i++) {
      point -= choices.get(i).weight();
      if (point < 0) return i;
    }
    return choices.size() - 1;
  }
}
