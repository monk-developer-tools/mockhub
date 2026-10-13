package io.mockhub.project;

public interface ProjectListener {
  default void onBasePathChanged(Project before, Project after) {}

  default void onProjectDeleted(Project project) {}
}
