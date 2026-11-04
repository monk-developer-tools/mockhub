package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;

final class PublicJson {
  private PublicJson() {}

  static JsonNode parse(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return Jsons.MAPPER.readTree(body);
    } catch (Exception e) {
      return null;
    }
  }
}
