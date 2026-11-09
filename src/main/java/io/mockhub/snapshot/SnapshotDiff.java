package io.mockhub.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.stub.StubPaths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SnapshotDiff {
  public static final String ADDED = "added";
  public static final String REMOVED = "removed";
  public static final String CHANGED = "changed";
  public static final String MOVED = "moved";

  public record Side(String label, List<ObjectNode> stubs, JsonNode runtime, String apiKeyHeader, Map<String, String> files, JsonNode environments) {
    public Side(String label, List<ObjectNode> stubs, JsonNode runtime, String apiKeyHeader, Map<String, String> files) {
      this(label, stubs, runtime, apiKeyHeader, files, null);
    }
  }

  private SnapshotDiff() {}

  public static ObjectNode compare(Side from, Side to) {
    Map<String, ObjectNode> before = byId(from.stubs());
    Map<String, ObjectNode> after = byId(to.stubs());

    ObjectNode out = Jsons.object();
    out.put("from", from.label()).put("to", to.label());
    ArrayNode stubs = out.putArray("stubs");
    int added = 0;
    int removed = 0;
    int changed = 0;
    int unchanged = 0;
    for (Map.Entry<String, ObjectNode> e : after.entrySet()) {
      ObjectNode old = before.get(e.getKey());
      if (old == null) {
        stubs.add(entry(e.getKey(), ADDED, null, e.getValue()));
        added++;
      } else if (!old.equals(e.getValue())) {
        String status = withoutPriority(old).equals(withoutPriority(e.getValue())) ? MOVED : CHANGED;
        stubs.add(entry(e.getKey(), status, old, e.getValue()));
        changed++;
      } else {
        unchanged++;
      }
    }
    for (Map.Entry<String, ObjectNode> e : before.entrySet()) {
      if (after.containsKey(e.getKey())) continue;
      stubs.add(entry(e.getKey(), REMOVED, e.getValue(), null));
      removed++;
    }
    out.putObject("counts").put(ADDED, added).put(REMOVED, removed).put(CHANGED, changed).put("unchanged", unchanged);

    ObjectNode runtime = out.putObject("runtime");
    runtime.put("changed", !Objects.equals(normalize(from.runtime()), normalize(to.runtime())));
    runtime.set("before", from.runtime());
    runtime.set("after", to.runtime());

    ObjectNode apiKey = out.putObject("apiKeyHeader");
    apiKey.put("changed", !Objects.equals(from.apiKeyHeader(), to.apiKeyHeader()));
    apiKey.put("before", from.apiKeyHeader());
    apiKey.put("after", to.apiKeyHeader());

    ObjectNode environments = out.putObject("environments");
    environments.put("changed", to.environments() != null && !to.environments().equals(from.environments() == null ? Jsons.MAPPER.createArrayNode() : from.environments()));
    environments.set("before", from.environments());
    environments.set("after", to.environments());

    ObjectNode files = out.putObject("files");
    ArrayNode addedFiles = files.putArray(ADDED);
    ArrayNode removedFiles = files.putArray(REMOVED);
    ArrayNode changedFiles = files.putArray(CHANGED);
    to.files().keySet().stream().sorted().forEach(name -> {
      if (!from.files().containsKey(name)) addedFiles.add(name);
      else if (!from.files().get(name).equals(to.files().get(name))) changedFiles.add(name);
    });
    from.files().keySet().stream().sorted().filter(name -> !to.files().containsKey(name)).forEach(removedFiles::add);

    boolean same = added + removed + changed == 0 && !runtime.get("changed").asBoolean() && !apiKey.get("changed").asBoolean()
        && !environments.get("changed").asBoolean() && addedFiles.isEmpty() && removedFiles.isEmpty() && changedFiles.isEmpty();
    out.put("identical", same);
    return out;
  }

  private static ObjectNode entry(String id, String status, ObjectNode before, ObjectNode after) {
    ObjectNode shown = after != null ? after : before;
    ObjectNode node = Jsons.object();
    node.put("id", id).put("status", status);
    node.put("name", shown.path("name").asText(""));
    node.put("method", shown.path("request").path("method").asText("ANY"));
    node.put("url", StubPaths.displayUrl(shown.path("request")));
    node.set("before", before);
    node.set("after", after);
    return node;
  }

  private static Map<String, ObjectNode> byId(List<ObjectNode> stubs) {
    Map<String, ObjectNode> out = new LinkedHashMap<>();
    for (ObjectNode stub : stubs) out.put(stub.path("id").asText(), stub);
    return out;
  }

  private static JsonNode withoutPriority(ObjectNode stub) {
    ObjectNode copy = stub.deepCopy();
    copy.remove("priority");
    return copy;
  }

  private static JsonNode normalize(JsonNode node) {
    return node == null || node.isNull() ? Jsons.object() : node;
  }
}
