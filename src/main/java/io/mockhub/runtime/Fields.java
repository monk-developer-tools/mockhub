package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;

final class Fields {
  private Fields() {}

  static boolean absent(JsonNode node) {
    return node == null || node.isNull() || node.isMissingNode();
  }

  static int integer(JsonNode node, String field, int fallback, int min, int max, String label) {
    JsonNode value = node.get(field);
    if (absent(value)) return fallback;
    if (!value.canConvertToInt() && !value.isTextual()) throw ApiException.badRequest(label + " must be a whole number");
    int number;
    try {
      number = value.isTextual() ? Integer.parseInt(value.asText().trim()) : value.asInt();
    } catch (NumberFormatException e) {
      throw ApiException.badRequest(label + " must be a whole number");
    }
    if (number < min || number > max) throw ApiException.badRequest(label + " must be between " + min + " and " + max);
    return number;
  }

  static double decimal(JsonNode node, String field, double fallback, double min, double max, String label) {
    JsonNode value = node.get(field);
    if (absent(value)) return fallback;
    double number = value.asDouble(Double.NaN);
    if (Double.isNaN(number) || number < min || number > max) throw ApiException.badRequest(label + " must be between " + min + " and " + max);
    return number;
  }
}
