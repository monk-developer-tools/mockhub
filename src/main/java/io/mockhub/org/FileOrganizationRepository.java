package io.mockhub.org;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mockhub.store.JsonFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FileOrganizationRepository implements OrganizationRepository {
  private final JsonFile<List<Organization>> file;
  private final List<Organization> organizations;

  public FileOrganizationRepository(Path rootDir) {
    this.file = new JsonFile<>(JsonFile.inDataDir(rootDir, "organizations.json"), new TypeReference<>() {});
    this.organizations = new ArrayList<>(file.read(List::of));
  }

  @Override
  public synchronized List<Organization> findAll() {
    List<Organization> copy = new ArrayList<>(organizations);
    copy.sort(Comparator.comparing(o -> o.name().toLowerCase()));
    return copy;
  }

  @Override
  public synchronized Optional<Organization> findById(String id) {
    return organizations.stream().filter(o -> o.id().equals(id)).findFirst();
  }

  @Override
  public synchronized Optional<Organization> findBySlug(String slug) {
    return organizations.stream().filter(o -> o.slug().equalsIgnoreCase(slug)).findFirst();
  }

  @Override
  public synchronized void save(Organization organization) {
    organizations.removeIf(o -> o.id().equals(organization.id()));
    organizations.add(organization);
    file.write(organizations);
  }

  @Override
  public synchronized void deleteById(String id) {
    organizations.removeIf(o -> o.id().equals(id));
    file.write(organizations);
  }
}
