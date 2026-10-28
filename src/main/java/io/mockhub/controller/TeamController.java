package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.ProjectService;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.user.User;
import io.mockhub.user.UserService;

public final class TeamController implements ApiController {
  private final OrganizationService orgs;
  private final TeamService teams;
  private final ProjectService projects;
  private final UserService users;
  private final UserViews userViews;

  public TeamController(OrganizationService orgs, TeamService teams, ProjectService projects, UserService users, UserViews userViews) {
    this.orgs = orgs;
    this.teams = teams;
    this.projects = projects;
    this.users = users;
    this.userViews = userViews;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/orgs/{id}/teams", ctx -> {
      String orgId = orgs.get(ctx.path("id")).id();
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      teams.findByOrg(orgId).forEach(t -> out.add(view(ctx, t)));
      return out;
    });
    r.post("/orgs/{id}/teams", ctx -> Response.json(201, view(ctx, teams.create(orgs.get(ctx.path("id")).id(), ctx.json()))));
    r.get("/orgs/{id}/teams/{teamId}", ctx -> view(ctx, team(ctx)));
    r.put("/orgs/{id}/teams/{teamId}", ctx -> view(ctx, teams.update(team(ctx).id(), ctx.json())));
    r.delete("/orgs/{id}/teams/{teamId}", ctx -> {
      Team team = team(ctx);
      int count = projects.findByTeam(team.id()).size();
      if (count > 0) {
        throw new ApiException(409, "Team '" + team.name() + "' still has " + count + (count == 1 ? " project" : " projects")
            + ". Move them to another team or delete them first.");
      }
      teams.delete(team.id());
      return null;
    });

    r.get("/orgs/{id}/teams/{teamId}/members", ctx -> {
      Team team = team(ctx);
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      for (User u : users.findAll()) {
        boolean member = u.inTeam(team.id());
        Role orgWide = u.roleIn(team.orgId());
        if (!member && !orgWide.atLeast(Role.VIEWER)) continue;
        ObjectNode n = userViews.summary(u);
        n.put("role", u.teamRole(team.id()).map(Role::id).orElse(""));
        n.put("groupRole", u.groupTeamRole(team.id()).map(Role::id).orElse(""));
        n.put("orgRole", orgWide.atLeast(Role.VIEWER) ? orgWide.id() : "");
        n.put("effectiveRole", u.roleInTeam(team.orgId(), team.id()).id());
        n.put("member", member);
        n.put("disabled", u.disabled());
        out.add(n);
      }
      return out;
    });
    r.put("/orgs/{id}/teams/{teamId}/members/{userId}", ctx -> {
      Team team = team(ctx);
      Role role = Role.teamRole(ctx.json().path("role").asText());
      guardSelfDemotion(ctx, team, role);
      return userViews.full(users.setTeamMembership(ctx.path("userId"), team.id(), team.orgId(), role));
    });
    r.delete("/orgs/{id}/teams/{teamId}/members/{userId}", ctx -> {
      Team team = team(ctx);
      guardSelfDemotion(ctx, team, Role.NONE);
      users.removeTeamMembership(ctx.path("userId"), team.id());
      return null;
    });
  }

  private Team team(Ctx ctx) {
    return teams.getInOrg(orgs.get(ctx.path("id")).id(), ctx.path("teamId"));
  }

  private void guardSelfDemotion(Ctx ctx, Team team, Role newRole) {
    Principal p = ctx.principal();
    if (p.isAdmin() || !p.id().equals(ctx.path("userId")) || newRole.atLeast(Role.MANAGER)) return;
    if (p.roleIn(team.orgId()).atLeast(Role.MANAGER)) return;
    throw ApiException.badRequest("You can't remove your own owner role in this team. Ask another owner.");
  }

  private ObjectNode view(Ctx ctx, Team t) {
    ObjectNode node = Jsons.MAPPER.valueToTree(t);
    node.put("projectCount", projects.findByTeam(t.id()).size());
    node.put("memberCount", users.teamMembersOf(t.id()).size());
    node.put("role", ctx.principal().roleInTeam(t.orgId(), t.id()).id());
    return node;
  }
}
