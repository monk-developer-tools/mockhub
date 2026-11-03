package io.mockhub.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;

public final class RuntimeService {
  private final ProjectService projects;
  private final DefaultResponseStubs defaultResponses;
  private final RateLimiter limiter;

  public RuntimeService(ProjectService projects, DefaultResponseStubs defaultResponses, RateLimiter limiter) {
    this.projects = projects;
    this.defaultResponses = defaultResponses;
    this.limiter = limiter;
  }

  public RuntimeSettings current(String projectId) {
    return RuntimeSettings.of(projects.get(projectId).runtime());
  }

  public RuntimeSettings validate(JsonNode json) {
    return RuntimeSettings.parse(json);
  }

  public RuntimeSettings update(String projectId, JsonNode json) {
    RuntimeSettings settings = RuntimeSettings.parse(json);
    Project updated = projects.updateRuntime(projectId, settings);
    defaultResponses.sync(updated);
    limiter.forget(updated.id());
    return settings;
  }
}
