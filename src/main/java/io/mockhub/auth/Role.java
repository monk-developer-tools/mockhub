package io.mockhub.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.mockhub.api.ApiException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum Role {
  NONE,
  VIEWER,
  EDITOR,
  OWNER,
  ADMIN;

  public static final Set<Role> SERVER_ROLES = EnumSet.of(NONE, VIEWER, EDITOR, ADMIN);
  public static final Set<Role> ORG_ROLES = EnumSet.of(VIEWER, EDITOR, OWNER);

  public boolean atLeast(Role other) {
    return compareTo(other) >= 0;
  }

  public static Role max(Role a, Role b) {
    return a.atLeast(b) ? a : b;
  }

  @JsonValue
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static Role parse(String value) {
    if (value == null || value.isBlank()) return NONE;
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Unknown role '" + value + "'");
    }
  }

  public static Role serverRole(String value) {
    Role role = parse(value);
    if (!SERVER_ROLES.contains(role)) throw ApiException.badRequest("Server role must be none, viewer, editor or admin");
    return role;
  }

  public static Role orgRole(String value) {
    Role role = parse(value);
    if (!ORG_ROLES.contains(role)) throw ApiException.badRequest("Organisation role must be viewer, editor or owner");
    return role;
  }
}
