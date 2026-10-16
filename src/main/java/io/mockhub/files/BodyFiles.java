package io.mockhub.files;

import io.mockhub.api.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BodyFiles {
  public static final long MAX_BYTES = 10L * 1024 * 1024;
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,120}");
  private static final Pattern PROJECT = Pattern.compile("[A-Za-z0-9_-]+");

  private final Path filesRoot;

  public BodyFiles(Path dataDir) {
    this.filesRoot = dataDir.resolve("__files");
  }

  public record Info(String name, long size, String contentType, String modified, String path) {}

  public static String bodyFileName(String projectId, String name) {
    return "projects/" + projectId + "/" + name;
  }

  public List<Info> list(String projectId) {
    Path dir = dir(projectId);
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> files = Files.list(dir)) {
      List<Info> out = new ArrayList<>();
      files.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).forEach(p -> out.add(info(projectId, p)));
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Info save(String projectId, String name, byte[] content) {
    checkName(name);
    if (content == null || content.length == 0) throw ApiException.badRequest("The file is empty");
    if (content.length > MAX_BYTES) throw new ApiException(413, "Files can be at most 10 MB");
    Path file = dir(projectId).resolve(name);
    try {
      Files.createDirectories(file.getParent());
      Files.write(file, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return info(projectId, file);
  }

  public byte[] read(String projectId, String name) {
    checkName(name);
    Path file = dir(projectId).resolve(name);
    if (!Files.isRegularFile(file)) throw ApiException.notFound("File " + name);
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public boolean delete(String projectId, String name) {
    checkName(name);
    try {
      return Files.deleteIfExists(dir(projectId).resolve(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void deleteProject(String projectId) {
    Path dir = dir(projectId);
    if (!Files.isDirectory(dir)) return;
    try (Stream<Path> files = Files.walk(dir)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Map<String, byte[]> snapshot(String projectId) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (Info f : list(projectId)) out.put(f.name(), read(projectId, f.name()));
    return out;
  }

  public String contentType(String name) {
    String guessed = URLConnection.guessContentTypeFromName(name);
    if (guessed != null) return guessed;
    String lower = name.toLowerCase();
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".svg")) return "image/svg+xml";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".csv")) return "text/csv";
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/yaml";
    return "application/octet-stream";
  }

  private Info info(String projectId, Path file) {
    try {
      String name = file.getFileName().toString();
      return new Info(name, Files.size(file), contentType(name), Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString(),
          bodyFileName(projectId, name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path dir(String projectId) {
    if (!PROJECT.matcher(projectId).matches()) throw ApiException.badRequest("Bad project id");
    return filesRoot.resolve("projects").resolve(projectId);
  }

  private static void checkName(String name) {
    if (name == null || !NAME.matcher(name).matches()) {
      throw ApiException.badRequest("File names can use letters, digits, dots, dashes and underscores, e.g. invoice-42.pdf");
    }
  }
}
