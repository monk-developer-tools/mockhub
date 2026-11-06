package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.Jsons;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.playground.TargetGuard;
import io.mockhub.support.MutableClock;
import io.mockhub.template.ValueHelpers;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinCalloutTest {

  @TempDir
  Path data;
  private final ExecutorService workers = Executors.newFixedThreadPool(2);
  private final TemplatePlayground templates = new TemplatePlayground(List.of(new ValueHelpers()), workers, 2000);
  private final List<String> sent = new ArrayList<>();
  private final List<String> bodies = new ArrayList<>();

  private final HttpSender upstream = (uri, method, headers, body, timeout, max) -> {
    sent.add(method + " " + uri + " " + headers.stream().map(h -> h.name() + "=" + h.value()).sorted().toList());
    bodies.add(body);
    return new HttpSender.Raw(202, List.of(new Header("Content-Type", "application/json"), new Header("Connection", "close")),
        "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8), false);
  };

  @AfterEach
  void stop() {
    workers.shutdownNow();
  }

  private BinResponder responder(String resolvedIp, int roll) {
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByName(resolvedIp)});
    return new BinResponder(templates, upstream, upstream, guard, null, bound -> Math.min(roll, bound - 1), System::currentTimeMillis);
  }

  private static BinMatcher.Call call(String method, String path, String body) {
    PublicRequest request = new PublicRequest(method, "/bins/abcdefghijkm" + path, "", Map.of(),
        Map.of("Content-Type", "application/json", "Host", "mockhub.example"), body, body.length(), "203.0.113.9");
    return new BinMatcher.Call(method, path, request);
  }

  private static Bins.Settings withRules(String rules) throws Exception {
    return Bins.Settings.DEFAULT.withRules(true, BinRule.listFrom(Jsons.MAPPER.readTree(rules)));
  }

  @Test
  void aSyncCalloutSendsTheTemplatedRequestWithAuthAndAnswersWithTheTargetsResponse() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"name": "Relay order", "conditions": [{"target": "path", "op": "template", "value": "/orders/:id"}],
          "callout": {"mode": "sync", "method": "PUT", "url": "https://api.example.com/v1/orders/{{pathParams.id}}?src=bin",
                      "payload": "custom", "body": "{\\"order\\": \\"{{pathParams.id}}\\", \\"amount\\": {{jsonPath request.body '$.amount'}} }",
                      "headers": {"X-Trace": "t-{{pathParams.id}}"}, "auth": {"type": "bearer", "value": "sk_test_1"}}}]
        """);

    // When
    BinResponder.Answer answer = responder("93.184.216.34", 0).answer("abcdefghijkm", settings, call("POST", "/orders/o7", "{\"amount\":12}"));

    // Then
    assertThat(sent).containsExactly("PUT https://api.example.com/v1/orders/o7?src=bin [Authorization=Bearer sk_test_1, Content-Type=application/json, User-Agent=MockHub-Bin-Callout, X-Trace=t-o7]");
    assertThat(bodies).containsExactly("{\"order\": \"o7\", \"amount\": 12 }");
    assertThat(answer.status()).isEqualTo(202);
    assertThat(answer.by()).isEqualTo("callout");
    assertThat(answer.rule()).isEqualTo("Relay order");
    assertThat(answer.headers()).containsEntry("Content-Type", "application/json").doesNotContainKey("Connection");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).isEqualTo("{\"accepted\":true}");
    Bins.Callout shown = answer.callout().orElseThrow().shown();
    assertThat(shown.state()).isEqualTo("sent");
    assertThat(shown.url()).isEqualTo("https://api.example.com/v1/orders/o7");
    assertThat(shown.status()).isEqualTo(202);
    assertThat(shown.response()).isEqualTo("{\"accepted\":true}");
  }

  @Test
  void anAsyncCalloutAnswersWithTheMockAtOnceAndPreparesTheOriginalPayloadForLater() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"name": "Ack then notify", "answers": [{"status": 202, "body": "{\\"queued\\":true}"}],
          "callout": {"mode": "async", "method": "same", "url": "https://hooks.example.com/notify", "payload": "original",
                      "auth": {"type": "basic", "name": "bot", "value": "s3cret"}, "minDelayMs": 1000, "maxDelayMs": 3000}}]
        """);
    BinResponder responder = responder("93.184.216.34", 500);

    // When
    BinResponder.Answer answer = responder.answer("abcdefghijkm", settings, call("PATCH", "/anything", "{\"n\":1}"));

    // Then
    assertThat(sent).isEmpty();
    assertThat(answer.status()).isEqualTo(202);
    assertThat(answer.by()).isEqualTo("rule");
    BinResponder.Followup followup = answer.callout().orElseThrow();
    assertThat(followup.waitMs()).isEqualTo(1500);
    assertThat(followup.shown().state()).isEqualTo("scheduled");
    assertThat(followup.shown().method()).isEqualTo("PATCH");

    // When
    Bins.Callout done = responder.callLater(followup.pending().orElseThrow(), followup.waitMs());

    // Then
    assertThat(sent).containsExactly("PATCH https://hooks.example.com/notify [Authorization=Basic Ym90OnMzY3JldA==, Content-Type=application/json, User-Agent=MockHub-Bin-Callout]");
    assertThat(bodies).containsExactly("{\"n\":1}");
    assertThat(done.mode()).isEqualTo("async");
    assertThat(done.state()).isEqualTo("sent");
    assertThat(done.status()).isEqualTo(202);
    assertThat(done.waitMs()).isEqualTo(1500);
  }

  @Test
  void aCalloutToAPrivateAddressFailsWithoutSendingAnything() throws Exception {
    // Given
    Bins.Settings settings = withRules("""
        [{"callout": {"url": "http://internal.example.com/admin", "auth": {"type": "header", "name": "X-Api-Key", "value": "k"}}}]
        """);

    // When
    BinResponder.Answer answer = responder("10.0.0.5", 0).answer("abcdefghijkm", settings, call("POST", "/x", "{}"));

    // Then
    assertThat(sent).isEmpty();
    assertThat(answer.status()).isEqualTo(502);
    assertThat(answer.by()).isEqualTo("callout-error");
    assertThat(new String(answer.body(), StandardCharsets.UTF_8)).contains("private or local address");
    assertThat(answer.callout().orElseThrow().shown().state()).isEqualTo("failed");
  }

  @Test
  void aBrokenCalloutTemplateFailsTheSyncAnswerButNotTheAsyncMock() throws Exception {
    // Given
    Bins.Settings sync = withRules("""
        [{"callout": {"url": "https://api.example.com/{{#if}}", "payload": "custom", "body": "x"}}]
        """);
    Bins.Settings async = withRules("""
        [{"answers": [{"status": 200, "body": "ok"}], "callout": {"mode": "async", "url": "https://api.example.com/{{#if}}"}}]
        """);

    // When
    BinResponder.Answer waited = responder("93.184.216.34", 0).answer("abcdefghijkm", sync, call("POST", "/x", "{}"));
    BinResponder.Answer mocked = responder("93.184.216.34", 0).answer("abcdefghijkm", async, call("POST", "/x", "{}"));

    // Then
    assertThat(waited.status()).isEqualTo(502);
    assertThat(new String(waited.body(), StandardCharsets.UTF_8)).contains("The callout URL template failed");
    assertThat(mocked.status()).isEqualTo(200);
    BinResponder.Followup followup = mocked.callout().orElseThrow();
    assertThat(followup.pending()).isEmpty();
    assertThat(followup.shown().state()).isEqualTo("failed");
    assertThat(followup.shown().error()).contains("The callout URL template failed");
    assertThat(sent).isEmpty();
  }

  @Test
  void calloutsAreCheckedWhenTheRuleIsSavedAndRoundTripThroughJson() throws Exception {
    // Given
    String rule = """
        [{"callout": {"mode": "async", "method": "post", "url": "https://h.example.com/x", "payload": "custom", "body": "{}",
                      "headers": {"X-A": "1"}, "auth": {"type": "header", "name": "X-Key", "value": "v"}, "minDelayMs": 0, "maxDelayMs": 60000},
          "answers": [{"status": 200}]}]
        """;

    // When
    List<BinRule> rules = BinRule.listFrom(Jsons.MAPPER.readTree(rule));
    List<BinRule> again = BinRule.listFrom(BinRule.toJson(rules));

    // Then
    assertThat(again).isEqualTo(rules);
    assertThat(again.get(0).callout().orElseThrow().method()).isEqualTo("POST");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"callout\": {\"url\": \"ftp://h.example.com\"}}]")))
        .hasMessage("Rule 1: Callout: The callout URL starts with http:// or https://");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"callout\": {\"mode\": \"async\", \"url\": \"https://h.example.com\"}}]")))
        .hasMessage("Rule 1: Give the rule an answer");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"answers\": [{}], \"callout\": {\"mode\": \"async\", \"url\": \"https://h.example.com\", \"minDelayMs\": 5000, \"maxDelayMs\": 1000}}]")))
        .hasMessage("Rule 1: Callout: The longest wait can't be shorter than the shortest");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"answers\": [{}], \"callout\": {\"mode\": \"async\", \"url\": \"https://h.example.com\", \"maxDelayMs\": 60001}}]")))
        .hasMessage("Rule 1: Callout: A callout waits up to 60 seconds before it is sent");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"callout\": {\"url\": \"https://h.example.com\", \"auth\": {\"type\": \"basic\", \"name\": \"a:b\"}}}]")))
        .hasMessage("Rule 1: Callout: Give the callout a user name without a colon");
    assertThatThrownBy(() -> BinRule.listFrom(Jsons.MAPPER.readTree("[{\"callout\": {\"url\": \"https://h.example.com\", \"method\": \"ANY\"}}]")))
        .hasMessageStartingWith("Rule 1: Callout: Call out with the same method");
  }

  @Test
  void aFinishedAsyncCalloutShowsOnItsRequestAndSurvivesARestart() {
    // Given
    MutableClock clock = new MutableClock(Instant.parse("2026-11-06T10:00:00Z"));
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    PublicRequest request = new PublicRequest("POST", "/bins/" + bin.id() + "/x", "", Map.of(), Map.of(), "{}", 2, "203.0.113.9");
    Bins.Outcome outcome = new Bins.Outcome("rule", "Ack", 202, 1, Map.of(), "{}", false);
    long seq = bins.record(bin.id(), request, "/x", "", outcome, Bins.Callout.scheduled("POST", "https://h.example.com/x", 2000));

    // When
    bins.calloutDone(bin.id(), seq, new Bins.Callout("async", "sent", "POST", "https://h.example.com/x", 204, 35, 2000, "", ""));

    // Then
    assertThat(bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow().requests()).singleElement()
        .satisfies(c -> assertThat(c.callout().state()).isEqualTo("sent"))
        .satisfies(c -> assertThat(c.callout().status()).isEqualTo(204));
    Bins reloaded = new Bins(data, clock);
    assertThat(reloaded.one(bin.id(), bin.token(), seq).orElseThrow().callout().status()).isEqualTo(204);
  }
}
