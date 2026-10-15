package io.mockhub.user;

import io.mockhub.auth.Role;
import java.util.List;

public record ExternalProfile(
    UserSource source,
    String externalId,
    String username,
    String displayName,
    String email,
    Role groupServerRole,
    List<Membership> groupMemberships) {}
