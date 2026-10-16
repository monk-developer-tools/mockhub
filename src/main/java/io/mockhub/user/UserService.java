package io.mockhub.user;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.Config.AuthUser;
import io.mockhub.api.ApiException;
import io.mockhub.auth.Role;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationListener;
import io.mockhub.team.Team;
import io.mockhub.team.TeamListener;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UserService implements OrganizationListener, TeamListener {
  private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._@+-]{2,128}");
  private static final int MIN_PASSWORD = 8;

  private final UserRepository repository;
  private final List<User> envUsers;
  private final PasswordHasher hasher;
  private final Clock clock;

  public UserService(UserRepository repository, List<AuthUser> envUsers, PasswordHasher hasher, Clock clock) {
    this.repository = repository;
    this.envUsers = envUsers.stream().map(UserService::fromEnv).toList();
    this.hasher = hasher;
    this.clock = clock;
  }

  private static User fromEnv(AuthUser u) {
    return new User.Builder()
        .id("env:" + u.username())
        .username(u.username())
        .displayName(u.username())
        .source(UserSource.ENV)
        .passwordHash(u.password())
        .serverRole(Role.serverRole(u.role()))
        .build();
  }

  public boolean anyUsers() {
    return !envUsers.isEmpty() || !repository.findAll().isEmpty();
  }

  public List<User> findAll() {
    List<User> all = new ArrayList<>(envUsers);
    all.addAll(repository.findAll());
    all.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
    return all;
  }

  public Optional<User> find(String id) {
    if (id == null) return Optional.empty();
    return envUsers.stream().filter(u -> u.id().equals(id)).findFirst().or(() -> repository.findById(id));
  }

  public User get(String id) {
    return find(id).orElseThrow(() -> ApiException.notFound("User " + id));
  }

  public Optional<User> findByUsername(String username) {
    return findAll().stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst();
  }

  public Optional<User> authenticate(String username, String password) {
    return findByUsername(username)
        .filter(User::canSignInWithPassword)
        .filter(u -> hasher.matches(u.passwordHash(), password));
  }

  public List<User> membersOf(String orgId) {
    return findAll().stream()
        .filter(u -> u.memberships().stream().anyMatch(m -> m.orgId().equals(orgId))
            || u.groupMemberships().stream().anyMatch(m -> m.orgId().equals(orgId))
            || u.teamMemberships().stream().anyMatch(m -> m.orgId().equals(orgId))
            || u.groupTeamMemberships().stream().anyMatch(m -> m.orgId().equals(orgId)))
        .toList();
  }

  public List<User> teamMembersOf(String teamId) {
    return findAll().stream().filter(u -> u.inTeam(teamId)).toList();
  }

  public User create(JsonNode body) {
    String username = text(body, "username");
    if (!USERNAME.matcher(username).matches()) {
      throw ApiException.badRequest("Username must be 2-128 letters, digits or . _ @ + -");
    }
    if (findByUsername(username).isPresent()) throw new ApiException(409, "Username '" + username + "' is taken");
    String password = body.path("password").asText("");
    checkPassword(password);
    String now = now();
    User user = new User.Builder()
        .id(UUID.randomUUID().toString())
        .username(username)
        .displayName(body.path("displayName").asText("").isBlank() ? username : body.path("displayName").asText().trim())
        .email(body.path("email").asText("").trim())
        .source(UserSource.LOCAL)
        .passwordHash(hasher.hash(password))
        .serverRole(Role.serverRole(body.path("serverRole").asText("none")))
        .memberships(memberships(body))
        .teamMemberships(teamMemberships(body))
        .createdAt(now)
        .build();
    repository.save(user);
    return user;
  }

  public User update(String id, JsonNode body) {
    User before = editable(id);
    User.Builder b = before.toBuilder();
    if (body.hasNonNull("displayName")) b.displayName(body.get("displayName").asText().trim());
    if (body.hasNonNull("email")) b.email(body.get("email").asText().trim());
    if (body.hasNonNull("serverRole")) b.serverRole(Role.serverRole(body.get("serverRole").asText()));
    if (body.path("memberships").isArray()) b.memberships(memberships(body));
    if (body.path("teamMemberships").isArray()) b.teamMemberships(teamMemberships(body));
    if (body.hasNonNull("disabled")) {
      boolean disabled = body.get("disabled").asBoolean();
      b.disabled(disabled);
      if (disabled && !before.disabled()) b.sessionEpoch(clock.millis());
    }
    User after = b.build();
    repository.save(after);
    return after;
  }

  public User setPassword(String id, String password) {
    User user = editable(id);
    if (user.source() != UserSource.LOCAL) throw ApiException.badRequest("Only local users have a MockHub password");
    checkPassword(password);
    User after = user.toBuilder().passwordHash(hasher.hash(password)).sessionEpoch(clock.millis()).build();
    repository.save(after);
    return after;
  }

  public User changeOwnPassword(String id, String current, String next) {
    User user = editable(id);
    if (!hasher.matches(user.passwordHash(), current)) throw ApiException.badRequest("The current password is wrong");
    return setPassword(id, next);
  }

  public void delete(String id) {
    editable(id);
    repository.deleteById(id);
  }

  public User setMembership(String userId, String orgId, Role role) {
    User user = editable(userId);
    List<Membership> memberships = new ArrayList<>(user.memberships());
    memberships.removeIf(m -> m.orgId().equals(orgId));
    memberships.add(new Membership(orgId, role));
    User after = user.toBuilder().memberships(memberships).build();
    repository.save(after);
    return after;
  }

  public User removeMembership(String userId, String orgId) {
    User user = editable(userId);
    List<Membership> memberships = new ArrayList<>(user.memberships());
    memberships.removeIf(m -> m.orgId().equals(orgId));
    User after = user.toBuilder().memberships(memberships).build();
    repository.save(after);
    return after;
  }

  public User setTeamMembership(String userId, String teamId, String orgId, Role role) {
    User user = editable(userId);
    List<TeamMembership> teams = new ArrayList<>(user.teamMemberships());
    teams.removeIf(m -> m.teamId().equals(teamId));
    teams.add(new TeamMembership(teamId, orgId, role));
    User after = user.toBuilder().teamMemberships(teams).build();
    repository.save(after);
    return after;
  }

  public User removeTeamMembership(String userId, String teamId) {
    User user = editable(userId);
    List<TeamMembership> teams = new ArrayList<>(user.teamMemberships());
    teams.removeIf(m -> m.teamId().equals(teamId));
    User after = user.toBuilder().teamMemberships(teams).build();
    repository.save(after);
    return after;
  }

  public User upsertExternal(ExternalProfile profile) {
    String now = now();
    Optional<User> existing = repository.findAll().stream()
        .filter(u -> u.source() == profile.source() && profile.externalId().equals(u.externalId()))
        .findFirst();
    User.Builder b;
    if (existing.isPresent()) {
      b = existing.get().toBuilder();
    } else {
      String username = availableUsername(profile.username());
      b = new User.Builder().id(UUID.randomUUID().toString()).username(username).source(profile.source())
          .externalId(profile.externalId()).createdAt(now);
    }
    User user = b.displayName(profile.displayName() == null || profile.displayName().isBlank() ? profile.username() : profile.displayName())
        .email(profile.email() == null ? "" : profile.email())
        .groupServerRole(profile.groupServerRole())
        .groupMemberships(profile.groupMemberships())
        .groupTeamMemberships(profile.groupTeamMemberships() == null ? List.of() : profile.groupTeamMemberships())
        .lastLoginAt(now)
        .build();
    repository.save(user);
    return user;
  }

  public void recordLogin(String id) {
    repository.findById(id).ifPresent(u -> repository.save(u.toBuilder().lastLoginAt(now()).build()));
  }

  @Override
  public void onOrganizationDeleted(Organization organization) {
    String orgId = organization.id();
    for (User u : repository.findAll()) {
      if (!u.belongsTo(orgId) && u.memberships().stream().noneMatch(m -> m.orgId().equals(orgId))) continue;
      List<Membership> manual = new ArrayList<>(u.memberships());
      List<Membership> group = new ArrayList<>(u.groupMemberships());
      List<TeamMembership> teams = new ArrayList<>(u.teamMemberships());
      List<TeamMembership> groupTeams = new ArrayList<>(u.groupTeamMemberships());
      manual.removeIf(m -> m.orgId().equals(orgId));
      group.removeIf(m -> m.orgId().equals(orgId));
      teams.removeIf(m -> m.orgId().equals(orgId));
      groupTeams.removeIf(m -> m.orgId().equals(orgId));
      repository.save(u.toBuilder().memberships(manual).groupMemberships(group).teamMemberships(teams).groupTeamMemberships(groupTeams).build());
    }
  }

  @Override
  public void onTeamDeleted(Team team) {
    for (User u : repository.findAll()) {
      if (!u.inTeam(team.id())) continue;
      List<TeamMembership> teams = new ArrayList<>(u.teamMemberships());
      List<TeamMembership> groupTeams = new ArrayList<>(u.groupTeamMemberships());
      teams.removeIf(m -> m.teamId().equals(team.id()));
      groupTeams.removeIf(m -> m.teamId().equals(team.id()));
      repository.save(u.toBuilder().teamMemberships(teams).groupTeamMemberships(groupTeams).build());
    }
  }

  private static List<Membership> memberships(JsonNode body) {
    List<Membership> list = new ArrayList<>();
    for (JsonNode m : body.path("memberships")) {
      String orgId = m.path("orgId").asText("");
      if (orgId.isBlank()) continue;
      list.removeIf(x -> x.orgId().equals(orgId));
      list.add(new Membership(orgId, Role.orgRole(m.path("role").asText())));
    }
    return list;
  }

  private static List<TeamMembership> teamMemberships(JsonNode body) {
    List<TeamMembership> list = new ArrayList<>();
    for (JsonNode m : body.path("teamMemberships")) {
      String teamId = m.path("teamId").asText("");
      String orgId = m.path("orgId").asText("");
      if (teamId.isBlank() || orgId.isBlank()) continue;
      list.removeIf(x -> x.teamId().equals(teamId));
      list.add(new TeamMembership(teamId, orgId, Role.teamRole(m.path("role").asText())));
    }
    return list;
  }

  private String availableUsername(String preferred) {
    String base = preferred == null || preferred.isBlank() ? "user" : preferred.toLowerCase(Locale.ROOT);
    String candidate = base;
    for (int i = 2; findByUsername(candidate).isPresent(); i++) candidate = base + "-" + i;
    return candidate;
  }

  private User editable(String id) {
    User user = get(id);
    if (user.source() == UserSource.ENV) {
      throw ApiException.badRequest("'" + user.username() + "' comes from MOCKHUB_USERS; change it there");
    }
    return user;
  }

  private static void checkPassword(String password) {
    if (password.length() < MIN_PASSWORD) {
      throw ApiException.badRequest("Passwords need at least " + MIN_PASSWORD + " characters");
    }
  }

  private static String text(JsonNode body, String field) {
    String v = body.path(field).asText("");
    if (v.isBlank()) throw ApiException.badRequest("'" + field + "' is required");
    return v.trim();
  }

  private String now() {
    return Instant.now(clock).toString();
  }
}
