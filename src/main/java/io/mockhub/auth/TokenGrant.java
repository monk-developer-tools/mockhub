package io.mockhub.auth;

import java.util.List;

public record TokenGrant(String tokenId, String name, Role ceiling, List<String> projectIds, boolean review) {

  public TokenGrant {
    projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
  }

  public boolean allowsProject(String projectId) {
    return projectIds.isEmpty() || projectIds.contains(projectId);
  }

  public boolean allProjects() {
    return projectIds.isEmpty();
  }

  public Role cap(Role role) {
    return role.atLeast(ceiling) ? ceiling : role;
  }
}
