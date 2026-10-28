package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String STUB = "{\"name\":\"List orders\",\"request\":{\"method\":\"GET\",\"url\":\"/orders\"},\"response\":{\"status\":200,\"body\":\"[]\"}}";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private String orgId;
  private String checkout;
  private String search;
  private String cart;
  private String index;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin", "MOCKHUB_AUDIT_RETENTION_DAYS", "30"));
    admin = server.api().login("root", "pw");
    orgId = admin.post(API + "/orgs", "{\"name\":\"Acme\"}").json().get("id").asText();
    checkout = admin.post(API + "/orgs/" + orgId + "/teams", "{\"name\":\"Checkout\"}").json().get("id").asText();
    search = admin.post(API + "/orgs/" + orgId + "/teams", "{\"name\":\"Search\"}").json().get("id").asText();
    cart = admin.post(API + "/projects", "{\"name\":\"Cart\",\"teamId\":\"" + checkout + "\"}").json().get("id").asText();
    index = admin.post(API + "/projects", "{\"name\":\"Index\",\"teamId\":\"" + search + "\"}").json().get("id").asText();
    user("dev", checkout, "editor");
    user("lead", checkout, "owner");
    user("finder", search, "editor");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private void user(String name, String teamId, String role) {
    admin.post(API + "/users", "{\"username\":\"" + name + "\",\"displayName\":\"" + name + "\",\"password\":\"longenough\",\"teamMemberships\":[{\"teamId\":\""
        + teamId + "\",\"role\":\"" + role + "\"}]}");
  }

  private ApiClient as(String name) {
    return server.api().login(name, "longenough");
  }

  private static List<String> summaries(JsonNode page) {
    return StreamSupport.stream(page.get("entries").spliterator(), false)
        .map(e -> e.get("actor").asText() + ": " + e.get("action").asText() + " " + e.get("target").asText() + " " + e.get("targetName").asText())
        .toList();
  }

  @Test
  void recordsWhoChangedAndDeletedWhatNewestFirst() {
    // Given
    ApiClient dev = as("dev");
    String stubId = dev.post(API + "/projects/" + cart + "/stubs", STUB).json().get("id").asText();
    dev.put(API + "/projects/" + cart + "/stubs/" + stubId + "/enabled", "{\"enabled\":false}");

    // When
    dev.delete(API + "/projects/" + cart + "/stubs/" + stubId);
    JsonNode page = dev.get(API + "/projects/" + cart + "/audit").json();

    // Then
    assertThat(page.get("retentionDays").asInt()).isEqualTo(30);
    assertThat(summaries(page)).containsExactly(
        "dev: deleted stub List orders",
        "dev: turned off stub List orders",
        "dev: created stub List orders",
        "root: created project Cart");
  }

  @Test
  void failedChangesAreNotRecorded() {
    // Given
    ApiClient dev = as("dev");

    // When
    ApiClient.Result missing = dev.delete(API + "/projects/" + cart + "/stubs/does-not-exist");
    ApiClient.Result forbidden = dev.post(API + "/projects/" + index + "/stubs", STUB);

    // Then
    assertThat(missing.status()).isGreaterThanOrEqualTo(400);
    assertThat(forbidden.status()).isEqualTo(403);
    assertThat(summaries(admin.get(API + "/projects/" + cart + "/audit").json())).containsExactly("root: created project Cart");
    assertThat(summaries(admin.get(API + "/projects/" + index + "/audit").json())).containsExactly("root: created project Index");
  }

  @Test
  void recordsAccessChangesWithTheRole() {
    // Given
    String id = admin.post(API + "/users", "{\"username\":\"newbie\",\"displayName\":\"New Person\",\"password\":\"longenough\"}").json().get("id").asText();

    // When
    as("lead").put(API + "/orgs/" + orgId + "/teams/" + checkout + "/members/" + id, "{\"role\":\"viewer\"}");

    // Then
    JsonNode latest = admin.get(API + "/audit?limit=1").json().at("/entries/0");
    assertThat(latest.get("actor").asText()).isEqualTo("lead");
    assertThat(latest.get("action").asText()).isEqualTo("gave team access to");
    assertThat(latest.get("targetName").asText()).isEqualTo("New Person");
    assertThat(latest.get("details").asText()).isEqualTo("as viewer");
    assertThat(latest.get("teamId").asText()).isEqualTo(checkout);
  }

  @Test
  void teamOwnersSeeOnlyTheirTeamsActivityAndEditorsSeeNoServerWideLog() {
    // Given
    as("dev").post(API + "/projects/" + cart + "/stubs", STUB);
    as("finder").post(API + "/projects/" + index + "/stubs", STUB);

    // When
    ApiClient.Result lead = as("lead").get(API + "/audit");
    ApiClient.Result dev = as("dev").get(API + "/audit");
    ApiClient.Result all = admin.get(API + "/audit");

    // Then
    assertThat(lead.status()).isEqualTo(200);
    assertThat(lead.json().get("entries")).allSatisfy(e -> assertThat(e.get("teamId").asText()).isEqualTo(checkout));
    assertThat(summaries(lead.json())).contains("dev: created stub List orders").doesNotContain("finder: created stub List orders");
    assertThat(dev.status()).isEqualTo(403);
    assertThat(summaries(all.json())).contains("dev: created stub List orders", "finder: created stub List orders", "root: created organisation Acme");
  }

  @Test
  void membersOfOneTeamCannotReadOrChangeAnotherTeamsMocks() {
    // Given
    String stubId = as("finder").post(API + "/projects/" + index + "/stubs", STUB).json().get("id").asText();
    ApiClient dev = as("dev");

    // When
    List<Integer> statuses = List.of(
        dev.get(API + "/projects/" + index).status(),
        dev.get(API + "/projects/" + index + "/stubs").status(),
        dev.get(API + "/projects/" + index + "/requests").status(),
        dev.get(API + "/projects/" + index + "/audit").status(),
        dev.put(API + "/projects/" + index + "/stubs/" + stubId, STUB).status(),
        dev.delete(API + "/projects/" + index + "/stubs/" + stubId).status(),
        dev.delete(API + "/projects/" + index).status(),
        dev.get(API + "/projects/" + index + "/export/postman").status());

    // Then
    assertThat(statuses).containsOnly(403);
    assertThat(admin.get(API + "/projects/" + index + "/stubs").json()).hasSize(1);
    assertThat(StreamSupport.stream(dev.get(API + "/projects").json().spliterator(), false).map(p -> p.get("name").asText())).containsExactly("Cart");
  }
}
