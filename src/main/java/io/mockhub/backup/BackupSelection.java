package io.mockhub.backup;

import io.mockhub.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public record BackupSelection(boolean everything, Set<String> organizationIds, Set<String> projectIds) {

  public static BackupSelection all() {
    return new BackupSelection(true, Set.of(), Set.of());
  }

  public static BackupSelection of(Collection<String> organizationIds, Collection<String> projectIds) {
    return new BackupSelection(false, Set.copyOf(organizationIds), Set.copyOf(projectIds));
  }

  public boolean isEmpty() {
    return !everything && organizationIds.isEmpty() && projectIds.isEmpty();
  }

  public boolean includes(Project project) {
    return everything || projectIds.contains(project.id()) || organizationIds.contains(project.orgId());
  }

  public List<Project> pick(List<Project> projects) {
    return projects.stream().filter(this::includes).toList();
  }
}
