package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EchoIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Svc\",\"slug\":\"svc\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private void echoStub(String response) {
    ApiClient.Result r = api.post(API + "/projects/" + projectId + "/stubs",
        "{\"request\":{\"method\":\"ANY\",\"urlPattern\":\"/.*\"},\"response\":" + response + "}");
    assertThat(r.status()).as(r.body()).isEqualTo(201);
  }

  @Test
  void describesTheRequestWithThePathInsideTheProject() {
    // Given
    echoStub("{\"status\":200,\"transformers\":[\"mockhub-echo\"]}");

    // When
    ApiClient.Result r = api.send("POST", "/svc/orders/7?x=1&x=2&y=z", "{\"total\":12.5}", "application/json", Map.of("X-Trace", "abc"));

    // Then
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.contentType()).startsWith("application/json");
    JsonNode echo = r.json();
    assertThat(echo.get("method").asText()).isEqualTo("POST");
    assertThat(echo.get("path").asText()).isEqualTo("/orders/7");
    assertThat(echo.at("/query/x").toString()).isEqualTo("[\"1\",\"2\"]");
    assertThat(echo.at("/query/y").asText()).isEqualTo("z");
    assertThat(echo.at("/headers/X-Trace").asText()).isEqualTo("abc");
    assertThat(echo.at("/json/total").asDouble()).isEqualTo(12.5);
    assertThat(echo.get("body").asText()).isEqualTo("{\"total\":12.5}");
  }

  @Test
  void keepsTheStubsStatusAndHeadersAndParsesForms() {
    // Given
    echoStub("{\"status\":202,\"headers\":{\"X-Mock\":\"echo\",\"Content-Type\":\"text/plain\"},\"transformers\":[\"mockhub-echo\"]}");

    // When
    ApiClient.Result r = api.send("PUT", "/svc/", "a=1&b=two+words", "application/x-www-form-urlencoded", Map.of());

    // Then
    assertThat(r.status()).isEqualTo(202);
    assertThat(r.header("X-Mock")).isEqualTo("echo");
    assertThat(r.contentType()).startsWith("application/json");
    assertThat(r.json().get("path").asText()).isEqualTo("/");
    assertThat(r.json().at("/form/b").asText()).isEqualTo("two words");
  }

  @Test
  void refusesBodiesOverOneMegabyte() {
    // Given
    echoStub("{\"status\":200,\"transformers\":[\"mockhub-echo\"]}");

    // When
    ApiClient.Result r = api.send("POST", "/svc/big", "x".repeat(1024 * 1024 + 1), "text/plain", Map.of());

    // Then
    assertThat(r.status()).isEqualTo(413);
    assertThat(r.json().get("error").asText()).contains("1 MB");
  }
}
