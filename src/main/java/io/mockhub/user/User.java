package io.mockhub.user;

import io.mockhub.auth.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record User(
    String id,
    String username,
    String displayName,
    String email,
    UserSource source,
    String externalId,
    String passwordHash,
    Role serverRole,
    List<Membership> memberships,
    Role groupServerRole,
    List<Membership> groupMemberships,
    boolean disabled,
    long sessionEpoch,
    String createdAt,
    String lastLoginAt) {

  public User {
    serverRole = serverRole == null ? Role.NONE : serverRole;
    groupServerRole = groupServerRole == null ? Role.NONE : groupServerRole;
    memberships = memberships == null ? List.of() : List.copyOf(memberships);
    groupMemberships = groupMemberships == null ? List.of() : List.copyOf(groupMemberships);
  }

  public Role effectiveServerRole() {
    return Role.max(serverRole, groupServerRole);
  }

  public Role roleIn(String orgId) {
    Role role = effectiveServerRole();
    for (Membership m : memberships) if (m.orgId().equals(orgId)) role = Role.max(role, m.role());
    for (Membership m : groupMemberships) if (m.orgId().equals(orgId)) role = Role.max(role, m.role());
    return role;
  }

  public Optional<Role> memberRole(String orgId) {
    return memberships.stream().filter(m -> m.orgId().equals(orgId)).map(Membership::role).findFirst();
  }

  public boolean canSignInWithPassword() {
    return !disabled && passwordHash != null && !passwordHash.isEmpty();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private UserSource source;
    private String externalId;
    private String passwordHash;
    private Role serverRole;
    private List<Membership> memberships;
    private Role groupServerRole;
    private List<Membership> groupMemberships;
    private boolean disabled;
    private long sessionEpoch;
    private String createdAt;
    private String lastLoginAt;

    public Builder() {
      memberships = new ArrayList<>();
      groupMemberships = new ArrayList<>();
    }

    private Builder(User u) {
      id = u.id;
      username = u.username;
      displayName = u.displayName;
      email = u.email;
      source = u.source;
      externalId = u.externalId;
      passwordHash = u.passwordHash;
      serverRole = u.serverRole;
      memberships = new ArrayList<>(u.memberships);
      groupServerRole = u.groupServerRole;
      groupMemberships = new ArrayList<>(u.groupMemberships);
      disabled = u.disabled;
      sessionEpoch = u.sessionEpoch;
      createdAt = u.createdAt;
      lastLoginAt = u.lastLoginAt;
    }

    public Builder id(String v) { id = v; return this; }
    public Builder username(String v) { username = v; return this; }
    public Builder displayName(String v) { displayName = v; return this; }
    public Builder email(String v) { email = v; return this; }
    public Builder source(UserSource v) { source = v; return this; }
    public Builder externalId(String v) { externalId = v; return this; }
    public Builder passwordHash(String v) { passwordHash = v; return this; }
    public Builder serverRole(Role v) { serverRole = v; return this; }
    public Builder memberships(List<Membership> v) { memberships = new ArrayList<>(v); return this; }
    public Builder groupServerRole(Role v) { groupServerRole = v; return this; }
    public Builder groupMemberships(List<Membership> v) { groupMemberships = new ArrayList<>(v); return this; }
    public Builder disabled(boolean v) { disabled = v; return this; }
    public Builder sessionEpoch(long v) { sessionEpoch = v; return this; }
    public Builder createdAt(String v) { createdAt = v; return this; }
    public Builder lastLoginAt(String v) { lastLoginAt = v; return this; }

    public User build() {
      return new User(id, username, displayName, email, source, externalId, passwordHash, serverRole, memberships,
          groupServerRole, groupMemberships, disabled, sessionEpoch, createdAt, lastLoginAt);
    }
  }
}
