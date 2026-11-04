package io.mockhub.snapshot;

import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;

public final class SnapshotCleanup implements ProjectListener {
  private final SnapshotRepository repository;

  public SnapshotCleanup(SnapshotRepository repository) {
    this.repository = repository;
  }

  @Override
  public void onProjectDeleted(Project project) {
    repository.deleteProject(project.id());
  }
}
