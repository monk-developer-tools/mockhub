package io.mockhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.user.Membership;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

  private final AccessPolicy policy = new AccessPolicy(id -> Optional.ofNullable(Map.of("pa", "orgA", "pb", "orgB").get(id)));

  private static Principal user(Role serverRole, Membership... memberships) {
    return Principal.of(new User.Builder().id("u").username("u").source(UserSource.LOCAL).serverRole(serverRole)
        .memberships(List.of(memberships)).build());
  }

  private static final Map<String, Principal> PEOPLE = Map.of(
      "admin", user(Role.ADMIN),
      "viewerA", user(Role.NONE, new Membership("orgA", Role.VIEWER)),
      "editorA", user(Role.NONE, new Membership("orgA", Role.EDITOR)),
      "ownerA", user(Role.NONE, new Membership("orgA", Role.OWNER)),
      "nobody", user(Role.NONE),
      "serverEditor", user(Role.EDITOR));

  @ParameterizedTest
  @CsvSource({
      "admin, POST, /mockhub/api/users, true",
      "admin, DELETE, /mappings, true",
      "viewerA, GET, /mockhub/api/projects, true",
      "viewerA, GET, /mockhub/api/projects/pa/stubs, true",
      "viewerA, POST, /mockhub/api/projects/pa/stubs, false",
      "viewerA, GET, /mockhub/api/projects/pb/stubs, false",
      "editorA, POST, /mockhub/api/projects/pa/stubs, true",
      "editorA, DELETE, /mockhub/api/projects/pa, true",
      "editorA, PUT, /mockhub/api/projects/pb, false",
      "editorA, PUT, /mockhub/api/orgs/orgA, false",
      "ownerA, PUT, /mockhub/api/orgs/orgA, true",
      "ownerA, PUT, /mockhub/api/orgs/orgA/members/x, true",
      "ownerA, DELETE, /mockhub/api/orgs/orgB, false",
      "ownerA, GET, /mockhub/api/users, true",
      "editorA, GET, /mockhub/api/users, false",
      "ownerA, POST, /mockhub/api/users, false",
      "ownerA, POST, /mockhub/api/orgs, false",
      "ownerA, GET, /mockhub/api/sso, false",
      "ownerA, GET, /mockhub/api/backup, false",
      "serverEditor, POST, /mockhub/api/projects/pb/stubs, true",
      "serverEditor, PUT, /mockhub/api/orgs/orgB, false",
      "nobody, GET, /mockhub/api/projects, true",
      "nobody, GET, /mockhub/api/auth/me, true",
      "nobody, GET, /mappings, false",
      "viewerA, GET, /mappings, true",
      "viewerA, POST, /requests/find, true",
      "viewerA, DELETE, /mappings/x, false",
      "editorA, POST, /scenarios/reset, true",
      "editorA, POST, /reset, false",
      "editorA, DELETE, /requests, false",
      "editorA, GET, /settings, true",
      "editorA, POST, /mockhub/api/examples/rest-basics/install, true",
      "viewerA, GET, /mockhub/api/stubs/unassigned, true",
      "nobody, GET, /mockhub/api/stubs/unassigned, false"
  })
  void decides(String who, String method, String path, boolean allowed) {
    // When
    AccessPolicy.Decision decision = policy.decide(PEOPLE.get(who), RequestMethod.fromString(method), path);

    // Then
    assertThat(decision.allowed()).as(who + " " + method + " " + path + ": " + decision.reason()).isEqualTo(allowed);
  }

  @Test
  void unknownProjectsFallThroughToA404() {
    // Then
    assertThat(policy.decide(PEOPLE.get("nobody"), RequestMethod.GET, "/mockhub/api/projects/missing").allowed()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "/health, true",
      "/mockhub, true",
      "/mockhub/ui/assets/app.js, true",
      "/mockhub/api/auth/info, true",
      "/mockhub/api/auth/login, true",
      "/mockhub/api/auth/sso/azure/start, true",
      "/mockhub/api/auth/sso/azure/callback, true",
      "/mockhub/api/auth/sso/exchange, true",
      "/mockhub/api/auth/me, false",
      "/mockhub/api/projects, false",
      "/mappings, false"
  })
  void knowsThePublicPaths(String path, boolean expected) {
    // Then
    assertThat(AccessPolicy.isPublic(path)).isEqualTo(expected);
  }
}
