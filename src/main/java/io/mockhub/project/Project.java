package io.mockhub.project;

import io.mockhub.runtime.RuntimeSettings;
import java.util.List;

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
    RuntimeSettings runtime,
    List<String> domains) {

  public Project {
    domains = domains == null ? List.of() : List.copyOf(domains);
  }

  public Project(String id, String name, String slug, String description, String color, String createdAt, String updatedAt,
      String orgId, String teamId, ApiKey apiKey, boolean root, RuntimeSettings runtime) {
    this(id, name, slug, description, color, createdAt, updatedAt, orgId, teamId, apiKey, root, runtime, List.of());
  }

  public String basePath() {
    return root ? "" : "/" + slug;
  }

  public Project withOrgId(String newOrgId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, teamId, apiKey, root, runtime, domains);
  }

  public Project withApiKey(ApiKey newApiKey) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, orgId, teamId, newApiKey, root, runtime, domains);
  }

  public Project withTeam(String newOrgId, String newTeamId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, newTeamId, apiKey, root, runtime, domains);
  }

  public Project withRuntime(RuntimeSettings newRuntime) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, orgId, teamId, apiKey, root, newRuntime, domains);
  }
}
