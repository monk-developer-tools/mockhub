package io.mockhub.token;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import io.mockhub.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AccessTokenService implements ProjectListener {
  public static final String PREFIX = "mh_";
  public static final int MAX_PER_USER = 50;
  private static final Set<Integer> EXPIRY_DAYS = Set.of(0, 7, 30, 90, 365);
  private static final Set<Role> ROLES = Set.of(Role.VIEWER, Role.EDITOR, Role.MANAGER, Role.ADMIN);
  private static final Duration USE_WRITE_INTERVAL = Duration.ofMinutes(5);

  public record Created(AccessToken token, String secret) {}

  private final AccessTokenRepository repository;
  private final Function<String, Optional<User>> users;
  private final Predicate<String> projectExists;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Instant> lastUse = new ConcurrentHashMap<>();
  private final Map<String, Instant> written = new ConcurrentHashMap<>();

  public AccessTokenService(AccessTokenRepository repository, Function<String, Optional<User>> users, Predicate<String> projectExists, Clock clock) {
    this.repository = repository;
    this.users = users;
    this.projectExists = projectExists;
    this.clock = clock;
  }

  public List<AccessToken> listFor(String userId) {
    return repository.findAll().stream()
        .filter(t -> t.userId().equals(userId))
        .map(t -> t.usedAt(lastUsed(t)))
        .sorted(Comparator.comparing(AccessToken::createdAt).reversed())
        .toList();
  }

  public Created create(Principal owner, JsonNode body) {
    if (owner.grant() != null) throw new ApiException(403, "An access token can't create other tokens");
    String name = body.path("name").asText("").trim();
    if (name.isEmpty() || name.length() > 80) throw ApiException.badRequest("Give the token a name of up to 80 characters");
    if (listFor(owner.id()).size() >= MAX_PER_USER) throw ApiException.badRequest("You have " + MAX_PER_USER + " tokens; revoke one first");
    Role role = Role.parse(body.path("role").asText("editor"));
    if (!ROLES.contains(role)) throw ApiException.badRequest("Role must be viewer, editor, manager or admin");
    if (!owner.highestRole().atLeast(role)) throw ApiException.badRequest("A token can't have more access than you: your highest role is " + owner.highestRole().id());
    int days = body.path("expiresInDays").asInt(90);
    if (!EXPIRY_DAYS.contains(days)) throw ApiException.badRequest("Expiry must be 7, 30, 90 or 365 days, or 0 for never");
    List<String> projectIds = new ArrayList<>();
    body.path("projectIds").forEach(id -> projectIds.add(id.asText()));
    for (String id : projectIds) {
      if (!projectExists.test(id)) throw ApiException.badRequest("Project " + id + " doesn't exist");
    }
    String mode = body.path("mode").asText(AccessToken.DIRECT);
    if (!mode.equals(AccessToken.DIRECT) && !mode.equals(AccessToken.REVIEW)) throw ApiException.badRequest("Mode must be direct or review");

    byte[] bytes = new byte[30];
    random.nextBytes(bytes);
    String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now(clock);
    AccessToken token = new AccessToken(UUID.randomUUID().toString(), owner.id(), name, hash(secret), secret.substring(0, PREFIX.length() + 6),
        role, projectIds.stream().distinct().toList(), mode, now.toString(), days == 0 ? null : now.plus(Duration.ofDays(days)).toString(), null);
    repository.save(token);
    return new Created(token, secret);
  }

  public AccessToken revoke(Principal principal, String tokenId) {
    if (principal.grant() != null) throw new ApiException(403, "An access token can't revoke tokens");
    AccessToken token = repository.findAll().stream().filter(t -> t.id().equals(tokenId)).findFirst()
        .filter(t -> t.userId().equals(principal.id()) || principal.isAdmin())
        .orElseThrow(() -> ApiException.notFound("Token " + tokenId + " not found"));
    repository.deleteById(tokenId);
    lastUse.remove(tokenId);
    written.remove(tokenId);
    return token;
  }

  public Optional<Principal> principal(String secret) {
    if (secret == null || !secret.startsWith(PREFIX)) return Optional.empty();
    Optional<AccessToken> found = repository.findByHash(hash(secret));
    if (found.isEmpty()) return Optional.empty();
    AccessToken token = found.get();
    Instant now = Instant.now(clock);
    if (token.expiresAt() != null && !now.isBefore(Instant.parse(token.expiresAt()))) return Optional.empty();
    Optional<User> user = users.apply(token.userId()).filter(u -> !u.disabled());
    if (user.isEmpty()) return Optional.empty();
    recordUse(token, now);
    return Optional.of(Principal.viaToken(user.get(), token.grant()));
  }

  public void flushUse() {
    List<AccessToken> changed = repository.findAll().stream()
        .filter(t -> lastUse.containsKey(t.id()) && !lastUsed(t).equals(t.lastUsedAt()))
        .map(t -> t.usedAt(lastUsed(t)))
        .toList();
    if (!changed.isEmpty()) repository.saveAll(changed);
  }

  @Override
  public void onProjectDeleted(Project project) {
    for (AccessToken token : repository.findAll()) {
      if (!token.projectIds().contains(project.id())) continue;
      AccessToken narrowed = token.withoutProject(project.id());
      if (narrowed.projectIds().isEmpty()) repository.deleteById(token.id());
      else repository.save(narrowed);
    }
  }

  private void recordUse(AccessToken token, Instant now) {
    lastUse.put(token.id(), now);
    Instant persisted = written.get(token.id());
    if (persisted == null && token.lastUsedAt() != null) persisted = Instant.parse(token.lastUsedAt());
    if (persisted == null || Duration.between(persisted, now).compareTo(USE_WRITE_INTERVAL) >= 0) {
      written.put(token.id(), now);
      repository.saveAll(List.of(token.usedAt(now.toString())));
    }
  }

  private String lastUsed(AccessToken token) {
    Instant used = lastUse.get(token.id());
    return used == null ? token.lastUsedAt() : used.toString();
  }

  static String hash(String secret) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
