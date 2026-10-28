package io.mockhub.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WireMockArchiveImporter {
  private final BodyFiles bodyFiles;
  private final WireMockJsonImporter mappings = new WireMockJsonImporter();

  public WireMockArchiveImporter(BodyFiles bodyFiles) {
    this.bodyFiles = bodyFiles;
  }

  public record Result(List<ObjectNode> stubs, int files, Set<String> missingFiles) {}

  public Result parse(Project project, byte[] zip) {
    WireMockArchive archive = WireMockArchive.read(zip);
    Set<String> available = new LinkedHashSet<>();
    archive.files().keySet().forEach(path -> available.add(normalise(path)));
    List<ObjectNode> stubs = new ArrayList<>();
    Set<String> missing = new LinkedHashSet<>();
    archive.mappings().forEach((path, json) -> {
      List<ObjectNode> parsed;
      try {
        parsed = mappings.parse(new StubImporter.Request(project, json, Map.of())).stubs();
      } catch (ApiException e) {
        throw ApiException.badRequest("mappings/" + path + ": " + e.getMessage());
      }
      for (ObjectNode stub : parsed) {
        relinkBodyFile(project, stub, available, missing);
        stubs.add(stub);
      }
    });
    archive.files().forEach((path, content) -> bodyFiles.save(project.id(), normalise(path), content));
    return new Result(stubs, available.size(), missing);
  }

  static String normalise(String reference) {
    String path = reference;
    while (path.startsWith("./") || path.startsWith("/")) path = path.startsWith("./") ? path.substring(2) : path.substring(1);
    return path.replace("/./", "/");
  }

  private static void relinkBodyFile(Project project, ObjectNode stub, Set<String> available, Set<String> missing) {
    JsonNode response = stub.get("response");
    if (!(response instanceof ObjectNode r) || !r.hasNonNull("bodyFileName")) return;
    String reference = normalise(r.get("bodyFileName").asText());
    if (!reference.contains("{{") && !available.contains(reference)) {
      missing.add(reference);
      return;
    }
    r.put("bodyFileName", BodyFiles.bodyFileName(project.id(), reference));
  }
}
