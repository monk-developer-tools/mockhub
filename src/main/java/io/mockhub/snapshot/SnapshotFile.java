package io.mockhub.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.Main;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.files.BodyFiles;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SnapshotFile {
  public static final String FORMAT = "mockhub-snapshot";
  public static final int VERSION = 1;
  public static final int MAX_STUBS = 5000;

  private SnapshotFile() {}

  public static ObjectNode write(Snapshot s) {
    ObjectNode out = Jsons.object();
    out.put("format", FORMAT).put("version", VERSION).put("mockhubVersion", Main.VERSION);
    out.put("name", s.name()).put("purpose", s.purpose()).put("createdAt", s.createdAt()).put("createdBy", s.createdBy());
    out.putObject("project").put("id", s.sourceProjectId()).put("name", s.sourceProjectName());
    ObjectNode settings = out.putObject("settings");
    settings.set("runtime", s.runtime());
    settings.put("apiKeyHeader", s.apiKeyHeader());
    out.set("stubs", Jsons.MAPPER.valueToTree(s.stubs()));
    out.set("files", Jsons.MAPPER.valueToTree(s.files()));
    return out;
  }

  public static Snapshot read(JsonNode file) {
    if (file == null || !file.isObject() || !FORMAT.equals(file.path("format").asText())) {
      throw ApiException.badRequest("This file isn't a MockHub snapshot");
    }
    if (file.path("version").asInt(0) > VERSION) {
      throw ApiException.badRequest("This snapshot was made by a newer MockHub; update this server first");
    }
    if (!file.path("stubs").isArray()) throw ApiException.badRequest("The snapshot has no stubs list");
    if (file.path("stubs").size() > MAX_STUBS) throw ApiException.badRequest("A snapshot can hold at most " + MAX_STUBS + " stubs");
    List<ObjectNode> stubs = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode stub : file.get("stubs")) {
      if (!stub.isObject()) throw ApiException.badRequest("Every stub in the snapshot must be a JSON object");
      String id = stub.path("id").asText("");
      if (!id.isEmpty() && !ids.add(id)) throw ApiException.badRequest("The snapshot has two stubs with id " + id);
      stubs.add((ObjectNode) stub);
    }
    Map<String, String> files = new LinkedHashMap<>();
    file.path("files").fields().forEachRemaining(e -> {
      BodyFiles.checkName(e.getKey());
      byte[] bytes;
      try {
        bytes = Base64.getDecoder().decode(e.getValue().asText(""));
      } catch (IllegalArgumentException ex) {
        throw ApiException.badRequest("File " + e.getKey() + " in the snapshot isn't valid base64");
      }
      if (bytes.length > BodyFiles.MAX_BYTES) throw new ApiException(413, "File " + e.getKey() + " is larger than 10 MB");
      files.put(e.getKey(), e.getValue().asText());
    });
    JsonNode project = file.path("project");
    JsonNode settings = file.path("settings");
    String header = settings.path("apiKeyHeader").isTextual() ? settings.get("apiKeyHeader").asText() : null;
    JsonNode runtime = settings.path("runtime").isObject() ? settings.get("runtime") : null;
    return new Snapshot(null, null, project.path("id").asText(""), project.path("name").asText(""),
        text(file, "name", "Uploaded snapshot"), text(file, "purpose", ""), text(file, "createdAt", ""), text(file, "createdBy", ""),
        false, runtime, header, stubs, files);
  }

  private static String text(JsonNode node, String field, String fallback) {
    String value = node.path(field).asText("").trim();
    return value.isEmpty() ? fallback : value.length() > 200 ? value.substring(0, 200) : value;
  }
}
