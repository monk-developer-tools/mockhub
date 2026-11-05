package io.mockhub.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ContractStore implements ProjectListener {
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private final Path root;

  public ContractStore(Path dataDir) {
    this.root = JsonFile.inDataDir(dataDir, "contracts");
  }

  public synchronized Optional<Contract> contract(String projectId) {
    return Optional.ofNullable(contractFile(projectId).read(() -> null));
  }

  public synchronized void save(String projectId, Contract contract) {
    contractFile(projectId).write(contract);
  }

  public synchronized DriftState drift(String projectId) {
    return driftFile(projectId).read(DriftState::empty);
  }

  public synchronized void saveDrift(String projectId, DriftState state) {
    driftFile(projectId).write(state);
  }

  public synchronized void delete(String projectId) {
    Path dir = dir(projectId);
    if (!Files.exists(dir)) return;
    try (Stream<Path> files = Files.walk(dir)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public synchronized java.util.List<String> projectsWithContracts() {
    if (!Files.isDirectory(root)) return java.util.List.of();
    try (Stream<Path> dirs = Files.list(root)) {
      return dirs.filter(d -> Files.exists(d.resolve("contract.json"))).map(d -> d.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void onProjectDeleted(Project project) {
    delete(project.id());
  }

  private JsonFile<Contract> contractFile(String projectId) {
    return new JsonFile<>(dir(projectId).resolve("contract.json"), new TypeReference<>() {});
  }

  private JsonFile<DriftState> driftFile(String projectId) {
    return new JsonFile<>(dir(projectId).resolve("drift.json"), new TypeReference<>() {});
  }

  private Path dir(String projectId) {
    if (projectId == null || !ID.matcher(projectId).matches()) throw new IllegalArgumentException("Bad project id " + projectId);
    return root.resolve(projectId);
  }
}
