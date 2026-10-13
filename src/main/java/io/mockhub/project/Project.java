package io.mockhub.project;

public record Project(
    String id,
    String name,
    String slug,
    String description,
    String color,
    String createdAt,
    String updatedAt) {
  public String basePath() {
    return "/" + slug;
  }
}
