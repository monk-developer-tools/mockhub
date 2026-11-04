package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StubTargetAccessIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String STUB = "{\"name\":\"List orders\",\"request\":{\"method\":\"GET\",\"urlPath\":\"/orders\"},\"response\":{\"status\":200}}";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient admin;
  private ApiClient dev;
  private String cart;
  private String cartQa;
  private String index;
  private String vault;
  private String stubId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "root:pw:admin"));
    admin = server.api().login("root", "pw");
    String orgId = admin.post(API + "/orgs", "{\"name\":\"Acme\"}").json().get("id").asText();
    String checkout = team(orgId, "Checkout");
    String search = team(orgId, "Search");
    String secrets = team(orgId, "Secrets");
    cart = project("Cart", checkout);
    cartQa = project("Cart QA", checkout);
    index = project("Index", search);
    vault = project("Vault", secrets);
    admin.post(API + "/users", "{\"username\":\"dev\",\"displayName\":\"dev\",\"password\":\"longenough\",\"teamMemberships\":["
        + "{\"teamId\":\"" + checkout + "\",\"role\":\"editor\"},{\"teamId\":\"" + search + "\",\"role\":\"viewer\"}]}");
    dev = server.api().login("dev", "longenough");
    stubId = dev.post(API + "/projects/" + cart + "/stubs", STUB).json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String team(String orgId, String name) {
    return admin.post(API + "/orgs/" + orgId + "/teams", "{\"name\":\"" + name + "\"}").json().get("id").asText();
  }

  private String project(String name, String teamId) {
    return admin.post(API + "/projects", "{\"name\":\"" + name + "\",\"teamId\":\"" + teamId + "\"}").json().get("id").asText();
  }

  private int stubCount(String projectId) {
    return admin.get(API + "/projects/" + projectId + "/stubs").json().size();
  }

  @Test
  void editorsCopyAndMoveStubsIntoProjectsTheyCanEdit() {
    // When
    int copied = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/clone", "{}").status();
    int cloned = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/clone?target=" + cartQa, "{}").status();
    int moved = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/move?target=" + cartQa, "{}").status();

    // Then
    assertThat(copied).isEqualTo(201);
    assertThat(cloned).isEqualTo(201);
    assertThat(moved).isEqualTo(200);
    assertThat(stubCount(cart)).isEqualTo(1);
    assertThat(stubCount(cartQa)).isEqualTo(2);
  }

  @Test
  void stubsCannotBeCopiedOrMovedIntoProjectsTheEditorCannotChange() {
    // When
    int cloneToViewed = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/clone?target=" + index, "{}").status();
    int moveToViewed = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/move?target=" + index, "{}").status();
    int cloneToHidden = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/clone?target=" + vault, "{}").status();
    int moveToHidden = dev.post(API + "/projects/" + cart + "/stubs/" + stubId + "/move?target=" + vault, "{}").status();

    // Then
    assertThat(cloneToViewed).isEqualTo(403);
    assertThat(moveToViewed).isEqualTo(403);
    assertThat(cloneToHidden).isEqualTo(404);
    assertThat(moveToHidden).isEqualTo(404);
    assertThat(stubCount(cart)).isEqualTo(1);
    assertThat(stubCount(index)).isZero();
    assertThat(stubCount(vault)).isZero();
  }

  @Test
  void aTokenScopedToOneProjectCannotCopyStubsOutOfIt() {
    // Given
    ApiClient scoped = server.api().bearer(dev.post(API + "/auth/tokens",
        "{\"name\":\"cart only\",\"role\":\"editor\",\"projectIds\":[\"" + cart + "\"]}").json().get("secret").asText());

    // When
    int cloned = scoped.post(API + "/projects/" + cart + "/stubs/" + stubId + "/clone?target=" + cartQa, "{}").status();

    // Then
    assertThat(cloned).isEqualTo(404);
    assertThat(stubCount(cartQa)).isZero();
  }
}
