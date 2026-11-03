package io.mockhub.token;

import io.mockhub.auth.Role;
import io.mockhub.auth.TokenGrant;
import java.util.List;

public record AccessToken(
    String id,
    String userId,
    String name,
    String hash,
    String prefix,
    Role role,
    List<String> projectIds,
    String mode,
    String createdAt,
    String expiresAt,
    String lastUsedAt) {

  public static final String DIRECT = "direct";
  public static final String REVIEW = "review";

  public AccessToken {
    projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
    mode = REVIEW.equals(mode) ? REVIEW : DIRECT;
  }

  public TokenGrant grant() {
    return new TokenGrant(id, name, role, projectIds, REVIEW.equals(mode));
  }

  public AccessToken usedAt(String at) {
    return new AccessToken(id, userId, name, hash, prefix, role, projectIds, mode, createdAt, expiresAt, at);
  }

  public AccessToken withoutProject(String projectId) {
    return new AccessToken(id, userId, name, hash, prefix, role,
        projectIds.stream().filter(p -> !p.equals(projectId)).toList(), mode, createdAt, expiresAt, lastUsedAt);
  }
}
