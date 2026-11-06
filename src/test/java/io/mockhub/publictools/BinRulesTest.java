package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BinRulesTest {

  private static JsonNode json(String text) {
    try {
      return Jsons.MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static BinRule rule(String text) {
    return BinRule.from(json(text));
  }

  private static BinMatcher.Call call(String method, String path, Map<String, String> headers, String body) {
    PublicRequest request = new PublicRequest(method, "/bins/abcdefghijkm" + path, "attempt=2", Map.of("attempt", List.of("2")), headers, body,
        body.length(), "203.0.113.9");
    return new BinMatcher.Call(method, path, request);
  }

  @Test
  void theFirstEnabledRuleThatMatchesAnswers() {
    // Given
    List<BinRule> rules = BinRule.listFrom(json("""
        [{"name": "Off", "enabled": false, "answers": [{"status": 500}]},
         {"name": "Failed payments", "method": "POST", "conditions": [{"target": "json", "key": "$.data.object.status", "op": "equals", "value": "failed"}],
          "answers": [{"status": 402}]},
         {"name": "Any payment", "method": "POST", "conditions": [{"target": "path", "op": "starts_with", "value": "/payments"}],
          "answers": [{"status": 200}]}]
        """));

    // When
    BinRule failed = BinMatcher.first(rules, call("POST", "/payments/hook", Map.of(), "{\"data\":{\"object\":{\"status\":\"failed\"}}}")).orElseThrow();
    BinRule paid = BinMatcher.first(rules, call("POST", "/payments/hook", Map.of(), "{\"data\":{\"object\":{\"status\":\"paid\"}}}")).orElseThrow();

    // Then
    assertThat(failed.name()).isEqualTo("Failed payments");
    assertThat(paid.name()).isEqualTo("Any payment");
    assertThat(BinMatcher.first(rules, call("GET", "/payments/hook", Map.of(), ""))).isEmpty();
  }

  @Test
  void conditionsCheckQueryHeadersBodyAndPatterns() {
    // Given
    BinMatcher.Call c = call("POST", "/orders/42", Map.of("X-GitHub-Event", "push"), "{\"items\":[{\"sku\":\"a-1\"}]}");

    // When / Then
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.QUERY, "attempt", BinRule.Op.EQUALS, "2"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.HEADER, "x-github-event", BinRule.Op.EQUALS, "push"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.BODY, "", BinRule.Op.CONTAINS, "a-1"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.PATH, "", BinRule.Op.MATCHES, "^/orders/\\d+$"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.JSON, "$.items[0].sku", BinRule.Op.EQUALS, "a-1"), c, c.json())).isTrue();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.JSON, "$.missing", BinRule.Op.EXISTS, ""), c, c.json())).isFalse();
    assertThat(BinMatcher.holds(new BinRule.Condition(BinRule.Target.HEADER, "X-Missing", BinRule.Op.EXISTS, ""), c, c.json())).isFalse();
  }

  @Test
  void aSlowPatternGivesUpInsteadOfHangingTheServer() {
    // Given
    String evil = "a".repeat(40) + "!";

    // When
    long started = System.nanoTime();
    boolean matched = BinMatcher.matches("^(a+)+$", evil);

    // Then
    assertThat(matched).isFalse();
    assertThat(System.nanoTime() - started).isLessThan(2_000_000_000L);
  }

  @Test
  void findsTheWebhookEventType() {
    // Given
    BinMatcher.Call github = call("POST", "/", Map.of("X-GitHub-Event", "pull_request"), "{\"action\":\"opened\"}");
    BinMatcher.Call stripe = call("POST", "/", Map.of(), "{\"type\":\"invoice.paid\",\"id\":\"evt_1\"}");
    BinMatcher.Call plain = call("POST", "/", Map.of(), "{\"amount\":4}");

    // When / Then
    assertThat(BinMatcher.eventOf(github.request(), github.json())).isEqualTo("pull_request");
    assertThat(BinMatcher.eventOf(stripe.request(), stripe.json())).isEqualTo("invoice.paid");
    assertThat(BinMatcher.eventOf(plain.request(), plain.json())).isEmpty();
  }

  @Test
  void refusesRulesThatCantWork() {
    // When / Then
    assertThatThrownBy(() -> rule("{\"answers\": []}")).hasMessageContaining("Give the rule an answer");
    assertThatThrownBy(() -> rule("{\"answers\": [{\"status\": 99}]}")).hasMessageContaining("100 to 599");
    assertThatThrownBy(() -> rule("{\"answers\": [{\"delayMs\": 20000}]}")).hasMessageContaining("15 seconds");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"target\": \"json\", \"key\": \"event\"}], \"answers\": [{}]}")).hasMessageContaining("start with $");
    assertThatThrownBy(() -> rule("{\"conditions\": [{\"op\": \"matches\", \"value\": \"(\"}], \"answers\": [{}]}")).hasMessageContaining("regular expression");
    assertThatThrownBy(() -> BinRule.listFrom(json("[{\"answers\": [{}]}, {\"method\": \"FETCH\", \"answers\": [{}]}]"))).hasMessageStartingWith("Rule 2:");
  }

  @Test
  void aRuleSurvivesARoundTripThroughJson() {
    // Given
    BinRule original = rule("""
        {"id": "r1", "name": "Slow", "method": "POST", "conditions": [{"target": "header", "key": "X-Retry", "op": "exists"}],
         "answers": [{"weight": 3, "status": 200, "delayMs": 5000, "headers": {"X-A": "1"}, "body": "ok", "template": true},
                     {"weight": 1, "status": 503, "body": "down"}]}
        """);

    // When
    BinRule copy = BinRule.from(original.toJson());

    // Then
    assertThat(copy).isEqualTo(original);
  }
}
