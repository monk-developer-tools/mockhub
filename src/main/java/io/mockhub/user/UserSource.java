package io.mockhub.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum UserSource {
  ENV,
  LOCAL,
  AZURE;

  @JsonValue
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static UserSource parse(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
