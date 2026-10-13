package io.mockhub.project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
  List<Project> findAll();

  Optional<Project> findById(String id);

  Optional<Project> findBySlug(String slug);

  void save(Project project);

  void deleteById(String id);
}
