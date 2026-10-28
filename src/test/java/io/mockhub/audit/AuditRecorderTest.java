package io.mockhub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.api.ChangeAudit;
import io.mockhub.api.ChangeAudit.Change;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.project.Project;
import io.mockhub.support.Fixtures;
import io.mockhub.support.MutableClock;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class AuditRecorderTest {

  private final List<AuditEntry> written = new ArrayList<>();
  private final Map<String, String> stubNames = new HashMap<>(Map.of("s1", "GET /orders"));
  private final Project shop = Fixtures.project("shop");
  private final AuditRecorder recorder = new AuditRecorder(new ListLog(), new FakeNames(), new MutableClock(Instant.parse("2026-03-01T10:00:00Z")));
  private final Principal sam = Principal.of(new User.Builder().id("u1").username("sam").displayName("Sam Patel")
      .source(UserSource.LOCAL).serverRole(Role.EDITOR).build());

  private Change change(RequestMethod method, String route, Map<String, String> params, String body) {
    JsonNode json = body == null ? null : readJson(body);
    return new Change(sam, method, route, params, json);
  }

  private static JsonNode readJson(String body) {
    try {
      return Jsons.MAPPER.readTree(body);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Test
  void recordsWhoDeletedAStubAndKeepsTheNameItHadBeforehand() {
    // Given
    ChangeAudit.Pending pending = recorder.begin(change(RequestMethod.DELETE, "/projects/{id}/stubs/{stubId}", Map.of("id", shop.id(), "stubId", "s1"), null));
    stubNames.clear();

    // When
    pending.succeeded(null);

    // Then
    AuditEntry e = written.get(0);
    assertThat(e.actor()).isEqualTo("Sam Patel");
    assertThat(e.actorId()).isEqualTo("u1");
    assertThat(e.summary()).isEqualTo("Deleted stub “GET /orders”");
    assertThat(e.projectId()).isEqualTo(shop.id());
    assertThat(e.projectName()).isEqualTo("shop");
    assertThat(e.orgId()).isEqualTo("org1");
    assertThat(e.teamId()).isEqualTo("team1");
    assertThat(e.at()).isEqualTo("2026-03-01T10:00:00Z");
    assertThat(e.id()).isNotBlank();
  }

  @Test
  void writesNothingWhenTheChangeFails() {
    // When
    recorder.begin(change(RequestMethod.DELETE, "/projects/{id}", Map.of("id", shop.id()), null));

    // Then
    assertThat(written).isEmpty();
  }

  @Test
  void ignoresRoutesThatChangeNothingShared() {
    // When
    ChangeAudit.Pending pending = recorder.begin(change(RequestMethod.POST, "/playground/send", Map.of(), "{}"));

    // Then
    assertThat(pending).isSameAs(ChangeAudit.Pending.NONE);
    assertThat(recorder.covers(RequestMethod.POST, "/playground/send")).isFalse();
    assertThat(recorder.covers(RequestMethod.DELETE, "/projects/{id}/stubs/{stubId}")).isTrue();
  }

  @Test
  void namesANewStubFromItsRequestWhenItHasNoName() {
    // When
    recorder.begin(change(RequestMethod.POST, "/projects/{id}/stubs", Map.of("id", shop.id()),
        "{\"request\":{\"method\":\"POST\",\"urlPath\":\"/orders\"}}")).succeeded(null);

    // Then
    assertThat(written.get(0).summary()).isEqualTo("Created stub “POST /orders”");
  }

  @Test
  void saysWhetherAStubWasTurnedOnOrOff() {
    // When
    recorder.begin(change(RequestMethod.PUT, "/projects/{id}/stubs/{stubId}/enabled", Map.of("id", shop.id(), "stubId", "s1"),
        "{\"enabled\":false}")).succeeded(null);

    // Then
    assertThat(written.get(0).summary()).isEqualTo("Turned off stub “GET /orders”");
  }

  @Test
  void recordsTheRoleWhenSomeoneGetsTeamAccess() {
    // When
    recorder.begin(change(RequestMethod.PUT, "/orgs/{id}/teams/{teamId}/members/{userId}",
        Map.of("id", "org1", "teamId", "team1", "userId", "u2"), "{\"role\":\"editor\"}")).succeeded(null);

    // Then
    AuditEntry e = written.get(0);
    assertThat(e.summary()).isEqualTo("Gave team access to person “Maria Lopez” (as editor)");
    assertThat(e.orgId()).isEqualTo("org1");
    assertThat(e.teamId()).isEqualTo("team1");
  }

  @Test
  void listsChangedFieldsButNeverPasswords() {
    // When
    recorder.begin(change(RequestMethod.PUT, "/users/{id}", Map.of("id", "u2"), "{\"displayName\":\"Maria\",\"password\":\"secret\"}")).succeeded(null);

    // Then
    assertThat(written.get(0).details()).isEqualTo("changed displayName");
  }

  @Test
  void takesTheNewProjectsIdAndTeamFromTheResponse() {
    // Given
    ChangeAudit.Pending pending = recorder.begin(change(RequestMethod.POST, "/projects", Map.of(), "{\"name\":\"Payments\"}"));

    // When
    pending.succeeded("{\"id\":\"p-new\",\"name\":\"Payments\",\"orgId\":\"org1\",\"teamId\":\"team2\"}");

    // Then
    AuditEntry e = written.get(0);
    assertThat(e.summary()).isEqualTo("Created project “Payments”");
    assertThat(e.projectId()).isEqualTo("p-new");
    assertThat(e.teamId()).isEqualTo("team2");
  }

  private final class ListLog implements AuditLog {
    @Override
    public void append(AuditEntry entry) {
      written.add(entry);
    }

    @Override
    public List<AuditEntry> recent(Predicate<AuditEntry> filter, int limit) {
      return written.stream().filter(filter).limit(limit).toList();
    }
  }

  private final class FakeNames implements AuditNames {
    @Override
    public Optional<Project> project(String id) {
      return shop.id().equals(id) ? Optional.of(shop) : Optional.empty();
    }

    @Override
    public Optional<String> stub(String projectId, String stubId) {
      return Optional.ofNullable(stubNames.get(stubId));
    }

    @Override
    public Optional<String> team(String id) {
      return Optional.of("Checkout");
    }

    @Override
    public Optional<String> org(String id) {
      return Optional.of("Acme");
    }

    @Override
    public Optional<String> user(String id) {
      return Optional.of("Maria Lopez");
    }
  }
}
