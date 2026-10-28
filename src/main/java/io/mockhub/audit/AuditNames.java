package io.mockhub.audit;

import io.mockhub.project.Project;
import java.util.Optional;

public interface AuditNames {
  Optional<Project> project(String id);

  Optional<String> stub(String projectId, String stubId);

  Optional<String> team(String id);

  Optional<String> org(String id);

  Optional<String> user(String id);
}
