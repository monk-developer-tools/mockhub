package io.mockhub.importer;

import static io.mockhub.support.Fixtures.project;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WireMockJsonImporterTest {

  private final WireMockJsonImporter importer = new WireMockJsonImporter();

  @Test
  void acceptsAMappingsEnvelopeAndStripsIdsAndBasePath() {
    // Given
    String body = "{\"mappings\":[{\"id\":\"5b8a7e5c-0000-0000-0000-000000000000\",\"request\":{\"url\":\"/pay/a\"}},{\"request\":{\"url\":\"/b\"}}]}";

    // When
    StubImporter.Result result = importer.parse(new StubImporter.Request(project("pay"), body, Map.of()));

    // Then
    assertThat(result.stubs()).extracting(s -> s.at("/request/url").asText()).containsExactly("/a", "/b");
    assertThat(result.stubs().get(0).has("id")).isFalse();
  }

  @Test
  void acceptsAnArrayOrASingleStub() {
    assertThat(importer.parse(new StubImporter.Request(project("pay"), "[{\"request\":{}},{\"request\":{}}]", Map.of())).stubs()).hasSize(2);
    assertThat(importer.parse(new StubImporter.Request(project("pay"), "{\"request\":{}}", Map.of())).stubs()).hasSize(1);
  }

  @Test
  void rejectsInvalidJson() {
    assertThatThrownBy(() -> importer.parse(new StubImporter.Request(project("pay"), "{nope", Map.of()))).hasMessageContaining("Invalid JSON");
  }
}
