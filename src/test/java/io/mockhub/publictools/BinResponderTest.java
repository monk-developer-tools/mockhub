package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.TargetGuard;
import io.mockhub.template.ValueHelpers;
import io.mockhub.tunnel.TunnelException;
import io.mockhub.tunnel.TunnelGateway;
import io.mockhub.tunnel.TunnelMessages;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BinResponderTest {

  private final ExecutorService workers = Executors.newFixedThreadPool(2);
  private final TemplatePlayground templates = new TemplatePlayground(List.of(new ValueHelpers()), workers, 2000);
  private final List<String> sent = new ArrayList<>();
  private final List<String> tunnelled = new ArrayList<>();
  private boolean tunnelUp = true;

  private final HttpSender upstream = (uri, method, headers, body, timeout, max) -> {
    sent.add(method + " " + uri + " " + headers.stream().map(h -> h.name() + "=" + h.value()).sorted().toList());
    return new HttpSender.Raw(201, List.of(new Header("Content-Type", "application/json"), new Header("Transfer-Encoding", "chunked")),
        "{\"from\":\"upstream\"}".getBytes(StandardCharsets.UTF_8), false);
  };

  private final TunnelGateway tunnel = (projectId, uri, method, headers, body, timeout) -> {
    if (!tunnelUp) throw new TunnelException(502, "Tunnel bin-abcdefghijkm isn't connected");
    tunnelled.add(projectId + " " + method + " " + uri);
    return new TunnelMessages.Response(200, List.of(new Header("Content-Type", "text/plain")), "from laptop".getBytes(StandardCharsets.UTF_8));
  };

  private BinResponder responder(String resolvedIp) {
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByName(resolvedIp)});
    return new BinResponder(templates, upstream, upstream, guard, tunnel, bound -> 0, System::currentTimeMillis);
  }

  @AfterEach
  void stop() {
    workers.shutdownNow();
  }

  private static BinMatcher.Call call(String method, String path, String body) {
    PublicRequest request = new PublicRequest(method, "/bins/abcdefghijkm" + path, "page=2", Map.of("page", List.of("2")),
        Map.of("Content-Type", "application/json", "Host", "mockhub.example", "X-Forwarded-For", "203.0.113.9"), body, body.length(), "203.0.113.9");
    return new BinMatcher.Call(method, path, request);
  }

  private static Bins.Settings withRules(String rules) throws Exception {
    return Bins.Settings.DEFAULT.withRules(true, BinRule.listFrom(Jsons.MAPPER.readTree(rules)));
  }

  @Test
  void aPathPatternsParametersFillTheAnswer() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"name": "One user", "method": "QUERY", "conditions": [{"target": "path", "op": "template", "value": "/users/:id"}],
          "answers": [{"status": 200, "template": true, "headers": {"X-User": "{{pathParams.id}}"}, "body": "{\\"user\\": \\"{{pathParams.id}}\\"}"}]}]
        """);

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("QUERY", "/users/u_42", ""));

    // Then
    assertThat(answer.by()).isEqualTo("rule");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("{\"user\": \"u_42\"}");
    assertThat(answer.headers()).containsEntry("X-User", "u_42");
  }

  @Test
  void aFileAnswerSendsItsBytesWithATypeFromItsName() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"answers": [{"status": 200, "body": "iVBORw==", "fileName": "logo.png"}]}]
        """);

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("GET", "/logo", ""));

    // Then
    assertThat(answer.body()).containsExactly((byte) 0x89, 'P', 'N', 'G');
    assertThat(answer.headers()).containsEntry("Content-Type", "image/png");
    assertThat(BinResponder.fileType("report.CSV")).isEqualTo("text/csv");
    assertThat(BinResponder.fileType("blob")).isEqualTo("application/octet-stream");
  }

  @Test
  void aMatchingRuleAnswersWithATemplatedBodyAndItsDelay() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"name": "Echo id", "conditions": [{"target": "path", "op": "starts_with", "value": "/orders"}],
          "answers": [{"status": 201, "delayMs": 1500, "template": true, "headers": {"X-Order": "{{jsonPath request.body '$.id'}}"},
                       "body": "{\\"received\\": \\"{{jsonPath request.body '$.id'}}\\", \\"path\\": \\"{{request.path}}\\"}"}]}]
        """);

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/orders", "{\"id\":\"ord_7\"}"));

    // Then
    assertThat(answer.status()).isEqualTo(201);
    assertThat(answer.delayMs()).isEqualTo(1500);
    assertThat(answer.by()).isEqualTo("rule");
    assertThat(answer.rule()).isEqualTo("Echo id");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("{\"received\": \"ord_7\", \"path\": \"/orders\"}");
    assertThat(answer.headers()).containsEntry("X-Order", "ord_7").containsEntry("Content-Type", "application/json");
  }

  @Test
  void weightedAnswersFollowTheRoll() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"answers": [{"weight": 1, "status": 200, "body": "fine"}, {"weight": 3, "status": 503, "body": "busy"}]}]
        """);
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
    BinResponder lowRoll = new BinResponder(templates, upstream, upstream, guard, tunnel, bound -> 0, System::currentTimeMillis);
    BinResponder highRoll = new BinResponder(templates, upstream, upstream, guard, tunnel, bound -> bound - 1, System::currentTimeMillis);

    // When / Then
    assertThat(lowRoll.answer("abcdefghijkm", settings, call("GET", "/", "")).status()).isEqualTo(200);
    assertThat(highRoll.answer("abcdefghijkm", settings, call("GET", "/", "")).status()).isEqualTo(503);
  }

  @Test
  void unmatchedCallsAreForwardedWithThePrefixAndExtraHeaders() throws Exception {
    // Given
    Bins.Settings settings = Bins.Settings.DEFAULT.withRouting(BinRouting.from(Jsons.MAPPER.readTree("""
        {"mode": "forward", "target": "https://api.example.com/", "pathPrefix": "/v1", "requestHeaders": {"Authorization": "Bearer t"},
         "responseHeaders": {"X-Via": "bin"}}
        """)));

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/charges", "{}"));

    // Then
    assertThat(answer.status()).isEqualTo(201);
    assertThat(answer.by()).isEqualTo("forward");
    assertThat(answer.headers()).containsEntry("X-Via", "bin").containsEntry("Content-Type", "application/json").doesNotContainKey("Transfer-Encoding");
    assertThat(sent).singleElement().asString()
        .startsWith("POST https://api.example.com/v1/charges?page=2 ")
        .contains("Authorization=Bearer t").contains("Content-Type=application/json")
        .doesNotContain("Host=").doesNotContain("X-Forwarded-For");
  }

  @Test
  void forwardingToAPrivateAddressIsRefused() throws Exception {
    // Given
    Bins.Settings settings = Bins.Settings.DEFAULT.withRouting(BinRouting.from(Jsons.MAPPER.readTree("{\"mode\": \"forward\", \"target\": \"http://intranet\"}")));

    // When
    BinResponder.Answer answer = responder("10.0.0.5").answer("abcdefghijkm", settings, call("GET", "/", ""));

    // Then
    assertThat(answer.status()).isEqualTo(502);
    assertThat(answer.by()).isEqualTo("forward-error");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).contains("private or local address");
    assertThat(sent).isEmpty();
  }

  @Test
  void tunnelRoutingReachesOnlyTheBinsOwnTunnel() throws Exception {
    // Given
    Bins.Settings settings = Bins.Settings.DEFAULT.withRouting(BinRouting.from(Jsons.MAPPER.readTree("{\"mode\": \"tunnel\", \"pathPrefix\": \"/hooks\"}")));

    // When
    BinResponder.Answer up = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/stripe", "{}"));
    tunnelUp = false;
    BinResponder.Answer down = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/stripe", "{}"));

    // Then
    assertThat(tunnelled).containsExactly("bin:abcdefghijkm POST tunnel://bin-abcdefghijkm/hooks/stripe?page=2");
    assertThat(up.status()).isEqualTo(200);
    assertThat(new String(up.body(), StandardCharsets.UTF_8)).isEqualTo("from laptop");
    assertThat(down.status()).isEqualTo(502);
    assertThat(down.by()).isEqualTo("tunnel-error");
  }

  @Test
  void turningRulesOffFallsBackToTheDefaultAnswer() throws Exception {
    // Given
    Bins.Settings settings = withRules("[{\"answers\": [{\"status\": 500}]}]").withRules(false, withRules("[{\"answers\": [{\"status\": 500}]}]").rules())
        .withReply(new Bins.Reply(202, "text/plain", "thanks", false));

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("GET", "/", ""));

    // Then
    assertThat(answer.status()).isEqualTo(202);
    assertThat(answer.by()).isEqualTo("default");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("thanks");
  }

  @Test
  void aDefaultAnswerCanFillInValuesFromEachRequest() throws Exception {
    // Given
    Bins.Settings settings = Bins.Settings.DEFAULT.withReply(new Bins.Reply(201, "application/json", "{\"got\": \"{{jsonPath request.body '$.id'}}\", \"path\": \"{{request.path}}\"}", true));

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/orders", "{\"id\": \"ord_7\"}"));

    // Then
    assertThat(answer.status()).isEqualTo(201);
    assertThat(answer.by()).isEqualTo("default");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("{\"got\": \"ord_7\", \"path\": \"/orders\"}");
  }

  @Test
  void aDefaultAnswerWithoutDynamicValuesIsSentAsWritten() throws Exception {
    // Given
    Bins.Settings settings = Bins.Settings.DEFAULT.withReply(new Bins.Reply(200, "text/plain", "{{request.path}}", false));

    // When
    BinResponder.Answer answer = responder("93.184.216.34").answer("abcdefghijkm", settings, call("POST", "/orders", "{}"));

    // Then
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("{{request.path}}");
  }

  @Test
  void anAiDraftBecomesAValidRule() {
    // Given
    BinDrafter drafter = new BinDrafter(() -> true, (system, user) -> """
        Here you go:
        ```json
        {"name": "Fail payments", "method": "POST", "conditions": [{"target": "json", "key": "$.type", "op": "equals", "value": "payment.failed"}],
         "answers": [{"status": 402, "body": "{\\"error\\": \\"card declined\\"}"}]}
        ```
        """);

    // When
    BinRule rule = drafter.draft("answer 402 when a payment fails");

    // Then
    assertThat(rule.name()).isEqualTo("Fail payments");
    assertThat(rule.conditions()).singleElement().satisfies(c -> assertThat(c.key()).isEqualTo("$.type"));
    assertThat(rule.answers().get(0).status()).isEqualTo(402);
    assertThat(new BinDrafter(() -> false, (s, u) -> "").available()).isFalse();
  }
}
