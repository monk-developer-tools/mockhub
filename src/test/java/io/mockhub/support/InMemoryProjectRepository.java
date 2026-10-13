package io.mockhub.support;

import io.mockhub.project.Project;
import io.mockhub.project.ProjectRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProjectRepository implements ProjectRepository {

  private final Map<String, Project> projects = new LinkedHashMap<>();

  @Override
  public List<Project> findAll() {
    List<Project> all = new ArrayList<>(projects.values());
    all.sort(Comparator.comparing(Project::name));
    return all;
  }

  @Override
  public Optional<Project> findById(String id) {
    return Optional.ofNullable(projects.get(id));
  }

  @Override
  public Optional<Project> findBySlug(String slug) {
    return projects.values().stream().filter(p -> p.slug().equals(slug)).findFirst();
  }

  @Override
  public void save(Project project) {
    projects.put(project.id(), project);
  }

  @Override
  public void deleteById(String id) {
    projects.remove(id);
  }
}
