package io.mockhub.project;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FileProjectRepository implements ProjectRepository {
  private final JsonFile<List<Project>> file;
  private final List<Project> projects;

  public FileProjectRepository(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "projects.json"), new TypeReference<>() {});
    this.projects = new ArrayList<>(file.read(List::of));
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
    file.write(projects);
  }

  @Override
  public synchronized void deleteById(String id) {
    projects.removeIf(p -> p.id().equals(id));
    file.write(projects);
  }
}
