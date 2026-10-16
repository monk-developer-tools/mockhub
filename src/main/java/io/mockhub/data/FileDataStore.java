package io.mockhub.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class FileDataStore implements DataStore {
  private static final TypeReference<LinkedHashMap<String, LinkedHashMap<String, ObjectNode>>> TYPE = new TypeReference<>() {};

  private final Path dir;
  private final Map<String, LinkedHashMap<String, LinkedHashMap<String, ObjectNode>>> cache = new HashMap<>();

  public FileDataStore(Path rootDir) {
    this.dir = JsonFile.inDataDir(rootDir, "data");
  }

  @Override
  public synchronized Map<String, Integer> resources(String projectId) {
    Map<String, Integer> out = new TreeMap<>();
    project(projectId).forEach((name, records) -> out.put(name, records.size()));
    return out;
  }

  @Override
  public synchronized List<ObjectNode> list(String projectId, String resource) {
    List<ObjectNode> out = new ArrayList<>();
    project(projectId).getOrDefault(resource, new LinkedHashMap<>()).values().forEach(r -> out.add(r.deepCopy()));
    return out;
  }

  @Override
  public synchronized Optional<ObjectNode> get(String projectId, String resource, String id) {
    return Optional.ofNullable(project(projectId).getOrDefault(resource, new LinkedHashMap<>()).get(id)).map(ObjectNode::deepCopy);
  }

  @Override
  public synchronized void put(String projectId, String resource, String id, ObjectNode record) {
    project(projectId).computeIfAbsent(resource, r -> new LinkedHashMap<>()).put(id, record.deepCopy());
    save(projectId);
  }

  @Override
  public synchronized boolean delete(String projectId, String resource, String id) {
    LinkedHashMap<String, ObjectNode> records = project(projectId).get(resource);
    if (records == null || records.remove(id) == null) return false;
    save(projectId);
    return true;
  }

  @Override
  public synchronized void clear(String projectId, String resource) {
    if (project(projectId).remove(resource) != null) save(projectId);
  }

  @Override
  public synchronized void clearProject(String projectId) {
    cache.remove(projectId);
    try {
      Files.deleteIfExists(file(projectId));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized Map<String, Map<String, ObjectNode>> snapshot(String projectId) {
    Map<String, Map<String, ObjectNode>> out = new LinkedHashMap<>();
    project(projectId).forEach((name, records) -> {
      Map<String, ObjectNode> copy = new LinkedHashMap<>();
      records.forEach((id, r) -> copy.put(id, r.deepCopy()));
      out.put(name, copy);
    });
    return out;
  }

  @Override
  public synchronized void restore(String projectId, Map<String, Map<String, ObjectNode>> data) {
    LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> copy = new LinkedHashMap<>();
    data.forEach((name, records) -> copy.put(name, new LinkedHashMap<>(records)));
    cache.put(projectId, copy);
    save(projectId);
  }

  private LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> project(String projectId) {
    return cache.computeIfAbsent(projectId, id -> new JsonFile<>(file(id), TYPE).read(LinkedHashMap::new));
  }

  private void save(String projectId) {
    new JsonFile<>(file(projectId), TYPE).write(project(projectId));
  }

  private Path file(String projectId) {
    if (!projectId.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Bad project id");
    return dir.resolve(projectId + ".json");
  }
}
