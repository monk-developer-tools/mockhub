package io.mockhub.sso;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.user.ExternalProfile;
import io.mockhub.user.User;
import io.mockhub.user.UserService;
import io.mockhub.user.UserSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SsoService {
  private static final Duration STATE_TTL = Duration.ofMinutes(10);
  private static final Duration CODE_TTL = Duration.ofSeconds(60);
  private static final int MAX_PENDING = 1000;
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private final Map<String, SsoProvider> providers;
  private final SsoSettingsStore settings;
  private final UserService users;
  private final RoleMapper roles;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Pending> pending = new ConcurrentHashMap<>();
  private final Map<String, Issued> issued = new ConcurrentHashMap<>();

  private record Pending(String provider, SsoProvider.AuthorizationRequest request, long createdAt) {}

  private record Issued(String userId, long createdAt) {}

  public SsoService(List<SsoProvider> providers, SsoSettingsStore settings, UserService users, RoleMapper roles, Clock clock) {
    this.providers = providers.stream().collect(Collectors.toMap(SsoProvider::id, Function.identity()));
    this.settings = settings;
    this.users = users;
    this.roles = roles;
    this.clock = clock;
  }

  public String start(String providerId, String redirectUri) {
    SsoSettings s = activeSettings(providerId);
    prune();
    if (pending.size() >= MAX_PENDING) throw new ApiException(429, "Too many sign-ins in progress; try again shortly");
    String verifier = randomToken(32);
    SsoProvider.AuthorizationRequest request =
        new SsoProvider.AuthorizationRequest(redirectUri, randomToken(24), randomToken(24), verifier, challenge(verifier));
    pending.put(request.state(), new Pending(providerId, request, clock.millis()));
    return provider(providerId).authorizationUrl(s, request);
  }

  public String complete(String providerId, String code, String state) {
    Pending p = state == null ? null : pending.remove(state);
    if (p == null || !p.provider().equals(providerId) || expired(p.createdAt(), STATE_TTL)) {
      throw new SsoException("This sign-in link has expired. Start again from the MockHub login page.");
    }
    if (code == null || code.isBlank()) throw new SsoException("Microsoft did not return an authorisation code");
    SsoSettings s = activeSettings(providerId);
    ExternalIdentity identity = provider(providerId).exchange(s, code, p.request());
    if (!RoleMapper.domainAllowed(s, identity)) {
      throw new SsoException("Accounts from this e-mail domain are not allowed to sign in to MockHub");
    }
    RoleMapper.Result mapped = roles.map(s, identity);
    User user = users.upsertExternal(new ExternalProfile(
        UserSource.valueOf(providerId.toUpperCase(Locale.ROOT)),
        identity.tenantId() + ":" + identity.subject(),
        identity.username(),
        identity.name(),
        identity.email(),
        mapped.serverRole(),
        mapped.memberships(),
        mapped.teamMemberships()));
    if (user.disabled()) throw new SsoException("Your MockHub account is disabled. Ask an admin to enable it.");
    String oneTime = randomToken(32);
    issued.put(oneTime, new Issued(user.id(), clock.millis()));
    return oneTime;
  }

  public Optional<User> redeem(String oneTimeCode) {
    Issued i = oneTimeCode == null ? null : issued.remove(oneTimeCode);
    if (i == null || expired(i.createdAt(), CODE_TTL)) return Optional.empty();
    return users.find(i.userId()).filter(u -> !u.disabled());
  }

  public JsonNode test(SsoSettings candidate) {
    return provider(candidate.provider()).testConnection(candidate);
  }

  public boolean supports(String providerId) {
    return providers.containsKey(providerId);
  }

  private SsoSettings activeSettings(String providerId) {
    SsoSettings s = settings.get();
    if (!s.active() || !s.provider().equals(providerId)) throw new SsoException("Single sign-on is not enabled");
    return s;
  }

  private SsoProvider provider(String id) {
    SsoProvider p = providers.get(id);
    if (p == null) throw ApiException.notFound("Sign-in provider " + id);
    return p;
  }

  private void prune() {
    pending.values().removeIf(p -> expired(p.createdAt(), STATE_TTL));
    issued.values().removeIf(i -> expired(i.createdAt(), CODE_TTL));
  }

  private boolean expired(long createdAt, Duration ttl) {
    return clock.millis() - createdAt > ttl.toMillis();
  }

  private String randomToken(int bytes) {
    byte[] b = new byte[bytes];
    random.nextBytes(b);
    return B64.encodeToString(b);
  }

  static String challenge(String verifier) {
    try {
      return B64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
