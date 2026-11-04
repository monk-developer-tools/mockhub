package io.mockhub.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DatasetIndex {
  private final List<ObjectNode> records;
  private final Map<String, Map<String, List<Integer>>> byField = new HashMap<>();

  public DatasetIndex(List<ObjectNode> records, Collection<String> fields) {
    this.records = records;
    for (String field : fields) {
      Map<String, List<Integer>> index = new HashMap<>();
      for (int i = 0; i < records.size(); i++) {
        for (String key : keys(at(records.get(i), field))) index.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
      }
      byField.put(field, index);
    }
  }

  public int size() {
    return records.size();
  }

  public List<ObjectNode> find(Map<String, String> filters, int offset, int limit) {
    List<ObjectNode> out = new ArrayList<>();
    int skipped = 0;
    for (int i : candidates(filters)) {
      ObjectNode record = records.get(i);
      if (!matchesAll(record, filters)) continue;
      if (skipped++ < offset) continue;
      out.add(record);
      if (out.size() >= limit) break;
    }
    return out;
  }

  public int count(Map<String, String> filters) {
    int n = 0;
    for (int i : candidates(filters)) if (matchesAll(records.get(i), filters)) n++;
    return n;
  }

  private Iterable<Integer> candidates(Map<String, String> filters) {
    List<Integer> best = null;
    for (Map.Entry<String, String> f : filters.entrySet()) {
      Map<String, List<Integer>> index = byField.get(f.getKey());
      if (index == null) continue;
      Set<Integer> hits = new TreeSet<>();
      for (String key : wanted(f.getValue())) hits.addAll(index.getOrDefault(key, List.of()));
      if (best == null || hits.size() < best.size()) best = new ArrayList<>(hits);
    }
    if (best != null) return best;
    List<Integer> all = new ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) all.add(i);
    return all;
  }

  public static boolean matchesAll(ObjectNode record, Map<String, String> filters) {
    for (Map.Entry<String, String> f : filters.entrySet()) {
      Set<String> have = keys(at(record, f.getKey()));
      if (wanted(f.getValue()).stream().noneMatch(have::contains)) return false;
    }
    return true;
  }

  static JsonNode at(ObjectNode record, String field) {
    JsonNode node = record.get(field);
    if (node != null || !field.contains(".")) return node;
    JsonNode current = record;
    for (String part : field.split("\\.")) {
      current = current == null ? null : current.get(part);
    }
    return current;
  }

  static Set<String> keys(JsonNode value) {
    Set<String> out = new LinkedHashSet<>();
    if (value == null || value.isNull() || value.isMissingNode()) return out;
    if (value.isArray()) {
      value.forEach(item -> out.addAll(keys(item)));
      return out;
    }
    if (value.isNumber()) out.add(normal(value.decimalValue()));
    else if (value.isBoolean()) out.add(value.asText());
    else if (value.isValueNode()) out.add(value.asText());
    return out;
  }

  static List<String> wanted(String value) {
    List<String> out = new ArrayList<>();
    out.add(value);
    String trimmed = value.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.equals("true") || lower.equals("false")) out.add(lower);
    try {
      out.add(normal(new BigDecimal(trimmed)));
    } catch (NumberFormatException ignored) {
      return out;
    }
    return out;
  }

  private static String normal(BigDecimal value) {
    return "#" + value.stripTrailingZeros().toPlainString();
  }
}
