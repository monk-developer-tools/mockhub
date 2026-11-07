package io.mockhub.org;

import java.util.List;
import java.util.Set;

public record Organization(String id, String name, String slug, String description, String createdAt, String updatedAt, List<String> maskedHeaders) {
  public static final String INSTANT_ID = "instant-mocks";
  public static final Set<String> PRIVATE_IDS = Set.of(INSTANT_ID);

  public Organization {
    maskedHeaders = maskedHeaders == null ? List.of() : List.copyOf(maskedHeaders);
  }

  public Organization(String id, String name, String slug, String description, String createdAt, String updatedAt) {
    this(id, name, slug, description, createdAt, updatedAt, List.of());
  }

  public static boolean isPrivate(String orgId) {
    return PRIVATE_IDS.contains(orgId);
  }
}
