package io.mockhub.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.auth.Role;
import io.mockhub.support.InMemoryUserRepository;
import io.mockhub.support.MutableClock;
import io.mockhub.user.PasswordHasher;
import io.mockhub.user.User;
import io.mockhub.user.UserService;
import io.mockhub.user.UserSource;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SsoServiceTest {

  @TempDir
  Path dir;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final UserService users = new UserService(new InMemoryUserRepository(), List.of(), new PasswordHasher(1000), clock);
  private final StubProvider provider = new StubProvider();
  private SsoSettingsStore store;
  private SsoService sso;

  @BeforeEach
  void setUp() {
    store = new SsoSettingsStore(dir, "");
    store.save(settings(List.of()));
    sso = new SsoService(List.of(provider), store, users, new RoleMapper(id -> id.equals("o1")), clock);
  }

  private static SsoSettings settings(List<String> domains) {
    return new SsoSettings(true, "azure", "t", "c", "s", "global", null, domains, Role.NONE, "o1", Role.EDITOR, List.of(), true);
  }

  @Test
  void completesASignInAndRedeemsTheOneTimeCodeOnce() {
    // Given
    String state = query(sso.start("azure", "https://mock/cb")).get("state");

    // When
    String code = sso.complete("azure", "auth-code", state);
    var first = sso.redeem(code);
    var second = sso.redeem(code);

    // Then
    User user = first.orElseThrow();
    assertThat(user.source()).isEqualTo(UserSource.AZURE);
    assertThat(user.externalId()).isEqualTo("t:oid-1");
    assertThat(user.roleIn("o1")).isEqualTo(Role.EDITOR);
    assertThat(provider.lastCode).isEqualTo("auth-code");
    assertThat(provider.lastRequest.codeChallenge()).isEqualTo(SsoService.challenge(provider.lastRequest.codeVerifier()));
    assertThat(second).isEmpty();
  }

  @Test
  void rejectsUnknownOrExpiredStateAndExpiredCodes() {
    // Given
    String state = query(sso.start("azure", "https://mock/cb")).get("state");

    // Then
    assertThatThrownBy(() -> sso.complete("azure", "c", "forged")).hasMessageContaining("expired");
    String code = sso.complete("azure", "c", state);
    assertThatThrownBy(() -> sso.complete("azure", "c", state)).hasMessageContaining("expired");
    clock.advance(Duration.ofMinutes(2));
    assertThat(sso.redeem(code)).isEmpty();

    String late = query(sso.start("azure", "https://mock/cb")).get("state");
    clock.advance(Duration.ofMinutes(11));
    assertThatThrownBy(() -> sso.complete("azure", "c", late)).hasMessageContaining("expired");
  }

  @Test
  void blocksOtherDomainsAndDisabledSso() {
    // Given
    store.save(settings(List.of("fabrikam.com")));
    String state = query(sso.start("azure", "https://mock/cb")).get("state");

    // Then
    assertThatThrownBy(() -> sso.complete("azure", "c", state)).hasMessageContaining("domain");
    store.save(SsoSettings.disabled());
    assertThatThrownBy(() -> sso.start("azure", "https://mock/cb")).hasMessageContaining("not enabled");
  }

  private static Map<String, String> query(String url) {
    return java.util.Arrays.stream(URI.create(url).getQuery().split("&"))
        .map(p -> p.split("=", 2))
        .collect(Collectors.toMap(p -> p[0], p -> p[1]));
  }

  static final class StubProvider implements SsoProvider {
    String lastCode;
    AuthorizationRequest lastRequest;

    @Override
    public String id() {
      return "azure";
    }

    @Override
    public String authorizationUrl(SsoSettings settings, AuthorizationRequest request) {
      return "https://idp/authorize?state=" + request.state() + "&nonce=" + request.nonce();
    }

    @Override
    public ExternalIdentity exchange(SsoSettings settings, String code, AuthorizationRequest request) {
      lastCode = code;
      lastRequest = request;
      return new ExternalIdentity("oid-1", "ana@contoso.com", "ana@contoso.com", "Ana", "t", List.of(), List.of(), false);
    }

    @Override
    public JsonNode testConnection(SsoSettings settings) {
      return null;
    }
  }
}
