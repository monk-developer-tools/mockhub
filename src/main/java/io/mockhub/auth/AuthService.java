package io.mockhub.auth;

import io.mockhub.user.User;
import io.mockhub.user.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class AuthService {
  public static final String ACCESS_TOKEN_PREFIX = "mh_";

  private final UserService users;
  private final TokenService tokens;
  private final BooleanSupplier ssoEnabled;
  private final Function<String, Optional<Principal>> accessTokens;

  public AuthService(UserService users, TokenService tokens, BooleanSupplier ssoEnabled) {
    this(users, tokens, ssoEnabled, secret -> Optional.empty());
  }

  public AuthService(UserService users, TokenService tokens, BooleanSupplier ssoEnabled, Function<String, Optional<Principal>> accessTokens) {
    this.users = users;
    this.tokens = tokens;
    this.ssoEnabled = ssoEnabled;
    this.accessTokens = accessTokens;
  }

  public boolean enabled() {
    return users.anyUsers() || ssoEnabled.getAsBoolean();
  }

  public Optional<Principal> principal(String authorizationHeader) {
    if (!enabled()) return Optional.of(Principal.anonymousAdmin());
    String bearer = bearer(authorizationHeader);
    if (bearer != null && bearer.startsWith(ACCESS_TOKEN_PREFIX)) return accessTokens.apply(bearer);
    return authenticate(authorizationHeader).map(Principal::of);
  }

  public Optional<User> authenticate(String header) {
    if (header == null) return Optional.empty();
    if (header.regionMatches(true, 0, "Bearer ", 0, 7)) return fromToken(header.substring(7).trim());
    if (header.regionMatches(true, 0, "Basic ", 0, 6)) return fromBasic(header.substring(6).trim());
    return Optional.empty();
  }

  public Optional<User> login(String username, String password) {
    Optional<User> user = users.authenticate(username, password);
    user.ifPresent(u -> users.recordLogin(u.id()));
    return user;
  }

  public String issueToken(User user) {
    return tokens.issue(user.id());
  }

  public long tokenLifetimeSeconds() {
    return tokens.lifetimeSeconds();
  }

  private static String bearer(String header) {
    return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
  }

  private Optional<User> fromToken(String token) {
    return tokens.verify(token).flatMap(claims -> users.find(claims.subject())
        .filter(u -> !u.disabled())
        .filter(u -> claims.issuedAt() >= u.sessionEpoch()));
  }

  private Optional<User> fromBasic(String encoded) {
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    int colon = decoded.indexOf(':');
    if (colon < 0) return Optional.empty();
    return users.authenticate(decoded.substring(0, colon), decoded.substring(colon + 1));
  }
}
