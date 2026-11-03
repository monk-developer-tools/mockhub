package io.mockhub.project;

import io.mockhub.runtime.RuntimeSettings;

public record Project(
    String id,
    String name,
    String slug,
    String description,
    String color,
    String createdAt,
    String updatedAt,
    String orgId,
    String teamId,
    ApiKey apiKey,
    boolean root,
    RuntimeSettings runtime) {
  public String basePath() {
    return root ? "" : "/" + slug;
  }

  public Project withOrgId(String newOrgId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, teamId, apiKey, root, runtime);
  }

  public Project withApiKey(ApiKey newApiKey) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, orgId, teamId, newApiKey, root, runtime);
  }

  public Project withTeam(String newOrgId, String newTeamId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, newTeamId, apiKey, root, runtime);
  }

  public Project withRuntime(RuntimeSettings newRuntime) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, orgId, teamId, apiKey, root, newRuntime);
  }
}
