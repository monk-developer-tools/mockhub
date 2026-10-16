package io.mockhub.data;

import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;

public final class ProjectDataCleaner implements ProjectListener {
  private final DataStore store;
  private final BodyFiles files;

  public ProjectDataCleaner(DataStore store, BodyFiles files) {
    this.store = store;
    this.files = files;
  }

  @Override
  public void onProjectDeleted(Project project) {
    store.clearProject(project.id());
    files.deleteProject(project.id());
  }
}
