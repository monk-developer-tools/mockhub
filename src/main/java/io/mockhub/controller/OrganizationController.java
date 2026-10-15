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
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.ProjectService;
import io.mockhub.user.User;
import io.mockhub.user.UserService;

public final class OrganizationController implements ApiController {
  private final OrganizationService orgs;
  private final ProjectService projects;
  private final UserService users;
  private final UserViews userViews;

  public OrganizationController(OrganizationService orgs, ProjectService projects, UserService users, UserViews userViews) {
    this.orgs = orgs;
    this.projects = projects;
    this.users = users;
    this.userViews = userViews;
  }

  @Override
  public void register(ApiRouter r) {
    r.get("/orgs", ctx -> {
      Principal p = ctx.principal();
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      orgs.findAll().stream().filter(o -> p.can(o.id(), Role.VIEWER)).forEach(o -> out.add(view(ctx, o)));
      return out;
    });
    r.post("/orgs", ctx -> Response.json(201, view(ctx, orgs.create(ctx.json()))));
    r.get("/orgs/{id}", ctx -> view(ctx, orgs.get(ctx.path("id"))));
    r.put("/orgs/{id}", ctx -> view(ctx, orgs.update(ctx.path("id"), ctx.json())));
    r.delete("/orgs/{id}", ctx -> {
      orgs.delete(ctx.path("id"));
      return null;
    });

    r.get("/orgs/{id}/members", ctx -> {
      String orgId = orgs.get(ctx.path("id")).id();
      ArrayNode out = Jsons.MAPPER.createArrayNode();
      for (User u : users.membersOf(orgId)) {
        ObjectNode n = userViews.summary(u);
        n.put("role", u.memberRole(orgId).map(Role::id).orElse(""));
        n.put("groupRole", u.groupMemberships().stream().filter(m -> m.orgId().equals(orgId)).map(m -> m.role().id()).findFirst().orElse(""));
        n.put("effectiveRole", u.roleIn(orgId).id());
        n.put("disabled", u.disabled());
        out.add(n);
      }
      return out;
    });
    r.put("/orgs/{id}/members/{userId}", ctx -> {
      String orgId = orgs.get(ctx.path("id")).id();
      Role role = Role.orgRole(ctx.json().path("role").asText());
      guardSelfDemotion(ctx, orgId, role);
      return userViews.full(users.setMembership(ctx.path("userId"), orgId, role));
    });
    r.delete("/orgs/{id}/members/{userId}", ctx -> {
      String orgId = orgs.get(ctx.path("id")).id();
      guardSelfDemotion(ctx, orgId, Role.NONE);
      users.removeMembership(ctx.path("userId"), orgId);
      return null;
    });
  }

  private void guardSelfDemotion(Ctx ctx, String orgId, Role newRole) {
    Principal p = ctx.principal();
    if (p.isAdmin() || !p.id().equals(ctx.path("userId")) || newRole.atLeast(Role.OWNER)) return;
    throw ApiException.badRequest("You can't remove your own owner role. Ask another owner or an admin.");
  }

  private ObjectNode view(Ctx ctx, Organization o) {
    ObjectNode node = Jsons.MAPPER.valueToTree(o);
    node.put("projectCount", projects.findByOrg(o.id()).size());
    node.put("memberCount", users.membersOf(o.id()).size());
    node.put("role", ctx.principal().roleIn(o.id()).id());
    return node;
  }
}
