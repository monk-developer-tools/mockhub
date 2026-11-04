package io.mockhub.dataset;

import io.mockhub.api.ApiException;
import java.util.Locale;

public enum UploadMode {
  REPLACE,
  UPSERT,
  ADD;

  public static UploadMode parse(String value) {
    if (value == null || value.isBlank()) return REPLACE;
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Mode must be replace, upsert (update and add) or add (add only), not " + value);
    }
  }
}
