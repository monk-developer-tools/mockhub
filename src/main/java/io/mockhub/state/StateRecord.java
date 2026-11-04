package io.mockhub.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.wiremock.extensions.state.internal.model.Context;

public record StateRecord(String name, Map<String, String> properties, List<Map<String, String>> list, long updateCount, String updatedAt) {
  public static final int MAX_NAME = 200;

  public StateRecord {
    properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    list = list.stream().map(item -> Collections.unmodifiableMap(new LinkedHashMap<>(item))).toList();
  }

  public static StateRecord of(Context context, Instant now) {
    List<Map<String, String>> items = new ArrayList<>();
    context.getList().forEach(item -> items.add(new LinkedHashMap<>(item)));
    Long count = context.getUpdateCount();
    return new StateRecord(context.getContextName(), new LinkedHashMap<>(context.getProperties()), items, count == null ? 0 : count, now.toString());
  }

  public static StateRecord of(JsonNode json) {
    return new StateRecord(json.path("name").asText(), strings(json.path("properties"), "properties"), items(json.path("list")),
        json.path("updateCount").asLong(0), json.path("updatedAt").asText(""));
  }

  public static StateRecord edited(String name, JsonNode body, long updateCount, Instant now) {
    if (name == null || name.isBlank()) throw ApiException.badRequest("Add ?name=<context name>");
    if (name.length() > MAX_NAME) throw ApiException.badRequest("Keep the context name under " + MAX_NAME + " characters");
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send {\"properties\": {...}, \"list\": [...]}");
    JsonNode list = body.path("list");
    if (!list.isMissingNode() && !list.isNull() && !list.isArray()) throw ApiException.badRequest("list must be an array of objects");
    return new StateRecord(name, strings(body.path("properties"), "properties"), items(list), updateCount, now.toString());
  }

  public Context toContext() {
    Context context = new Context(name);
    context.getProperties().putAll(properties);
    list.forEach(item -> context.getList().add(new LinkedHashMap<>(item)));
    setUpdateCount(context, updateCount);
    return context;
  }

  public ObjectNode toJson() {
    ObjectNode out = Jsons.object().put("name", name);
    ObjectNode props = out.putObject("properties");
    properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> props.put(e.getKey(), e.getValue()));
    ArrayNode items = out.putArray("list");
    list.forEach(item -> {
      ObjectNode row = items.addObject();
      item.forEach(row::put);
    });
    return out.put("updateCount", updateCount).put("updatedAt", updatedAt);
  }

  private static Map<String, String> strings(JsonNode node, String what) {
    Map<String, String> out = new LinkedHashMap<>();
    if (node.isMissingNode() || node.isNull()) return out;
    if (!node.isObject()) throw ApiException.badRequest(what + " must be an object of names and values");
    node.fields().forEachRemaining(e -> {
      JsonNode v = e.getValue();
      if (v.isContainerNode()) throw ApiException.badRequest(what + "." + e.getKey() + " must be text, a number or true/false");
      if (!v.isNull()) out.put(e.getKey(), v.asText());
    });
    return out;
  }

  private static List<Map<String, String>> items(JsonNode node) {
    List<Map<String, String>> out = new ArrayList<>();
    if (!node.isArray()) return out;
    for (int i = 0; i < node.size(); i++) out.add(strings(node.get(i), "list[" + i + "]"));
    return out;
  }

  private static void setUpdateCount(Context context, long count) {
    try {
      Field field = Context.class.getDeclaredField("updateCount");
      field.setAccessible(true);
      field.set(context, count);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("The state extension changed: can't restore a context's update count", e);
    }
  }
}
