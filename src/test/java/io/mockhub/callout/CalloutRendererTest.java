package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.playground.Header;
import io.mockhub.support.Requests;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalloutRendererTest {

  private final TemplateEngine engine = TemplateEngine.defaultTemplateEngine();
  private final CalloutRenderer renderer = new CalloutRenderer(() -> engine);

  private static ServeEvent incoming() {
    return Requests.event(Requests.logged("POST", "http://mocks.test/pay/payments?region=eu",
        Map.of("Content-Type", "application/json", "X-Trace", "abc", "Host", "mocks.test", "Content-Length", "64"),
        "{\"id\":\"pay_42\",\"callbackUrl\":\"https://partner.test/cb\"}"), null);
  }

  private static LoggedResponse mockResponse() {
    return Requests.response(202, "application/json", "{\"reference\":\"R-1\"}");
  }

  @Test
  void rendersUrlHeadersAndBodyFromTheRequestAndTheMockResponse() {
    // Given
    CalloutSpec spec = new CalloutSpec("post", "{{jsonPath originalRequest.body '$.callbackUrl'}}",
        Map.of("X-Trace", "{{originalRequest.headers.X-Trace}}"),
        "{\"payment\":\"{{jsonPath originalRequest.body '$.id'}}\",\"ref\":\"{{jsonPath originalResponse.body '$.reference'}}\",\"status\":{{originalResponse.status}},\"region\":\"{{originalRequest.query.region}}\"}",
        0, false);

    // When
    OutboundRequest out = renderer.render(spec, incoming(), mockResponse());

    // Then
    assertThat(out.method()).isEqualTo("POST");
    assertThat(out.url()).isEqualTo("https://partner.test/cb");
    assertThat(out.headers()).containsExactly(new Header("X-Trace", "abc"));
    assertThat(out.body()).isEqualTo("{\"payment\":\"pay_42\",\"ref\":\"R-1\",\"status\":202,\"region\":\"eu\"}");
  }

  @Test
  void forwardsTheIncomingRequestWithoutHopByHopHeaders() {
    // Given
    CalloutSpec spec = new CalloutSpec(null, "https://partner.test/forward", Map.of("X-Extra", "yes"), "ignored", 0, true);

    // When
    OutboundRequest out = renderer.render(spec, incoming(), null);

    // Then
    assertThat(out.method()).isEqualTo("POST");
    assertThat(out.body()).contains("pay_42");
    assertThat(out.headers()).extracting(Header::name).contains("Content-Type", "X-Trace", "X-Extra").doesNotContain("Host", "Content-Length");
  }

  @Test
  void leavesTheBodyOutOfGetCallouts() {
    // Given
    CalloutSpec spec = new CalloutSpec("GET", "https://partner.test/ping", Map.of(), "{\"x\":1}", 0, false);

    // When
    OutboundRequest out = renderer.render(spec, incoming(), null);

    // Then
    assertThat(out.body()).isEmpty();
  }

  @Test
  void explainsTemplateErrors() {
    // Given
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "{{#each}}", 0, false);

    // When / Then
    assertThatThrownBy(() -> renderer.render(spec, incoming(), null))
        .isInstanceOf(CalloutException.class)
        .hasMessageContaining("Template error");
  }
}
