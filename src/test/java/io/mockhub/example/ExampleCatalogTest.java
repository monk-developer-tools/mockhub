package io.mockhub.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import org.junit.jupiter.api.Test;

class ExampleCatalogTest {

  private final ExampleCatalog catalog = ExampleCatalog.fromClasspath();

  @Test
  void listsTheBundledExamples() {
    assertThat(catalog.all()).extracting(Example::id)
        .containsExactly("rest-basics", "fake-data", "templating", "patterns", "crud", "stateful-cart", "webhooks", "chaos", "bookshop", "soap-calculator");
  }

  @Test
  void everyExampleStubIsAValidWireMockMapping() throws Exception {
    for (Example example : catalog.all()) {
      // Given
      JsonNode content = catalog.content(example);

      // When / Then
      for (JsonNode stub : content.path("stubs")) {
        StubMapping parsed = Json.read(Jsons.MAPPER.writeValueAsString(stub), StubMapping.class);
        assertThat(parsed.getName()).as(example.id()).isNotBlank();
      }
      for (JsonNode imp : content.path("imports")) {
        assertThat(catalog.file(imp.get("file").asText())).isNotBlank();
      }
    }
  }

  @Test
  void unknownExampleIsNotFound() {
    assertThatThrownBy(() -> catalog.get("nope")).extracting("status").isEqualTo(404);
  }
}
