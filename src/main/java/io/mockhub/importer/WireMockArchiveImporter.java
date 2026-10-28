package io.mockhub.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.files.BodyFiles;
import io.mockhub.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    Map<String, String> saved = new LinkedHashMap<>();
    archive.files().keySet().forEach(path -> saved.put(path, BodyFiles.bodyFileName(project.id(), flatName(path))));
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
        relinkBodyFile(stub, saved, missing);
        stubs.add(stub);
      }
    });
    archive.files().forEach((path, content) -> bodyFiles.save(project.id(), flatName(path), content));
    return new Result(stubs, saved.size(), missing);
  }

  static String flatName(String path) {
    String flat = path.replaceAll("^/+", "").replace('/', '-').replaceAll("[^A-Za-z0-9._-]", "_");
    if (flat.length() > 120) flat = flat.substring(flat.length() - 120);
    return flat.isEmpty() || !Character.isLetterOrDigit(flat.charAt(0)) ? "f" + flat : flat;
  }

  private static void relinkBodyFile(ObjectNode stub, Map<String, String> saved, Set<String> missing) {
    JsonNode response = stub.get("response");
    if (!(response instanceof ObjectNode r) || !r.hasNonNull("bodyFileName")) return;
    String reference = r.get("bodyFileName").asText().replaceAll("^/+", "");
    String target = saved.get(reference);
    if (target == null) {
      missing.add(reference);
      return;
    }
    r.put("bodyFileName", target);
  }
}
