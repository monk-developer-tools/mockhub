package io.mockhub.data;

import java.util.Optional;

public interface RecordPolicy {
  RecordPolicy NONE = (projectId, recordsInProject, recordBytes) -> Optional.empty();

  Optional<String> refuse(String projectId, int recordsInProject, int recordBytes);
}
