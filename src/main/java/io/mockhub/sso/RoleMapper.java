package io.mockhub.sso;

import io.mockhub.auth.Role;
import io.mockhub.user.Membership;
import io.mockhub.user.TeamMembership;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RoleMapper {
  private final Predicate<String> orgExists;
  private final Function<String, Optional<String>> orgOfTeam;

  public RoleMapper(Predicate<String> orgExists) {
    this(orgExists, teamId -> Optional.empty());
  }

  public RoleMapper(Predicate<String> orgExists, Function<String, Optional<String>> orgOfTeam) {
    this.orgExists = orgExists;
    this.orgOfTeam = orgOfTeam;
  }

  public record Result(Role serverRole, List<Membership> memberships, List<TeamMembership> teamMemberships) {}

  public Result map(SsoSettings settings, ExternalIdentity identity) {
    Role serverRole = settings.defaultServerRole();
    Map<String, Role> byOrg = new LinkedHashMap<>();
    Map<String, Role> byTeam = new LinkedHashMap<>();
    if (!settings.defaultOrgId().isBlank() && Role.ORG_ROLES.contains(settings.defaultOrgRole())) {
      byOrg.put(settings.defaultOrgId(), settings.defaultOrgRole());
    }
    for (RoleMapping mapping : settings.roleMappings()) {
      if (!matches(mapping.claim(), identity)) continue;
      if (mapping.serverWide()) serverRole = Role.max(serverRole, mapping.role());
      else if (mapping.teamScoped()) byTeam.merge(mapping.teamId(), mapping.role(), Role::max);
      else byOrg.merge(mapping.orgId(), mapping.role(), Role::max);
    }
    List<Membership> memberships = new ArrayList<>();
    byOrg.forEach((orgId, role) -> {
      if (orgExists.test(orgId)) memberships.add(new Membership(orgId, role));
    });
    List<TeamMembership> teamMemberships = new ArrayList<>();
    byTeam.forEach((teamId, role) -> orgOfTeam.apply(teamId).ifPresent(orgId -> teamMemberships.add(new TeamMembership(teamId, orgId, role))));
    return new Result(serverRole, memberships, teamMemberships);
  }

  public static boolean domainAllowed(SsoSettings settings, ExternalIdentity identity) {
    if (settings.allowedDomains().isEmpty()) return true;
    String address = identity.email().isBlank() ? identity.username() : identity.email();
    int at = address.lastIndexOf('@');
    if (at < 0) return false;
    String domain = address.substring(at + 1);
    return settings.allowedDomains().stream().anyMatch(d -> d.trim().equalsIgnoreCase(domain));
  }

  private static boolean matches(String claim, ExternalIdentity identity) {
    if (claim == null || claim.isBlank()) return false;
    return identity.groups().stream().anyMatch(g -> g.equalsIgnoreCase(claim.trim()))
        || identity.roles().stream().anyMatch(r -> r.equalsIgnoreCase(claim.trim()));
  }
}
