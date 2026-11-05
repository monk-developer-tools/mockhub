package io.mockhub.grpc;

import java.util.List;
import java.util.Map;

public interface DescriptorStore {
  Map<String, Map<String, byte[]>> loadAll();

  void save(String projectId, String name, byte[] content);

  boolean delete(String projectId, String name);

  void deleteProject(String projectId);

  List<String> names(String projectId);
}
