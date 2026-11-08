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
  static final Comparator<Path> OLDEST_FIRST = Comparator.comparingLong((Path p) -> hourOf(p)).thenComparingInt(p -> partOf(p));

  private final Path root;
  private final String kind;
  private final Class<T> type;
  private final long segmentBytes;
  private final java.util.Map<String, Integer> parts = new java.util.HashMap<>();

  NdjsonSegments(Path root, String kind, Class<T> type, long segmentBytes) {
    this.root = root;
    this.kind = kind;
    this.type = type;
    this.segmentBytes = segmentBytes;
  }

  void append(String projectId, Collection<Line> lines) {
    if (lines.isEmpty()) return;
    Path dir = dir(projectId);
    try {
      Files.createDirectories(dir);
      var byHour = new java.util.LinkedHashMap<String, List<String>>();
      for (Line line : lines) byHour.computeIfAbsent(HOUR.format(Instant.ofEpochMilli(line.at())), k -> new ArrayList<>()).add(line.json());
      for (var e : byHour.entrySet()) write(dir, e.getKey(), e.getValue());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void write(Path dir, String hour, List<String> jsons) throws IOException {
    Path file = current(dir, hour);
    long size = Files.exists(file) ? Files.size(file) : 0;
    StringBuilder chunk = new StringBuilder();
    for (String json : jsons) {
      if (size > 0 && size + json.length() + 1 > segmentBytes) {
        if (chunk.length() > 0) append(file, chunk);
        chunk.setLength(0);
        file = next(dir, hour);
        size = 0;
      }
      chunk.append(json).append('\n');
      size += json.length() + 1;
    }
    if (chunk.length() > 0) append(file, chunk);
  }

  private static void append(Path file, CharSequence text) throws IOException {
    Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private Path next(Path dir, String hour) {
    String key = dir.getFileName() + "/" + hour;
    int part = parts.getOrDefault(key, 0) + 1;
    parts.put(key, part);
    return dir.resolve(fileName(hour, part));
  }

  private Path current(Path dir, String hour) throws IOException {
    String key = dir.getFileName() + "/" + hour;
    Integer part = parts.get(key);
    if (part == null) {
      part = 0;
      for (Path f : files(dir)) if (HOUR.format(Instant.ofEpochMilli(hourOf(f))).equals(hour)) part = Math.max(part, partOf(f));
    }
    Path file = dir.resolve(fileName(hour, part));
    if (Files.exists(file) && Files.size(file) >= segmentBytes) {
      part++;
      file = dir.resolve(fileName(hour, part));
    }
    if (parts.size() > 10_000) parts.clear();
    parts.put(key, part);
    return file;
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
    return files(dir(projectId)).stream().sorted(OLDEST_FIRST.reversed()).toList();
  }

  private List<Path> files(Path dir) {
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith("." + kind + ".ndjson")).toList();
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

  static int partOf(Path file) {
    String name = file.getFileName().toString();
    if (name.length() < 12 || name.charAt(10) != '-') return 0;
    int end = name.indexOf('.', 11);
    try {
      return Integer.parseInt(name.substring(11, end < 0 ? name.length() : end));
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private String fileName(String hour, int part) {
    return hour + (part == 0 ? "" : String.format(java.util.Locale.ROOT, "-%03d", part)) + "." + kind + ".ndjson";
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
