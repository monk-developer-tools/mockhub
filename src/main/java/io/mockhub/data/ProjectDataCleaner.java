package io.mockhub.data;

import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;

public final class ProjectDataCleaner implements ProjectListener {
  private final DataStore store;

  public ProjectDataCleaner(DataStore store) {
    this.store = store;
  }

  @Override
  public void onProjectDeleted(Project project) {
    store.clearProject(project.id());
  }
}
