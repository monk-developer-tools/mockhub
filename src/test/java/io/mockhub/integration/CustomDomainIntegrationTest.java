package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomDomainIntegrationTest {

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
    projectId = api.post(API + "/projects", "{\"name\":\"Payments\",\"slug\":\"payments\",\"domains\":[\"payments.mocks.test\"]}").json().get("id").asText();
    ApiClient.Result stub = api.post(API + "/projects/" + projectId + "/stubs",
        "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/charges/42\"},\"response\":{\"status\":200,\"body\":\"charge {{request.path}}\",\"transformers\":[\"response-template\"]}}");
    assertThat(stub.status()).as(stub.body()).isEqualTo(201);
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String call(String host, String path) {
    try (Socket socket = new Socket("localhost", server.port())) {
      socket.setSoTimeout(20_000);
      OutputStream out = socket.getOutputStream();
      out.write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      out.flush();
      InputStream in = socket.getInputStream();
      ByteArrayOutputStream response = new ByteArrayOutputStream();
      in.transferTo(response);
      return response.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void aMappedHostAnswersWithoutTheBasePath() {
    // When
    String response = call("payments.mocks.test:8080", "/charges/42");

    // Then
    assertThat(response).startsWith("HTTP/1.1 200").contains("charge /payments/charges/42");
  }

  @Test
  void theBasePathStillWorksAndOtherHostsAreNotRebased() {
    // When
    String byPath = call("localhost", "/payments/charges/42");
    String otherHost = call("other.mocks.test", "/charges/42");

    // Then
    assertThat(byPath).startsWith("HTTP/1.1 200");
    assertThat(otherHost).startsWith("HTTP/1.1 404");
  }

  @Test
  void callsThroughADomainLandInTheProjectsRequestLog() {
    // Given
    call("PAYMENTS.mocks.test", "/charges/42");

    // When
    ApiClient.Result log = api.get(API + "/projects/" + projectId + "/requests");

    // Then
    assertThat(log.body()).contains("/payments/charges/42");
  }

  @Test
  void theAdminApiIsNotRebasedOnAMappedHost() {
    // When
    String response = call("payments.mocks.test", "/__admin/mappings");

    // Then
    assertThat(response).doesNotStartWith("HTTP/1.1 404");
  }

  @Test
  void aDomainCanPointToOneProjectOnly() {
    // When
    ApiClient.Result other = api.post(API + "/projects", "{\"name\":\"Other\",\"domains\":[\"payments.mocks.test\"]}");
    ApiClient.Result invalid = api.put(API + "/projects/" + projectId, "{\"domains\":[\"https://x.test\"]}");

    // Then
    assertThat(other.status()).isEqualTo(409);
    assertThat(other.body()).contains("already points to project 'Payments'");
    assertThat(invalid.status()).isEqualTo(400);
  }
}
