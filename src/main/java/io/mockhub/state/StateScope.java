package io.mockhub.state;

import java.util.Optional;

public final class StateScope {
  private final ThreadLocal<String> project = new ThreadLocal<>();

  public void enter(String projectId) {
    if (projectId == null) project.remove();
    else project.set(projectId);
  }

  public void leave() {
    project.remove();
  }

  public Optional<String> current() {
    return Optional.ofNullable(project.get());
  }
}
