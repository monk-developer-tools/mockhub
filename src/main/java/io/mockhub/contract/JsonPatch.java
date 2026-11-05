package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

public final class JsonPatch {
  public static final class PatchException extends RuntimeException {
    PatchException(String message) {
      super(message);
    }
  }

  private JsonPatch() {}

  public static JsonNode apply(JsonNode document, JsonNode operations) {
    if (operations == null || !operations.isArray()) throw new PatchException("A patch is a list of operations");
    JsonNode doc = document.deepCopy();
    int index = 0;
    for (JsonNode op : operations) {
      index++;
      String kind = op.path("op").asText("");
      String path = op.path("path").asText(null);
      if (path == null) throw new PatchException("Operation " + index + " has no path");
      switch (kind) {
        case "add" -> doc = add(doc, path, value(op, index));
        case "replace" -> {
          resolve(doc, path, index);
          doc = remove(doc, path, index);
          doc = add(doc, path, value(op, index));
        }
        case "remove" -> doc = remove(doc, path, index);
        default -> throw new PatchException("Operation " + index + " uses '" + kind + "'; only add, replace and remove are allowed");
      }
    }
    return doc;
  }

  private static JsonNode value(JsonNode op, int index) {
    if (!op.has("value")) throw new PatchException("Operation " + index + " has no value");
    return op.get("value").deepCopy();
  }

  private static JsonNode add(JsonNode doc, String path, JsonNode value) {
    if (path.isEmpty()) return value;
    List<String> parts = parts(path);
    JsonNode parent = parentOf(doc, parts, path);
    String last = parts.get(parts.size() - 1);
    if (parent.isObject()) {
      ((ObjectNode) parent).set(last, value);
    } else if (parent.isArray()) {
      ArrayNode array = (ArrayNode) parent;
      if (last.equals("-")) array.add(value);
      else array.insert(arrayIndex(array, last, path, true), value);
    } else {
      throw new PatchException("Can't add at " + path);
    }
    return doc;
  }

  private static JsonNode remove(JsonNode doc, String path, int index) {
    if (path.isEmpty()) throw new PatchException("Operation " + index + " can't remove the whole document");
    List<String> parts = parts(path);
    JsonNode parent = parentOf(doc, parts, path);
    String last = parts.get(parts.size() - 1);
    if (parent.isObject() && parent.has(last)) {
      ((ObjectNode) parent).remove(last);
    } else if (parent.isArray()) {
      ((ArrayNode) parent).remove(arrayIndex((ArrayNode) parent, last, path, false));
    } else {
      throw new PatchException("Operation " + index + ": nothing at " + path);
    }
    return doc;
  }

  private static void resolve(JsonNode doc, String path, int index) {
    JsonNode cur = doc;
    for (String part : parts(path)) {
      cur = cur.isArray() ? cur.path(safeIndex(part)) : cur.path(part);
      if (cur.isMissingNode()) throw new PatchException("Operation " + index + ": nothing at " + path);
    }
  }

  private static JsonNode parentOf(JsonNode doc, List<String> parts, String path) {
    JsonNode cur = doc;
    for (int i = 0; i < parts.size() - 1; i++) {
      String part = parts.get(i);
      cur = cur.isArray() ? cur.path(safeIndex(part)) : cur.path(part);
      if (cur.isMissingNode() || !(cur.isObject() || cur.isArray())) throw new PatchException("Nothing at " + path + " to change");
    }
    return cur;
  }

  private static int arrayIndex(ArrayNode array, String part, String path, boolean adding) {
    int i = safeIndex(part);
    if (i < 0 || i > array.size() || (!adding && i == array.size())) throw new PatchException("Index out of range at " + path);
    return i;
  }

  private static int safeIndex(String part) {
    try {
      return Integer.parseInt(part);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static List<String> parts(String path) {
    if (!path.startsWith("/")) throw new PatchException("Paths must start with /, got " + path);
    List<String> out = new ArrayList<>();
    for (String raw : path.substring(1).split("/", -1)) out.add(raw.replace("~1", "/").replace("~0", "~"));
    return out;
  }
}
