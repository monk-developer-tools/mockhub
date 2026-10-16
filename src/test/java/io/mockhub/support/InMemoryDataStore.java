package io.mockhub.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.data.DataStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class InMemoryDataStore implements DataStore {
  private final Map<String, Map<String, Map<String, ObjectNode>>> data = new HashMap<>();

  private Map<String, Map<String, ObjectNode>> project(String projectId) {
    return data.computeIfAbsent(projectId, p -> new LinkedHashMap<>());
  }

  @Override
  public Map<String, Integer> resources(String projectId) {
    Map<String, Integer> out = new TreeMap<>();
    project(projectId).forEach((k, v) -> out.put(k, v.size()));
    return out;
  }

  @Override
  public List<ObjectNode> list(String projectId, String resource) {
    return new ArrayList<>(project(projectId).getOrDefault(resource, Map.of()).values());
  }

  @Override
  public Optional<ObjectNode> get(String projectId, String resource, String id) {
    return Optional.ofNullable(project(projectId).getOrDefault(resource, Map.of()).get(id)).map(ObjectNode::deepCopy);
  }

  @Override
  public void put(String projectId, String resource, String id, ObjectNode record) {
    project(projectId).computeIfAbsent(resource, r -> new LinkedHashMap<>()).put(id, record.deepCopy());
  }

  @Override
  public boolean delete(String projectId, String resource, String id) {
    Map<String, ObjectNode> records = project(projectId).get(resource);
    return records != null && records.remove(id) != null;
  }

  @Override
  public void clear(String projectId, String resource) {
    project(projectId).remove(resource);
  }

  @Override
  public void clearProject(String projectId) {
    data.remove(projectId);
  }

  @Override
  public Map<String, Map<String, ObjectNode>> snapshot(String projectId) {
    Map<String, Map<String, ObjectNode>> out = new LinkedHashMap<>();
    project(projectId).forEach((k, v) -> out.put(k, new LinkedHashMap<>(v)));
    return out;
  }

  @Override
  public void restore(String projectId, Map<String, Map<String, ObjectNode>> records) {
    Map<String, Map<String, ObjectNode>> copy = new LinkedHashMap<>();
    records.forEach((k, v) -> copy.put(k, new LinkedHashMap<>(v)));
    data.put(projectId, copy);
  }
}
