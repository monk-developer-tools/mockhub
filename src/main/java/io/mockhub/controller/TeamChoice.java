package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import java.util.List;
import java.util.Optional;

public final class TeamChoice {
  private final OrganizationService orgs;
  private final TeamService teams;

  public TeamChoice(OrganizationService orgs, TeamService teams) {
    this.orgs = orgs;
    this.teams = teams;
  }

  public Team writable(Principal principal, String orgId, String teamId) {
    if (teamId != null && !teamId.isBlank()) {
      Team team = teams.get(teamId);
      if (orgId != null && !orgId.isBlank() && !team.orgId().equals(orgId)) {
        throw ApiException.badRequest("Team '" + team.name() + "' is not in that organisation");
      }
      if (!principal.canInTeam(team.orgId(), team.id(), Role.MANAGER)) {
        throw new ApiException(403, "You need to be a manager of team '" + team.name() + "' to add projects there");
      }
      return team;
    }
    List<Organization> candidates = orgId != null && !orgId.isBlank() ? List.of(orgs.get(orgId)) : orgs.findAll();
    for (Organization org : candidates) {
      Optional<Team> team = writableIn(principal, org.id());
      if (team.isPresent()) return team.get();
    }
    throw new ApiException(403, "You need to be a team manager to create projects");
  }

  private Optional<Team> writableIn(Principal principal, String orgId) {
    if (principal.can(orgId, Role.MANAGER)) return Optional.of(teams.ensureDefault(orgId));
    return teams.findByOrg(orgId).stream().filter(t -> principal.canInTeam(orgId, t.id(), Role.MANAGER)).findFirst();
  }
}
