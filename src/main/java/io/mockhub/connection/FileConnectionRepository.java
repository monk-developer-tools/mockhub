package io.mockhub.connection;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class FileConnectionRepository implements ConnectionRepository {
  private static final TypeReference<List<Connection>> TYPE = new TypeReference<>() {};

  private final Path dir;
  private final Map<String, TreeMap<String, Connection>> cache = new HashMap<>();

  public FileConnectionRepository(Path rootDir) {
    this.dir = JsonFile.inDataDir(rootDir, "connections");
  }

  @Override
  public synchronized List<Connection> findAll(String orgId) {
    return List.copyOf(org(orgId).values());
  }

  @Override
  public synchronized Optional<Connection> find(String orgId, String key) {
    return Optional.ofNullable(org(orgId).get(key));
  }

  @Override
  public synchronized void save(Connection connection) {
    org(connection.orgId()).put(connection.key(), connection);
    write(connection.orgId());
  }

  @Override
  public synchronized boolean delete(String orgId, String key) {
    if (org(orgId).remove(key) == null) return false;
    write(orgId);
    return true;
  }

  @Override
  public synchronized void deleteOrg(String orgId) {
    cache.remove(orgId);
    try {
      Files.deleteIfExists(file(orgId));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private TreeMap<String, Connection> org(String orgId) {
    return cache.computeIfAbsent(orgId, id -> {
      TreeMap<String, Connection> out = new TreeMap<>();
      new JsonFile<>(file(id), TYPE).read(List::of).forEach(c -> out.put(c.key(), c));
      return out;
    });
  }

  private void write(String orgId) {
    new JsonFile<>(file(orgId), TYPE).write(new ArrayList<>(org(orgId).values()));
  }

  private Path file(String orgId) {
    if (!orgId.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Bad organisation id");
    return dir.resolve(orgId + ".json");
  }
}
