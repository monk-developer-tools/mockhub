package io.mockhub.audit;

import io.mockhub.org.OrganizationService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.user.UserService;
import java.util.Optional;
import java.util.function.Supplier;

public final class ServiceAuditNames implements AuditNames {
  private final ProjectService projects;
  private final StubService stubs;
  private final TeamService teams;
  private final OrganizationService orgs;
  private final UserService users;

  public ServiceAuditNames(ProjectService projects, StubService stubs, TeamService teams, OrganizationService orgs, UserService users) {
    this.projects = projects;
    this.stubs = stubs;
    this.teams = teams;
    this.orgs = orgs;
    this.users = users;
  }

  @Override
  public Optional<Project> project(String id) {
    return id == null ? Optional.empty() : projects.find(id);
  }

  @Override
  public Optional<String> stub(String projectId, String stubId) {
    return quietly(() -> AuditRecorder.stubLabel(stubs.get(projectId, stubId))).filter(s -> !s.isBlank());
  }

  @Override
  public Optional<String> team(String id) {
    return id == null ? Optional.empty() : teams.find(id).map(Team::name);
  }

  @Override
  public Optional<String> org(String id) {
    return quietly(() -> orgs.get(id).name());
  }

  @Override
  public Optional<String> user(String id) {
    return id == null ? Optional.empty() : users.find(id).map(u -> u.displayName() == null || u.displayName().isBlank() ? u.username() : u.displayName());
  }

  private static Optional<String> quietly(Supplier<String> lookup) {
    try {
      return Optional.ofNullable(lookup.get());
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
