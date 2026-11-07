package io.mockhub.team;

public record Team(String id, String orgId, String name, String slug, String description, String createdAt, String updatedAt, boolean restricted) {

  public Team(String id, String orgId, String name, String slug, String description, String createdAt, String updatedAt) {
    this(id, orgId, name, slug, description, createdAt, updatedAt, false);
  }
}
