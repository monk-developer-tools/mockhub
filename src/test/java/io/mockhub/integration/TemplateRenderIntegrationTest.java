package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRenderIntegrationTest {

  private static final String RENDER = "/__admin/mockhub/api/templates/render";

  @TempDir
  Path data;
  private MockHubServer server;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_USERS", "admin:pw:admin,vic:pw:viewer"));
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void signedInPeopleTryTemplatesWithoutThePublicTools() {
    // Given
    ApiClient viewer = server.api().login("vic", "pw");
    String body = "{\"template\":\"{\\\"id\\\": \\\"{{request.path.[1]}}\\\", \\\"who\\\": \\\"{{request.query.name}}\\\"}\","
        + "\"request\":{\"method\":\"GET\",\"url\":\"/orders/42?name=Ada\",\"headers\":{},\"body\":\"\"}}";

    // When
    ApiClient.Result rendered = viewer.post(RENDER, body);
    ApiClient.Result broken = viewer.post(RENDER, "{\"template\":\"{{nope 1}}\",\"request\":{\"url\":\"/\"}}");
    ApiClient.Result anonymous = server.api().post(RENDER, body);

    // Then
    assertThat(rendered.status()).as(rendered.body()).isEqualTo(200);
    assertThat(rendered.json().get("output").asText()).isEqualTo("{\"id\": \"42\", \"who\": \"Ada\"}");
    assertThat(broken.status()).isEqualTo(422);
    assertThat(broken.json().get("error").asText()).contains("nope");
    assertThat(anonymous.status()).isEqualTo(401);
  }
}
