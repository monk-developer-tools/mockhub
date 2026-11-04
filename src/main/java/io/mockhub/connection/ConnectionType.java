package io.mockhub.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.mockhub.api.ApiException;
import java.util.Locale;

public enum ConnectionType {
  OAUTH2,
  HEADER;

  @JsonValue
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static ConnectionType parse(String value) {
    if (value == null || value.isBlank()) return OAUTH2;
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("type must be oauth2 or header");
    }
  }
}
