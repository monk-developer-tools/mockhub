package io.mockhub.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.mockhub.api.Jsons;
import java.util.LinkedHashMap;
import java.util.Map;

final class JsonValue extends LinkedHashMap<String, Object> {

  JsonValue(Map<String, Object> fields) {
    super(fields);
  }

  static String stringify(Object value, boolean pretty) {
    try {
      return pretty
          ? Jsons.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value)
          : Jsons.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Can't turn this value into JSON: " + e.getOriginalMessage());
    }
  }

  @Override
  public String toString() {
    return stringify(this, false);
  }
}
