package io.mockhub.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record Snapshot(
    String id,
    String projectId,
    String sourceProjectId,
    String sourceProjectName,
    String name,
    String purpose,
    String createdAt,
    String createdBy,
    boolean safety,
    JsonNode runtime,
    String apiKeyHeader,
    List<ObjectNode> stubs,
    Map<String, String> files,
    JsonNode environments) {

  public record Summary(String id, String name, String purpose, String createdAt, String createdBy, boolean safety,
      String sourceProjectName, int stubCount, int fileCount, List<String> crudResources) {}

  public Snapshot {
    stubs = stubs == null ? List.of() : List.copyOf(stubs);
    files = files == null ? Map.of() : Map.copyOf(files);
  }

  public Snapshot(String id, String projectId, String sourceProjectId, String sourceProjectName, String name, String purpose, String createdAt,
      String createdBy, boolean safety, JsonNode runtime, String apiKeyHeader, List<ObjectNode> stubs, Map<String, String> files) {
    this(id, projectId, sourceProjectId, sourceProjectName, name, purpose, createdAt, createdBy, safety, runtime, apiKeyHeader, stubs, files, null);
  }

  public Summary summary() {
    List<String> crud = new ArrayList<>();
    for (ObjectNode stub : stubs) {
      String resource = stub.path("metadata").path("mockhub").path("crud").path("resource").asText("");
      if (!resource.isEmpty()) crud.add(resource);
    }
    return new Summary(id, name, purpose, createdAt, createdBy, safety, sourceProjectName, stubs.size(), files.size(), crud);
  }

  public Snapshot ownedBy(String newId, String newProjectId, String newName, String newCreatedAt, String newCreatedBy) {
    return new Snapshot(newId, newProjectId, sourceProjectId, sourceProjectName, newName, purpose, newCreatedAt, newCreatedBy,
        false, runtime, apiKeyHeader, stubs, files, environments);
  }
}
