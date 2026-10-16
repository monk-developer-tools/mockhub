package io.mockhub.team;

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

public final class TeamService implements OrganizationListener {
  public static final String DEFAULT_NAME = "General";

  private final TeamRepository repository;
  private final List<TeamListener> listeners;
  private final Clock clock;

  public TeamService(TeamRepository repository, List<TeamListener> listeners, Clock clock) {
    this.repository = repository;
    this.listeners = listeners;
    this.clock = clock;
  }

  public List<Team> findAll() {
    return repository.findAll();
  }

  public List<Team> findByOrg(String orgId) {
    return repository.findAll().stream().filter(t -> t.orgId().equals(orgId)).toList();
  }

  public Optional<Team> find(String id) {
    return id == null ? Optional.empty() : repository.findById(id);
  }

  public Team get(String id) {
    return find(id).orElseThrow(() -> ApiException.notFound("Team " + id));
  }

  public Team getInOrg(String orgId, String teamId) {
    Team team = get(teamId);
    if (!team.orgId().equals(orgId)) throw ApiException.notFound("Team " + teamId);
    return team;
  }

  public Team ensureDefault(String orgId) {
    List<Team> existing = findByOrg(orgId);
    if (!existing.isEmpty()) {
      return existing.stream().filter(t -> t.slug().equals("general")).findFirst().orElse(existing.get(0));
    }
    String now = now();
    Team team = new Team(newId(), orgId, DEFAULT_NAME, "general", "Everyone working on this organisation's mocks", now, now);
    repository.save(team);
    return team;
  }

  public Team create(String orgId, JsonNode body) {
    String name = requiredName(body);
    String requested = body.path("slug").asText("");
    String slug = requested.isBlank() ? availableSlug(orgId, Slugs.slugify(name, "team")) : requested;
    validateSlug(orgId, slug, null);
    String now = now();
    Team team = new Team(newId(), orgId, name, slug, body.path("description").asText(""), now, now);
    repository.save(team);
    return team;
  }

  public Team update(String id, JsonNode body) {
    Team before = get(id);
    String slug = body.hasNonNull("slug") ? body.get("slug").asText() : before.slug();
    validateSlug(before.orgId(), slug, id);
    Team after = new Team(
        before.id(),
        before.orgId(),
        body.hasNonNull("name") ? requiredName(body) : before.name(),
        slug,
        body.hasNonNull("description") ? body.get("description").asText() : before.description(),
        before.createdAt(),
        now());
    repository.save(after);
    return after;
  }

  public void delete(String id) {
    Team team = get(id);
    listeners.forEach(l -> l.onTeamDeleted(team));
    repository.deleteById(id);
  }

  public Team restore(Team team) {
    boolean taken = findByOrg(team.orgId()).stream().anyMatch(t -> t.slug().equals(team.slug()) && !t.id().equals(team.id()));
    Team saved = taken
        ? new Team(team.id(), team.orgId(), team.name(), availableSlug(team.orgId(), team.slug()), team.description(), team.createdAt(), team.updatedAt())
        : team;
    repository.save(saved);
    return saved;
  }

  @Override
  public void onOrganizationDeleted(Organization organization) {
    findByOrg(organization.id()).forEach(t -> delete(t.id()));
  }

  private String availableSlug(String orgId, String preferred) {
    String candidate = preferred;
    for (int i = 2; slugTaken(orgId, candidate, null); i++) candidate = preferred + "-" + i;
    return candidate;
  }

  private boolean slugTaken(String orgId, String slug, String selfId) {
    return findByOrg(orgId).stream().anyMatch(t -> t.slug().equalsIgnoreCase(slug) && !t.id().equals(selfId));
  }

  private void validateSlug(String orgId, String slug, String selfId) {
    if (!Slugs.valid(slug)) throw ApiException.badRequest("Team slug must be lowercase letters, digits and dashes");
    if (slugTaken(orgId, slug, selfId)) throw new ApiException(409, "This organisation already has a team called '" + slug + "'");
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
