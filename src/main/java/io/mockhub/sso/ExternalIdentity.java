package io.mockhub.sso;

import java.util.List;

public record ExternalIdentity(
    String subject,
    String username,
    String email,
    String name,
    String tenantId,
    List<String> groups,
    List<String> roles,
    boolean groupsOverflowed) {}
