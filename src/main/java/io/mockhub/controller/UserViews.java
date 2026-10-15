package io.mockhub.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.user.Membership;
import io.mockhub.user.User;

public final class UserViews {
  private final OrganizationService orgs;

  public UserViews(OrganizationService orgs) {
    this.orgs = orgs;
  }

  public ObjectNode summary(User u) {
    ObjectNode node = Jsons.object();
    node.put("id", u.id());
    node.put("username", u.username());
    node.put("displayName", u.displayName());
    node.put("email", u.email() == null ? "" : u.email());
    node.put("source", u.source().id());
    return node;
  }

  public ObjectNode full(User u) {
    ObjectNode node = summary(u);
    node.put("serverRole", u.serverRole().id());
    node.put("groupServerRole", u.groupServerRole().id());
    node.put("effectiveServerRole", u.effectiveServerRole().id());
    node.put("disabled", u.disabled());
    node.put("hasPassword", u.canSignInWithPassword() || (u.passwordHash() != null && !u.passwordHash().isEmpty()));
    if (u.createdAt() != null) node.put("createdAt", u.createdAt());
    if (u.lastLoginAt() != null) node.put("lastLoginAt", u.lastLoginAt());
    node.set("memberships", memberships(u.memberships()));
    node.set("groupMemberships", memberships(u.groupMemberships()));
    return node;
  }

  public ObjectNode me(Principal p, boolean authEnabled) {
    ObjectNode node = full(p.user());
    node.put("anonymous", p.anonymous());
    node.put("isAdmin", p.isAdmin());
    node.put("authEnabled", authEnabled);
    ArrayNode list = node.putArray("orgs");
    for (Organization o : orgs.findAll()) {
      Role role = p.roleIn(o.id());
      if (!role.atLeast(Role.VIEWER)) continue;
      list.add(Jsons.object().put("id", o.id()).put("name", o.name()).put("slug", o.slug()).put("role", role.id()));
    }
    return node;
  }

  private ArrayNode memberships(Iterable<Membership> list) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    for (Membership m : list) {
      ObjectNode n = out.addObject().put("orgId", m.orgId()).put("role", m.role().id());
      orgs.findAll().stream().filter(o -> o.id().equals(m.orgId())).findFirst().ifPresent(o -> n.put("orgName", o.name()));
    }
    return out;
  }
}
