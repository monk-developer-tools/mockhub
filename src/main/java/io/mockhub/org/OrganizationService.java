package io.mockhub.org;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.common.Slugs;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OrganizationService {
  static final int MAX_MASKED = 50;
  private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9a-z-]{1,100}");
  public static final String DEFAULT_NAME = "Default";

  private final OrganizationRepository repository;
  private final List<OrganizationListener> listeners;
  private final Clock clock;

  public OrganizationService(OrganizationRepository repository, List<OrganizationListener> listeners, Clock clock) {
    this.repository = repository;
    this.listeners = listeners;
    this.clock = clock;
  }

  public List<Organization> findAll() {
    return repository.findAll();
  }

  public Optional<Organization> find(String id) {
    return id == null ? Optional.empty() : repository.findById(id);
  }

  public Organization get(String id) {
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Organisation " + id));
  }

  public boolean exists(String id) {
    return id != null && repository.findById(id).isPresent();
  }

  public Organization ensureDefault() {
    List<Organization> all = repository.findAll();
    if (!all.isEmpty()) return all.stream().filter(o -> o.slug().equals("default")).findFirst().orElse(all.get(0));
    String now = now();
    Organization org = new Organization(newId(), DEFAULT_NAME, "default", "Projects that existed before organisations", now, now);
    repository.save(org);
    return org;
  }

  public Organization ensureInstant() {
    return repository.findById(Organization.INSTANT_ID).orElseGet(() -> {
      String now = now();
      Organization org = new Organization(Organization.INSTANT_ID, "Instant mocks", availableSlug("instant-mocks"),
          "Temporary mocks made on the public tools pages, without an account", now, now);
      repository.save(org);
      return org;
    });
  }

  public Organization create(JsonNode body) {
    String name = requiredName(body);
    String requested = body.path("slug").asText("");
    String slug = requested.isBlank() ? availableSlug(Slugs.slugify(name, "org")) : requested;
    validateSlug(slug, null);
    String now = now();
    Organization org = new Organization(newId(), name, slug, body.path("description").asText(""), now, now);
    repository.save(org);
    return org;
  }

  public Organization update(String id, JsonNode body) {
    Organization before = get(id);
    String slug = body.hasNonNull("slug") ? body.get("slug").asText() : before.slug();
    validateSlug(slug, id);
    Organization after = new Organization(
        before.id(),
        body.hasNonNull("name") ? requiredName(body) : before.name(),
        slug,
        body.hasNonNull("description") ? body.get("description").asText() : before.description(),
        before.createdAt(),
        now(),
        body.has("maskedHeaders") ? maskedHeaders(body.get("maskedHeaders")) : before.maskedHeaders());
    repository.save(after);
    return after;
  }

  static List<String> maskedHeaders(JsonNode list) {
    if (list == null || list.isNull()) return List.of();
    if (!list.isArray()) throw ApiException.badRequest("maskedHeaders is a list of header names, like [\"Authorization\", \"X-Api-Key\"]");
    Set<String> names = new LinkedHashSet<>();
    for (JsonNode item : list) {
      String name = item.asText("").trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty()) continue;
      if (!HEADER_NAME.matcher(name).matches()) throw ApiException.badRequest("'" + item.asText() + "' isn't a header name");
      names.add(name);
    }
    if (names.size() > MAX_MASKED) throw ApiException.badRequest("Mask at most " + MAX_MASKED + " headers");
    return List.copyOf(names);
  }

  public void delete(String id) {
    Organization org = get(id);
    if (repository.findAll().size() == 1) throw ApiException.badRequest("MockHub needs at least one organisation");
    listeners.forEach(l -> l.onOrganizationDeleted(org));
    repository.deleteById(id);
  }

  public int restore(List<Organization> organizations) {
    organizations.forEach(this::restore);
    return organizations.size();
  }

  public Organization restore(Organization organization) {
    boolean taken = repository.findBySlug(organization.slug()).filter(o -> !o.id().equals(organization.id())).isPresent();
    Organization saved = taken
        ? new Organization(organization.id(), organization.name(), availableSlug(organization.slug()), organization.description(),
            organization.createdAt(), organization.updatedAt(), organization.maskedHeaders())
        : organization;
    repository.save(saved);
    return saved;
  }

  private String availableSlug(String preferred) {
    String candidate = preferred;
    for (int i = 2; repository.findBySlug(candidate).isPresent(); i++) candidate = preferred + "-" + i;
    return candidate;
  }

  private void validateSlug(String slug, String selfId) {
    if (!Slugs.valid(slug)) throw ApiException.badRequest("Organisation slug must be lowercase letters, digits and dashes");
    repository.findBySlug(slug).filter(o -> !o.id().equals(selfId)).ifPresent(o -> {
      throw new ApiException(409, "Slug '" + slug + "' is already used by organisation '" + o.name() + "'");
    });
  }

  private static String requiredName(JsonNode body) {
    String name = body.path("name").asText("").trim();
    if (name.isEmpty()) throw ApiException.badRequest("'name' is required");
    return name;
  }

  private static String newId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private String now() {
    return Instant.now(clock).toString();
  }
}
