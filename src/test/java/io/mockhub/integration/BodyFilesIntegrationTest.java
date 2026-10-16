package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BodyFilesIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final byte[] PDF = "%PDF-1.4 fake invoice".getBytes(StandardCharsets.UTF_8);

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Billing\",\"slug\":\"billing\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private ApiClient.Result upload(String name, byte[] bytes) {
    return api.send("POST", API + "/projects/" + projectId + "/files?name=" + name, new String(bytes, StandardCharsets.ISO_8859_1), "application/pdf");
  }

  @Test
  void uploadedFilesServeAsResponseBodiesAndShowWhoUsesThem() {
    // Given
    ApiClient.Result uploaded = upload("invoice-42.pdf", PDF);
    String path = uploaded.json().get("path").asText();
    api.post(API + "/projects/" + projectId + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/invoices/42\"},\"response\":{\"status\":200,\"bodyFileName\":\"" + path
            + "\",\"headers\":{\"Content-Type\":\"application/pdf\"}}}");

    // When
    ApiClient.Result served = api.get("/billing/invoices/42");
    JsonNode list = api.get(API + "/projects/" + projectId + "/files").json();
    ApiClient.Result downloaded = api.get(API + "/projects/" + projectId + "/files/content?name=invoice-42.pdf");

    // Then
    assertThat(uploaded.status()).isEqualTo(201);
    assertThat(path).isEqualTo("projects/" + projectId + "/invoice-42.pdf");
    assertThat(served.status()).isEqualTo(200);
    assertThat(served.body()).isEqualTo("%PDF-1.4 fake invoice");
    assertThat(list.get(0).get("contentType").asText()).isEqualTo("application/pdf");
    assertThat(list.get(0).get("usedBy").asInt()).isEqualTo(1);
    assertThat(downloaded.contentDisposition()).contains("invoice-42.pdf");
  }

  @Test
  void rejectsUnsafeNamesAndDeletesFiles() {
    // Given
    upload("logo.png", PDF);

    // Then
    assertThat(upload("../escape.txt", PDF).status()).isEqualTo(400);
    assertThat(upload("empty.txt", new byte[0]).status()).isEqualTo(400);
    assertThat(api.delete(API + "/projects/" + projectId + "/files?name=logo.png").status()).isEqualTo(204);
    assertThat(api.delete(API + "/projects/" + projectId + "/files?name=logo.png").status()).isEqualTo(404);
    assertThat(api.get(API + "/projects/" + projectId + "/files").json()).isEmpty();
  }

  @Test
  void filesTravelInBackupsAndGoWithTheProject(@TempDir Path other) {
    // Given
    String path = upload("invoice-42.pdf", PDF).json().get("path").asText();
    api.post(API + "/projects/" + projectId + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/invoices/42\"},\"response\":{\"status\":200,\"bodyFileName\":\"" + path + "\"}}");
    String backup = api.get(API + "/backup").body();

    // When
    try (MockHubServer fresh = MockHubServer.start(other)) {
      fresh.api().post(API + "/restore", backup);

      // Then
      assertThat(fresh.api().get("/billing/invoices/42").body()).isEqualTo("%PDF-1.4 fake invoice");
    }
    api.delete(API + "/projects/" + projectId);
    assertThat(data.resolve("__files/projects/" + projectId)).doesNotExist();
  }
}
