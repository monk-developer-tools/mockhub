package io.mockhub.auth;

import io.mockhub.user.Membership;
import io.mockhub.user.TeamMembership;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;

public record Principal(User user, boolean anonymous) {

  public static Principal anonymousAdmin() {
    return new Principal(
        new User.Builder().id("anonymous").username("anonymous").displayName("Anonymous").source(UserSource.LOCAL)
            .serverRole(Role.ADMIN).build(),
        true);
  }

  public static Principal of(User user) {
    return new Principal(user, false);
  }

  public String id() {
    return user.id();
  }

  public String username() {
    return user.username();
  }

  public boolean isAdmin() {
    return user.effectiveServerRole() == Role.ADMIN;
  }

  public Role roleIn(String orgId) {
    return user.roleIn(orgId);
  }

  public Role roleInTeam(String orgId, String teamId) {
    return user.roleInTeam(orgId, teamId);
  }

  public boolean canInTeam(String orgId, String teamId, Role needed) {
    return roleInTeam(orgId, teamId).atLeast(needed);
  }

  public boolean belongsTo(String orgId) {
    return isAdmin() || user.belongsTo(orgId);
  }

  public boolean can(String orgId, Role needed) {
    return roleIn(orgId).atLeast(needed);
  }

  public Role highestRole() {
    Role role = user.effectiveServerRole();
    for (Membership m : user.memberships()) role = Role.max(role, m.role());
    for (Membership m : user.groupMemberships()) role = Role.max(role, m.role());
    for (TeamMembership m : user.teamMemberships()) role = Role.max(role, m.role());
    for (TeamMembership m : user.groupTeamMemberships()) role = Role.max(role, m.role());
    return role;
  }
}
