package io.mockhub.data;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DataStore {
  Map<String, Integer> resources(String projectId);

  List<ObjectNode> list(String projectId, String resource);

  Optional<ObjectNode> get(String projectId, String resource, String id);

  void put(String projectId, String resource, String id, ObjectNode record);

  boolean delete(String projectId, String resource, String id);

  void clear(String projectId, String resource);

  void clearProject(String projectId);

  Map<String, Map<String, ObjectNode>> snapshot(String projectId);

  void restore(String projectId, Map<String, Map<String, ObjectNode>> data);
}
