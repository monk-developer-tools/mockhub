package io.mockhub.org;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {
  List<Organization> findAll();

  Optional<Organization> findById(String id);

  Optional<Organization> findBySlug(String slug);

  void save(Organization organization);

  void deleteById(String id);
}
