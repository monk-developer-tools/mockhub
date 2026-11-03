package io.mockhub.token;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.auth.TokenGrant;
import io.mockhub.project.Project;
import io.mockhub.support.MutableClock;
import io.mockhub.user.Membership;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessTokenServiceTest {

  @TempDir
  Path dir;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-03T10:00:00Z"));
  private final Map<String, User> users = new HashMap<>();
  private AccessTokenService tokens;
  private Principal alex;

  @BeforeEach
  void setUp() {
    User user = new User.Builder().id("u1").username("alex").displayName("Alex").source(UserSource.LOCAL)
        .memberships(List.of(new Membership("org1", Role.EDITOR))).build();
    users.put("u1", user);
    alex = Principal.of(user);
    tokens = new AccessTokenService(new FileAccessTokenRepository(dir), id -> Optional.ofNullable(users.get(id)),
        Set.of("p1", "p2")::contains, clock);
  }

  @Test
  void showsTheSecretOnceAndKeepsOnlyItsHash() throws Exception {
    // When
    AccessTokenService.Created created = tokens.create(alex, tree("{'name':'Claude Code','role':'editor'}"));

    // Then
    assertThat(created.secret()).startsWith("mh_").hasSizeGreaterThan(40);
    assertThat(created.token().prefix()).isEqualTo(created.secret().substring(0, 9));
    String stored = Files.readString(dir.resolve("mockhub").resolve("access-tokens.json"));
    assertThat(stored).doesNotContain(created.secret()).contains(created.token().hash());
    assertThat(created.token().expiresAt()).isEqualTo("2027-02-01T10:00:00Z");
  }

  @Test
  void signsInAsTheOwnerWithTheTokensLimits() {
    // Given
    String secret = tokens.create(alex, tree("{'name':'CI','role':'viewer','projectIds':['p1'],'mode':'review'}")).secret();

    // When
    Principal principal = tokens.principal(secret).orElseThrow();

    // Then
    assertThat(principal.id()).isEqualTo("u1");
    assertThat(principal.grant()).extracting(TokenGrant::name, TokenGrant::ceiling, TokenGrant::projectIds, TokenGrant::review)
        .containsExactly("CI", Role.VIEWER, List.of("p1"), true);
    assertThat(principal.roleIn("org1")).isEqualTo(Role.VIEWER);
    assertThat(principal.allowsProject("p2")).isFalse();
    assertThat(principal.label()).isEqualTo("Alex via token “CI”");
  }

  @Test
  void rejectsUnknownExpiredRevokedAndDisabledTokens() {
    // Given
    String expiring = tokens.create(alex, tree("{'name':'Short','expiresInDays':7}")).secret();
    AccessTokenService.Created revoked = tokens.create(alex, tree("{'name':'Old'}"));
    String forever = tokens.create(alex, tree("{'name':'Forever','expiresInDays':0}")).secret();
    tokens.revoke(alex, revoked.token().id());

    // When
    clock.advance(Duration.ofDays(8));

    // Then
    assertThat(tokens.principal("mh_unknown")).isEmpty();
    assertThat(tokens.principal(expiring)).isEmpty();
    assertThat(tokens.principal(revoked.secret())).isEmpty();
    assertThat(tokens.principal(forever)).isPresent();
    users.put("u1", users.get("u1").toBuilder().disabled(true).build());
    assertThat(tokens.principal(forever)).isEmpty();
  }

  @Test
  void neverGivesMoreAccessThanTheOwnerHas() {
    // When / Then
    assertThatThrownBy(() -> tokens.create(alex, tree("{'name':'Root','role':'admin'}")))
        .isInstanceOf(ApiException.class).hasMessageContaining("more access than you");
    assertThatThrownBy(() -> tokens.create(alex, tree("{'name':'X','projectIds':['nope']}")))
        .isInstanceOf(ApiException.class).hasMessageContaining("doesn't exist");
    Principal viaToken = tokens.principal(tokens.create(alex, tree("{'name':'T'}")).secret()).orElseThrow();
    assertThatThrownBy(() -> tokens.create(viaToken, tree("{'name':'Child'}")))
        .isInstanceOf(ApiException.class).hasMessageContaining("can't create other tokens");
  }

  @Test
  void recordsWhenATokenWasLastUsed() {
    // Given
    String secret = tokens.create(alex, tree("{'name':'CI'}")).secret();

    // When
    clock.advance(Duration.ofMinutes(1));
    tokens.principal(secret);
    clock.advance(Duration.ofMinutes(1));
    tokens.principal(secret);
    tokens.flushUse();

    // Then
    assertThat(tokens.listFor("u1")).singleElement().satisfies(t -> assertThat(t.lastUsedAt()).isEqualTo("2026-11-03T10:02:00Z"));
    assertThat(new AccessTokenService(new FileAccessTokenRepository(dir), id -> Optional.empty(), id -> true, clock).listFor("u1"))
        .singleElement().satisfies(t -> assertThat(t.lastUsedAt()).isEqualTo("2026-11-03T10:02:00Z"));
  }

  @Test
  void dropsADeletedProjectFromScopesAndRevokesTokensLeftWithNone() {
    // Given
    tokens.create(alex, tree("{'name':'Both','projectIds':['p1','p2']}"));
    tokens.create(alex, tree("{'name':'Only p1','projectIds':['p1']}"));

    // When
    tokens.onProjectDeleted(new Project("p1", "P1", "p1", "", "#000000", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", "org1", "team1", null, false, null));

    // Then
    assertThat(tokens.listFor("u1")).singleElement().satisfies(t -> {
      assertThat(t.name()).isEqualTo("Both");
      assertThat(t.projectIds()).containsExactly("p2");
    });
  }
}
