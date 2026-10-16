package io.mockhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.Ctx;
import io.mockhub.api.Jsons;
import io.mockhub.api.Response;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.org.OrganizationService;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.user.User;
import io.mockhub.user.UserService;

public final class UserController implements ApiController {
  private final UserService users;
  private final UserViews views;
  private final AuthService auth;
  private final OrganizationService orgs;
  private final TeamService teams;

  public UserController(UserService users, UserViews views, AuthService auth, OrganizationService orgs, TeamService teams) {
    this.users = users;
    this.views = views;
    this.auth = auth;
    this.orgs = orgs;
    this.teams = teams;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/users", ctx -> {
      boolean admin = ctx.principal().isAdmin();
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      users.findAll().forEach(u -> out.add(admin ? views.full(u) : views.summary(u)));
      return out;
    });
    r.post("/users", ctx -> {
      boolean firstUser = !auth.enabled();
      JsonNode body = resolveAccess(ctx.json());
      if (firstUser) ((ObjectNode) body).put("serverRole", Role.ADMIN.id());
      User created = users.create(body);
      ObjectNode out = views.full(created);
      if (firstUser) out.put("token", auth.issueToken(created));
      return Response.json(201, out);
    });
    r.get("/users/{id}", ctx -> views.full(users.get(ctx.path("id"))));
    r.put("/users/{id}", ctx -> {
      JsonNode body = resolveAccess(ctx.json());
      if (isSelf(ctx)) {
        if (body.path("disabled").asBoolean(false)) throw ApiException.badRequest("You can't disable your own account");
        if (body.hasNonNull("serverRole") && Role.serverRole(body.get("serverRole").asText()) != Role.ADMIN
            && ctx.principal().user().groupServerRole() != Role.ADMIN) {
          throw ApiException.badRequest("You can't remove your own admin role");
        }
      }
      return views.full(users.update(ctx.path("id"), body));
    });
    r.post("/users/{id}/password", ctx -> views.full(users.setPassword(ctx.path("id"), ctx.json().path("password").asText(""))));
    r.delete("/users/{id}", ctx -> {
      if (isSelf(ctx)) throw ApiException.badRequest("You can't delete your own account");
      users.delete(ctx.path("id"));
      return null;
    });
  }

  private JsonNode resolveAccess(JsonNode body) {
    for (JsonNode m : body.path("memberships")) {
      orgs.get(m.path("orgId").asText(""));
    }
    for (JsonNode m : body.path("teamMemberships")) {
      Team team = teams.get(m.path("teamId").asText(""));
      ((ObjectNode) m).put("orgId", team.orgId());
    }
    return body;
  }

  private static boolean isSelf(Ctx ctx) {
    Principal p = ctx.principal();
    return !p.anonymous() && p.id().equals(ctx.path("id"));
  }
}
