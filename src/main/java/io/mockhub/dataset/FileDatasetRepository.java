package io.mockhub.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class FileDatasetRepository implements DatasetRepository {
  private static final TypeReference<StoredDataset> TYPE = new TypeReference<>() {};
  private static final String SAFE = "[A-Za-z0-9_-]+";

  private final Path dir;
  private final Map<String, TreeMap<String, StoredDataset>> cache = new HashMap<>();

  public FileDatasetRepository(Path rootDir) {
    this.dir = JsonFile.inDataDir(rootDir, "datasets");
  }

  @Override
  public synchronized List<StoredDataset> findAll(String orgId) {
    return List.copyOf(org(orgId).values());
  }

  @Override
  public synchronized Optional<StoredDataset> find(String orgId, String key) {
    return Optional.ofNullable(org(orgId).get(key));
  }

  @Override
  public synchronized void save(StoredDataset stored) {
    Dataset d = stored.dataset();
    new JsonFile<>(file(d.orgId(), d.key()), TYPE).write(stored);
    org(d.orgId()).put(d.key(), stored);
  }

  @Override
  public synchronized boolean delete(String orgId, String key) {
    if (org(orgId).remove(key) == null) return false;
    try {
      Files.deleteIfExists(file(orgId, key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }

  @Override
  public synchronized void deleteOrg(String orgId) {
    cache.remove(orgId);
    Path orgDir = orgDir(orgId);
    if (!Files.isDirectory(orgDir)) return;
    try (Stream<Path> files = Files.walk(orgDir)) {
      for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private TreeMap<String, StoredDataset> org(String orgId) {
    return cache.computeIfAbsent(orgId, this::load);
  }

  private TreeMap<String, StoredDataset> load(String orgId) {
    TreeMap<String, StoredDataset> out = new TreeMap<>();
    Path orgDir = orgDir(orgId);
    if (!Files.isDirectory(orgDir)) return out;
    try (Stream<Path> files = Files.list(orgDir)) {
      files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
        StoredDataset stored = new JsonFile<>(p, TYPE).read(() -> null);
        if (stored != null) out.put(stored.dataset().key(), stored);
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  private Path orgDir(String orgId) {
    if (!orgId.matches(SAFE)) throw new IllegalArgumentException("Bad organisation id");
    return dir.resolve(orgId);
  }

  private Path file(String orgId, String key) {
    if (!key.matches(SAFE)) throw new IllegalArgumentException("Bad dataset key");
    return orgDir(orgId).resolve(key + ".json");
  }
}
