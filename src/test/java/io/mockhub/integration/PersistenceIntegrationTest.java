package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;

  @Test
  void projectsAndStubsSurviveARestart() {
    // Given
    try (MockHubServer first = MockHubServer.start(data)) {
      String id = first.api().post(API + "/projects", "{\"name\":\"Orders\"}").json().get("id").asText();
      first.api().post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/x\"},\"response\":{\"status\":201}}");
    }

    // When
    try (MockHubServer second = MockHubServer.start(data)) {
      // Then
      assertThat(second.api().get(API + "/projects").json().get(0).get("stubCount").asInt()).isEqualTo(1);
      assertThat(second.api().get("/orders/x").status()).isEqualTo(201);
    }
  }

  @Test
  void editsAndDeletesArePersisted() {
    // Given
    String id;
    String stubId;
    try (MockHubServer first = MockHubServer.start(data)) {
      id = first.api().post(API + "/projects", "{\"name\":\"Orders\"}").json().get("id").asText();
      stubId = first.api().post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/x\"}}").json().get("id").asText();
      String other = first.api().post(API + "/projects/" + id + "/stubs", "{\"request\":{\"method\":\"GET\",\"url\":\"/y\"}}").json().get("id").asText();

      // When
      first.api().put(API + "/projects/" + id + "/stubs/" + stubId, "{\"request\":{\"method\":\"GET\",\"url\":\"/x\"},\"response\":{\"status\":418}}");
      first.api().delete(API + "/projects/" + id + "/stubs/" + other);
    }

    // Then
    try (MockHubServer second = MockHubServer.start(data)) {
      assertThat(second.api().get("/orders/x").status()).isEqualTo(418);
      assertThat(second.api().get("/orders/y").status()).isEqualTo(404);
      assertThat(data.resolve("mappings").toFile().list()).hasSize(1);
    }
  }
}
