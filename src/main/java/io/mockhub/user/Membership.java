package io.mockhub.user;

import io.mockhub.auth.Role;

public record Membership(String orgId, Role role) {}
