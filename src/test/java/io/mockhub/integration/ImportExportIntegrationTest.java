package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportExportIntegrationTest {

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
    projectId = api.post(API + "/projects", "{\"name\":\"Calc\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void importsAWsdlAndAnswersSoapCalls() throws IOException {
    // Given
    String wsdl = resource("calculator.wsdl");

    // When
    ApiClient.Result imported = api.postXml(API + "/projects/" + projectId + "/import/wsdl", wsdl);

    // Then
    assertThat(imported.json().get("imported").asInt()).isEqualTo(4);
    ApiClient.Result add = api.postXml("/calc/ws/calculator",
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><c:Add xmlns:c=\"http://example.com/calculator\"><c:a>1</c:a></c:Add></s:Body></s:Envelope>");
    assertThat(add.status()).isEqualTo(200);
    assertThat(add.body()).contains("AddResponse");

    ApiClient.Result unknown = api.postXml("/calc/ws/calculator", "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><Nope/></s:Body></s:Envelope>");
    assertThat(unknown.status()).isEqualTo(500);
    assertThat(unknown.body()).contains("faultstring");

    ApiClient.Result served = api.get("/calc/ws/calculator?wsdl");
    assertThat(served.status()).isEqualTo(200);
    assertThat(served.body()).contains("location=\"" + server.baseUrl() + "/calc/ws/calculator\"");
  }

  @Test
  void exportsPostmanBrunoAndWireMockFiles() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", "{\"name\":\"Ping\",\"request\":{\"method\":\"GET\",\"url\":\"/ping\"}}");

    // When
    ApiClient.Result postman = api.get(API + "/projects/" + projectId + "/export/postman");
    ApiClient.Result bruno = api.get(API + "/projects/" + projectId + "/export/bruno");
    ApiClient.Result wiremock = api.get(API + "/projects/" + projectId + "/export/wiremock");

    // Then
    assertThat(postman.contentDisposition()).contains("calc.postman_collection.json");
    assertThat(postman.json().at("/variable/0/value").asText()).isEqualTo(server.baseUrl() + "/calc");
    assertThat(bruno.contentType()).isEqualTo("application/zip");
    assertThat(bruno.bytes()[0]).isEqualTo((byte) 'P');
    assertThat(wiremock.json().at("/mappings/0/request/url").asText()).isEqualTo("/calc/ping");
    assertThat(api.get(API + "/projects/" + projectId + "/export/nope").status()).isEqualTo(400);
  }

  @Test
  void wireMockExportsRoundTripIntoAnotherProject() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/ping\"},\"response\":{\"status\":202}}");
    String exported = api.get(API + "/projects/" + projectId + "/export/wiremock").body();
    String other = api.post(API + "/projects", "{\"name\":\"Other\"}").json().get("id").asText();

    // When
    api.post(API + "/projects/" + other + "/import/wiremock", exported);

    // Then
    assertThat(api.get("/other/ping").status()).isEqualTo(202);
  }

  @Test
  void importsOpenApi() throws IOException {
    // When
    ApiClient.Result imported = api.send("POST", API + "/projects/" + projectId + "/import/openapi", resource("petstore.yaml"), "application/yaml");

    // Then
    assertThat(imported.json().get("imported").asInt()).isEqualTo(3);
    assertThat(api.get("/calc/pets/5").json().get("name").asText()).isEqualTo("Fido");
  }

  @Test
  void backupRestoresProjectsAndStubsOnAFreshServer(@TempDir Path otherData) {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/ping\"},\"response\":{\"status\":203}}");
    String backup = api.get(API + "/backup").body();

    try (MockHubServer fresh = MockHubServer.start(otherData)) {
      // When
      ApiClient.Result restored = fresh.api().post(API + "/restore", backup);

      // Then
      assertThat(restored.json().get("projects").asInt()).isEqualTo(1);
      assertThat(fresh.api().get("/calc/ping").status()).isEqualTo(203);
    }
  }

  private static String resource(String name) throws IOException {
    try (InputStream in = ImportExportIntegrationTest.class.getResourceAsStream("/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
