package io.mockhub.history;

import io.mockhub.api.Jsons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class NdjsonSegments<T> {
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final long HOUR_MS = 3_600_000L;

  private final Path root;
  private final String kind;
  private final Class<T> type;

  NdjsonSegments(Path root, String kind, Class<T> type) {
    this.root = root;
    this.kind = kind;
    this.type = type;
  }

  void append(String projectId, Collection<Line> lines) {
    if (lines.isEmpty()) return;
    Path dir = dir(projectId);
    try {
      Files.createDirectories(dir);
      var byFile = new java.util.LinkedHashMap<Path, StringBuilder>();
      for (Line line : lines) byFile.computeIfAbsent(dir.resolve(fileName(line.at())), k -> new StringBuilder()).append(line.json()).append('\n');
      for (var e : byFile.entrySet()) {
        Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  record Line(long at, String json) {}

  record Scan(int scanned, boolean truncated) {}

  Scan scanNewestFirst(String projectId, long from, long to, int maxScan, Predicate<T> visitor) {
    int scanned = 0;
    for (Path file : files(projectId)) {
      long hour = hourOf(file);
      if (hour > to || hour + HOUR_MS <= from) continue;
      List<String> lines = readLines(file);
      for (int i = lines.size() - 1; i >= 0; i--) {
        if (scanned >= maxScan) return new Scan(scanned, true);
        String line = lines.get(i);
        if (line.isBlank()) continue;
        T item = parse(line);
        if (item == null) continue;
        scanned++;
        if (!visitor.test(item)) return new Scan(scanned, false);
      }
    }
    return new Scan(scanned, false);
  }

  void delete(String projectId) {
    for (Path file : files(projectId)) deleteQuietly(file);
  }

  List<Path> files(String projectId) {
    Path dir = dir(projectId);
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith("." + kind + ".ndjson"))
          .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  static long hourOf(Path file) {
    String name = file.getFileName().toString();
    try {
      return java.time.LocalDateTime.of(Integer.parseInt(name.substring(0, 4)), Integer.parseInt(name.substring(4, 6)),
          Integer.parseInt(name.substring(6, 8)), Integer.parseInt(name.substring(8, 10)), 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private String fileName(long at) {
    return HOUR.format(Instant.ofEpochMilli(at)) + "." + kind + ".ndjson";
  }

  Path dir(String projectId) {
    if (projectId == null || !SAFE_ID.matcher(projectId).matches()) throw new IllegalArgumentException("Bad project id");
    return root.resolve(projectId);
  }

  private T parse(String line) {
    try {
      return Jsons.MAPPER.readValue(line, type);
    } catch (IOException e) {
      return null;
    }
  }

  private static List<String> readLines(Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return new ArrayList<>();
    }
  }

  static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
    }
  }
}
