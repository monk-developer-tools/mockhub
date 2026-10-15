package io.mockhub.sso;

import io.mockhub.auth.Role;

public record RoleMapping(String claim, String orgId, Role role) {
  public boolean serverWide() {
    return orgId == null || orgId.isBlank();
  }
}
