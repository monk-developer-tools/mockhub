package io.mockhub.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FileSnapshotRepository implements SnapshotRepository {
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private final Path root;

  public FileSnapshotRepository(Path dataDir) {
    this.root = JsonFile.inDataDir(dataDir, "snapshots");
  }

  @Override
  public synchronized List<Snapshot.Summary> list(String projectId) {
    return index(projectId).read(ArrayList::new).stream()
        .sorted(Comparator.comparing(Snapshot.Summary::createdAt).reversed())
        .toList();
  }

  @Override
  public synchronized Optional<Snapshot> find(String projectId, String id) {
    if (!ID.matcher(id).matches()) return Optional.empty();
    Path file = dir(projectId).resolve(id + ".json");
    if (!Files.isRegularFile(file)) return Optional.empty();
    return Optional.of(new JsonFile<>(file, new TypeReference<Snapshot>() {}).read(() -> null));
  }

  @Override
  public synchronized void save(Snapshot snapshot) {
    if (!ID.matcher(snapshot.id()).matches()) throw new IllegalArgumentException("Bad snapshot id " + snapshot.id());
    new JsonFile<>(dir(snapshot.projectId()).resolve(snapshot.id() + ".json"), new TypeReference<Snapshot>() {}).write(snapshot);
    List<Snapshot.Summary> summaries = new ArrayList<>(index(snapshot.projectId()).read(ArrayList::new));
    summaries.removeIf(s -> s.id().equals(snapshot.id()));
    summaries.add(snapshot.summary());
    index(snapshot.projectId()).write(summaries);
  }

  @Override
  public synchronized void delete(String projectId, String id) {
    if (!ID.matcher(id).matches()) return;
    try {
      Files.deleteIfExists(dir(projectId).resolve(id + ".json"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<Snapshot.Summary> summaries = new ArrayList<>(index(projectId).read(ArrayList::new));
    summaries.removeIf(s -> s.id().equals(id));
    index(projectId).write(summaries);
  }

  @Override
  public synchronized void deleteProject(String projectId) {
    Path dir = dir(projectId);
    if (!Files.isDirectory(dir)) return;
    try (Stream<Path> files = Files.walk(dir)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JsonFile<List<Snapshot.Summary>> index(String projectId) {
    return new JsonFile<>(dir(projectId).resolve("index.json"), new TypeReference<List<Snapshot.Summary>>() {});
  }

  private Path dir(String projectId) {
    if (!ID.matcher(projectId).matches()) throw new IllegalArgumentException("Bad project id " + projectId);
    return root.resolve(projectId);
  }
}
