package io.mockhub.export;

import io.mockhub.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExporterRegistry {
  private final Map<String, CollectionExporter> byFormat = new LinkedHashMap<>();

  public ExporterRegistry(List<CollectionExporter> exporters) {
    exporters.forEach(e -> byFormat.put(e.format(), e));
  }

  public CollectionExporter get(String format) {
    CollectionExporter e = byFormat.get(format);
    if (e == null) throw ApiException.badRequest("Unknown export format '" + format + "'. Supported: " + formats());
    return e;
  }

  public Set<String> formats() {
    return byFormat.keySet();
  }
}
