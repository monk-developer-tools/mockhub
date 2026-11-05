package io.mockhub.grpc;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class MemoryDescriptorStore implements DescriptorStore {
  final Map<String, Map<String, byte[]>> files = new TreeMap<>();

  @Override
  public Map<String, Map<String, byte[]>> loadAll() {
    return files;
  }

  @Override
  public void save(String projectId, String name, byte[] content) {
    files.computeIfAbsent(projectId, k -> new TreeMap<>()).put(name, content);
  }

  @Override
  public boolean delete(String projectId, String name) {
    Map<String, byte[]> project = files.get(projectId);
    return project != null && project.remove(name) != null;
  }

  @Override
  public void deleteProject(String projectId) {
    files.remove(projectId);
  }

  @Override
  public List<String> names(String projectId) {
    return List.copyOf(files.getOrDefault(projectId, Map.of()).keySet());
  }
}
