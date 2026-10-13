package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin,qa:pw:viewer"));
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void protectsTheAdminApiButNotHealthUiOrMocks() {
    ApiClient anonymous = server.api();
    assertThat(anonymous.get(API + "/projects").status()).isEqualTo(401);
    assertThat(anonymous.get("/__admin/mappings").status()).isEqualTo(401);
    assertThat(anonymous.get("/__admin/health").status()).isEqualTo(200);
    assertThat(anonymous.get(API + "/auth/info").json().get("authEnabled").asBoolean()).isTrue();
    assertThat(anonymous.get("/__admin/mockhub/ui/").status()).isEqualTo(200);
    assertThat(anonymous.get("/anything").status()).isEqualTo(404);
  }

  @Test
  void viewersCanReadButNotWrite() {
    // Given
    server.api().as("admin", "pw").post(API + "/projects", "{\"name\":\"Orders\"}");
    ApiClient viewer = server.api().as("qa", "pw");

    // When / Then
    assertThat(viewer.get(API + "/projects").json()).hasSize(1);
    assertThat(viewer.post(API + "/projects", "{\"name\":\"X\"}").status()).isEqualTo(403);
    assertThat(viewer.post(API + "/auth/login", "").json().get("role").asText()).isEqualTo("viewer");
  }

  @Test
  void loginRejectsBadCredentials() {
    assertThat(server.api().as("admin", "wrong").post(API + "/auth/login", "").status()).isEqualTo(401);
  }
}
