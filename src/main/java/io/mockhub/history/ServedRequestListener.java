package io.mockhub.history;

import io.mockhub.project.Project;

@FunctionalInterface
public interface ServedRequestListener extends AutoCloseable {
  ServedRequestListener NONE = (project, entry) -> {};

  void served(Project project, HistoryEntry entry);

  @Override
  default void close() {}
}
