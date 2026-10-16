package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakeDataIntegrationTest {

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
    projectId = api.post(API + "/projects", "{\"name\":\"People\",\"slug\":\"people\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void templatesGenerateRealisticValuesOnEveryCall() {
    // Given
    api.post(API + "/projects/" + projectId + "/stubs", """
        {"request":{"method":"GET","urlPath":"/person"},
         "response":{"status":200,"headers":{"Content-Type":"application/json"},"transformers":["response-template"],
           "body":"{\\"name\\":\\"{{fake 'Name.fullName'}}\\",\\"email\\":\\"{{fake 'Internet.emailAddress'}}\\",\\"age\\":{{fake 'Number.numberBetween' 18 65}},\\"city\\":\\"{{fake 'Address.city' locale='de'}}\\",\\"oops\\":\\"{{fake 'Nope.nothing'}}\\"}"}}""");

    // When
    JsonNode first = api.get("/people/person").json();
    JsonNode second = api.get("/people/person").json();

    // Then
    assertThat(first.get("name").asText()).contains(" ");
    assertThat(first.get("email").asText()).contains("@");
    assertThat(first.get("age").asInt()).isBetween(18, 64);
    assertThat(first.get("city").asText()).isNotBlank();
    assertThat(first.get("oops").asText()).startsWith("[fake: can't generate");
    assertThat(second.get("email").asText()).isNotEqualTo(first.get("email").asText());
  }
}
