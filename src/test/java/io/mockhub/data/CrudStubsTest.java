package io.mockhub.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrudStubsTest {

  @Test
  void buildsOneStubThatTheTransformerAndTheUiRecognise() {
    // Given
    CrudResource resource = new CrudResource("v1/orders", "orderId", CrudResource.IdType.UUID);

    // When
    ObjectNode stub = CrudStubs.stub(resource);
    StubMapping mapping = Jsons.toWireMock(stub, StubMapping.class);

    // Then
    assertThat(stub.at("/request/method").asText()).isEqualTo("ANY");
    assertThat(stub.at("/request/urlPathPattern").asText()).isEqualTo("/v1/orders(/[^/]+)?/?");
    assertThat(stub.at("/response/transformers/0").asText()).isEqualTo("mockhub-crud");
    assertThat(stub.at("/response/transformerParameters/idField").asText()).isEqualTo("orderId");
    assertThat(CrudStubs.of(mapping)).contains(resource);
    assertThat(CrudStubs.resources(List.of(mapping))).containsKey("v1/orders");
  }
}
