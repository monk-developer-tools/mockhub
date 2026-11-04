package io.mockhub.dataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

public enum ColumnType {
  STRING,
  NUMBER,
  BOOLEAN,
  JSON;

  @JsonValue
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static ColumnType of(String value) {
    return named(value).orElse(STRING);
  }

  public static Optional<ColumnType> named(String value) {
    return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
      case "string", "text" -> Optional.of(STRING);
      case "number", "int", "integer", "decimal" -> Optional.of(NUMBER);
      case "boolean", "bool" -> Optional.of(BOOLEAN);
      case "json", "object", "array" -> Optional.of(JSON);
      default -> Optional.empty();
    };
  }
}
