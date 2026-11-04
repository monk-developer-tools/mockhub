package io.mockhub.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CrudService {
  private final DataStore store;
  private final RecordPolicy policy;

  public CrudService(DataStore store) {
    this(store, RecordPolicy.NONE);
  }

  public CrudService(DataStore store, RecordPolicy policy) {
    this.store = store;
    this.policy = policy;
  }

  public record Page(List<ObjectNode> items, int total) {}

  public sealed interface Result permits Ok, NotFound, Invalid, Conflict {}

  public record Ok(ObjectNode record) implements Result {}

  public record NotFound(String message) implements Result {}

  public record Invalid(String message) implements Result {}

  public record Conflict(String message) implements Result {}

  public Page list(String projectId, CrudResource resource, CrudQuery query) {
    List<ObjectNode> all = query.apply(store.list(projectId, resource.name()));
    List<ObjectNode> page = all.stream().skip(query.offset()).limit(query.limit()).toList();
    return new Page(page, all.size());
  }

  public Optional<ObjectNode> get(String projectId, CrudResource resource, String id) {
    return store.get(projectId, resource.name(), id);
  }

  public Result create(String projectId, CrudResource resource, JsonNode body) {
    if (body == null || !body.isObject()) return new Invalid("Send a JSON object");
    ObjectNode record = ((ObjectNode) body).deepCopy();
    JsonNode given = record.get(resource.idField());
    String id;
    if (given == null || given.isNull() || given.asText().isBlank()) {
      id = nextId(projectId, resource);
      if (resource.idType() == CrudResource.IdType.NUMBER) record.put(resource.idField(), Long.parseLong(id));
      else record.put(resource.idField(), id);
    } else {
      id = given.asText();
    }
    if (store.get(projectId, resource.name(), id).isPresent()) {
      return new Conflict(resource.name() + " " + id + " already exists");
    }
    Optional<String> refused = refuse(projectId, 1, record);
    if (refused.isPresent()) return new Invalid(refused.get());
    reorder(record, resource.idField());
    store.put(projectId, resource.name(), id, record);
    return new Ok(record);
  }

  public Result replace(String projectId, CrudResource resource, String id, JsonNode body) {
    if (body == null || !body.isObject()) return new Invalid("Send a JSON object");
    Optional<ObjectNode> existing = store.get(projectId, resource.name(), id);
    if (existing.isEmpty()) return new NotFound(resource.name() + " " + id + " not found");
    ObjectNode record = ((ObjectNode) body).deepCopy();
    record.set(resource.idField(), existing.get().get(resource.idField()));
    Optional<String> refused = refuse(projectId, 0, record);
    if (refused.isPresent()) return new Invalid(refused.get());
    reorder(record, resource.idField());
    store.put(projectId, resource.name(), id, record);
    return new Ok(record);
  }

  public Result patch(String projectId, CrudResource resource, String id, JsonNode body) {
    if (body == null || !body.isObject()) return new Invalid("Send a JSON object");
    Optional<ObjectNode> existing = store.get(projectId, resource.name(), id);
    if (existing.isEmpty()) return new NotFound(resource.name() + " " + id + " not found");
    ObjectNode record = existing.get().deepCopy();
    JsonNode originalId = record.get(resource.idField());
    merge(record, (ObjectNode) body);
    record.set(resource.idField(), originalId);
    Optional<String> refused = refuse(projectId, 0, record);
    if (refused.isPresent()) return new Invalid(refused.get());
    store.put(projectId, resource.name(), id, record);
    return new Ok(record);
  }

  public boolean delete(String projectId, CrudResource resource, String id) {
    return store.delete(projectId, resource.name(), id);
  }

  public int seed(String projectId, CrudResource resource, JsonNode records) {
    int count = 0;
    if (records == null || !records.isArray()) return 0;
    for (JsonNode r : records) {
      if (create(projectId, resource, r) instanceof Ok) count++;
    }
    return count;
  }

  private Optional<String> refuse(String projectId, int adding, ObjectNode record) {
    int records = store.resources(projectId).values().stream().mapToInt(Integer::intValue).sum() + adding;
    return policy.refuse(projectId, records, Jsons.write(record).getBytes(StandardCharsets.UTF_8).length);
  }

  private String nextId(String projectId, CrudResource resource) {
    if (resource.idType() == CrudResource.IdType.UUID) return UUID.randomUUID().toString();
    long max = store.list(projectId, resource.name()).stream()
        .map(r -> r.path(resource.idField()))
        .filter(JsonNode::canConvertToLong)
        .mapToLong(JsonNode::asLong)
        .max().orElse(0);
    return String.valueOf(max + 1);
  }

  private static void merge(ObjectNode target, ObjectNode patch) {
    patch.fields().forEachRemaining(e -> {
      JsonNode current = target.get(e.getKey());
      if (e.getValue().isNull()) target.remove(e.getKey());
      else if (current != null && current.isObject() && e.getValue().isObject()) merge((ObjectNode) current, (ObjectNode) e.getValue());
      else target.set(e.getKey(), e.getValue().deepCopy());
    });
  }

  private static void reorder(ObjectNode record, String idField) {
    JsonNode id = record.remove(idField);
    ObjectNode copy = record.deepCopy();
    record.removeAll();
    record.set(idField, id);
    record.setAll(copy);
  }

  public static ObjectNode error(String message) {
    return Jsons.object().put("error", message);
  }
}
