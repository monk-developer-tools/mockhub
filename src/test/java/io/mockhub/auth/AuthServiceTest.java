package io.mockhub.auth;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.Config.AuthUser;
import io.mockhub.support.InMemoryUserRepository;
import io.mockhub.support.MutableClock;
import io.mockhub.user.PasswordHasher;
import io.mockhub.user.User;
import io.mockhub.user.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final AtomicBoolean sso = new AtomicBoolean(false);

  private AuthService service(List<AuthUser> envUsers, UserService[] holder) {
    UserService users = new UserService(new InMemoryUserRepository(), envUsers, new PasswordHasher(1000), clock);
    holder[0] = users;
    TokenService tokens = new TokenService("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), clock, Duration.ofHours(12));
    return new AuthService(users, tokens, sso::get);
  }

  @Test
  void everyoneIsAnAnonymousAdminWhileSignInIsOff() {
    // Given
    AuthService auth = service(List.of(), new UserService[1]);

    // Then
    assertThat(auth.enabled()).isFalse();
    assertThat(auth.principal(null)).hasValueSatisfying(p -> {
      assertThat(p.anonymous()).isTrue();
      assertThat(p.isAdmin()).isTrue();
    });
  }

  @Test
  void turningOnSsoAloneRequiresSignIn() {
    // Given
    AuthService auth = service(List.of(), new UserService[1]);

    // When
    sso.set(true);

    // Then
    assertThat(auth.enabled()).isTrue();
    assertThat(auth.principal(null)).isEmpty();
  }

  @Test
  void acceptsBearerTokensAndBasicCredentials() {
    // Given
    UserService[] users = new UserService[1];
    AuthService auth = service(List.of(new AuthUser("root", "pw", "admin")), users);
    User root = auth.login("root", "pw").orElseThrow();

    // Then
    assertThat(auth.principal("Bearer " + auth.issueToken(root))).hasValueSatisfying(p -> assertThat(p.username()).isEqualTo("root"));
    assertThat(auth.principal(basic("root", "pw"))).isPresent();
    assertThat(auth.principal(basic("root", "bad"))).isEmpty();
    assertThat(auth.principal("Bearer junk")).isEmpty();
    assertThat(auth.principal("Digest x")).isEmpty();
  }

  @Test
  void tokensIssuedBeforeAPasswordChangeStopWorking() {
    // Given
    UserService[] users = new UserService[1];
    AuthService auth = service(List.of(new AuthUser("root", "pw", "admin")), users);
    User ana = users[0].create(json("{'username':'ana','password':'longenough'}"));
    String token = auth.issueToken(ana);
    clock.advance(Duration.ofSeconds(1));

    // When
    users[0].setPassword(ana.id(), "brandnewpw");

    // Then
    assertThat(auth.principal("Bearer " + token)).isEmpty();
    assertThat(auth.principal("Bearer " + auth.issueToken(users[0].get(ana.id())))).isPresent();
  }

  private static String basic(String u, String p) {
    return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
  }
}
