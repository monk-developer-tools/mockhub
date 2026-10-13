package io.mockhub.project;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FileProjectRepository implements ProjectRepository {
  private final Path file;
  private final List<Project> projects = new ArrayList<>();

  public FileProjectRepository(Path rootDir) {
    this.file = rootDir.resolve("mockhub").resolve("projects.json");
    load();
  }

  private void load() {
    if (!Files.exists(file)) return;
    try {
      projects.addAll(Jsons.MAPPER.readValue(file.toFile(), new TypeReference<List<Project>>() {}));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }

  @Override
  public synchronized List<Project> findAll() {
    List<Project> copy = new ArrayList<>(projects);
    copy.sort(Comparator.comparing(p -> p.name().toLowerCase()));
    return copy;
  }

  @Override
  public synchronized Optional<Project> findById(String id) {
    return projects.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  @Override
  public synchronized Optional<Project> findBySlug(String slug) {
    return projects.stream().filter(p -> p.slug().equalsIgnoreCase(slug)).findFirst();
  }

  @Override
  public synchronized void save(Project project) {
    projects.removeIf(p -> p.id().equals(project.id()));
    projects.add(project);
    flush();
  }

  @Override
  public synchronized void deleteById(String id) {
    projects.removeIf(p -> p.id().equals(id));
    flush();
  }

  private void flush() {
    try {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Jsons.MAPPER.writeValue(tmp.toFile(), projects);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + file, e);
    }
  }
}
