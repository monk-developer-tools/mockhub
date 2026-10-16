package io.mockhub.sso;

import io.mockhub.auth.Role;

public record RoleMapping(String claim, String orgId, Role role, String teamId) {
  public RoleMapping {
    teamId = teamId == null ? "" : teamId;
  }

  public RoleMapping(String claim, String orgId, Role role) {
    this(claim, orgId, role, "");
  }

  public boolean serverWide() {
    return orgId == null || orgId.isBlank();
  }

  public boolean teamScoped() {
    return !teamId.isBlank();
  }
}
