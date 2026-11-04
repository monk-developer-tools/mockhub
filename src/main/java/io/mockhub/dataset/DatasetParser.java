package io.mockhub.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class DatasetParser {
  private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");

  public record Parsed(List<ObjectNode> records, List<Column> columns) {}

  private DatasetParser() {}

  public static Parsed parse(DatasetFormat format, String text) {
    return switch (format) {
      case CSV -> csv(text);
      case JSON -> json(text);
      case NDJSON -> ndjson(text);
    };
  }

  static Parsed csv(String text) {
    List<CsvReader.Row> rows = CsvReader.read(text);
    if (rows.isEmpty()) throw ApiException.badRequest("The CSV is empty; the first line names the columns");
    List<String> names = new ArrayList<>();
    List<ColumnType> declared = new ArrayList<>();
    for (String header : rows.get(0).cells()) {
      String raw = header.trim();
      int colon = raw.lastIndexOf(':');
      ColumnType type = colon > 0 ? ColumnType.named(raw.substring(colon + 1)).orElse(null) : null;
      String name = type == null ? raw : raw.substring(0, colon).trim();
      if (name.isEmpty()) throw ApiException.badRequest("Column " + (names.size() + 1) + " has no name");
      if (names.contains(name)) throw ApiException.badRequest("Two columns are called " + name);
      names.add(name);
      declared.add(type);
    }
    List<CsvReader.Row> data = rows.subList(1, rows.size());
    for (CsvReader.Row row : data) {
      if (row.cells().size() > names.size()) {
        throw ApiException.badRequest("Line " + row.line() + " has " + row.cells().size() + " values but the header has " + names.size() + " columns");
      }
    }
    List<Column> columns = new ArrayList<>();
    for (int c = 0; c < names.size(); c++) {
      ColumnType type = declared.get(c) != null ? declared.get(c) : infer(data, c);
      columns.add(new Column(names.get(c), type));
    }
    List<ObjectNode> records = new ArrayList<>();
    for (CsvReader.Row row : data) {
      ObjectNode record = Jsons.object();
      for (int c = 0; c < columns.size(); c++) {
        String cell = c < row.cells().size() ? row.cells().get(c) : "";
        record.set(columns.get(c).name(), value(columns.get(c), cell, row.line()));
      }
      records.add(record);
    }
    return new Parsed(records, columns);
  }

  static Parsed json(String text) {
    JsonNode root;
    try {
      root = Jsons.MAPPER.readTree(text);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("The JSON doesn't parse" + where(e));
    }
    if (root == null || !root.isArray()) throw ApiException.badRequest("Upload a JSON array of objects, e.g. [{\"id\": 1, \"name\": \"Ada\"}]");
    List<ObjectNode> records = new ArrayList<>();
    for (int i = 0; i < root.size(); i++) {
      if (!root.get(i).isObject()) throw ApiException.badRequest("Item " + (i + 1) + " isn't an object");
      records.add((ObjectNode) root.get(i));
    }
    return new Parsed(records, columnsOf(records));
  }

  static Parsed ndjson(String text) {
    List<ObjectNode> records = new ArrayList<>();
    String[] lines = text.split("\r?\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = i == 0 && lines[i].startsWith("﻿") ? lines[i].substring(1) : lines[i];
      if (line.isBlank()) continue;
      JsonNode node;
      try {
        node = Jsons.MAPPER.readTree(line);
      } catch (JsonProcessingException e) {
        throw ApiException.badRequest("Line " + (i + 1) + " isn't valid JSON");
      }
      if (node == null || !node.isObject()) throw ApiException.badRequest("Line " + (i + 1) + " isn't a JSON object");
      records.add((ObjectNode) node);
    }
    return new Parsed(records, columnsOf(records));
  }

  public static List<Column> columnsOf(List<ObjectNode> records) {
    Map<String, ColumnType> types = new LinkedHashMap<>();
    for (ObjectNode record : records) {
      record.fields().forEachRemaining(e -> {
        ColumnType type = typeOf(e.getValue());
        if (type == null) {
          types.putIfAbsent(e.getKey(), null);
          return;
        }
        ColumnType known = types.get(e.getKey());
        if (known == null) types.put(e.getKey(), type);
        else if (known != type) types.put(e.getKey(), ColumnType.JSON);
      });
    }
    List<Column> columns = new ArrayList<>();
    types.forEach((name, type) -> columns.add(new Column(name, type == null ? ColumnType.STRING : type)));
    return columns;
  }

  private static ColumnType typeOf(JsonNode value) {
    if (value == null || value.isNull()) return null;
    if (value.isNumber()) return ColumnType.NUMBER;
    if (value.isBoolean()) return ColumnType.BOOLEAN;
    if (value.isContainerNode()) return ColumnType.JSON;
    return ColumnType.STRING;
  }

  private static ColumnType infer(List<CsvReader.Row> rows, int column) {
    boolean any = false;
    boolean numbers = true;
    boolean booleans = true;
    for (CsvReader.Row row : rows) {
      String cell = column < row.cells().size() ? row.cells().get(column).trim() : "";
      if (cell.isEmpty()) continue;
      any = true;
      numbers &= NUMBER.matcher(cell).matches();
      booleans &= cell.equalsIgnoreCase("true") || cell.equalsIgnoreCase("false");
    }
    if (!any) return ColumnType.STRING;
    if (numbers) return ColumnType.NUMBER;
    return booleans ? ColumnType.BOOLEAN : ColumnType.STRING;
  }

  private static JsonNode value(Column column, String cell, int line) {
    String trimmed = cell.trim();
    if (column.type() != ColumnType.STRING && trimmed.isEmpty()) return Jsons.MAPPER.nullNode();
    String where = "Line " + line + ", column " + column.name() + ": ";
    return switch (column.type()) {
      case STRING -> Jsons.MAPPER.getNodeFactory().textNode(cell);
      case NUMBER -> {
        try {
          yield number(new BigDecimal(trimmed));
        } catch (NumberFormatException e) {
          throw ApiException.badRequest(where + trimmed + " isn't a number");
        }
      }
      case BOOLEAN -> {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.equals("true") && !lower.equals("false")) throw ApiException.badRequest(where + trimmed + " isn't true or false");
        yield Jsons.MAPPER.getNodeFactory().booleanNode(lower.equals("true"));
      }
      case JSON -> {
        try {
          yield Jsons.MAPPER.readTree(trimmed);
        } catch (JsonProcessingException e) {
          throw ApiException.badRequest(where + "isn't valid JSON");
        }
      }
    };
  }

  private static JsonNode number(BigDecimal value) {
    BigDecimal plain = value.stripTrailingZeros();
    if (plain.scale() <= 0 && plain.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
      return Jsons.MAPPER.getNodeFactory().numberNode(plain.longValueExact());
    }
    return Jsons.MAPPER.getNodeFactory().numberNode(value.doubleValue());
  }

  private static String where(JsonProcessingException e) {
    return e.getLocation() == null ? "" : " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")";
  }
}
