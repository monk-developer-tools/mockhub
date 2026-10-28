package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretsIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String KEY = "k-9f2c71d4";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private ApiClient manager;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin"));
    admin = server.api().login("root", "pw");
    String orgId = admin.post(API + "/orgs", "{\"name\":\"Acme\"}").json().get("id").asText();
    String teamId = admin.post(API + "/orgs/" + orgId + "/teams", "{\"name\":\"Checkout\"}").json().get("id").asText();
    admin.post(API + "/users", "{\"username\":\"lead\",\"password\":\"longenough\",\"teamMemberships\":[{\"teamId\":\"" + teamId + "\",\"role\":\"manager\"}]}");
    projectId = admin.post(API + "/projects", "{\"name\":\"Payments\",\"teamId\":\"" + teamId + "\",\"apiKey\":{\"header\":\"X-API-Key\",\"value\":\"" + KEY + "\"}}")
        .json().get("id").asText();
    admin.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/ping\"},\"response\":{\"status\":200}}");
    manager = server.api().login("lead", "longenough");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void onlyAdminsSeeAProjectsApiKey() {
    // When
    JsonNode asManager = manager.get(API + "/projects/" + projectId).json();
    JsonNode asAdmin = admin.get(API + "/projects/" + projectId).json();
    String list = manager.get(API + "/projects").body();

    // Then
    assertThat(asManager.at("/apiKey/header").asText()).isEqualTo("X-API-Key");
    assertThat(asManager.at("/apiKey/set").asBoolean()).isTrue();
    assertThat(asManager.at("/apiKey/value").isMissingNode()).isTrue();
    assertThat(list).doesNotContain(KEY);
    assertThat(asAdmin.at("/apiKey/value").asText()).isEqualTo(KEY);
  }

  @Test
  void onlyAdminsSetOrChangeApiKeys() {
    // When
    ApiClient.Result change = manager.put(API + "/projects/" + projectId, "{\"apiKey\":{\"value\":\"stolen\"}}");
    ApiClient.Result remove = manager.put(API + "/projects/" + projectId, "{\"apiKey\":null}");
    ApiClient.Result rename = manager.put(API + "/projects/" + projectId, "{\"description\":\"Card payments\"}");

    // Then
    assertThat(change.status()).isEqualTo(403);
    assertThat(remove.status()).isEqualTo(403);
    assertThat(rename.status()).isEqualTo(200);
    assertThat(server.api().send("GET", "/payments/ping", null, null, Map.of("X-API-Key", KEY)).status()).isEqualTo(200);
    assertThat(server.api().get("/payments/ping").status()).isEqualTo(401);
  }

  @Test
  void exportsUseAPlaceholderInsteadOfTheKeyForNonAdmins() {
    // When
    String forManager = manager.get(API + "/projects/" + projectId + "/export/postman").body();
    String forAdmin = admin.get(API + "/projects/" + projectId + "/export/postman").body();

    // Then
    assertThat(forManager).doesNotContain(KEY).contains("{{apiKey}}");
    assertThat(forAdmin).contains(KEY);
  }

  @Test
  void serverSecretsAndOrganisationsStayWithAdmins() {
    // Then
    assertThat(manager.get(API + "/ai/settings").status()).isEqualTo(403);
    assertThat(manager.put(API + "/ai/settings", "{\"apiKey\":\"sk-x\"}").status()).isEqualTo(403);
    assertThat(manager.get(API + "/sso").status()).isEqualTo(403);
    assertThat(manager.post(API + "/orgs", "{\"name\":\"Shadow IT\"}").status()).isEqualTo(403);
    assertThat(admin.get(API + "/ai/settings").body()).doesNotContain("sk-");
  }
}
