package io.mockhub.grpc;

import io.mockhub.store.JsonFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class FileDescriptorStore implements DescriptorStore {
  private final Path dir;

  public FileDescriptorStore(Path rootDir) {
    this.dir = JsonFile.inDataDir(rootDir, "grpc");
  }

  @Override
  public synchronized Map<String, Map<String, byte[]>> loadAll() {
    Map<String, Map<String, byte[]>> out = new TreeMap<>();
    if (!Files.isDirectory(dir)) return out;
    try (Stream<Path> projects = Files.list(dir)) {
      for (Path project : projects.filter(Files::isDirectory).toList()) {
        Map<String, byte[]> files = new TreeMap<>();
        for (String name : names(project)) files.put(name, Files.readAllBytes(project.resolve(name)));
        if (!files.isEmpty()) out.put(project.getFileName().toString(), files);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  @Override
  public synchronized void save(String projectId, String name, byte[] content) {
    try {
      Path project = dir.resolve(DescriptorNames.safe(projectId));
      Files.createDirectories(project);
      Path tmp = project.resolve(name + ".tmp");
      Files.write(tmp, content);
      Files.move(tmp, project.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized boolean delete(String projectId, String name) {
    try {
      return Files.deleteIfExists(dir.resolve(DescriptorNames.safe(projectId)).resolve(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized void deleteProject(String projectId) {
    Path project = dir.resolve(DescriptorNames.safe(projectId));
    if (!Files.isDirectory(project)) return;
    try (Stream<Path> files = Files.walk(project)) {
      for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public synchronized List<String> names(String projectId) {
    Path project = dir.resolve(DescriptorNames.safe(projectId));
    return Files.isDirectory(project) ? names(project) : List.of();
  }

  private static List<String> names(Path project) {
    try (Stream<Path> files = Files.list(project)) {
      return files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(DescriptorNames::isValid).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
