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

  private final List<Long> waits = new ArrayList<>();

  private CalloutService authed(HttpSender sender, CalloutAuth auth) {
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {93, (byte) 184, (byte) 215, 14})});
    return new CalloutService(new CalloutRenderer(TemplateEngine::defaultTemplateEngine),
        new CalloutTargets(guard, () -> 8080, ""), sender, log, Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC),
        auth, waits::add);
  }

  private static HttpSender.Raw answer(int status) {
    return new HttpSender.Raw(status, List.of(), new byte[0], false);
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

  @Test
  void addsTheConnectionsHeaderAndHidesItInTheLog() {
    // Given
    List<List<Header>> headersSent = new ArrayList<>();
    CalloutService service = authed((uri, method, headers, body, timeout, max) -> {
      headersSent.add(headers);
      return answer(200);
    }, (projectId, connection) -> new Header("Authorization", "Bearer tok-" + projectId + "-" + connection));
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of("authorization", "Bearer stale", "X-Id", "1"), "{}", 0, false,
        "bank", null);

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.ASYNC);

    // Then
    assertThat(headersSent.get(0)).extracting(Header::name, Header::value)
        .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("X-Id", "1"), org.assertj.core.groups.Tuple.tuple("Authorization", "Bearer tok-p1-bank"));
    assertThat(outcome.entry().request().headers()).extracting(Header::value)
        .contains("\u2022\u2022\u2022 (connection bank)")
        .noneMatch(v -> v.contains("tok-"));
  }

  @Test
  void logsAConnectionProblemWithoutSendingTheCallout() {
    // Given
    CalloutService service = authed((uri, method, headers, body, timeout, max) -> {
      sent.add(uri);
      return answer(200);
    }, (projectId, connection) -> {
      throw new CalloutException("Connection bank: the token endpoint answered 401");
    });
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "", 0, false, "bank", new CalloutRetry(3, 10, null));

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.SYNC);

    // Then
    assertThat(sent).isEmpty();
    assertThat(outcome.entry().error()).isEqualTo("Connection bank: the token endpoint answered 401");
    assertThat(log.list("p1", 10)).hasSize(1);
  }

  @Test
  void retriesServerErrorsWithGrowingWaitsAndLogsEveryAttempt() {
    // Given
    List<Integer> statuses = new ArrayList<>(List.of(503, 500, 200));
    CalloutService service = authed((uri, method, headers, body, timeout, max) -> answer(statuses.remove(0)), CalloutAuth.NONE);
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "", 0, false, null, new CalloutRetry(5, 100, null));

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.SYNC);

    // Then
    assertThat(outcome.entry().response().status()).isEqualTo(200);
    assertThat(outcome.entry().attempt()).isEqualTo(3);
    assertThat(waits).containsExactly(100L, 200L);
    assertThat(log.list("p1", 10)).extracting(CalloutEntry::attempt).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void stopsAfterTheLastRetryAndKeepsTheFailure() {
    // Given
    CalloutService service = authed((uri, method, headers, body, timeout, max) -> {
      throw new ConnectException("refused");
    }, CalloutAuth.NONE);
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "", 0, false, null, new CalloutRetry(2, 50, null));

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.SYNC);

    // Then
    assertThat(outcome.entry().attempt()).isEqualTo(3);
    assertThat(outcome.entry().error()).startsWith("Couldn't connect");
    assertThat(waits).containsExactly(50L, 100L);
  }

  @Test
  void doesNotRetryAClientError() {
    // Given
    CalloutService service = authed((uri, method, headers, body, timeout, max) -> answer(400), CalloutAuth.NONE);
    CalloutSpec spec = new CalloutSpec("POST", "https://partner.test/cb", Map.of(), "", 0, false, null, new CalloutRetry(3, 50, null));

    // When
    CalloutService.Outcome outcome = service.call(spec, event(), CalloutService.SYNC);

    // Then
    assertThat(outcome.entry().attempt()).isEqualTo(1);
    assertThat(waits).isEmpty();
  }
}
