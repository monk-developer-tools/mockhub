package io.mockhub.user;

import io.mockhub.auth.Role;

public record TeamMembership(String teamId, String orgId, Role role) {}
