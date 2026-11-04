package io.mockhub.dataset;

import io.mockhub.api.ApiException;
import java.util.Locale;

public enum DatasetFormat {
  CSV,
  JSON,
  NDJSON;

  public static DatasetFormat parse(String value) {
    if (value == null || value.isBlank()) throw ApiException.badRequest("Add ?format=csv, json or ndjson");
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Format must be csv, json or ndjson, not " + value);
    }
  }
}
