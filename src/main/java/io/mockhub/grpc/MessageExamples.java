package io.mockhub.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.HashSet;
import java.util.Set;

public final class MessageExamples {
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
  private static final int MAX_DEPTH = 4;

  private MessageExamples() {}

  public static ObjectNode of(Descriptor type) {
    JsonNode node = message(type, 0, new HashSet<>());
    return node instanceof ObjectNode o ? o : JSON.objectNode();
  }

  private static JsonNode message(Descriptor type, int depth, Set<String> seen) {
    if (type.getFullName().equals("google.protobuf.Any")) return null;
    JsonNode wellKnown = wellKnown(type.getFullName());
    if (wellKnown != null) return wellKnown;
    ObjectNode out = JSON.objectNode();
    if (depth > MAX_DEPTH || !seen.add(type.getFullName())) return out;
    for (FieldDescriptor field : type.getFields()) {
      if (field.getRealContainingOneof() != null && field.getRealContainingOneof().getFields().get(0) != field) continue;
      JsonNode value = field(field, depth, seen);
      if (value != null) out.set(field.getJsonName(), value);
    }
    seen.remove(type.getFullName());
    return out;
  }

  private static JsonNode field(FieldDescriptor field, int depth, Set<String> seen) {
    if (field.isMapField()) {
      Descriptor entry = field.getMessageType();
      ObjectNode map = JSON.objectNode();
      JsonNode value = single(entry.findFieldByName("value"), depth, seen);
      if (value != null) map.set("key", value);
      return map;
    }
    JsonNode value = single(field, depth, seen);
    if (value == null || !field.isRepeated()) return value;
    ArrayNode list = JSON.arrayNode();
    list.add(value);
    return list;
  }

  private static JsonNode single(FieldDescriptor field, int depth, Set<String> seen) {
    return switch (field.getJavaType()) {
      case STRING -> JSON.textNode(text(field.getName()));
      case INT -> JSON.numberNode(1);
      case LONG -> JSON.textNode("1");
      case FLOAT, DOUBLE -> JSON.numberNode(1.5);
      case BOOLEAN -> JSON.booleanNode(true);
      case BYTE_STRING -> JSON.textNode("AQID");
      case ENUM -> JSON.textNode(enumValue(field));
      case MESSAGE -> seen.contains(field.getMessageType().getFullName()) ? null : message(field.getMessageType(), depth + 1, seen);
    };
  }

  private static String enumValue(FieldDescriptor field) {
    var values = field.getEnumType().getValues();
    return (values.size() > 1 ? values.get(1) : values.get(0)).getName();
  }

  private static String text(String name) {
    String n = name.toLowerCase();
    if (n.equals("id") || n.endsWith("_id")) return "42";
    if (n.contains("email")) return "ada@example.com";
    if (n.contains("name")) return "Ada Lovelace";
    if (n.contains("url")) return "https://example.com";
    if (n.contains("title")) return "Notes on the Analytical Engine";
    return "example";
  }

  private static JsonNode wellKnown(String fullName) {
    return switch (fullName) {
      case "google.protobuf.Timestamp" -> JSON.textNode("2026-01-01T00:00:00Z");
      case "google.protobuf.Duration" -> JSON.textNode("1.5s");
      case "google.protobuf.StringValue" -> JSON.textNode("example");
      case "google.protobuf.BoolValue" -> JSON.booleanNode(true);
      case "google.protobuf.Int32Value", "google.protobuf.UInt32Value", "google.protobuf.FloatValue", "google.protobuf.DoubleValue" -> JSON.numberNode(1);
      case "google.protobuf.Int64Value", "google.protobuf.UInt64Value" -> JSON.textNode("1");
      case "google.protobuf.BytesValue" -> JSON.textNode("AQID");
      case "google.protobuf.Struct", "google.protobuf.Empty" -> JSON.objectNode();
      case "google.protobuf.Value" -> JSON.textNode("example");
      case "google.protobuf.ListValue" -> JSON.arrayNode();
      case "google.protobuf.FieldMask" -> JSON.textNode("name");
      default -> null;
    };
  }
}
