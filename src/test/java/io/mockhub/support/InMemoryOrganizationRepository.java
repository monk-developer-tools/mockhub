package io.mockhub.support;

import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryOrganizationRepository implements OrganizationRepository {
  private final Map<String, Organization> orgs = new LinkedHashMap<>();

  @Override
  public List<Organization> findAll() {
    List<Organization> all = new ArrayList<>(orgs.values());
    all.sort(Comparator.comparing(Organization::name));
    return all;
  }

  @Override
  public Optional<Organization> findById(String id) {
    return Optional.ofNullable(orgs.get(id));
  }

  @Override
  public Optional<Organization> findBySlug(String slug) {
    return orgs.values().stream().filter(o -> o.slug().equals(slug)).findFirst();
  }

  @Override
  public void save(Organization organization) {
    orgs.put(organization.id(), organization);
  }

  @Override
  public void deleteById(String id) {
    orgs.remove(id);
  }
}
