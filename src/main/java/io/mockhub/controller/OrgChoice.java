package io.mockhub.controller;

import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;

public final class OrgChoice {
  private final OrganizationService orgs;

  public OrgChoice(OrganizationService orgs) {
    this.orgs = orgs;
  }

  public String writable(Principal principal, String requested) {
    if (requested != null && !requested.isBlank()) {
      Organization org = orgs.get(requested);
      if (!principal.can(org.id(), Role.EDITOR)) {
        throw new ApiException(403, "You need the editor role in '" + org.name() + "' to add projects there");
      }
      return org.id();
    }
    return orgs.findAll().stream()
        .filter(o -> principal.can(o.id(), Role.EDITOR))
        .map(Organization::id)
        .findFirst()
        .orElseThrow(() -> new ApiException(403, "You need the editor role in an organisation to create projects"));
  }
}
