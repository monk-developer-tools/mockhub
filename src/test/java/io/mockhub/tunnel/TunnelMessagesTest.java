package io.mockhub.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TunnelMessagesTest {

  @Test
  void aRequestSurvivesTheRoundTripWithRepeatedHeadersAndBinaryBody() {
    // Given
    byte[] body = {0, 1, (byte) 0xff, 42};
    TunnelMessages.Request request = new TunnelMessages.Request("r1", "POST", "/a?b=c",
        List.of(new Header("Accept", "a"), new Header("Accept", "b")), body);

    // When
    TunnelMessages.Request read = TunnelMessages.readRequest(TunnelMessages.write(request));

    // Then
    assertThat(read.id()).isEqualTo("r1");
    assertThat(read.method()).isEqualTo("POST");
    assertThat(read.path()).isEqualTo("/a?b=c");
    assertThat(read.headers()).containsExactly(new Header("Accept", "a"), new Header("Accept", "b"));
    assertThat(read.body()).containsExactly(body);
  }

  @Test
  void aResponseSurvivesTheRoundTrip() {
    // Given
    TunnelMessages.Response response = new TunnelMessages.Response(201, List.of(new Header("X-A", "1")), "ok".getBytes(StandardCharsets.UTF_8));

    // When
    TunnelMessages.Response read = TunnelMessages.readResponse(TunnelMessages.write(response));

    // Then
    assertThat(read.status()).isEqualTo(201);
    assertThat(read.headers()).containsExactly(new Header("X-A", "1"));
    assertThat(new String(read.body(), StandardCharsets.UTF_8)).isEqualTo("ok");
  }

  @Test
  void refusesAnAnswerWithoutAValidStatusOrBody() {
    // Given / When / Then
    assertThatThrownBy(() -> TunnelMessages.readResponse(Jsons.MAPPER.readTree("{\"status\":42}")))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(400));
    assertThatThrownBy(() -> TunnelMessages.readResponse(Jsons.MAPPER.readTree("{\"status\":200,\"body\":\"%%%\"}")))
        .hasMessageContaining("base64");
  }

  @Test
  void theForwardedPathDropsTheProjectPrefix() {
    // Given / When / Then
    assertThat(TunnelProxyTransformer.forwardedPath("/pay/orders/7?x=1", "/pay")).isEqualTo("/orders/7?x=1");
    assertThat(TunnelProxyTransformer.forwardedPath("/pay", "/pay")).isEqualTo("/");
    assertThat(TunnelProxyTransformer.forwardedPath("/pay?x=1", "/pay")).isEqualTo("/?x=1");
    assertThat(TunnelProxyTransformer.forwardedPath("/orders", null)).isEqualTo("/orders");
  }
}
