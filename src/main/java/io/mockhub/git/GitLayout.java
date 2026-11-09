package io.mockhub.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.common.Slugs;
import io.mockhub.files.BodyFiles;
import io.mockhub.snapshot.Snapshot;
import io.mockhub.snapshot.SnapshotFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class GitLayout {
  public static final String FORMAT = "mockhub-git";
  public static final int VERSION = 1;
  public static final String MANIFEST = "mockhub.json";
  public static final String MAPPINGS = "mappings/";
  public static final String FILES = "__files/";
  private static final ObjectWriter PRETTY = Jsons.MAPPER.writerWithDefaultPrettyPrinter();
  private static final String[] URL_KEYS = {"url", "urlPath", "urlPathTemplate", "urlPathPattern", "urlPattern"};

  private GitLayout() {}

  public static Map<String, byte[]> write(Snapshot snapshot) {
    Map<String, byte[]> out = new TreeMap<>();
    ArrayNode order = Jsons.MAPPER.createArrayNode();
    Set<String> used = new HashSet<>();
    for (ObjectNode stub : snapshot.stubs()) {
      String name = fileName(stub, used);
      order.add(name);
      out.put(MAPPINGS + name, pretty(stub));
    }
    snapshot.files().forEach((name, base64) -> out.put(FILES + name, Base64.getDecoder().decode(base64)));
    ObjectNode manifest = Jsons.object().put("format", FORMAT).put("version", VERSION);
    manifest.putObject("project").put("id", snapshot.sourceProjectId()).put("name", snapshot.sourceProjectName());
    ObjectNode settings = manifest.putObject("settings");
    settings.set("runtime", snapshot.runtime());
    settings.put("apiKeyHeader", snapshot.apiKeyHeader());
    manifest.set("order", order);
    out.put(MANIFEST, pretty(manifest));
    return out;
  }

  public static Snapshot read(Map<String, byte[]> files, String label) {
    JsonNode manifest = files.containsKey(MANIFEST) ? parse(MANIFEST, files.get(MANIFEST)) : Jsons.object();
    if (manifest.has("format") && !FORMAT.equals(manifest.path("format").asText())) {
      throw ApiException.badRequest(MANIFEST + " in the repository isn't a MockHub Git file");
    }
    if (manifest.path("version").asInt(0) > VERSION) {
      throw ApiException.badRequest("The repository was written by a newer MockHub; update this server first");
    }
    Map<String, byte[]> mappings = new TreeMap<>();
    files.forEach((path, bytes) -> {
      if (path.startsWith(MAPPINGS) && path.toLowerCase(Locale.ROOT).endsWith(".json") && !path.substring(MAPPINGS.length()).contains("/")) {
        mappings.put(path.substring(MAPPINGS.length()), bytes);
      }
    });
    List<String> names = new ArrayList<>();
    manifest.path("order").forEach(n -> {
      if (mappings.containsKey(n.asText()) && !names.contains(n.asText())) names.add(n.asText());
    });
    mappings.keySet().stream().filter(n -> !names.contains(n)).forEach(names::add);
    List<ObjectNode> stubs = new ArrayList<>();
    for (String name : names) {
      JsonNode node = parse(MAPPINGS + name, mappings.get(name));
      if (node.has("mappings")) node.path("mappings").forEach(m -> stubs.add(stub(name, m)));
      else stubs.add(stub(name, node));
    }
    if (stubs.size() > SnapshotFile.MAX_STUBS) throw ApiException.badRequest("A project can take at most " + SnapshotFile.MAX_STUBS + " stubs from Git");
    Map<String, String> bodies = new LinkedHashMap<>();
    files.forEach((path, bytes) -> {
      if (!path.startsWith(FILES)) return;
      String name = path.substring(FILES.length());
      BodyFiles.checkName(name);
      if (bytes.length > BodyFiles.MAX_BYTES) throw new ApiException(413, "File " + name + " in the repository is larger than 10 MB");
      bodies.put(name, Base64.getEncoder().encodeToString(bytes));
    });
    JsonNode project = manifest.path("project");
    JsonNode settings = manifest.path("settings");
    JsonNode runtime = settings.path("runtime").isObject() ? settings.get("runtime") : null;
    String header = settings.path("apiKeyHeader").isTextual() ? settings.get("apiKeyHeader").asText() : null;
    return new Snapshot(null, null, project.path("id").asText(""), project.path("name").asText(""), label, "", "", "", false,
        runtime, header, stubs, bodies);
  }

  static String fileName(JsonNode stub, Set<String> used) {
    String base = Slugs.slugify(stub.path("name").asText(""), "");
    if (base.isEmpty()) base = Slugs.slugify(stub.path("request").path("method").asText("any") + " " + url(stub.path("request")), "stub");
    if (base.length() > 60) base = base.substring(0, 60).replaceAll("-+$", "");
    String id = stub.path("id").asText("");
    String name = base + (id.isEmpty() ? "" : "-" + id.substring(0, Math.min(8, id.length()))) + ".json";
    for (int i = 2; !used.add(name); i++) name = base + "-" + i + ".json";
    return name;
  }

  private static String url(JsonNode request) {
    for (String key : URL_KEYS) {
      if (request.hasNonNull(key)) return request.get(key).asText();
    }
    return "";
  }

  private static ObjectNode stub(String file, JsonNode node) {
    if (!node.isObject() || !node.has("request")) throw ApiException.badRequest(MAPPINGS + file + " isn't a WireMock stub with a request");
    return (ObjectNode) node;
  }

  private static JsonNode parse(String path, byte[] bytes) {
    try {
      return Jsons.MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw ApiException.badRequest(path + " in the repository isn't valid JSON: " + e.getMessage());
    }
  }

  private static byte[] pretty(JsonNode node) {
    try {
      return (PRETTY.writeValueAsString(node) + "\n").getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
