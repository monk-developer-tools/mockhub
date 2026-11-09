package io.mockhub.environment;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.project.ProjectService;

public final class EnvironmentService {
  private final EnvironmentStore store;
  private final ProjectService projects;

  public EnvironmentService(EnvironmentStore store, ProjectService projects) {
    this.store = store;
    this.projects = projects;
  }

  public ProjectEnvironments get(String projectId) {
    return store.get(projects.get(projectId).id());
  }

  public ProjectEnvironments replace(String projectId, JsonNode body) {
    String id = projects.get(projectId).id();
    ProjectEnvironments parsed = ProjectEnvironments.parse(body);
    store.save(id, parsed);
    return parsed;
  }

  public ProjectEnvironments switchTo(String projectId, String name) {
    String id = projects.get(projectId).id();
    ProjectEnvironments next = store.get(id).withActive(name == null ? "" : name.trim());
    store.save(id, next);
    return next;
  }

  public ProjectEnvironments forProject(String projectId) {
    return store.get(projectId);
  }
}
