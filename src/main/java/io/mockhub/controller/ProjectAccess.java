package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;

final class ProjectAccess {
  private final ProjectService projects;

  ProjectAccess(ProjectService projects) {
    this.projects = projects;
  }

  String readable(Principal principal, String projectId) {
    return visible(principal, projectId).id();
  }

  String editable(Principal principal, String projectId) {
    Project project = visible(principal, projectId);
    if (!principal.canInTeam(project.orgId(), project.teamId(), Role.EDITOR)) {
      throw new ApiException(403, "You need the editor role in " + project.name());
    }
    return project.id();
  }

  private Project visible(Principal principal, String projectId) {
    Project project = projects.find(projectId).orElseThrow(() -> ApiException.notFound("Project " + projectId));
    if (!principal.allowsProject(project.id()) || !principal.canInTeam(project.orgId(), project.teamId(), Role.VIEWER)) {
      throw ApiException.notFound("Project " + projectId);
    }
    return project;
  }
}
