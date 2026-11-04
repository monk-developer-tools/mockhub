package io.mockhub.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import io.mockhub.api.ApiException;
import io.mockhub.dataset.DatasetService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class DatasetHelpers implements TemplateHelperProviderExtension {
  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_LIMIT = 1000;
  private static final Set<String> OPTIONS = Set.of("limit");

  private final DatasetService datasets;
  private final Supplier<Optional<String>> currentOrg;

  public DatasetHelpers(DatasetService datasets, Supplier<Optional<String>> currentOrg) {
    this.datasets = datasets;
    this.currentOrg = currentOrg;
  }

  @Override
  public String getName() {
    return "mockhub-datasets";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "dataset-one", (Helper<Object>) (key, options) -> {
          List<Object> found = lookup(key, options, 1);
          return found.isEmpty() ? null : found.get(0);
        },
        "dataset", (Helper<Object>) (key, options) -> new JsonList(lookup(key, options, limit(options))));
  }

  private List<Object> lookup(Object key, Options options, int limit) {
    String name = key == null ? "" : key.toString();
    if (name.isBlank()) throw new IllegalArgumentException("Name the dataset, e.g. {{dataset-one 'customers' id=request.path.id}}");
    String orgId = currentOrg.get().orElseThrow(() -> new IllegalArgumentException("Datasets work in the stubs of a project"));
    Map<String, String> filters = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : options.hash.entrySet()) {
      if (OPTIONS.contains(e.getKey())) continue;
      if (e.getValue() == null) return List.of();
      filters.put(e.getKey(), e.getValue().toString());
    }
    try {
      List<Object> out = new ArrayList<>();
      datasets.find(orgId, name, filters, limit).forEach(record -> out.add(toTemplate(record)));
      return out;
    } catch (ApiException e) {
      if (e.status == 404) throw new IllegalArgumentException("No dataset called " + name + " in this organisation");
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  private static int limit(Options options) {
    Object raw = options.hash("limit");
    if (raw == null) return DEFAULT_LIMIT;
    try {
      int limit = Integer.parseInt(raw.toString().trim());
      return Math.max(1, Math.min(MAX_LIMIT, limit));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("limit must be a number, e.g. limit=10");
    }
  }

  static Object toTemplate(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    if (node.isObject()) {
      Map<String, Object> fields = new LinkedHashMap<>();
      node.fields().forEachRemaining(e -> fields.put(e.getKey(), toTemplate(e.getValue())));
      return new JsonValue(fields);
    }
    if (node.isArray()) {
      List<Object> items = new ArrayList<>();
      node.forEach(item -> items.add(toTemplate(item)));
      return new JsonList(items);
    }
    if (node.isBoolean()) return node.booleanValue();
    if (node.isIntegralNumber()) return node.longValue();
    if (node.isNumber()) return node.doubleValue();
    return node.asText();
  }
}
