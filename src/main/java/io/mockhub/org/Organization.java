package io.mockhub.org;

import java.util.Set;

public record Organization(String id, String name, String slug, String description, String createdAt, String updatedAt) {
  public static final String INSTANT_ID = "instant-mocks";
  public static final Set<String> PRIVATE_IDS = Set.of(INSTANT_ID);

  public static boolean isPrivate(String orgId) {
    return PRIVATE_IDS.contains(orgId);
  }
}
