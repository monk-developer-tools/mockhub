package io.mockhub.importer;

import io.mockhub.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImporterRegistry {
  private final Map<String, StubImporter> byFormat = new LinkedHashMap<>();

  public ImporterRegistry(List<StubImporter> importers) {
    importers.forEach(i -> byFormat.put(i.format(), i));
  }

  public StubImporter get(String format) {
    StubImporter i = byFormat.get(format);
    if (i == null) throw ApiException.badRequest("Unknown import format '" + format + "'. Supported: " + byFormat.keySet());
    return i;
  }
}
