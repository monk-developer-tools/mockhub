package io.mockhub.callout;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class CalloutPortabilityTest {

  @Test
  void turnsMockHubCalloutsIntoWireMockWebhooks() {
    // Given
    ObjectNode stub = (ObjectNode) tree("{'serveEventListeners':["
        + "{'name':'mockhub-callout','parameters':{'method':'POST','url':'https://a.test','body':'{}'}},"
        + "{'name':'mockhub-callout','parameters':{'url':'https://b.test','forward':true}},"
        + "{'name':'recordState','parameters':{}}]}");

    // When
    CalloutPortability.toWireMockWebhooks(stub);

    // Then
    assertThat(stub.at("/serveEventListeners/0/name").asText()).isEqualTo("webhook");
    assertThat(stub.at("/serveEventListeners/0/parameters/body").asText()).isEqualTo("{}");
    assertThat(stub.at("/serveEventListeners/1/parameters/body").asText()).isEqualTo("{{{originalRequest.body}}}");
    assertThat(stub.at("/serveEventListeners/1/parameters/method").asText()).isEqualTo("{{originalRequest.method}}");
    assertThat(stub.at("/serveEventListeners/1/parameters").has("forward")).isFalse();
    assertThat(stub.at("/serveEventListeners/2/name").asText()).isEqualTo("recordState");
  }
}
