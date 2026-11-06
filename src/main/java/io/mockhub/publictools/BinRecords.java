package io.mockhub.publictools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.data.DataStore;
import io.mockhub.data.RecordPolicy;
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

final class BinRecords implements DataStore {
  static final int MAX_RECORDS = 500;
  static final int MAX_RECORD_BYTES = 8 * 1024;
  static final int MAX_RESOURCES = 20;
  static final String FILE = "records.json";
  static final RecordPolicy POLICY = (binId, records, bytes) -> {
    if (bytes > MAX_RECORD_BYTES) return Optional.of("A record can be up to " + MAX_RECORD_BYTES / 1024 + " KB");
    if (records > MAX_RECORDS) return Optional.of("A bin holds up to " + MAX_RECORDS + " records; delete some first");
    return Optional.empty();
  };
  private static final TypeReference<LinkedHashMap<String, LinkedHashMap<String, ObjectNode>>> TYPE = new TypeReference<>() {};

  private final Optional<Path> root;
  private final Map<String, LinkedHashMap<String, LinkedHashMap<String, ObjectNode>>> cache = new HashMap<>();

  BinRecords(Optional<Path> root) {
    this.root = root;
  }

  synchronized void forget(String binId) {
    cache.remove(binId);
  }

  @Override
  public synchronized Map<String, Integer> resources(String binId) {
    Map<String, Integer> out = new TreeMap<>();
    bin(binId).forEach((name, records) -> out.put(name, records.size()));
    return out;
  }

  @Override
  public synchronized List<ObjectNode> list(String binId, String resource) {
    List<ObjectNode> out = new ArrayList<>();
    bin(binId).getOrDefault(resource, new LinkedHashMap<>()).values().forEach(r -> out.add(r.deepCopy()));
    return out;
  }

  @Override
  public synchronized Optional<ObjectNode> get(String binId, String resource, String id) {
    return Optional.ofNullable(bin(binId).getOrDefault(resource, new LinkedHashMap<>()).get(id)).map(ObjectNode::deepCopy);
  }

  @Override
  public synchronized void put(String binId, String resource, String id, ObjectNode record) {
    LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> bin = bin(binId);
    if (!bin.containsKey(resource) && bin.size() >= MAX_RESOURCES) throw new IllegalStateException("A bin stores up to " + MAX_RESOURCES + " collections");
    bin.computeIfAbsent(resource, r -> new LinkedHashMap<>()).put(id, record.deepCopy());
    save(binId);
  }

  @Override
  public synchronized boolean delete(String binId, String resource, String id) {
    LinkedHashMap<String, ObjectNode> records = bin(binId).get(resource);
    if (records == null || records.remove(id) == null) return false;
    if (records.isEmpty()) bin(binId).remove(resource);
    save(binId);
    return true;
  }

  @Override
  public synchronized int keepNewest(String binId, String resource, int max) {
    LinkedHashMap<String, ObjectNode> records = bin(binId).get(resource);
    if (records == null || records.size() <= max) return 0;
    int removed = records.size() - max;
    records.keySet().stream().limit(removed).toList().forEach(records::remove);
    save(binId);
    return removed;
  }

  @Override
  public synchronized void clear(String binId, String resource) {
    if (bin(binId).remove(resource) != null) save(binId);
  }

  @Override
  public synchronized void clearProject(String binId) {
    bin(binId).clear();
    save(binId);
  }

  @Override
  public synchronized Map<String, Map<String, ObjectNode>> snapshot(String binId) {
    Map<String, Map<String, ObjectNode>> out = new LinkedHashMap<>();
    bin(binId).forEach((name, records) -> {
      Map<String, ObjectNode> copy = new LinkedHashMap<>();
      records.forEach((id, r) -> copy.put(id, r.deepCopy()));
      out.put(name, copy);
    });
    return out;
  }

  @Override
  public synchronized void restore(String binId, Map<String, Map<String, ObjectNode>> data) {
    LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> copy = new LinkedHashMap<>();
    data.forEach((name, records) -> copy.put(name, new LinkedHashMap<>(records)));
    cache.put(binId, copy);
    save(binId);
  }

  private LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> bin(String binId) {
    return cache.computeIfAbsent(binId, id -> file(id).map(f -> new JsonFile<>(f, TYPE).read(LinkedHashMap::new)).orElseGet(LinkedHashMap::new));
  }

  private void save(String binId) {
    Optional<Path> file = file(binId);
    if (file.isEmpty()) return;
    LinkedHashMap<String, LinkedHashMap<String, ObjectNode>> data = bin(binId);
    try {
      if (data.isEmpty()) {
        Files.deleteIfExists(file.get());
        return;
      }
      Files.createDirectories(file.get().getParent());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    new JsonFile<>(file.get(), TYPE).write(data);
  }

  private Optional<Path> file(String binId) {
    if (!binId.matches("[a-z0-9]{12}")) throw new IllegalArgumentException("Bad bin id");
    return root.map(r -> r.resolve(binId).resolve(FILE));
  }
}
