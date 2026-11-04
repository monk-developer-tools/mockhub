package io.mockhub.dataset;

import io.mockhub.api.ApiException;
import java.util.ArrayList;
import java.util.List;

final class CsvReader {
  record Row(int line, List<String> cells) {}

  private CsvReader() {}

  static List<Row> read(String text) {
    List<Row> rows = new ArrayList<>();
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    boolean wasQuoted = false;
    int line = 1;
    int rowLine = 1;
    int i = text.startsWith("﻿") ? 1 : 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          cell.append('"');
          i += 2;
          continue;
        }
        if (c == '"') quoted = false;
        else {
          if (c == '\n') line++;
          cell.append(c);
        }
        i++;
        continue;
      }
      if (c == '"' && cell.isEmpty() && !wasQuoted) {
        quoted = true;
        wasQuoted = true;
      } else if (c == ',') {
        cells.add(cell.toString());
        cell.setLength(0);
        wasQuoted = false;
      } else if (c == '\n' || c == '\r') {
        cells.add(cell.toString());
        cell.setLength(0);
        wasQuoted = false;
        if (!(cells.size() == 1 && cells.get(0).isEmpty())) rows.add(new Row(rowLine, List.copyOf(cells)));
        cells.clear();
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        line++;
        rowLine = line;
      } else if (wasQuoted) {
        throw ApiException.badRequest("Line " + line + ": text after a closing quote; put the whole value in quotes");
      } else {
        cell.append(c);
      }
      i++;
    }
    if (quoted) throw ApiException.badRequest("Line " + rowLine + ": a quoted value is never closed");
    cells.add(cell.toString());
    if (!(cells.size() == 1 && cells.get(0).isEmpty())) rows.add(new Row(rowLine, List.copyOf(cells)));
    return rows;
  }
}
