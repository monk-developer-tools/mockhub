package io.mockhub.project;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.common.Slugs;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationListener;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ProjectService implements OrganizationListener {
  private static final List<String> RESERVED_SLUGS = List.of("__admin", "__files", "mappings", "health");
  private static final String[] PALETTE = {"#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#14b8a6"};

  private final ProjectRepository repository;
  private final List<ProjectListener> listeners;
  private final Clock clock;

  public ProjectService(ProjectRepository repository, List<ProjectListener> listeners, Clock clock) {
    this.repository = repository;
    this.listeners = listeners;
    this.clock = clock;
  }

  public List<Project> findAll() {
    return repository.findAll();
  }

  public List<Project> findByOrg(String orgId) {
    return repository.findAll().stream().filter(p -> orgId.equals(p.orgId())).toList();
  }

  public int assignOrphans(String orgId) {
    List<Project> orphans = repository.findAll().stream().filter(p -> p.orgId() == null || p.orgId().isBlank()).toList();
    orphans.forEach(p -> repository.save(p.withOrgId(orgId)));
    return orphans.size();
  }

  public Optional<Project> find(String id) {
    return repository.findById(id);
  }

  public Project get(String id) {
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Project " + id));
  }

  public Project create(JsonNode body) {
    String name = requiredText(body, "name").trim();
    String requestedSlug = body.path("slug").asText("");
    String slug = requestedSlug.isBlank() ? slugify(name) : requestedSlug;
    validateSlug(slug, null);
    String now = Instant.now(clock).toString();
    String color = body.path("color").asText("");
    Project project = new Project(
        UUID.randomUUID().toString().substring(0, 8),
        name,
        slug,
        body.path("description").asText(""),
        color.isBlank() ? PALETTE[Math.floorMod(slug.hashCode(), PALETTE.length)] : color,
        now,
        now,
        requiredText(body, "orgId"));
    repository.save(project);
    return project;
  }

  public Project update(String id, JsonNode body) {
    Project before = get(id);
    String slug = body.hasNonNull("slug") ? body.get("slug").asText() : before.slug();
    validateSlug(slug, id);
    Project after = new Project(
        before.id(),
        body.hasNonNull("name") ? requiredText(body, "name").trim() : before.name(),
        slug,
        body.hasNonNull("description") ? body.get("description").asText() : before.description(),
        body.hasNonNull("color") ? body.get("color").asText() : before.color(),
        before.createdAt(),
        Instant.now(clock).toString(),
        body.hasNonNull("orgId") && !body.get("orgId").asText().isBlank() ? body.get("orgId").asText() : before.orgId());
    if (!before.slug().equals(after.slug())) {
      listeners.forEach(l -> l.onBasePathChanged(before, after));
    }
    repository.save(after);
    return after;
  }

  public void delete(String id) {
    Project project = get(id);
    listeners.forEach(l -> l.onProjectDeleted(project));
    repository.deleteById(id);
  }

  @Override
  public void onOrganizationDeleted(Organization organization) {
    findByOrg(organization.id()).forEach(p -> delete(p.id()));
  }

  public int restore(List<Project> projects) {
    projects.forEach(repository::save);
    return projects.size();
  }

  public String availableSlug(String preferred) {
    String candidate = preferred;
    for (int i = 2; repository.findBySlug(candidate).isPresent(); i++) candidate = preferred + "-" + i;
    return candidate;
  }

  void validateSlug(String slug, String selfId) {
    if (!Slugs.valid(slug)) {
      throw ApiException.badRequest("Base path must be lowercase letters, digits and dashes (e.g. payment-service)");
    }
    if (RESERVED_SLUGS.contains(slug)) {
      throw ApiException.badRequest("'" + slug + "' is reserved");
    }
    repository.findBySlug(slug).filter(other -> !other.id().equals(selfId)).ifPresent(other -> {
      throw new ApiException(409, "Base path /" + slug + " is already used by project '" + other.name() + "'");
    });
  }

  static String slugify(String name) {
    return Slugs.slugify(name, "project");
  }

  private static String requiredText(JsonNode body, String field) {
    String v = body.path(field).asText("");
    if (v.isBlank()) throw ApiException.badRequest("'" + field + "' is required");
    return v;
  }
}
