package io.mockhub.snapshot;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository {
  List<Snapshot.Summary> list(String projectId);

  Optional<Snapshot> find(String projectId, String id);

  void save(Snapshot snapshot);

  void delete(String projectId, String id);

  void deleteProject(String projectId);
}
