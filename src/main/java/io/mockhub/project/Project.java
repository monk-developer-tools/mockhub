package io.mockhub.project;

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
    boolean root) {
  public String basePath() {
    return root ? "" : "/" + slug;
  }

  public Project withOrgId(String newOrgId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, teamId, apiKey, root);
  }

  public Project withTeam(String newOrgId, String newTeamId) {
    return new Project(id, name, slug, description, color, createdAt, updatedAt, newOrgId, newTeamId, apiKey, root);
  }
}
