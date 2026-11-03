package io.mockhub.callout;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.TargetGuard;
import io.mockhub.support.Requests;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalloutServiceTest {

  private final List<URI> sent = new ArrayList<>();
  private final InMemoryCalloutLog log = new InMemoryCalloutLog(50);

  private CalloutService service(HttpSender sender) {
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {93, (byte) 184, (byte) 215, 14})});
    return new CalloutService(new CalloutRenderer(TemplateEngine::defaultTemplateEngine),
        new CalloutTargets(guard, () -> 8080, ""), sender, log, Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));
  }

  private static ServeEvent event() {
    StubMapping stub = StubMapping.buildFrom("""
        {"id":"8f0c6b9e-2f53-4d1f-9a49-6f1c2b7b8a11","name":"Take payment","request":{"method":"POST"},"response":{"status":202},
         "metadata":{"mockhub":{"projectId":"p1"}}}""");
    return Requests.event(Requests.logged("POST", "http://mocks.test/pay/payments", Map.of(), "{\"id\":\"pay_1\"}"), stub);
  }

  @Test
  void sendsTheCalloutAndLogsTheAnswerUnderTheStubsProject() {
    // Given
    CalloutService service = service((uri, method, headers, body, timeout, max) -> {
      sent.add(uri);
      return new HttpSender.Raw(201, List.of(new Header("Content-Type", "application/json")), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), false);
    });
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "{\"id\":\"{{jsonPath originalRequest.body '$.id'}}\"}", 0, false);

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.ASYNC);

    // Then
    assertThat(sent).containsExactly(URI.create("https://partner.test/cb"));
    assertThat(outcome.entry().succeeded()).isTrue();
    assertThat(outcome.entry().request().body()).isEqualTo("{\"id\":\"pay_1\"}");
    assertThat(new String(outcome.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    assertThat(log.list("p1", 10)).singleElement().satisfies(e -> {
      assertThat(e.stubName()).isEqualTo("Take payment");
      assertThat(e.stubId()).isEqualTo("8f0c6b9e-2f53-4d1f-9a49-6f1c2b7b8a11");
      assertThat(e.mode()).isEqualTo("async");
      assertThat(e.at()).isEqualTo("2026-11-03T10:00:00Z");
      assertThat(e.trigger().url()).isEqualTo("/pay/payments");
      assertThat(e.response().status()).isEqualTo(201);
    });
  }

  @Test
  void logsConnectionFailuresInPlainWords() {
    // Given
    CalloutService service = service((uri, method, headers, body, timeout, max) -> {
      throw new ConnectException("refused");
    });

    // When
    CalloutService.Outcome outcome = service.call(new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "", 0, false), event(), CalloutService.SYNC);

    // Then
    assertThat(outcome.entry().response()).isNull();
    assertThat(outcome.entry().error()).startsWith("Couldn't connect");
    assertThat(outcome.entry().succeeded()).isFalse();
    assertThat(log.list("p1", 10)).hasSize(1);
  }

  @Test
  void logsTheTemplateWhenItCannotBeRendered() {
    // Given
    CalloutService service = service((uri, method, headers, body, timeout, max) -> {
      throw new AssertionError("must not send");
    });

    // When
    CalloutService.Outcome outcome = service.call(new CalloutSpec("POST", "{{#each}}", Map.of(), "", 0, false), event(), CalloutService.ASYNC);

    // Then
    assertThat(outcome.entry().error()).contains("Template error");
    assertThat(outcome.entry().request().url()).isEqualTo("{{#each}}");
  }
}
